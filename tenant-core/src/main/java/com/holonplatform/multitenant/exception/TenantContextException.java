package com.holonplatform.multitenant.exception;

/**
 * Thrown when no {@link com.holonplatform.multitenant.TenantDetails} is bound
 * to the current thread but one was required via
 * {@link com.holonplatform.multitenant.TenantContext#require()}.
 */
public class TenantContextException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TenantContextException(String message) {
		super(message);
	}

	public TenantContextException(String message, Throwable cause) {
		super(message, cause);
	}
}

