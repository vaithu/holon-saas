package com.holonplatform.multitenant.data.datasource;

import com.holonplatform.jdbc.TenantDataSourceProvider;
import com.holonplatform.multitenant.data.SchemaInitSqlProvider;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceCreationException;
import com.holonplatform.multitenant.data.config.TenantDataProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.micrometer.MicrometerMetricsTrackerFactory;
import io.micrometer.core.instrument.MeterRegistry;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * A {@link TenantDataSourceProvider} for the {@code SCHEMA_PER_TENANT} strategy.
 *
 * <p>Creates a dedicated HikariCP pool for each tenant. All pools connect to
 * the <em>same</em> database but use the dialect-specific connection-init SQL
 * (e.g. {@code SET search_path TO tenant_acme} for PostgreSQL) to switch the
 * active schema at checkout time.
 *
 * <h2>Pool isolation guarantee</h2>
 * <p>Because each tenant has its own pool, connections never escape to a different
 * tenant's schema — even under concurrent load or connection recycling.
 *
 * <h2>Bootstrap / no-tenant connections</h2>
 * <p>{@link #getDataSource(String)} accepts {@code null} — following
 * {@code com.holonplatform.jdbc.TenantDataSourceProvider}'s own convention — to mean
 * "the platform/bootstrap connection" (e.g. reading the platform-schema {@code tenant}
 * table, or running startup Flyway migrations). No {@code connectionInitSql} is applied
 * in that case, so the connection lands on the database's default schema.
 *
 * <h2>Metrics (optional)</h2>
 * <p>When {@link #setMeterRegistry(MeterRegistry)} is called (auto-wired by
 * {@code TenantDataAutoConfiguration} whenever a {@code MeterRegistry} bean is present —
 * e.g. via {@code spring-boot-starter-actuator}), every pool created afterwards publishes
 * HikariCP's standard {@code hikaricp.*} metrics per tenant pool. Without this, hundreds of
 * dynamically-created per-tenant pools would otherwise be a complete observability blind
 * spot — pool exhaustion or connection starvation for a specific tenant would be invisible
 * until it surfaced as request timeouts.
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * @Bean
 * public TenantDataSourceProvider tenantDataSourceProvider(
 *         HikariDataSource master,
 *         SchemaNameResolver schemaNames,
 *         SchemaInitSqlProvider initSql,
 *         TenantDataProperties properties) {
 *
 *     return SchemaPerTenantDataSourceProvider.from(master, schemaNames, initSql, properties);
 * }
 * }</pre>
 */
public class SchemaPerTenantDataSourceProvider implements TenantDataSourceProvider {

	private final HikariConfig         baseConfig;
	private final SchemaNameResolver   schemaNameResolver;
	private final SchemaInitSqlProvider initSqlProvider;

	/** Maximum connections per tenant pool. Overridable via {@link #setMaxPoolSize(int)}. */
	private int  maxPoolSize  = 5;
	/** Idle timeout (ms) for pool connections. Overridable via {@link #setIdleTimeoutMs(long)}. */
	private long idleTimeoutMs = 300_000L;
	/** Max lifetime (ms) for pool connections. Overridable via {@link #setMaxLifetimeMs(long)}. */
	private long maxLifetimeMs = 1_800_000L;
	/**
	 * Optional Micrometer registry. When set (see {@link #setMeterRegistry(MeterRegistry)}),
	 * every pool created from this point on is wired with HikariCP's own
	 * {@link MicrometerMetricsTrackerFactory}, exposing the standard {@code hikaricp.*} metrics
	 * (active/idle/pending connections, connection acquisition/usage timers) per tenant pool
	 * (tagged by {@code pool = tenant-<tenantId>}) through whatever {@code MeterRegistry} the
	 * application already has (Actuator/Prometheus, etc.) — no bespoke metrics code needed.
	 */
	private MeterRegistry meterRegistry;

	/**
	 * Creates a provider that derives pool configuration from the supplied master
	 * HikariDataSource.
	 *
	 * @param masterDataSource  the base datasource whose connection config is inherited;
	 *                          must be a {@link HikariDataSource}
	 * @param schemaNameResolver maps tenant IDs to schema names
	 * @param initSqlProvider   generates the schema-switch SQL
	 */
	public SchemaPerTenantDataSourceProvider(HikariDataSource masterDataSource,
			SchemaNameResolver schemaNameResolver,
			SchemaInitSqlProvider initSqlProvider) {
		Objects.requireNonNull(masterDataSource, "masterDataSource must not be null");
		this.schemaNameResolver = Objects.requireNonNull(schemaNameResolver, "schemaNameResolver must not be null");
		this.initSqlProvider    = Objects.requireNonNull(initSqlProvider, "initSqlProvider must not be null");

		// Derive base config from the master pool
		this.baseConfig = new HikariConfig();
		baseConfig.setJdbcUrl(masterDataSource.getJdbcUrl());
		baseConfig.setUsername(masterDataSource.getUsername());
		baseConfig.setPassword(masterDataSource.getPassword());
		baseConfig.setDriverClassName(masterDataSource.getDriverClassName());
	}

	/**
	 * Creates a provider using an explicit JDBC URL, username, and password.
	 *
	 * @param jdbcUrl           base JDBC connection URL
	 * @param username          database username
	 * @param password          database password
	 * @param schemaNameResolver resolves tenant IDs to schema names
	 * @param initSqlProvider   generates the schema-switch SQL
	 */
	public SchemaPerTenantDataSourceProvider(String jdbcUrl,
			String username,
			String password,
			SchemaNameResolver schemaNameResolver,
			SchemaInitSqlProvider initSqlProvider) {
		Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
		this.schemaNameResolver = Objects.requireNonNull(schemaNameResolver, "schemaNameResolver must not be null");
		this.initSqlProvider    = Objects.requireNonNull(initSqlProvider, "initSqlProvider must not be null");

		this.baseConfig = new HikariConfig();
		baseConfig.setJdbcUrl(jdbcUrl);
		baseConfig.setUsername(username);
		baseConfig.setPassword(password);
	}

