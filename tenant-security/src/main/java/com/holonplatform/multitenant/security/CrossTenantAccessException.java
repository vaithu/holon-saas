package com.holonplatform.multitenant.security;

/**
 * Thrown when an authenticated user attempts to access data belonging to a
 * different tenant than the one resolved for the current HTTP request.
 *
 * <p>This is a security violation — the user's authentication token belongs
 * to tenant A, but the request is targeting tenant B's data.
 */
public class CrossTenantAccessException extends TenantSecurityException {

	private static final long serialVersionUID = 1L;

	private final String userTenantId;
	private final String requestTenantId;

	public CrossTenantAccessException(String userTenantId, String requestTenantId) {
		super("Cross-tenant access denied: authenticated tenant '" + userTenantId
				+ "' attempted to access resources of tenant '" + requestTenantId + "'");
		this.userTenantId    = userTenantId;
		this.requestTenantId = requestTenantId;
	}

	/** The tenant ID from the user's authentication token. */
	public String getUserTenantId()    { return userTenantId; }

	/** The tenant ID resolved from the current HTTP request. */
	public String getRequestTenantId() { return requestTenantId; }
}

