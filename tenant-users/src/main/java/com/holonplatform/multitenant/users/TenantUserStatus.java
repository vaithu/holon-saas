package com.holonplatform.multitenant.users;

/**
 * Lifecycle state of a user within a tenant.
 */
public enum TenantUserStatus {

    /**
     * The user has been invited but has not yet accepted the invitation.
     * Limited access; cannot log in until transitioned to {@link #ACTIVE}.
     */
    INVITED,

    /** The user is active and can log in and use the application. */
    ACTIVE,

    /** The user account has been suspended by an admin. Cannot log in. */
    SUSPENDED,

    /**
     * The user has been removed from the tenant.
     * Records are retained for audit purposes but the user can no longer access.
     */
    REMOVED;

    /** Returns {@code true} if the user can currently access the application. */
    public boolean canAccess() {
        return this == ACTIVE;
    }
}

