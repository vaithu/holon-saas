package com.holonplatform.multitenant.billing;

import com.holonplatform.multitenant.TenantPlan;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing tenant subscription lifecycle.
 *
 * <h2>Lazy-load principle</h2>
 * <p>All history listings accept {@code offset} and {@code limit}.
 * Count is a separate explicit call.
 */
public interface TenantBillingService {

    // ── Subscription CRUD ─────────────────────────────────────────────────────

    /**
     * Creates or replaces the subscription for a tenant.
     *
     * @param subscription the new subscription; must not be null
     * @return the stored subscription
     */
    Subscription save(Subscription subscription);

    /**
     * Returns the current subscription for the tenant, or empty if none.
     *
     * @param tenantId tenant; must not be null
     */
    Optional<Subscription> findByTenant(String tenantId);

    /**
     * Changes the plan for a tenant, transitioning status to {@link SubscriptionStatus#ACTIVE}.
     *
     * @param tenantId tenant; must not be null
     * @param newPlan  target plan; must not be null
     * @return updated subscription
     * @throws TenantBillingException if no subscription exists for the tenant
     */
    Subscription changePlan(String tenantId, TenantPlan newPlan);

    /**
     * Cancels the subscription. Status transitions to {@link SubscriptionStatus#CANCELLED}.
     * Access continues until {@link Subscription#periodEnd()}.
     *
     * @param tenantId tenant; must not be null
     * @return updated subscription
     * @throws TenantBillingException if no subscription exists
     */
    Subscription cancel(String tenantId);

    /**
     * Marks a subscription as expired.
     *
     * @param tenantId tenant; must not be null
     * @return updated subscription
     * @throws TenantBillingException if no subscription exists
     */
    Subscription expire(String tenantId);

    // ── Invoice tracking ──────────────────────────────────────────────────────

    /**
     * Records a new invoice for the tenant.
     *
     * @param invoice invoice to store; must not be null
     * @return the stored invoice
     */
    Invoice saveInvoice(Invoice invoice);

    /**
     * Returns invoices for the tenant, newest first.
     *
     * @param tenantId tenant; must not be null
     * @param offset   zero-based start index
     * @param limit    max results
     * @return invoices list; never null
     */
    List<Invoice> findInvoices(String tenantId, int offset, int limit);

    /**
     * Counts invoices for the tenant.
     *
     * @param tenantId tenant; must not be null
     */
    long countInvoices(String tenantId);

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Removes all billing data for a tenant.
     *
     * @param tenantId tenant; must not be null
     */
    void deleteAll(String tenantId);
}

