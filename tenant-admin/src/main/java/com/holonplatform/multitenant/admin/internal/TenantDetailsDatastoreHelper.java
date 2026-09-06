package com.holonplatform.multitenant.admin.internal;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsRepository;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA-based helper for {@link TenantDetails} persistence used by the tenant-admin
 * module.
 *
 * <p>Wraps a plain {@link TenantDetailsRepository} (a standard {@code JpaRepository}) rather
 * than Holon's {@code BeanDatastoreHelper} — see {@link TenantDetailsRepository}'s Javadoc for
 * the rationale. This is a self-contained copy of the pattern established in
 * {@code tenant-onboarding}; the tenant-admin module owns its own helper independently, even
 * though it maps the same {@code tenant} table, since each module is designed to be
 * self-contained.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.admin.TenantAdminAutoConfiguration} rather than
 * {@code @Component}-scanned — this class lives in an internal package outside any consuming
 * application's own base package, so component-scanning would never find it.
 */
public class TenantDetailsDatastoreHelper {

    private final TenantDetailsRepository repository;
    private final EntityManager entityManager;

    public TenantDetailsDatastoreHelper(TenantDetailsRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
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
     * Deletes the tenant with the given ID, if present. No-op if not found.
     *
     * @param tenantId the tenant ID; must not be null
     */
    public void delete(String tenantId) {
        repository.deleteById(tenantId);
    }

    /**
     * Returns all tenants, unsorted (callers are responsible for any ordering).
     */
    public List<TenantDetails> findAll() {
        return repository.findAll();
    }

    /**
     * Returns all tenants whose {@code name} or {@code tenantId} contains the given search
     * term (case-insensitive), unsorted.
     *
     * @param searchTerm the filter text; must not be null or blank
     */
    public List<TenantDetails> search(String searchTerm) {
        String term = searchTerm.trim();
        return repository.findByNameContainingIgnoreCaseOrTenantIdContainingIgnoreCase(term, term);
    }

    /**
     * Returns a slice of tenants matching the given search term (or all tenants when
     * {@code searchTerm} is null/blank), ordered by {@code name}.
     *
     * <p>Implemented directly against {@link EntityManager} (rather than a Spring Data
     * derived query / {@code Pageable}) because {@code offset} here is an arbitrary row
     * index, not necessarily a multiple of {@code limit} — {@code Pageable}/{@code PageRequest}
     * can only express a (page number, page size) pair, not an arbitrary offset.
     *
     * @param offset     zero-based start index
     * @param limit      maximum number of results to return
     * @param searchTerm case-insensitive filter on name / tenantId (null or blank = no filter)
     */
    public List<TenantDetails> findSlice(int offset, int limit, String searchTerm) {
        boolean hasSearch = searchTerm != null && !searchTerm.isBlank();
        String jpql = "select t from tenantdetails t"
                + (hasSearch ? " where lower(t.name) like :term or lower(t.tenantId) like :term" : "")
                + " order by t.name asc, t.tenantId asc";
        TypedQuery<TenantDetails> query = entityManager.createQuery(jpql, TenantDetails.class);
        if (hasSearch) {
            query.setParameter("term", "%" + searchTerm.trim().toLowerCase() + "%");
        }
        return query.setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    /**
     * Returns the total number of tenants.
     */
    public long count() {
        return repository.count();
    }

    /**
     * Returns the number of tenants matching the given search term (or all tenants when
     * {@code searchTerm} is null/blank).
     *
     * @param searchTerm case-insensitive filter on name / tenantId (null or blank = no filter)
     */
    public long count(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return count();
        }
        String term = searchTerm.trim();
        return repository.countByNameContainingIgnoreCaseOrTenantIdContainingIgnoreCase(term, term);
    }

    /**
     * Returns the number of tenants with the given status.
     *
     * @param status the status to filter by; must not be null
     */
    public long countByStatus(TenantStatus status) {
        return repository.countByStatus(status);
    }

    /**
     * Returns the number of tenants on the given plan.
     *
     * @param plan the plan to filter by; must not be null
     */
    public long countByPlan(TenantPlan plan) {
        return repository.countByPlan(plan);
    }
}

