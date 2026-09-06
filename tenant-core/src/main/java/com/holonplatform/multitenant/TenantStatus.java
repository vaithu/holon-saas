package com.holonplatform.multitenant;

/**
 * Lifecycle state of a tenant.
 *
 * <p>Only {@link #ACTIVE} tenants are allowed to access the application. The
 * {@link com.holonplatform.multitenant.filter.TenantContextFilter} rejects
 * all other states unless explicitly configured otherwise.
 */
public enum TenantStatus {

	/**
	 * Schema is being provisioned; tenant is not yet accessible.
	 */
	PROVISIONING,

	/**
	 * Tenant is fully operational. This is the only state that grants UI access.
	 */
	ACTIVE,

	/**
	 * Tenant has been temporarily suspended (e.g. unpaid invoice).
	 * Access is blocked; data is preserved.
	 */
	SUSPENDED,

	/**
	 * Tenant has been permanently deleted. Data may be purged after a retention window.
	 */
	DELETED
}

