package com.holonplatform.multitenant.data.config;

import com.holonplatform.multitenant.data.DatabaseDialect;
import com.holonplatform.multitenant.data.TenantSchemaStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the multi-tenant data layer.
 *
 * <p>All properties are prefixed with {@code holon.tenant.data} in
 * {@code application.yml} / {@code application.properties}.
 *
 * <h2>Minimal YAML (schema-per-tenant on PostgreSQL)</h2>
 * <pre>{@code
 * holon:
 *   tenant:
 *     data:
 *       schema-strategy: SCHEMA_PER_TENANT
 *       dialect: POSTGRESQL
 *       schema-prefix: tenant_
 *       max-pools: 200
 *       pool-max-size: 5
 * }</pre>
 *
 * <h2>Shared-schema (simplest)</h2>
 * <pre>{@code
 * holon:
 *   tenant:
 *     data:
 *       schema-strategy: SHARED
 * }</pre>
 */
@ConfigurationProperties(prefix = "holon.tenant.data")
public class TenantDataProperties {

	/** Data isolation strategy. Default: {@link TenantSchemaStrategy#SCHEMA_PER_TENANT}. */
	private TenantSchemaStrategy schemaStrategy = TenantSchemaStrategy.SCHEMA_PER_TENANT;

	/** SQL dialect for the schema-switching connection init SQL. Default: POSTGRESQL. */
	private DatabaseDialect dialect = DatabaseDialect.POSTGRESQL;

	/** Schema name prefix applied by the default {@link com.holonplatform.multitenant.data.SchemaNameResolver}. Default: {@code "tenant_"}. */
	private String schemaPrefix = "tenant_";

	/** Schema name suffix. Default: empty string. */
	private String schemaSuffix = "";

	/** Maximum number of simultaneously cached per-tenant HikariCP pools. Default: 200. */
	private int maxPools = 200;

	/** Maximum connections per tenant HikariCP pool. Default: 5. */
	private int poolMaxSize = 5;

	/** Idle timeout (ms) for per-tenant pool connections. Default: 300 000 ms (5 min). */
	private long poolIdleTimeoutMs = 300_000L;

	/** Max lifetime (ms) for per-tenant pool connections. Default: 1 800 000 ms (30 min). */
	private long poolMaxLifetimeMs = 1_800_000L;

	/**
	 * Flyway migration SQL locations for tenant schemas.
	 * Default: {@code classpath:db/tenant-migration}.
	 */
	private String[] migrationLocations = {"classpath:db/tenant-migration"};

	/** Whether to auto-register the tenant DataSource as the primary {@code DataSource} bean. Default: true. */
	private boolean primaryDataSource = true;

	/** Whether to create the tenant schema automatically on first access (requires CREATE SCHEMA privilege). Default: true. */
	private boolean autoCreateSchema = true;

	// -------------------------------------------------------------------------
	// Getters / setters
	// -------------------------------------------------------------------------

	public TenantSchemaStrategy getSchemaStrategy() { return schemaStrategy; }
	public void setSchemaStrategy(TenantSchemaStrategy schemaStrategy) { this.schemaStrategy = schemaStrategy; }

	public DatabaseDialect getDialect() { return dialect; }
	public void setDialect(DatabaseDialect dialect) { this.dialect = dialect; }

	public String getSchemaPrefix() { return schemaPrefix; }
	public void setSchemaPrefix(String schemaPrefix) { this.schemaPrefix = schemaPrefix; }

	public String getSchemaSuffix() { return schemaSuffix; }
	public void setSchemaSuffix(String schemaSuffix) { this.schemaSuffix = schemaSuffix; }

	public int getMaxPools() { return maxPools; }
	public void setMaxPools(int maxPools) { this.maxPools = maxPools; }

	public int getPoolMaxSize() { return poolMaxSize; }
	public void setPoolMaxSize(int poolMaxSize) { this.poolMaxSize = poolMaxSize; }

	public long getPoolIdleTimeoutMs() { return poolIdleTimeoutMs; }
	public void setPoolIdleTimeoutMs(long poolIdleTimeoutMs) { this.poolIdleTimeoutMs = poolIdleTimeoutMs; }

	public long getPoolMaxLifetimeMs() { return poolMaxLifetimeMs; }
	public void setPoolMaxLifetimeMs(long poolMaxLifetimeMs) { this.poolMaxLifetimeMs = poolMaxLifetimeMs; }

	public String[] getMigrationLocations() { return migrationLocations; }
	public void setMigrationLocations(String[] migrationLocations) { this.migrationLocations = migrationLocations; }

	public boolean isPrimaryDataSource() { return primaryDataSource; }
	public void setPrimaryDataSource(boolean primaryDataSource) { this.primaryDataSource = primaryDataSource; }

	public boolean isAutoCreateSchema() { return autoCreateSchema; }
	public void setAutoCreateSchema(boolean autoCreateSchema) { this.autoCreateSchema = autoCreateSchema; }
}

