package com.holonplatform.multitenant.billing.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.billing.Subscription;
import com.holonplatform.multitenant.billing.SubscriptionProperties;
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
        return helper.insert(subscription).getResult().orElse(subscription);
    }

    public Optional<Subscription> findByTenantId(String tenantId) {
        return helper.findTop(1, QueryFilter.eq(SubscriptionProperties.TENANT_ID, tenantId)).findFirst();
    }

    public List<Subscription> findByStatus(String status) {
        return helper.findAll(QueryFilter.eq(SubscriptionProperties.STATUS, status)).toList();
    }

    public List<Subscription> findByStatusWithPagination(String status, Pageable pageable) {
        return helper.findPage(pageable.getPageNumber(), pageable.getPageSize(), SubscriptionProperties.STATUS.eq(status))
                .toList();
    }

    public long countByStatus(String status) {
        return helper.count(QueryFilter.eq(SubscriptionProperties.STATUS, status));
    }

    public void deleteByTenantId(String tenantId) {
        helper.findAll(QueryFilter.eq(SubscriptionProperties.TENANT_ID, tenantId))
                        .forEach(subscription -> helper.delete(subscription));
    }
}






