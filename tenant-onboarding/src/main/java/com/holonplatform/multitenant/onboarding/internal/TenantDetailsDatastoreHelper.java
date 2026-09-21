package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsRepository;

import java.util.Optional;

/**
 * Spring Data JPA-based helper for {@link TenantDetails} persistence.
 *
 * <p>Wraps a plain {@link TenantDetailsRepository} (a standard {@code JpaRepository}) rather
 * than Holon's {@code BeanDatastoreHelper} — see {@link TenantDetailsRepository}'s Javadoc for
 * the rationale. {@link #save(TenantDetails)} is a genuine upsert: {@code JpaRepository#save}
 * merges when the entity's {@code tenantId} primary key is already assigned (always true here,
 * since {@code tenantId} is a caller-supplied natural key, never generated), so it is safe to
 * call repeatedly for the same tenant — required by the onboarding workflow, which persists a
 * tenant once with {@code PROVISIONING} status and again with {@code ACTIVE} status.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.onboarding.config.TenantOnboardingAutoConfiguration}
 * rather than {@code @Component}-scanned — this class lives in an internal package outside
 * any consuming application's own base package, so component-scanning would never find it.
 */
public class TenantDetailsDatastoreHelper {

    private final TenantDetailsRepository repository;

    public TenantDetailsDatastoreHelper(TenantDetailsRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists (insert or update) the given tenant.
     *
     * @param tenant the tenant to save; must not be null
     * @return the saved tenant
     */
    public TenantDetails save(TenantDetails tenant) {
        return repository.save(tenant);
    }

    /**
     * Loads a tenant by its unique identifier.
     *
     * @param tenantId the tenant ID; must not be null
     * @return the tenant details, or empty if not found
     */
    public Optional<TenantDetails> findById(String tenantId) {
        return repository.findById(tenantId);
    }

    /**
     * Returns {@code true} if a tenant with the given ID already exists.
     *
     * @param tenantId the tenant ID; must not be null
     */
    public boolean exists(String tenantId) {
        return repository.existsById(tenantId);
    }

    /**
     * Removes a tenant record entirely. Used only to undo a {@code PROVISIONING} row whose
     * onboarding then failed — see {@code TenantRepository#deleteById}.
     *
     * @param tenantId the tenant ID; must not be null
     */
    public void deleteById(String tenantId) {
        repository.deleteById(tenantId);
    }
}

