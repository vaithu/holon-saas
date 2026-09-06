package com.holonplatform.multitenant.billing;

/**
 * Thrown when a billing operation cannot be completed.
 */
public class TenantBillingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String tenantId;

    public TenantBillingException(String tenantId, String message) {
        super(message);
        this.tenantId = tenantId;
    }

    public TenantBillingException(String tenantId, String message, Throwable cause) {
        super(message, cause);
        this.tenantId = tenantId;
    }

    /** Returns the tenant for which the error occurred. */
    public String getTenantId() {
        return tenantId;
    }
}

