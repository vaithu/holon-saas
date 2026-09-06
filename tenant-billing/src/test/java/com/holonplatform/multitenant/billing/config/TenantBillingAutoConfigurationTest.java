package com.holonplatform.multitenant.billing.config;

import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.billing.Invoice;
import com.holonplatform.multitenant.billing.Subscription;
import com.holonplatform.multitenant.billing.SubscriptionStatus;
import com.holonplatform.multitenant.billing.TenantBillingService;
import com.holonplatform.multitenant.billing.internal.DatabaseTenantBillingService;
import com.holonplatform.multitenant.billing.internal.InvoiceDatastoreHelper;
import com.holonplatform.multitenant.billing.internal.SubscriptionDatastoreHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link TenantBillingAutoConfiguration}.
 *
 * <p>Mocked {@link SubscriptionDatastoreHelper} and {@link InvoiceDatastoreHelper} beans
 * stand in for a real Holon Datastore so the context can be exercised without a database.
 */
class TenantBillingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantBillingAutoConfiguration.class))
            .withUserConfiguration(DatastoreHelperConfig.class);

    // ── Default bean registration ─────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers DatabaseTenantBillingService when no custom bean present")
    void autoConfig_registersDefaultBillingService() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantBillingService.class);
            assertThat(ctx.getBean(TenantBillingService.class))
                    .isInstanceOf(DatabaseTenantBillingService.class);
        });
    }

    // ── Back-off when custom bean present ─────────────────────────────────────

    @Test
    @DisplayName("auto-config backs off when a custom TenantBillingService bean is declared")
    void autoConfig_backsOff_whenCustomBillingServicePresent() {
        contextRunner
                .withUserConfiguration(CustomBillingConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantBillingService.class);
                    assertThat(ctx.getBean(TenantBillingService.class))
                            .isInstanceOf(CustomBillingConfig.NoOpBillingService.class);
                });
    }

    // ── Only one bean registered ──────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers exactly one TenantBillingService bean")
    void autoConfig_registersSingleBean() {
        contextRunner.run(ctx ->
                assertThat(ctx.getBeansOfType(TenantBillingService.class)).hasSize(1));
    }

    // ── Registered service is functional ─────────────────────────────────────

    @Test
    @DisplayName("auto-configured service can save and retrieve subscriptions")
    void autoConfig_service_isFunctional() {
        contextRunner.run(ctx -> {
            TenantBillingService service = ctx.getBean(TenantBillingService.class);
            service.save(Subscription.builder("acme")
                    .plan(TenantPlan.STARTER)
                    .status(SubscriptionStatus.ACTIVE)
                    .periodStart(Instant.now())
                    .build());
            assertThat(service.findByTenant("acme")).isPresent();
        });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link SubscriptionDatastoreHelper}
     * and {@link InvoiceDatastoreHelper} (which normally require a Holon {@code Datastore}
     * bean) with in-memory-backed mocks, so the auto-configuration can be exercised without
     * a real database.
     */
    static class DatastoreHelperConfig {

        @Bean
        SubscriptionDatastoreHelper subscriptionDatastoreHelper() {
            SubscriptionDatastoreHelper helper = mock(SubscriptionDatastoreHelper.class);
            java.util.Map<String, Subscription> store = new java.util.concurrent.ConcurrentHashMap<>();

            when(helper.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription subscription = inv.getArgument(0);
                store.put(subscription.tenantId(), subscription);
                return subscription;
            });
            when(helper.findByTenantId(anyString())).thenAnswer(inv ->
                    Optional.ofNullable(store.get(inv.getArgument(0))));
            return helper;
        }

        @Bean
        InvoiceDatastoreHelper invoiceDatastoreHelper() {
            InvoiceDatastoreHelper helper = mock(InvoiceDatastoreHelper.class);
            when(helper.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));
            when(helper.findByTenant(anyString())).thenReturn(List.of());
            when(helper.countByTenant(anyString())).thenReturn(0L);
            return helper;
        }
    }

    static class CustomBillingConfig {

        @Bean
        TenantBillingService tenantBillingService() {
            return new NoOpBillingService();
        }

        static class NoOpBillingService implements TenantBillingService {

            @Override
            public Subscription save(Subscription subscription) {
                return subscription;
            }

            @Override
            public Optional<Subscription> findByTenant(String tenantId) {
                return Optional.empty();
            }

            @Override
            public Subscription changePlan(String tenantId, TenantPlan newPlan) {
                return null;
            }

            @Override
            public Subscription cancel(String tenantId) {
                return null;
            }

            @Override
            public Subscription expire(String tenantId) {
                return null;
            }

            @Override
            public Invoice saveInvoice(Invoice invoice) {
                return invoice;
            }

            @Override
            public List<Invoice> findInvoices(String tenantId, int offset, int limit) {
                return List.of();
            }

            @Override
            public long countInvoices(String tenantId) {
                return 0;
            }

            @Override
            public void deleteAll(String tenantId) {
            }
        }
    }
}