	/**
	 * Convenience factory that also applies the pool-tuning values declared in
	 * {@link TenantDataProperties} ({@code poolMaxSize}, {@code poolIdleTimeoutMs},
	 * {@code poolMaxLifetimeMs}), so applications don't have to call each setter by hand.
	 *
	 * @param masterDataSource   the base datasource whose connection config is inherited
	 * @param schemaNameResolver maps tenant IDs to schema names
	 * @param initSqlProvider    generates the schema-switch SQL
	 * @param properties         pool-tuning configuration
	 * @return a configured provider
	 */
	public static SchemaPerTenantDataSourceProvider from(
			HikariDataSource masterDataSource,
			SchemaNameResolver schemaNameResolver,
			SchemaInitSqlProvider initSqlProvider,
			TenantDataProperties properties) {
		Objects.requireNonNull(properties, "properties must not be null");
		SchemaPerTenantDataSourceProvider provider =
				new SchemaPerTenantDataSourceProvider(masterDataSource, schemaNameResolver, initSqlProvider);
		provider.setMaxPoolSize(properties.getPoolMaxSize());
		provider.setIdleTimeoutMs(properties.getPoolIdleTimeoutMs());
		provider.setMaxLifetimeMs(properties.getPoolMaxLifetimeMs());
		return provider;
	}

	/**
	 * Sets the maximum number of connections in each per-tenant pool.
	 * Default: {@code 5}. For 10k users with 1000 active tenants this yields ~5000
	 * total connections; use PgBouncer to multiplex these.
	 */
	public void setMaxPoolSize(int maxPoolSize) {
		this.maxPoolSize = maxPoolSize;
	}

	/** Sets the idle timeout (ms) for pool connections above the minimum idle count. Default: 300 000 ms. */
	public void setIdleTimeoutMs(long idleTimeoutMs) {
		this.idleTimeoutMs = idleTimeoutMs;
	}

	/** Sets the max lifetime (ms) for pool connections — a hard cap avoiding DB-side timeouts. Default: 1 800 000 ms. */
	public void setMaxLifetimeMs(long maxLifetimeMs) {
		this.maxLifetimeMs = maxLifetimeMs;
	}

	/**
	 * Sets the {@link MeterRegistry} used to publish per-pool HikariCP metrics.
	 * When not set (the default), pools are created without Micrometer instrumentation —
	 * exactly today's behavior.
	 *
	 * @param meterRegistry the registry to publish {@code hikaricp.*} metrics to, or
	 *                      {@code null} to disable metrics again
	 */
	public void setMeterRegistry(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public DataSource getDataSource(String tenantId) {
		try {
			HikariConfig config = new HikariConfig();
			config.setJdbcUrl(baseConfig.getJdbcUrl());
			config.setUsername(baseConfig.getUsername());
			config.setPassword(baseConfig.getPassword());
			if (baseConfig.getDriverClassName() != null) {
				config.setDriverClassName(baseConfig.getDriverClassName());
			}

			// tenantId == null means "platform/bootstrap connection" (see class javadoc) —
			// no schema-switch SQL is applied, the connection lands on the default schema.
			if (tenantId != null) {
				String schemaName = schemaNameResolver.resolveSchema(tenantId);
				config.setConnectionInitSql(initSqlProvider.buildInitSql(schemaName));
				config.setPoolName("tenant-" + tenantId);
			} else {
				config.setPoolName("platform-master");
			}

			// ── Pool sizing ──────────────────────────────────────────────────
			// Start with 1 idle connection so the first user gets a connection
			// immediately (no pool warm-up delay). HikariCP scales up to
			// maxPoolSize on demand, which is far better than blocking the
			// very first request while maxPoolSize connections are established.
			config.setMaximumPoolSize(maxPoolSize);
			config.setMinimumIdle(1);

			// ── Timeouts ─────────────────────────────────────────────────────
			// Fail fast under overload rather than queuing indefinitely.
			config.setConnectionTimeout(30_000);   // 30 s — max wait for connection from pool
			config.setValidationTimeout(5_000);    //  5 s — max time to validate a connection
			// Aggressively reclaim truly idle connections.
			config.setIdleTimeout(idleTimeoutMs);
			config.setMaxLifetime(maxLifetimeMs);

			// ── Keep-alive ───────────────────────────────────────────────────
			// Send a cheap keep-alive query when a connection has been idle for
			// 60 s to prevent the DB / LB from silently dropping it.
			config.setKeepaliveTime(60_000);

			// ── Metrics (optional) ───────────────────────────────────────────
			// If a MeterRegistry is configured, every pool self-reports the standard
			// hikaricp.* metrics (active/idle/pending connections, acquisition/usage
			// timers) tagged with this pool's name — this is what makes per-tenant pool
			// exhaustion/starvation visible instead of a silent capacity ceiling.
			if (meterRegistry != null) {
				config.setMetricsTrackerFactory(new MicrometerMetricsTrackerFactory(meterRegistry));
			}

			return new HikariDataSource(config);
		} catch (Exception e) {
			throw new TenantDataSourceCreationException(tenantId,
					"Failed to create DataSource for tenant '" + tenantId + "'", e);
		}
	}
}

