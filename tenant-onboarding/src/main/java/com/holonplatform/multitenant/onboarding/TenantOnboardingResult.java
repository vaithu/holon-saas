package com.holonplatform.multitenant.onboarding;

import com.holonplatform.multitenant.TenantDetails;

import java.util.Objects;

/**
 * The result of a tenant onboarding operation.
 *
 * <p>Always produced by {@link TenantOnboardingService#onboard(TenantOnboardingRequest)}.
 * On success ({@link #success()} == {@code true}) the provisioned {@link TenantDetails}
 * is available via {@link #tenant()}. On failure the cause is accessible via
 * {@link #cause()} and a human-readable summary via {@link #errorMessage()}.
 *
 * @param success      {@code true} if the tenant was onboarded and its schema migrated
 * @param tenantId     the requested tenant ID (always set, even on failure)
 * @param tenant       the provisioned {@link TenantDetails}; non-null on success, null on failure
 * @param errorMessage human-readable failure description (null on success)
 * @param cause        the root exception (null on success)
 */
public record TenantOnboardingResult(
        boolean success,
        String tenantId,
        TenantDetails tenant,
        String errorMessage,
        Throwable cause
) {

    // -------------------------------------------------------------------------
    // Factories
    // -------------------------------------------------------------------------

    /**
     * Creates a successful result.
     *
     * @param tenant the fully provisioned and activated {@link TenantDetails}; must not be null
     */
    public static TenantOnboardingResult success(TenantDetails tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        return new TenantOnboardingResult(true, tenant.tenantId(), tenant, null, null);
    }

    /**
     * Creates a failed result.
     *
     * @param tenantId     the tenant ID that failed
     * @param errorMessage human-readable description of the failure
     * @param cause        the root exception
     */
    public static TenantOnboardingResult failure(String tenantId, String errorMessage, Throwable cause) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return new TenantOnboardingResult(false, tenantId, null, errorMessage, cause);
    }

    // -------------------------------------------------------------------------
    // Convenience
    // -------------------------------------------------------------------------

    /** Throws if this result is a failure; returns {@code this} otherwise (for chaining). */
    public TenantOnboardingResult orThrow() {
        if (!success) {
            throw new RuntimeException("Tenant onboarding failed for '" + tenantId + "': " + errorMessage, cause);
        }
        return this;
    }
}

