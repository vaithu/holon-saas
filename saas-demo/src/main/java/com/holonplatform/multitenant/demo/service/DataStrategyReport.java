package com.holonplatform.multitenant.demo.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.TenantSchemaStrategy;
import com.holonplatform.multitenant.data.config.TenantDataProperties;

/**
 * F22 — what the configured data strategy is actually doing underneath every query.
 *
 * <h2>Why this is a report rather than a switch</h2>
 * <p>{@code TenantAwareDataSource} is handed its {@link TenantSchemaStrategy} once, when the
 * bean is built, and there is no per-tenant override: the strategy is a property of the
 * deployment, not of a request. Offering a button to change it at runtime would misrepresent
 * how the framework works. What can honestly be shown is the effect of the strategy that is
 * running — which schema a tenant's connections land in, and which tenants currently hold a
 * pool. The other two strategies are covered by tests, where a registry can be substituted.
 *
 * <h2>The three strategies, and what each costs</h2>
 * <ul>
 *   <li>{@code SCHEMA_PER_TENANT} (this demo): one pool per tenant, connections initialised
 *       into {@code tenant_&lt;id&gt;}. Strong isolation, and a pool per tenant to pay for —
 *       hence the bounded registry.</li>
 *   <li>{@code DATABASE_PER_TENANT}: one pool per tenant against a different database entirely.
 *       The routing code is identical; only the provider differs.</li>
 *   <li>{@code SHARED}: every tenant on one pool, isolation expressed as a {@code tenant_id}
 *       column. The framework adds <em>no</em> query predicate for this — there is no filter or
 *       interceptor — so with {@code SHARED} the isolation is entirely the application's to
 *       write and entirely the application's to get wrong.</li>
 * </ul>
 */
@Service
public class DataStrategyReport {

    private final TenantDataProperties properties;

    private final ObjectProvider<TenantDataSourceRegistry> registry;

    public DataStrategyReport(TenantDataProperties properties,
                              ObjectProvider<TenantDataSourceRegistry> registry) {
        this.properties = properties;
        this.registry   = registry;
    }

    public TenantSchemaStrategy strategy() {
        return properties.getSchemaStrategy();
    }

    /** The schema name the current tenant's connections are initialised into. */
    public String schemaForCurrentTenant() {
        return TenantContext.currentTenantId()
                .map(schemaNameResolver()::resolveSchema)
                .orElse("(no tenant bound)");
    }

    private SchemaNameResolver schemaNameResolver() {
        return SchemaNameResolver.withPrefixAndSuffix(
                properties.getSchemaPrefix() == null ? "" : properties.getSchemaPrefix(),
                properties.getSchemaSuffix() == null ? "" : properties.getSchemaSuffix());
    }

    /** How many tenants currently hold a connection pool, and which ones. */
    public int pooledTenantCount() {
        TenantDataSourceRegistry current = registry.getIfAvailable();
        return current == null ? 0 : current.size();
    }

    public List<String> pooledTenantIds() {
        TenantDataSourceRegistry current = registry.getIfAvailable();
        return current == null ? List.of() : current.activeTenantIds().stream().sorted().toList();
    }

    /**
     * Asks the database itself which schema the connection landed in, rather than repeating the
     * resolver's arithmetic. This is the only assertion that can distinguish a strategy that is
     * configured from one that is working.
     */
    public String observedSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String schema = connection.getSchema();
            return schema == null ? "(driver reports none)" : schema;
        } catch (SQLException e) {
            return "(failed: " + e.getMessage() + ")";
        }
    }
}
