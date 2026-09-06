package com.holonplatform.multitenant.exception;

/**
 * Thrown by a {@link com.holonplatform.multitenant.TenantDetailsLoader}
 * when no tenant with the supplied ID exists in the persistent store.
 */
public class TenantNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** The unresolvable tenant ID. */
	private final String tenantId;

	public TenantNotFoundException(String tenantId) {
		super("Tenant not found: " + tenantId);
		this.tenantId = tenantId;
	}

	public TenantNotFoundException(String tenantId, Throwable cause) {
		super("Tenant not found: " + tenantId, cause);
		this.tenantId = tenantId;
	}

	/** Returns the tenant ID that could not be resolved. */
	public String getTenantId() {
		return tenantId;
	}
}

