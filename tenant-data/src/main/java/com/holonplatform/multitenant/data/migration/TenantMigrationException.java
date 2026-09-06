package com.holonplatform.multitenant.data.migration;

/**
 * Thrown when {@link TenantSchemaInitializer} fails to provision or migrate
 * a tenant's schema.
 */
public class TenantMigrationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String tenantId;
	private final String schemaName;

	public TenantMigrationException(String tenantId, String schemaName, Throwable cause) {
		super("Schema migration failed for tenant '" + tenantId + "' (schema: '" + schemaName + "')", cause);
		this.tenantId   = tenantId;
		this.schemaName = schemaName;
	}

	public String getTenantId() {
		return tenantId;
	}

	public String getSchemaName() {
		return schemaName;
	}
}

