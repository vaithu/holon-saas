package com.holonplatform.multitenant.admin.internal;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.admin.service.TenantAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Holon Datastore-backed {@link TenantAdminService} implementation.
 *
 * <p>Persists tenant records to the relational database via
 * {@link TenantDetailsDatastoreHelper}, replacing the previous in-memory default. Unlike the
 * removed {@code InMemoryTenantAdminService}, this implementation does <strong>not</strong>
 * seed any sample data — production deployments start with an empty tenant table. A Holon
 * {@code Datastore} bean (e.g. via {@code holon-datastore-jpa}) must be configured for this
 * implementation to be usable.
 */
@Service
public class DatabaseTenantAdminService implements TenantAdminService {

    private final TenantDetailsDatastoreHelper datastoreHelper;

    public DatabaseTenantAdminService(TenantDetailsDatastoreHelper datastoreHelper) {
        this.datastoreHelper = Objects.requireNonNull(datastoreHelper, "datastoreHelper must not be null");
    }

    @Override
    public List<TenantDetails> findAll() {
        return sortedByNameOrTenantId(datastoreHelper.findAll());
    }

    @Override
    public List<TenantDetails> search(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return findAll();
        }
        return sortedByNameOrTenantId(datastoreHelper.search(searchTerm));
    }

    @Override
    public Optional<TenantDetails> findById(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return datastoreHelper.findById(tenantId);
    }

    @Override
    @Transactional
    public TenantDetails save(TenantDetails tenant) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        return datastoreHelper.save(tenant);
    }

    @Override
    @Transactional
    public void delete(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        datastoreHelper.delete(tenantId);
    }

    @Override
    @Transactional
    public TenantDetails suspend(String tenantId) {
        TenantDetails existing = require(tenantId);
        TenantDetails suspended = existing.toBuilder().status(TenantStatus.SUSPENDED).build();
        return datastoreHelper.save(suspended);
    }

    @Override
    @Transactional
    public TenantDetails activate(String tenantId) {
        TenantDetails existing = require(tenantId);
        TenantDetails activated = existing.toBuilder().status(TenantStatus.ACTIVE).build();
        return datastoreHelper.save(activated);
    }

    @Override
    public long countAll() {
        return datastoreHelper.count();
    }

    @Override
    public long countByStatus(TenantStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return datastoreHelper.countByStatus(status);
    }

    @Override
    public long countByPlan(TenantPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        return datastoreHelper.countByPlan(plan);
    }

    @Override
    public List<TenantDetails> findSlice(int offset, int limit, String searchTerm) {
        return datastoreHelper.findSlice(offset, limit, searchTerm);
    }

    @Override
    public long count(String searchTerm) {
        return datastoreHelper.count(searchTerm);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TenantDetails require(String tenantId) {
        return findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
    }

    private static List<TenantDetails> sortedByNameOrTenantId(List<TenantDetails> tenants) {
        return tenants.stream()
                .sorted(Comparator.comparing(t -> t.name() != null ? t.name() : t.tenantId()))
                .toList();
    }
}
