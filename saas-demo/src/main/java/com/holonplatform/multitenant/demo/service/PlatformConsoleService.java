package com.holonplatform.multitenant.demo.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.CachingTenantDetailsLoader;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.admin.service.TenantAdminService;
import com.holonplatform.multitenant.billing.Invoice;
import com.holonplatform.multitenant.billing.InvoiceStatus;
import com.holonplatform.multitenant.billing.Subscription;
import com.holonplatform.multitenant.billing.SubscriptionStatus;
import com.holonplatform.multitenant.billing.TenantBillingService;

/**
 * The platform operator's view of every workspace — F13, F14 and F15.
 *
 * <h2>Two services, two schemas</h2>
 * <p>The two framework services this wraps are called from opposite ends of the tenancy model,
 * and getting that wrong is the single easiest way to break this page:
 *
 * <ul>
 *   <li>{@link TenantAdminService} reads and writes the {@code tenant} registry, which lives in
 *       the <strong>control plane</strong>. The operator already has no tenant bound, so those
 *       calls are made directly — but they are still wrapped in
 *       {@link TenantBinding#onControlPlane} so the service is equally usable from a request
 *       that <em>does</em> have a tenant bound (the service-level tests do exactly that).</li>
 *   <li>{@link TenantBillingService} reads and writes {@code tenant_billing_subscription} and
 *       {@code tenant_billing_invoice}, which the onboarding migration creates
 *       <strong>inside each tenant's own schema</strong>. Reaching them therefore requires
 *       binding the target tenant first, via {@link TenantBinding#asTenant}.</li>
 * </ul>
 *
 * <h2>Why nothing here is {@code @Audited}</h2>
 * <p>The audit trail is per-tenant: {@code audit_entries} lives in the tenant's schema and the
 * aspect resolves the tenant from the context at the moment the method is entered. For an
 * operator with no tenant bound there is nothing for the aspect to write against, so these
 * actions are logged rather than audited. Auditing cross-tenant administration properly would
 * need a control-plane audit table, which the framework does not ship.
 */
@Service
public class PlatformConsoleService {

    private static final Logger log = LoggerFactory.getLogger(PlatformConsoleService.class);

    private final TenantAdminService   adminService;
    private final TenantBillingService billingService;
    private final TenantDetailsLoader  tenantDetailsLoader;

    public PlatformConsoleService(TenantAdminService adminService,
                                  TenantBillingService billingService,
                                  TenantDetailsLoader tenantDetailsLoader) {
        this.adminService        = Objects.requireNonNull(adminService, "adminService must not be null");
        this.billingService      = Objects.requireNonNull(billingService, "billingService must not be null");
        this.tenantDetailsLoader = Objects.requireNonNull(tenantDetailsLoader,
                "tenantDetailsLoader must not be null");
    }

    // ── F13: cross-tenant listing and search ──────────────────────────────────────────

    /**
     * Returns one page of workspaces matching the search term.
     *
     * <p>Deliberately uses the framework's slice API rather than {@code findAll}: it issues no
     * upfront count query, which is the behaviour a grid with thousands of tenants needs.
     *
     * @param offset zero-based start index
     * @param limit  maximum rows to return
     * @param search case-insensitive filter on name or id; null or blank matches everything
     */
    public List<TenantDetails> list(int offset, int limit, String search) {
        return TenantBinding.onControlPlane(() -> adminService.findSlice(offset, limit, search));
    }

    /** Counts the workspaces matching the same search term as {@link #list}. */
    public long count(String search) {
        return TenantBinding.onControlPlane(() -> adminService.count(search));
    }

    /** Counts the workspaces currently in the given status. */
    public long countByStatus(TenantStatus status) {
        return TenantBinding.onControlPlane(() -> adminService.countByStatus(status));
    }

    /** Counts the workspaces currently on the given plan. */
    public long countByPlan(TenantPlan plan) {
        return TenantBinding.onControlPlane(() -> adminService.countByPlan(plan));
    }

    // ── F14: suspension and reactivation ──────────────────────────────────────────────

    /**
     * Suspends a workspace. Its members are refused at sign-in, and any member already signed
     * in loses their tenant binding on their next request.
     *
     * @param tenantId the workspace to suspend; must not be null
     * @return the updated workspace
     */
    public TenantDetails suspend(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        TenantDetails updated = TenantBinding.onControlPlane(() -> adminService.suspend(tenantId));
        invalidateCachedDetails(tenantId);
        log.info("Workspace '{}' suspended by the platform operator", tenantId);
        return updated;
    }

    /**
     * Returns a suspended workspace to service.
     *
     * @param tenantId the workspace to reactivate; must not be null
     * @return the updated workspace
     */
    public TenantDetails activate(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        TenantDetails updated = TenantBinding.onControlPlane(() -> adminService.activate(tenantId));
        invalidateCachedDetails(tenantId);
        log.info("Workspace '{}' reactivated by the platform operator", tenantId);
        return updated;
    }

    // ── F15: subscriptions and invoices ───────────────────────────────────────────────

