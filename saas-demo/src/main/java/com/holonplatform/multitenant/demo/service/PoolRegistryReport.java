package com.holonplatform.multitenant.demo.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.config.TenantDataProperties;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * F24 — the bounded pool registry, and what happens when a deployment has more tenants than
 * it has room to keep pools for.
 *
 * <h2>The problem the registry exists to solve</h2>
 * <p>{@code SCHEMA_PER_TENANT} gives every tenant its own connection pool. That is what makes
 * the isolation real, and it is also what makes the memory cost grow with the customer list: a
 * few thousand tenants each holding an idle Hikari pool will exhaust a server long before the
 * database notices. {@code BoundedTenantDataSourceRegistry} caps the number of live pools and
 * closes the ones it no longer has room for, so that memory tracks <em>concurrent activity</em>
 * rather than total signups.
 *
 * <h2>Why the eviction policy is the whole story</h2>
 * <p>Capping is easy; choosing the victim is not. This demo runs with a deliberately tiny cap
 * ({@code holon.tenant.data.max-pools}) so the choice is visible after a handful of clicks.
 * Evicting by creation order looks reasonable until you notice that the tenant who signed up
 * first is usually the one sending the most traffic — so it gets evicted, re-created by the
 * next request, and evicted again, thrashing pools exactly when the system is at capacity.
 * Evicting by <em>last use</em> keeps the pools that are actually being used, which is what the
 * registry now does; {@link #touch} exists so that can be demonstrated rather than asserted.
 *
 * <h2>Reading the numbers</h2>
 * <p>{@link #pooledCount()} can exceed {@link #pooledTenantIds()}{@code .size()} by one. That is
 * not a bug: the registry also caches a pool for the {@code null} tenant — the bootstrap
 * connection used for platform-level work before any tenant is bound — and that pool counts
 * against the cap while deliberately not being reported as a tenant.
 */
@Service
public class PoolRegistryReport {

    private static final String ACTIVE_GAUGE = "holon.tenant.datasource.pools.active";

    private final TenantDataProperties properties;

    private final ObjectProvider<TenantDataSourceRegistry> registry;

    private final ObjectProvider<MeterRegistry> meters;

    public PoolRegistryReport(TenantDataProperties properties,
                              ObjectProvider<TenantDataSourceRegistry> registry,
                              ObjectProvider<MeterRegistry> meters) {
        this.properties = properties;
        this.registry   = registry;
        this.meters     = meters;
    }

    /** The configured ceiling on simultaneously-open pools. */
    public int maxPools() {
        return properties.getMaxPools();
    }

    /** Pools currently held, including the bootstrap pool for the {@code null} tenant. */
    public int pooledCount() {
        TenantDataSourceRegistry current = registry.getIfAvailable();
        return current == null ? 0 : current.size();
    }

    /** The real tenants holding a pool; excludes the bootstrap pool. */
    public List<String> pooledTenantIds() {
        TenantDataSourceRegistry current = registry.getIfAvailable();
        return current == null ? List.of() : current.activeTenantIds().stream().sorted().toList();
    }

    /**
     * The same count as seen by the metrics backend, so the gauge can be checked against the
     * registry rather than assumed to agree with it.
     */
    public String activeGauge() {
        MeterRegistry meterRegistry = meters.getIfAvailable();
        if (meterRegistry == null) {
            return "(no meter registry)";
        }
        var gauge = meterRegistry.find(ACTIVE_GAUGE).gauge();
        return gauge == null ? "(gauge not registered)" : String.valueOf((int) gauge.value());
    }

    /**
     * Uses a tenant's pool, creating it if it is not cached and marking it as recently used if
     * it is. A connection is actually opened, so a pool that was evicted and silently failed to
     * be re-creatable would surface here rather than look like a success.
     *
     * @return a short description of what happened, for display
     */
    public String touch(String tenantId) {
        TenantDataSourceRegistry current = registry.getIfAvailable();
        if (current == null) {
            return tenantId + " → no registry available";
        }
        boolean wasCached = current.activeTenantIds().contains(tenantId);
        DataSource dataSource = current.getDataSource(tenantId);
        try (Connection connection = dataSource.getConnection()) {
            String schema = connection.getSchema();
            return tenantId + " → " + (wasCached ? "reused cached pool" : "created new pool")
                    + ", connected to schema " + (schema == null ? "(none)" : schema);
        } catch (SQLException e) {
            return tenantId + " → FAILED to connect: " + e.getMessage();
        }
    }
}
