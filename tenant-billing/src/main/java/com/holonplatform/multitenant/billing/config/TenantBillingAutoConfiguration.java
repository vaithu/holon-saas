package com.holonplatform.multitenant.billing.config;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.billing.TenantBillingService;
import com.holonplatform.multitenant.billing.internal.DatabaseTenantBillingService;
import com.holonplatform.multitenant.billing.internal.InvoiceDatastoreHelper;
import com.holonplatform.multitenant.billing.internal.SubscriptionDatastoreHelper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for the tenant billing subsystem.
 *
 * <p>Registers a single {@link TenantBillingService} bean when none is already present:
 * {@link DatabaseTenantBillingService}, backed by Holon Datastore via
 * {@link SubscriptionDatastoreHelper} and {@link InvoiceDatastoreHelper}. A {@code Datastore}
 * bean must be configured (e.g. via {@code holon-datastore-jpa}) for this module to start.
 */
@AutoConfiguration
@Import(TenantBillingEntityScanRegistrar.class)
public class TenantBillingAutoConfiguration {

    /**
     * Registers {@link SubscriptionDatastoreHelper} and {@link InvoiceDatastoreHelper}
     * explicitly — neither is {@code @Component}-scanned (they live in an internal package
     * outside any consuming application's own base package), so this module must construct
     * them itself.
     */
    @Bean
    @ConditionalOnMissingBean(SubscriptionDatastoreHelper.class)
    public SubscriptionDatastoreHelper subscriptionDatastoreHelper(Datastore datastore) {
        return new SubscriptionDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(InvoiceDatastoreHelper.class)
    public InvoiceDatastoreHelper invoiceDatastoreHelper(Datastore datastore) {
        return new InvoiceDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(TenantBillingService.class)
    public TenantBillingService databaseTenantBillingService(
            SubscriptionDatastoreHelper subscriptionDatastoreHelper,
            InvoiceDatastoreHelper invoiceDatastoreHelper) {
        return new DatabaseTenantBillingService(subscriptionDatastoreHelper, invoiceDatastoreHelper);
    }
}

