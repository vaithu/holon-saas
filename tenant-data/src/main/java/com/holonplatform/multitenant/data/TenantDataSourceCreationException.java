package com.holonplatform.multitenant.data;

/**
 * Thrown when a {@link com.holonplatform.jdbc.TenantDataSourceProvider} fails to create a
 * {@link javax.sql.DataSource} for the requested tenant.
 */
public class TenantDataSourceCreationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String tenantId;

	public TenantDataSourceCreationException(String tenantId, String message) {
		super(message);
		this.tenantId = tenantId;
	}

	public TenantDataSourceCreationException(String tenantId, String message, Throwable cause) {
		super(message, cause);
		this.tenantId = tenantId;
	}

	/** Returns the tenant ID for which DataSource creation failed. */
	public String getTenantId() {
		return tenantId;
	}
}

