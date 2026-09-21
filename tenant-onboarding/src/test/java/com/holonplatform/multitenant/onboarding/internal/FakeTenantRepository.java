package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.onboarding.TenantRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Test-only in-memory {@link TenantRepository} double, used in place of the removed
 * {@code InMemoryTenantRepository} production class to exercise
 * {@link DefaultTenantOnboardingService} (and other consumers) without a real Holon Datastore.
 */
public class FakeTenantRepository implements TenantRepository {

    protected final Map<String, TenantDetails> store = new ConcurrentHashMap<>();

    private volatile Consumer<TenantDetails> onSave = tenant -> { };

    /**
     * Registers a hook run inside {@link #save(TenantDetails)}, for asserting on the conditions
     * a write happens under (ambient tenant, transaction state) rather than only on its result.
     */
    public void setOnSave(Consumer<TenantDetails> hook) {
        this.onSave = Objects.requireNonNull(hook, "hook must not be null");
    }

    @Override
    public TenantDetails save(TenantDetails tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        onSave.accept(tenant);
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

    /**
     * Deliberately left unimplemented. {@link TenantRepository#deleteById(String)} is a
     * {@code default} method that refuses, and this double is the thing that proves an existing
     * implementation which never heard of it still works — onboarding falls back to marking the
     * record {@code DELETED} rather than failing. {@link DeletingFakeTenantRepository} covers
     * the implemented case.
     */
}

/**
 * A {@link FakeTenantRepository} that supports removal, for exercising the rollback path that
 * actually frees the identifier again.
 */
class DeletingFakeTenantRepository extends FakeTenantRepository {

    @Override
    public void deleteById(String tenantId) {
        store.remove(tenantId);
    }
}
