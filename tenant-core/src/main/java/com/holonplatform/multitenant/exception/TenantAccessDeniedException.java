package com.holonplatform.multitenant.exception;

/**
 * Thrown when a tenant is resolved but its {@link com.holonplatform.multitenant.TenantStatus}
 * does not permit access (e.g. {@code SUSPENDED}, {@code PROVISIONING}, {@code DELETED}).
 */
public class TenantAccessDeniedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final String tenantId;
	private final com.holonplatform.multitenant.TenantStatus status;

	public TenantAccessDeniedException(String tenantId,
			com.holonplatform.multitenant.TenantStatus status) {
		super("Tenant '" + tenantId + "' is not accessible — status: " + status);
		this.tenantId = tenantId;
		this.status   = status;
	}

	public String getTenantId() {
		return tenantId;
	}

	public com.holonplatform.multitenant.TenantStatus getStatus() {
		return status;
	}
}

