package com.holonplatform.multitenant.audit;

/**
 * Canonical set of actions that may appear in the tenant audit log.
 *
 * <p>Implementations may extend this vocabulary by providing their own
 * {@link TenantAuditService} and logging entries directly via
 * {@link TenantAuditService#log(AuditEntry)} rather than using {@code @Audited}.
 */
public enum AuditAction {

    // ── Tenant lifecycle ───────────────────────────────────────────────────────
    TENANT_ONBOARDED("Tenant provisioned and activated"),
    TENANT_UPDATED("Tenant details updated"),
    TENANT_SUSPENDED("Tenant suspended"),
    TENANT_ACTIVATED("Tenant re-activated"),
    TENANT_DELETED("Tenant deleted"),

    // ── Plan / billing ─────────────────────────────────────────────────────────
    PLAN_CHANGED("Subscription plan changed"),
    TRIAL_STARTED("Free trial started"),
    TRIAL_EXPIRED("Free trial period expired"),
    PLAN_QUOTA_EXCEEDED("Usage quota for plan exceeded"),

    // ── Feature flags ──────────────────────────────────────────────────────────
    FEATURE_ENABLED("Feature enabled for tenant"),
    FEATURE_DISABLED("Feature disabled for tenant"),

    // ── User management ────────────────────────────────────────────────────────
    USER_INVITED("User invited to tenant"),
    USER_JOINED("User joined tenant"),
    USER_REMOVED("User removed from tenant"),
    USER_ROLE_CHANGED("User role changed within tenant"),

    // ── Authentication ─────────────────────────────────────────────────────────
    LOGIN("User authenticated"),
    LOGOUT("User logged out"),
    LOGIN_FAILED("Authentication attempt failed"),
    PASSWORD_CHANGED("Password changed"),
    PASSWORD_RESET("Password reset requested"),

    // ── Settings ───────────────────────────────────────────────────────────────
    SETTINGS_CHANGED("Tenant settings updated"),

    // ── API keys ───────────────────────────────────────────────────────────────
    API_KEY_CREATED("API key created"),
    API_KEY_REVOKED("API key revoked"),

    // ── Impersonation ──────────────────────────────────────────────────────────
    IMPERSONATION_STARTED("Admin impersonation started"),
    IMPERSONATION_ENDED("Admin impersonation ended"),

    // ── Data ───────────────────────────────────────────────────────────────────
    DATA_EXPORTED("Tenant data exported"),
    DATA_DELETED("Tenant data deleted (GDPR erasure)"),

    // ── Generic CRUD (fallback) ────────────────────────────────────────────────
    CREATE("Record created"),
    UPDATE("Record updated"),
    DELETE("Record deleted"),
    READ("Record accessed");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    /** Human-readable default description for this action. */
    public String getDescription() {
        return description;
    }
}

