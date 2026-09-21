package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.onboarding.TenantRepository;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

/**
 * Holon Datastore-backed {@link TenantRepository} implementation.
 *
 * <p>Persists tenant records to the relational database via
 * {@link TenantDetailsDatastoreHelper}, replacing the previous in-memory default. A Holon
 * {@code Datastore} bean (e.g. via {@code holon-datastore-jpa}) must be configured for this
 * implementation to be usable.
 */
@Repository
public class DatabaseTenantRepository implements TenantRepository {

    private final TenantDetailsDatastoreHelper datastoreHelper;

    public DatabaseTenantRepository(TenantDetailsDatastoreHelper datastoreHelper) {
        this.datastoreHelper = Objects.requireNonNull(datastoreHelper, "datastoreHelper must not be null");
    }

    @Override
    public TenantDetails save(TenantDetails tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        return datastoreHelper.save(tenant);
    }

    @Override
    public Optional<TenantDetails> findById(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return datastoreHelper.findById(tenantId);
    }

    @Override
    public boolean exists(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return datastoreHelper.exists(tenantId);
    }

    @Override
    public void deleteById(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        datastoreHelper.deleteById(tenantId);
    }
}
