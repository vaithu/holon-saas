package com.holonplatform.multitenant.data;

/**
 * Describes how tenant data is physically stored at the database level.
 *
 * <p>Choose the strategy that matches your SaaS scalability and isolation requirements:
 *
 * <table border="1">
 *   <tr><th>Strategy</th><th>Isolation</th><th>Operational complexity</th><th>Best for</th></tr>
 *   <tr><td>{@link #SHARED}</td><td>Low</td><td>Low</td><td>SMB SaaS, free tiers</td></tr>
 *   <tr><td>{@link #SCHEMA_PER_TENANT}</td><td>High</td><td>Medium</td><td>Regulated industries, enterprise SaaS</td></tr>
 *   <tr><td>{@link #DATABASE_PER_TENANT}</td><td>Highest</td><td>High</td><td>Enterprise, highest data compliance</td></tr>
 * </table>
 */
public enum TenantSchemaStrategy {

	/**
	 * All tenants share a single schema. Tenant isolation is enforced at the
	 * application level via a {@code tenant_id} discriminator column.
	 *
	 * <p>No per-tenant DataSource management is required — the application's default
	 * DataSource is used as-is.
	 */
	SHARED,

	/**
	 * Each tenant owns a dedicated schema within the same database instance.
	 *
	 * <p>The framework maintains one HikariCP connection pool per active tenant.
	 * Each pool's {@code connectionInitSql} switches the schema at connection
	 * checkout time, so no cross-tenant schema leakage can occur.
	 *
	 * <p>This is the recommended default for most SaaS applications.
	 */
	SCHEMA_PER_TENANT,

	/**
	 * Each tenant owns a dedicated database instance (or catalog).
	 *
	 * <p>Requires the application to provide separate JDBC connection details per
	 * tenant via a {@link com.holonplatform.jdbc.TenantDataSourceProvider} implementation.
	 */
	DATABASE_PER_TENANT
}

