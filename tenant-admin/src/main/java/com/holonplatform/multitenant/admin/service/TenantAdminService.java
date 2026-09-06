package com.holonplatform.multitenant.admin.service;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for tenant administration.
 *
 * <p>This is a pure-Java service interface — no Vaadin dependency.
 * Implementations are responsible for persistence; the default
 * {@code DatabaseTenantAdminService} is backed by a Holon Datastore and
 * requires a {@code Datastore} bean to be configured (e.g. via
 * {@code holon-datastore-jpa}).</p>
 */
public interface TenantAdminService {

    /**
     * Returns all tenants ordered by name.
     */
    List<TenantDetails> findAll();

    /**
     * Returns all tenants whose {@code name} or {@code tenantId} contains
     * the given search term (case-insensitive). An empty term returns all tenants.
     *
     * @param searchTerm the filter text (may be null or blank for all)
     */
    List<TenantDetails> search(String searchTerm);

    /**
     * Finds a tenant by its unique identifier.
     *
     * @param tenantId the tenant ID
     * @return the tenant details, or empty if not found
     */
    Optional<TenantDetails> findById(String tenantId);

    /**
     * Saves (insert or update) a tenant.
     *
     * @param tenant the tenant to save (not null)
     * @return the saved tenant
     */
    TenantDetails save(TenantDetails tenant);

    /**
     * Deletes a tenant by its ID.
     *
     * @param tenantId the tenant ID (not null)
     */
    void delete(String tenantId);

    /**
     * Sets the tenant's status to {@link TenantStatus#SUSPENDED}.
     *
     * @param tenantId the tenant ID
     * @return the updated tenant
     */
    TenantDetails suspend(String tenantId);

    /**
     * Sets the tenant's status to {@link TenantStatus#ACTIVE}.
     *
     * @param tenantId the tenant ID
     * @return the updated tenant
     */
    TenantDetails activate(String tenantId);

    /**
     * Returns the total number of tenants.
     */
    long countAll();

    /**
     * Returns the number of tenants with the given status.
     *
     * @param status the status to filter by
     */
    long countByStatus(TenantStatus status);

    /**
     * Returns the number of tenants on the given plan.
     *
     * @param plan the plan to filter by
     */
    long countByPlan(TenantPlan plan);

    // ── Lazy / paginated API ───────────────────────────────��─────────────────

    /**
     * Returns a slice of tenants matching the given search term, suitable for
     * lazy / infinite-scroll grids.
     *
     * <p>This method <strong>never</strong> issues an upfront total-count query.
     * The caller is responsible for requesting counts explicitly via
     * {@link #count(String)} only when pagination UI actually needs the number.
     *
     * @param offset     zero-based start index
     * @param limit      maximum number of results to return
     * @param searchTerm case-insensitive filter on name / tenantId (null or blank = no filter)
     * @return ordered slice, never null
     */
    List<TenantDetails> findSlice(int offset, int limit, String searchTerm);

    /**
     * Returns the total count of tenants matching the given search term.
     *
     * <p>Call this <strong>only when</strong> the UI explicitly needs the total
     * (e.g. count badge, pagination bar). Never call it to gate a data-fetch.
     *
     * @param searchTerm case-insensitive filter on name / tenantId (null or blank = no filter)
     * @return total matching count
     */
    long count(String searchTerm);
}

