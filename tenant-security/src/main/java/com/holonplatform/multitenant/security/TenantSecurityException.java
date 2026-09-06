package com.holonplatform.multitenant.security;

/**
 * Base exception for tenant security violations.
 */
public class TenantSecurityException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public TenantSecurityException(String message) { super(message); }
	public TenantSecurityException(String message, Throwable cause) { super(message, cause); }
}

