package com.holonplatform.multitenant.data;

import java.util.Objects;

/**
 * Resolves a schema name from a tenant identifier.
 *
 * <p>The default implementation ({@link DefaultSchemaNameResolver}) applies a
 * configurable prefix and suffix, e.g. {@code "acme"} → {@code "tenant_acme"}.
 *
 * <p>Register a custom bean to override the default naming convention.
 */
@FunctionalInterface
public interface SchemaNameResolver {

	/**
	 * Given a tenant ID, returns the database schema name to use.
	 *
	 * @param tenantId the tenant identifier; never null
	 * @return the schema name; never null or blank
	 */
	String resolveSchema(String tenantId);

	// -------------------------------------------------------------------------
	// Factory methods
	// -------------------------------------------------------------------------

	/**
	 * Creates a resolver that applies the given prefix and empty suffix.
	 *
	 * <p>Example: {@code withPrefix("tenant_")} maps {@code "acme"} → {@code "tenant_acme"}.
	 *
	 * @param prefix schema prefix; may be empty but not null
	 */
	static SchemaNameResolver withPrefix(String prefix) {
		Objects.requireNonNull(prefix, "prefix must not be null");
		return tenantId -> prefix + tenantId;
	}

	/**
	 * Creates a resolver that applies the given prefix and suffix.
	 *
	 * <p>Example: {@code withPrefixAndSuffix("", "_db")} maps {@code "acme"} → {@code "acme_db"}.
	 *
	 * @param prefix schema prefix; may be empty but not null
	 * @param suffix schema suffix; may be empty but not null
	 */
	static SchemaNameResolver withPrefixAndSuffix(String prefix, String suffix) {
		Objects.requireNonNull(prefix, "prefix must not be null");
		Objects.requireNonNull(suffix, "suffix must not be null");
		return tenantId -> prefix + tenantId + suffix;
	}

	/**
	 * Creates a resolver that returns the tenant ID as-is (no prefix or suffix).
	 */
	static SchemaNameResolver identity() {
		return tenantId -> tenantId;
	}
}

