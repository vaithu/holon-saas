package com.holonplatform.multitenant.data;

import javax.sql.DataSource;
import java.util.Set;

/**
 * Manages the lifecycle of per-tenant {@link DataSource} instances.
 *
 * <p>Implementations maintain a bounded pool of active DataSources, evicting the
 * least-recently-used entries when capacity is exceeded.
 *
 * <p>The primary implementation is
 * {@link com.holonplatform.multitenant.data.datasource.BoundedTenantDataSourceRegistry}.
 */
public interface TenantDataSourceRegistry extends AutoCloseable {

	/**
	 * Returns the {@link DataSource} for the given tenant, creating it on first access.
	 *
	 * @param tenantId the tenant identifier, or {@code null} for the platform/bootstrap
	 *                 connection (see {@code com.holonplatform.jdbc.TenantDataSourceProvider}'s
	 *                 own {@code null}-means-bootstrap convention)
	 * @return the DataSource for the tenant; never null
	 * @throws TenantDataSourceCreationException if the DataSource cannot be created
	 */
	DataSource getDataSource(String tenantId);

	/**
	 * Explicitly removes and closes the DataSource for the given tenant.
	 * Safe to call even if no DataSource exists for the tenant.
	 *
	 * @param tenantId the tenant identifier; must not be null
	 */
	void evict(String tenantId);

	/**
	 * Returns the set of currently cached tenant IDs.
	 * The returned set is a snapshot; it may change after this call returns.
	 */
	Set<String> activeTenantIds();

	/**
	 * Returns the current number of cached tenant DataSources.
	 */
	int size();

	/** Closes and disposes all registered DataSources. */
	@Override
	void close();
}

