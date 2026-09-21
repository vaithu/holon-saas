package com.holonplatform.multitenant.billing.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.billing.Subscription;
import com.holonplatform.multitenant.billing.SubscriptionProperties;
import com.holonplatform.multitenant.billing.SubscriptionStatus;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link Subscription} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on subscription management.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.billing.config.TenantBillingAutoConfiguration} rather
 * than {@code @Component}-scanned — this class lives in an internal package outside any
 * consuming application's own base package, so component-scanning would never find it.
 */
public class SubscriptionDatastoreHelper {

    private final BeanDatastoreHelper<Subscription> helper;

    public SubscriptionDatastoreHelper(Datastore datastore) {
        // Register the exclusion rule before Holon caches this entity's PropertySet, so
        // computed getters (e.g. Subscription#isTrialing()/isAccessible()) never leak into HQL.
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.helper = BeanDatastoreHelper.of(datastore, Subscription.class);
    }

    public Subscription save(Subscription subscription) {
        // Deliberately BeanDatastoreHelper#save (upsert), NOT #insert — the same bug and fix
        // documented on TenantUserDatastoreHelper#save. createSubscription() passes a
        // brand-new Subscription (insert is correct), but changePlan()/cancel()/expire() all
        // first load the EXISTING subscription, derive a mutated copy and call this same
        // method; #insert() on an already-persisted row fails with a duplicate-key violation
        // ("Detached entity passed to persist"), so every plan change, cancellation and
        // expiry would fail at runtime.
        return helper.save(subscription).getResult().orElse(subscription);
    }

    public Optional<Subscription> findByTenantId(String tenantId) {
        return helper.findTop(1, QueryFilter.eq(SubscriptionProperties.TENANT_ID, tenantId)).findFirst();
    }

    public List<Subscription> findByStatus(SubscriptionStatus status) {
        return helper.findAll(QueryFilter.eq(SubscriptionProperties.STATUS, status)).toList();
    }

    public List<Subscription> findByStatusWithPagination(SubscriptionStatus status, Pageable pageable) {
        return helper.findPage(pageable.getPageNumber(), pageable.getPageSize(), SubscriptionProperties.STATUS.eq(status))
                .toList();
    }

    public long countByStatus(SubscriptionStatus status) {
        return helper.count(QueryFilter.eq(SubscriptionProperties.STATUS, status));
    }

    public void deleteByTenantId(String tenantId) {
        helper.findAll(QueryFilter.eq(SubscriptionProperties.TENANT_ID, tenantId))
                        .forEach(subscription -> helper.delete(subscription));
    }
}






