package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.onboarding.TenantRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only in-memory {@link TenantRepository} double, used in place of the removed
 * {@code InMemoryTenantRepository} production class to exercise
 * {@link DefaultTenantOnboardingService} (and other consumers) without a real Holon Datastore.
 */
public class FakeTenantRepository implements TenantRepository {

    private final Map<String, TenantDetails> store = new ConcurrentHashMap<>();

    @Override
    public TenantDetails save(TenantDetails tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        store.put(tenant.tenantId(), tenant);
        return tenant;
    }

    @Override
    public Optional<TenantDetails> findById(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return Optional.ofNullable(store.get(tenantId));
    }

    @Override
    public boolean exists(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return store.containsKey(tenantId);
    }
}
