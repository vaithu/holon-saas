package com.holonplatform.multitenant.onboarding.config;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.onboarding.TenantOnboardingService;
import com.holonplatform.multitenant.onboarding.TenantRepository;
import com.holonplatform.multitenant.onboarding.internal.DatabaseTenantRepository;
import com.holonplatform.multitenant.onboarding.internal.DefaultTenantOnboardingService;
import com.holonplatform.multitenant.onboarding.internal.TenantDetailsDatastoreHelper;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.onboarding.TenantOnboardingRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link TenantOnboardingAutoConfiguration}.
 *
 * <p>A mocked {@link TenantDetailsDatastoreHelper} stands in for a real Holon Datastore so the
 * context can be exercised without a database.
 */
class TenantOnboardingAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantOnboardingAutoConfiguration.class))
            .withUserConfiguration(DatastoreHelperConfig.class);

    // ── TenantRepository ──────────────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers DatabaseTenantRepository as default")
    void autoConfig_registersDefaultTenantRepository() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantRepository.class);
            assertThat(ctx.getBean(TenantRepository.class))
                    .isInstanceOf(DatabaseTenantRepository.class);
        });
    }

    @Test
    @DisplayName("auto-config backs off TenantRepository when custom bean is declared")
    void autoConfig_backsOff_whenCustomTenantRepositoryPresent() {
        contextRunner
                .withUserConfiguration(CustomRepositoryConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantRepository.class);
                    assertThat(ctx.getBean(TenantRepository.class))
                            .isInstanceOf(CustomRepositoryConfig.CustomRepository.class);
                });
    }

    // ── TenantOnboardingService ───────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers DefaultTenantOnboardingService")
    void autoConfig_registersDefaultOnboardingService() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantOnboardingService.class);
            assertThat(ctx.getBean(TenantOnboardingService.class))
                    .isInstanceOf(DefaultTenantOnboardingService.class);
        });
    }

    @Test
    @DisplayName("auto-config backs off TenantOnboardingService when custom bean is declared")
    void autoConfig_backsOff_whenCustomOnboardingServicePresent() {
        contextRunner
                .withUserConfiguration(CustomOnboardingServiceConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantOnboardingService.class);
                    assertThat(ctx.getBean(TenantOnboardingService.class))
                            .isInstanceOf(CustomOnboardingServiceConfig.CustomService.class);
                });
    }

    // ── No TenantSchemaInitializer present (shared-schema mode) ───────────────

    @Test
    @DisplayName("auto-config works without TenantSchemaInitializer (shared-schema mode)")
    void autoConfig_worksWithoutSchemaInitializer() {
        contextRunner.run(ctx -> {
            // No TenantSchemaInitializer on classpath — service should still be registered
            assertThat(ctx).hasSingleBean(TenantOnboardingService.class);

            // And it should be functional: onboard without Flyway
            TenantOnboardingService service = ctx.getBean(TenantOnboardingService.class);
            var result = service.onboard(TenantOnboardingRequest.builder("test-tenant")
                    .plan(TenantPlan.FREE).build());
            assertThat(result.success()).isTrue();
        });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link TenantDetailsDatastoreHelper}
     * (which normally requires a Holon {@code Datastore} bean) with an in-memory-backed mock,
     * so the auto-configuration can be exercised without a real database.
     */
    static class DatastoreHelperConfig {

        @org.springframework.context.annotation.Bean
        TenantDetailsDatastoreHelper tenantDetailsDatastoreHelper() {
            TenantDetailsDatastoreHelper helper = mock(TenantDetailsDatastoreHelper.class);
            Map<String, TenantDetails> store = new ConcurrentHashMap<>();

            when(helper.save(any(TenantDetails.class))).thenAnswer(inv -> {
                TenantDetails tenant = inv.getArgument(0);
                store.put(tenant.tenantId(), tenant);
                return tenant;
            });
            when(helper.findById(anyString())).thenAnswer(inv ->
                    Optional.ofNullable(store.get((String) inv.getArgument(0))));
            when(helper.exists(anyString())).thenAnswer(inv ->
                    store.containsKey((String) inv.getArgument(0)));
            return helper;
        }
    }

    static class CustomRepositoryConfig {
        @org.springframework.context.annotation.Bean
        TenantRepository tenantRepository() {
            return new CustomRepository();
        }

        static class CustomRepository implements TenantRepository {
            private final Map<String, TenantDetails> store = new ConcurrentHashMap<>();

            @Override
            public TenantDetails save(TenantDetails tenant) {
                store.put(tenant.tenantId(), tenant);
                return tenant;
            }

            @Override
            public Optional<TenantDetails> findById(String tenantId) {
                return Optional.ofNullable(store.get(tenantId));
            }

            @Override
            public boolean exists(String tenantId) {
                return store.containsKey(tenantId);
            }
        }
    }

    static class CustomOnboardingServiceConfig {
        @org.springframework.context.annotation.Bean
        TenantOnboardingService tenantOnboardingService() {
            return new CustomService();
        }

        static class CustomService implements TenantOnboardingService {
            @Override
            public com.holonplatform.multitenant.onboarding.TenantOnboardingResult onboard(
                    TenantOnboardingRequest request) {
                return com.holonplatform.multitenant.onboarding.TenantOnboardingResult
                        .success(com.holonplatform.multitenant.TenantDetails.builder(request.tenantId())
                                .plan(request.plan()).build());
            }

            @Override
            public boolean isOnboarded(String tenantId) { return false; }
        }
    }
}

