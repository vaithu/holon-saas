package com.holonplatform.multitenant.onboarding;

/**
 * Orchestrates the complete lifecycle of bringing a new tenant into service:
 * create tenant record → provision database schema → run Flyway → activate.
 *
 * <h2>Guaranteed lifecycle</h2>
 * <ol>
 *   <li>Validate the {@link TenantOnboardingRequest}.</li>
 *   <li>Check for duplicate tenant ID — throws {@link com.holonplatform.multitenant.onboarding.exception.TenantAlreadyExistsException} if found.</li>
 *   <li>Persist the tenant with status {@code PROVISIONING}.</li>
 *   <li>Create the database schema (if using {@code SCHEMA_PER_TENANT} strategy).</li>
 *   <li>Execute all Flyway migration scripts at {@code classpath:db/tenant-migration}.</li>
 *   <li>Update tenant status to {@code ACTIVE}.</li>
 *   <li>Publish {@link com.holonplatform.multitenant.onboarding.event.TenantOnboardingCompletedEvent}.</li>
 * </ol>
 *
 * <h2>Failure handling</h2>
 * <p>If any step after persistence fails, the tenant is marked {@code DELETED} in the
 * repository and a {@link com.holonplatform.multitenant.onboarding.event.TenantOnboardingFailedEvent}
 * is published before re-throwing the wrapped exception.
 *
 * <h2>Shared-schema mode</h2>
 * <p>When no {@code TenantSchemaInitializer} bean is present (e.g. {@code SHARED} strategy)
 * the schema provisioning step is skipped automatically.
 *
 * <h2>Minimal usage</h2>
 * <pre>{@code
 * TenantOnboardingResult result = onboardingService.onboard(
 *     TenantOnboardingRequest.builder("acme")
 *         .name("Acme Corporation")
 *         .plan(TenantPlan.PROFESSIONAL)
 *         .adminEmail("admin@acme.com")
 *         .locale("en-US")
 *         .timezone("America/New_York")
 *         .build());
 *
 * result.orThrow(); // throws if onboarding failed
 * TenantDetails activeTenant = result.tenant();
 * }</pre>
 */
public interface TenantOnboardingService {

    /**
     * Onboards a new tenant synchronously.
     *
     * <p>The call blocks until schema provisioning and all Flyway migrations complete.
     * For large migration sets consider running this on a background thread.
     *
     * @param request the onboarding request; must not be null
     * @return a {@link TenantOnboardingResult} describing the outcome
     * @throws com.holonplatform.multitenant.onboarding.exception.TenantAlreadyExistsException
     *         if a tenant with the same ID is already registered
     * @throws com.holonplatform.multitenant.onboarding.exception.TenantOnboardingException
     *         if schema creation or Flyway migration fails
     */
    TenantOnboardingResult onboard(TenantOnboardingRequest request);

    /**
     * Returns {@code true} if the tenant with the given ID has already been onboarded
     * (i.e. exists in the {@link TenantRepository}).
     *
     * @param tenantId the tenant ID to check; must not be null
     */
    boolean isOnboarded(String tenantId);
}

