package com.holonplatform.multitenant.data;

import java.util.Objects;

/**
 * Provides the connection-initialisation SQL that is executed for every connection
 * in a per-tenant HikariCP pool, switching the active schema.
 *
 * <p>The default implementation delegates to {@link DatabaseDialect#buildInitSql(String)}.
 *
 * <p>Usage in auto-configuration:
 * <pre>{@code
 * SchemaInitSqlProvider provider = SchemaInitSqlProvider.forDialect(DatabaseDialect.POSTGRESQL);
 * String initSql = provider.buildInitSql("tenant_acme"); // → "SET search_path TO tenant_acme"
 * }</pre>
 */
@FunctionalInterface
public interface SchemaInitSqlProvider {

	/**
	 * Returns the SQL string to be executed as {@code connectionInitSql} for the
	 * HikariCP pool of the given schema.
	 *
	 * @param schemaName the database schema name; never null
	 * @return the ready-to-execute SQL; never null or blank
	 */
	String buildInitSql(String schemaName);

	// -------------------------------------------------------------------------
	// Factory methods
	// -------------------------------------------------------------------------

	/** Creates a provider for the given {@link DatabaseDialect}. */
	static SchemaInitSqlProvider forDialect(DatabaseDialect dialect) {
		Objects.requireNonNull(dialect, "dialect must not be null");
		return dialect::buildInitSql;
	}
}

