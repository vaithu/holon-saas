package com.holonplatform.multitenant.users;

/**
 * Role a user holds within a specific tenant.
 *
 * <p>Roles are hierarchical; higher ordinal implies broader access.
 * Application code should use {@link #isAtLeast(TenantRole)} rather than
 * ordinal comparison directly.
 */
public enum TenantRole {

    /** Read-only access — can view data but cannot modify anything. */
    VIEWER,

    /** Standard member — full access to the application features for the plan. */
    MEMBER,

    /** Tenant administrator — manages users, settings, and billing. */
    ADMIN,

    /**
     * Tenant owner — has all admin rights plus the ability to delete the tenant
     * and transfer ownership. Each tenant has exactly one owner at any time.
     */
    OWNER;

    /**
     * Returns {@code true} if this role is at least as powerful as {@code required}.
     *
     * @param required the minimum required role
     */
    public boolean isAtLeast(TenantRole required) {
        return this.ordinal() >= required.ordinal();
    }
}

