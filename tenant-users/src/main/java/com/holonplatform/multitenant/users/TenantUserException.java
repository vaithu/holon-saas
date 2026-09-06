package com.holonplatform.multitenant.users;

/**
 * Thrown when a user management operation cannot be completed.
 */
public class TenantUserException extends RuntimeException {

    private final String tenantId;
    private final String userId;

    public TenantUserException(String message, String tenantId, String userId) {
        super(message);
        this.tenantId = tenantId;
        this.userId   = userId;
    }

    public TenantUserException(String message, String tenantId, String userId, Throwable cause) {
        super(message, cause);
        this.tenantId = tenantId;
        this.userId   = userId;
    }

    /** Returns the tenant ID that was involved in the failed operation. */
    public String getTenantId() { return tenantId; }

    /** Returns the user ID that was involved in the failed operation (may be null). */
    public String getUserId()   { return userId;   }
}

