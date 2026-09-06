package com.holonplatform.multitenant.onboarding.exception;

/**
 * Thrown by {@link com.holonplatform.multitenant.onboarding.TenantOnboardingService#onboard}
 * when a tenant with the requested ID is already registered in the
 * {@link com.holonplatform.multitenant.onboarding.TenantRepository}.
 */
public class TenantAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String tenantId;

    public TenantAlreadyExistsException(String tenantId) {
        super("Tenant already exists: '" + tenantId + "'");
        this.tenantId = tenantId;
    }

    /** The tenant ID that caused the conflict. */
    public String getTenantId() {
        return tenantId;
    }
}

