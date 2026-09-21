package com.holonplatform.multitenant.onboarding;

import com.holonplatform.multitenant.TenantDetails;

import java.util.Optional;

/**
 * Persistence SPI for the onboarding module to read and write {@link TenantDetails}
 * without depending on any specific storage technology.
 *
 * <p>Applications must provide an implementation as a Spring bean.  The default
 * {@code DatabaseTenantRepository} (registered by auto-configuration when no other
 * bean of this type is present) is backed by a Holon Datastore and requires a
 * {@code Datastore} bean to be configured (e.g. via {@code holon-datastore-jpa}).
 *
 * <p>Typical production implementations:
 * <ul>
 *   <li>Delegate to a JPA entity repository</li>
 *   <li>Delegate to a Redis hash</li>
 *   <li>Delegate to the existing {@code TenantAdminService} bean if already present</li>
 * </ul>
 *
 * <p>All methods must be idempotent with respect to calls from the onboarding service
 * (save may be called twice — once with PROVISIONING and once with ACTIVE).
 */
public interface TenantRepository {

    /**
     * Persists (insert or update) the given tenant.
     *
     * @param tenant the tenant to save; must not be null
     * @return the saved tenant (may be the same instance or an enriched copy)
     */
    TenantDetails save(TenantDetails tenant);

    /**
     * Loads a tenant by its unique identifier.
     *
     * @param tenantId the tenant ID; must not be null
     * @return the tenant details, or empty if not found
     */
    Optional<TenantDetails> findById(String tenantId);

    /**
     * Returns {@code true} if a tenant with the given ID already exists.
     *
     * @param tenantId the tenant ID; must not be null
     */
    boolean exists(String tenantId);

    /**
     * Removes a tenant record entirely.
     *
     * <p>Called by the onboarding service when provisioning fails, to undo the {@code
     * PROVISIONING} row it wrote a moment earlier. That row exists only to stop a concurrent
     * signup claiming the same identifier while provisioning runs; once provisioning has failed
     * it describes nothing, and leaving it behind makes {@link #exists(String)} keep reporting
     * the identifier as taken — permanently blocking a name for a failure the customer neither
     * caused nor can see.
     *
     * <p>Distinct from marking a tenant {@code DELETED}, which records that a real tenant once
     * existed and whose schema may still hold its data. Only a record that never became a
     * tenant may be removed this way.
     *
     * <p>The default implementation refuses, so that an existing implementation that has not
     * considered this operation cannot silently destroy data. The onboarding service treats a
     * refusal as non-fatal and falls back to marking the record {@code DELETED}.
     *
     * @param tenantId the tenant ID; must not be null
     */
    default void deleteById(String tenantId) {
        throw new UnsupportedOperationException(
                "deleteById is not implemented by " + getClass().getName()
                        + "; onboarding rollback will fall back to marking the tenant DELETED");
    }
}

