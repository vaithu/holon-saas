package com.holonplatform.multitenant.billing.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.billing.Invoice;
import com.holonplatform.multitenant.billing.InvoiceProperties;
import com.holonplatform.multitenant.billing.InvoiceStatus;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link Invoice} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on invoice management.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.billing.config.TenantBillingAutoConfiguration} rather
 * than {@code @Component}-scanned — this class lives in an internal package outside any
 * consuming application's own base package, so component-scanning would never find it.
 */
public class InvoiceDatastoreHelper {

    private final BeanDatastoreHelper<Invoice> helper;

    public InvoiceDatastoreHelper(Datastore datastore) {
        // Register the exclusion rule before Holon caches this entity's PropertySet, so
        // computed getters (e.g. Invoice#isPaid()) never leak into generated HQL.
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.helper = BeanDatastoreHelper.of(datastore, Invoice.class);
    }

    public Invoice save(Invoice invoice) {
        // Deliberately BeanDatastoreHelper#save (upsert), NOT #insert — the same bug and fix
        // documented on TenantUserDatastoreHelper#save. Invoices are created new, but
        // saveInvoice() is also the only path that persists Invoice#markPaid(...) on an
        // already-persisted invoice; #insert() would fail there with a duplicate-key
        // violation on INVOICE_ID.
        return helper.save(invoice).getResult().orElse(invoice);
    }

    public Optional<Invoice> findById(String invoiceId) {
        return helper.findTop(1, QueryFilter.eq(InvoiceProperties.INVOICE_ID, invoiceId)).findFirst();
    }

    public List<Invoice> findByTenant(String tenantId) {
        return helper.findAll(QueryFilter.eq(InvoiceProperties.TENANT_ID, tenantId),
                InvoiceProperties.ISSUED_AT.desc()).toList();
    }

    public List<Invoice> findByTenantWithPagination(String tenantId, Pageable pageable) {
        return helper.findPage(pageable.getPageNumber(), pageable.getPageSize(),
                QueryFilter.eq(InvoiceProperties.TENANT_ID, tenantId),
                InvoiceProperties.ISSUED_AT.desc()).toList();
    }

    public List<Invoice> findByStatus(String tenantId, InvoiceStatus status) {
        return helper.findAll(
                QueryFilter.eq(InvoiceProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(InvoiceProperties.STATUS, status)),
                InvoiceProperties.ISSUED_AT.desc()
        ).toList();
    }

    public long countByTenant(String tenantId) {
        return helper.count(QueryFilter.eq(InvoiceProperties.TENANT_ID, tenantId));
    }

    public void deleteById(String invoiceId) {
helper.findAll(QueryFilter.eq(InvoiceProperties.INVOICE_ID, invoiceId))
                .forEach(invoice -> helper.delete(invoice));
    }

    public void deleteByTenantId(String tenantId) {
        helper.findAll(QueryFilter.eq(InvoiceProperties.TENANT_ID, tenantId))
                .forEach(invoice -> helper.delete(invoice));
    }
}

