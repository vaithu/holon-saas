package com.holonplatform.multitenant.data.datasource;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.TenantSchemaStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;

/**
 * A {@link DataSource} implementation that routes each {@link #getConnection()} call
 * to the correct tenant-specific pool based on the current {@link TenantContext}.
 *
 * <h2>Routing strategies</h2>
 * <ul>
 *   <li><strong>{@link TenantSchemaStrategy#SHARED}</strong> — always routes to
 *       {@code registry.getDataSource(null)}; the registered
 *       {@code com.holonplatform.jdbc.TenantDataSourceProvider} is expected to return the
 *       same DataSource regardless of tenant id for this strategy. Tenant isolation is
 *       the application's responsibility via a {@code tenant_id} column.</li>
 *   <li><strong>{@link TenantSchemaStrategy#SCHEMA_PER_TENANT}</strong> and
 *       <strong>{@link TenantSchemaStrategy#DATABASE_PER_TENANT}</strong> — looks up the
 *       current tenant from {@link TenantContext} and delegates to the matching pool in
 *       {@link TenantDataSourceRegistry}.</li>
 * </ul>
 *
 * <h2>No-tenant fallback</h2>
 * <p>When {@link TenantContext} is empty (e.g. background jobs, migration tasks,
 * platform-schema lookups), {@code registry.getDataSource(null)} is used as a fallback —
 * following {@code com.holonplatform.jdbc.TenantDataSourceProvider}'s own
 * {@code null}-means-bootstrap convention.
 *
 * <h2>Registration</h2>
 * <p>Register this bean as {@code @Primary DataSource} so that Spring, JPA providers,
 * Flyway, and other framework tools all use it automatically:
 * <pre>{@code
 * @Bean @Primary
 * public DataSource dataSource(TenantAwareDataSource router) {
 *     return router;
 * }
 * }</pre>
 */
public class TenantAwareDataSource implements DataSource {

	private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);

	private final TenantSchemaStrategy     strategy;
	private final TenantDataSourceRegistry registry;

	/**
	 * Full constructor.
	 *
	 * @param strategy data isolation strategy; must not be null
	 * @param registry per-tenant pool registry; must not be null. For
	 *                 {@link TenantSchemaStrategy#SHARED}, the registered
	 *                 {@code com.holonplatform.jdbc.TenantDataSourceProvider} is expected to
	 *                 return the same DataSource regardless of tenant id (including
	 *                 {@code null}) — no special-casing is needed here.
	 */
	public TenantAwareDataSource(TenantSchemaStrategy strategy, TenantDataSourceRegistry registry) {
		this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
	}

	// -------------------------------------------------------------------------
	// DataSource implementation
	// -------------------------------------------------------------------------

	@Override
	public Connection getConnection() throws SQLException {
		return resolveDataSource().getConnection();
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return resolveDataSource().getConnection(username, password);
	}

	@Override
	public PrintWriter getLogWriter() throws SQLException {
		return resolveDataSource().getLogWriter();
	}

	@Override
	public void setLogWriter(PrintWriter out) throws SQLException {
		resolveDataSource().setLogWriter(out);
	}

	@Override
	public void setLoginTimeout(int seconds) throws SQLException {
		resolveDataSource().setLoginTimeout(seconds);
	}

	@Override
	public int getLoginTimeout() throws SQLException {
		return resolveDataSource().getLoginTimeout();
	}

	@Override
	public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
		return resolveDataSource().getParentLogger();
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return resolveDataSource().unwrap(iface);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return resolveDataSource().isWrapperFor(iface);
	}

	// -------------------------------------------------------------------------
	// Routing logic
	// -------------------------------------------------------------------------

	/**
	 * Determines which DataSource to use for the current thread:
	 * <ul>
	 *   <li>SHARED — {@code registry.getDataSource(null)}; the provider is expected
	 *       to return the same DataSource regardless of tenant id for this strategy</li>
	 *   <li>SCHEMA / DATABASE — the tenant's pool from the registry; falls back to
	 *       {@code registry.getDataSource(null)} (the platform/bootstrap connection)
	 *       when no tenant context is present (e.g. admin operations, startup migrations)</li>
	 * </ul>
	 */
	DataSource resolveDataSource() {
		if (strategy == TenantSchemaStrategy.SHARED) {
			return registry.getDataSource(null);
		}

		// peekTenantId() avoids allocating Optional objects on this hot JDBC path.
		// At 10k concurrent users each issuing multiple queries, this saves millions
		// of short-lived Optional allocations per second vs currentTenantId().
		String tenantId = TenantContext.peekTenantId();
		if (tenantId == null) {
			log.debug("No tenant in context — using the platform/bootstrap connection as fallback");
		} else {
			log.trace("Routing to DataSource for tenant '{}'", tenantId);
		}
		return registry.getDataSource(tenantId);
	}

	/** Returns the configured strategy. */
	public TenantSchemaStrategy getStrategy() {
		return strategy;
	}

	/** Returns the registry backing this router. */
	public TenantDataSourceRegistry getRegistry() {
		return registry;
	}
}

