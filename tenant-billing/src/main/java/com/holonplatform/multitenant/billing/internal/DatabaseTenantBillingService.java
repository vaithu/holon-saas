package com.holonplatform.multitenant.billing.internal;

import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.billing.Invoice;
import com.holonplatform.multitenant.billing.Subscription;
import com.holonplatform.multitenant.billing.SubscriptionStatus;
import com.holonplatform.multitenant.billing.TenantBillingException;
import com.holonplatform.multitenant.billing.TenantBillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Database-backed {@link TenantBillingService} implementation using Holon Datastore.
 *
 * <p>Persists subscription and invoice data to the relational database via
 * {@link SubscriptionDatastoreHelper} and {@link InvoiceDatastoreHelper}.
 */
@Service
public class DatabaseTenantBillingService implements TenantBillingService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTenantBillingService.class);

    private final SubscriptionDatastoreHelper subscriptionDatastoreHelper;
    private final InvoiceDatastoreHelper invoiceDatastoreHelper;

    public DatabaseTenantBillingService(SubscriptionDatastoreHelper subscriptionDatastoreHelper,
            InvoiceDatastoreHelper invoiceDatastoreHelper) {
        this.subscriptionDatastoreHelper = Objects.requireNonNull(subscriptionDatastoreHelper,
                "subscriptionDatastoreHelper must not be null");
        this.invoiceDatastoreHelper = Objects.requireNonNull(invoiceDatastoreHelper,
                "invoiceDatastoreHelper must not be null");
    }

    // ── Subscription CRUD ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public Subscription save(Subscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        subscriptionDatastoreHelper.save(subscription);
        log.info("Saved subscription for tenant '{}': plan={}, status={}",
                subscription.tenantId(), subscription.plan(), subscription.status());
        return subscription;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Subscription> findByTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return subscriptionDatastoreHelper.findByTenantId(tenantId);
    }

    @Override
    @Transactional
    public Subscription changePlan(String tenantId, TenantPlan newPlan) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(newPlan, "newPlan must not be null");
        Subscription current = require(tenantId);
        Subscription updated = current.withPlan(newPlan, SubscriptionStatus.ACTIVE);
        subscriptionDatastoreHelper.save(updated);
        log.info("Changed plan for tenant '{}' to {}", tenantId, newPlan);
        return updated;
    }

    @Override
    @Transactional
    public Subscription cancel(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Subscription current = require(tenantId);
        Subscription updated = current.withStatus(SubscriptionStatus.CANCELLED);
        subscriptionDatastoreHelper.save(updated);
        log.info("Cancelled subscription for tenant '{}'", tenantId);
        return updated;
    }

    @Override
    @Transactional
    public Subscription expire(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Subscription current = require(tenantId);
        Subscription updated = current.withStatus(SubscriptionStatus.EXPIRED);
        subscriptionDatastoreHelper.save(updated);
        log.info("Expired subscription for tenant '{}'", tenantId);
        return updated;
    }

    // ── Invoice tracking ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public Invoice saveInvoice(Invoice invoice) {
        Objects.requireNonNull(invoice, "invoice must not be null");
        invoiceDatastoreHelper.save(invoice);
        log.info("Saved invoice '{}' for tenant '{}'", invoice.invoiceId(), invoice.tenantId());
        return invoice;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Invoice> findInvoices(String tenantId, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return invoiceDatastoreHelper.findByTenant(tenantId).stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long countInvoices(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return invoiceDatastoreHelper.countByTenant(tenantId);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteAll(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        subscriptionDatastoreHelper.deleteByTenantId(tenantId);
        invoiceDatastoreHelper.deleteByTenantId(tenantId);
        log.info("Deleted all billing data for tenant '{}'", tenantId);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Subscription require(String tenantId) {
        return subscriptionDatastoreHelper.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantBillingException(tenantId,
                        "No subscription found for tenant: " + tenantId));
    }
}