    /**
     * Returns the workspace's subscription, creating one on first sight.
     *
     * <p>Sign-up does not open a subscription — the framework's onboarding and billing modules
     * are independent — so the operator console treats the first visit to a workspace's billing
     * page as the moment the subscription record comes into being, seeded from the plan already
     * recorded on the tenant.
     *
     * @param tenantId the workspace; must not be null
     */
    public Subscription subscriptionOf(String tenantId) {
        TenantDetails tenant = requireTenant(tenantId);
        return TenantBinding.asTenant(tenant, () ->
                billingService.findByTenant(tenantId).orElseGet(() ->
                        billingService.save(Subscription.builder(tenantId)
                                .plan(tenant.plan())
                                .status(SubscriptionStatus.ACTIVE)
                                .periodStart(Instant.now())
                                .build())));
    }

    /**
     * Returns a page of the workspace's invoices, newest first.
     *
     * @param tenantId the workspace; must not be null
     * @param offset   zero-based start index
     * @param limit    maximum rows to return
     */
    public List<Invoice> invoicesOf(String tenantId, int offset, int limit) {
        TenantDetails tenant = requireTenant(tenantId);
        return TenantBinding.asTenant(tenant, () -> billingService.findInvoices(tenantId, offset, limit));
    }

    /** Counts the workspace's invoices. */
    public long countInvoices(String tenantId) {
        TenantDetails tenant = requireTenant(tenantId);
        return TenantBinding.asTenant(tenant, () -> billingService.countInvoices(tenantId));
    }

    /**
     * Moves a workspace onto a new plan from the operator console, and raises the invoice that
     * the change entails.
     *
     * <p>Three records have to agree for a plan change to be real, and they live in two
     * different schemas:
     * <ol>
     *   <li>the <em>subscription</em> and the <em>invoice</em>, in the tenant's schema — what
     *       the customer is billed for;</li>
     *   <li>the <em>tenant registry entry</em>, in the control plane — what
     *       {@code @RequiresPlan} and {@code @CheckTenantFeature} actually read.</li>
     * </ol>
     * Updating only the subscription would produce a workspace that is billed for
     * PROFESSIONAL but still refused PROFESSIONAL features, so both are written here and the
     * cached tenant details are dropped so the next request sees the new plan.
     *
     * @param tenantId the workspace; must not be null
     * @param newPlan  the plan to move to; must not be null
     * @return the updated subscription
     */
    public Subscription changePlan(String tenantId, TenantPlan newPlan) {
        Objects.requireNonNull(newPlan, "newPlan must not be null");
        TenantDetails tenant = requireTenant(tenantId);

        // Make sure a subscription exists before asking the billing service to change its plan;
        // changePlan throws TenantBillingException when there is nothing to change.
        subscriptionOf(tenantId);

        Subscription updated = TenantBinding.asTenant(tenant, () -> {
            Subscription subscription = billingService.changePlan(tenantId, newPlan);
            billingService.saveInvoice(Invoice.builder(UUID.randomUUID().toString(), tenantId)
                    .amountDue(priceOf(newPlan))
                    .currency("USD")
                    .status(InvoiceStatus.OPEN)
                    .issuedAt(Instant.now())
                    .externalRef("plan change to " + newPlan.name())
                    .build());
            return subscription;
        });

        TenantBinding.onControlPlane(() ->
                adminService.save(tenant.toBuilder().plan(newPlan).build()));
        invalidateCachedDetails(tenantId);

        log.info("Workspace '{}' moved to plan {} by the platform operator", tenantId, newPlan);
        return updated;
    }

    /**
     * Cancels a workspace's subscription. Access continues until the period ends, which is why
     * this does not suspend the tenant.
     *
     * @param tenantId the workspace; must not be null
     */
    public Subscription cancelSubscription(String tenantId) {
        TenantDetails tenant = requireTenant(tenantId);
        subscriptionOf(tenantId);
        return TenantBinding.asTenant(tenant, () -> billingService.cancel(tenantId));
    }

    // ── Internals ─────────────────────────────────────────────────────────────────────

    /**
     * The list price of a plan, used only to give demo invoices a plausible amount.
     *
     * <p>A switch rather than an ordinal lookup, so inserting a plan into the middle of
     * {@link TenantPlan} is a compile error here instead of a silent mispricing.
     */
    public static BigDecimal priceOf(TenantPlan plan) {
        return switch (plan) {
            case FREE         -> new BigDecimal("0.00");
            case STARTER      -> new BigDecimal("29.00");
            case PROFESSIONAL -> new BigDecimal("99.00");
            case ENTERPRISE   -> new BigDecimal("499.00");
        };
    }

    private TenantDetails requireTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return TenantBinding.onControlPlane(() -> adminService.findById(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Unknown workspace: " + tenantId));
    }

    /**
     * Drops the cached copy of the tenant. Without this the request filter would keep binding
     * the stale details — so a suspended workspace would carry on working until the cache
     * entry expired.
     */
    private void invalidateCachedDetails(String tenantId) {
        if (tenantDetailsLoader instanceof CachingTenantDetailsLoader caching) {
            caching.invalidate(tenantId);
        }
    }
}
