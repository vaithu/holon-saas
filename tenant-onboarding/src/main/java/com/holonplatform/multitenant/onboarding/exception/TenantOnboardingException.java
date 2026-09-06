package com.holonplatform.multitenant.onboarding.exception;

/**
 * Thrown by {@link com.holonplatform.multitenant.onboarding.TenantOnboardingService#onboard}
 * when schema creation or Flyway migration fails.
 *
 * <p>When this exception is thrown the tenant record has already been persisted
 * with status {@code DELETED} (best-effort rollback) and a
 * {@link com.holonplatform.multitenant.onboarding.event.TenantOnboardingFailedEvent}
 * has been published.
 */
public class TenantOnboardingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String tenantId;

    public TenantOnboardingException(String tenantId, String message) {
        super("Onboarding failed for tenant '" + tenantId + "': " + message);
        this.tenantId = tenantId;
    }

    public TenantOnboardingException(String tenantId, Throwable cause) {
        super("Onboarding failed for tenant '" + tenantId + "'", cause);
        this.tenantId = tenantId;
    }

    /** The tenant ID whose onboarding failed. */
    public String getTenantId() {
        return tenantId;
    }
}

