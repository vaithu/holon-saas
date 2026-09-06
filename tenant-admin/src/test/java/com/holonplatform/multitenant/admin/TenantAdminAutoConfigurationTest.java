package com.holonplatform.multitenant.admin;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.admin.internal.DatabaseTenantAdminService;
import com.holonplatform.multitenant.admin.internal.TenantDetailsDatastoreHelper;
import com.holonplatform.multitenant.admin.service.TenantAdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link TenantAdminAutoConfiguration}.
 *
 * <p>A mocked {@link TenantDetailsDatastoreHelper} stands in for a real Holon Datastore so the
 * context can be exercised without a database.
 */
class TenantAdminAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAdminAutoConfiguration.class))
            .withUserConfiguration(DatastoreHelperConfig.class);

    // ── Default bean registration ─────────────────────────────────────────────

    @Test
    @DisplayName("auto-config ensures TenantAdminService bean is available")
    void autoConfig_registersTenantAdminService() {
        contextRunner.run(ctx ->
                assertThat(ctx).hasSingleBean(TenantAdminService.class));
    }

    @Test
    @DisplayName("auto-configured TenantAdminService is a DatabaseTenantAdminService")
    void autoConfig_serviceIsDatabaseImplementation() {
        contextRunner.run(ctx ->
                assertThat(ctx.getBean(TenantAdminService.class))
                        .isInstanceOf(DatabaseTenantAdminService.class));
    }

    // ── Registered service is functional ─────────────────────────────────────

    @Test
    @DisplayName("auto-configured service starts empty (no seeded sample data)")
    void autoConfig_service_startsEmpty() {
        contextRunner.run(ctx -> {
            TenantAdminService service = ctx.getBean(TenantAdminService.class);
            assertThat(service.findAll()).isEmpty();
            assertThat(service.countAll()).isZero();
        });
    }

    @Test
    @DisplayName("auto-configured service can save and retrieve a new tenant")
    void autoConfig_service_saveAndRetrieve() {
        contextRunner.run(ctx -> {
            TenantAdminService service = ctx.getBean(TenantAdminService.class);

            com.holonplatform.multitenant.TenantDetails tenant =
                    com.holonplatform.multitenant.TenantDetails.builder("autoconf-test")
                            .name("AutoConf Test")
                            .plan(com.holonplatform.multitenant.TenantPlan.FREE)
                            .status(com.holonplatform.multitenant.TenantStatus.ACTIVE)
                            .build();

            service.save(tenant);
            assertThat(service.findById("autoconf-test")).isPresent();
        });
    }

    @Test
    @DisplayName("auto-configured service findAll returns list sorted by name")
    void autoConfig_service_findAllSortedByName() {
        contextRunner.run(ctx -> {
            TenantAdminService service = ctx.getBean(TenantAdminService.class);

            service.save(TenantDetails.builder("vehement")
                    .name("Vehement Capital")
                    .plan(com.holonplatform.multitenant.TenantPlan.ENTERPRISE)
                    .status(com.holonplatform.multitenant.TenantStatus.ACTIVE)
                    .build());
            service.save(TenantDetails.builder("acme")
                    .name("Acme Corporation")
                    .plan(com.holonplatform.multitenant.TenantPlan.ENTERPRISE)
                    .status(com.holonplatform.multitenant.TenantStatus.ACTIVE)
                    .build());
            service.save(TenantDetails.builder("globex")
                    .name("Globex Corp")
                    .plan(com.holonplatform.multitenant.TenantPlan.PROFESSIONAL)
                    .status(com.holonplatform.multitenant.TenantStatus.ACTIVE)
                    .build());

            List<com.holonplatform.multitenant.TenantDetails> all = service.findAll();
            List<String> names = all.stream()
                    .map(t -> t.name() != null ? t.name() : t.tenantId())
                    .toList();
            assertThat(names).isSortedAccordingTo(String::compareToIgnoreCase);
        });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link TenantDetailsDatastoreHelper}
     * (which normally requires a Holon {@code Datastore} bean) with an in-memory-backed mock,
     * so the auto-configuration can be exercised without a real database.
     */
    static class DatastoreHelperConfig {

        @Bean
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
            when(helper.findAll()).thenAnswer(inv -> List.copyOf(store.values()));
            when(helper.count()).thenAnswer(inv -> (long) store.size());
            return helper;
        }
    }
}
