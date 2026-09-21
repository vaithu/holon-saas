package com.holonplatform.multitenant.settings.config;

import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.settings.TenantPlanSettingDefault;
import com.holonplatform.multitenant.settings.TenantSetting;
import com.holonplatform.multitenant.settings.TenantSettingKeys;
import com.holonplatform.multitenant.settings.TenantSettingOverride;
import com.holonplatform.multitenant.settings.TenantSettingsService;
import com.holonplatform.multitenant.settings.internal.DatabaseTenantSettingsService;
import com.holonplatform.multitenant.settings.internal.TenantPlanSettingDefaultDatastoreHelper;
import com.holonplatform.multitenant.settings.internal.TenantSettingOverrideDatastoreHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link TenantSettingsAutoConfiguration}.
 *
 * <p>Mocked {@link TenantSettingOverrideDatastoreHelper} and
 * {@link TenantPlanSettingDefaultDatastoreHelper} beans stand in for a real Holon
 * Datastore so the context can be exercised without a database.
 */
class TenantSettingsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantSettingsAutoConfiguration.class))
            .withUserConfiguration(DatastoreHelperConfig.class);

    // ── Default bean registration ─────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers DatabaseTenantSettingsService when no custom bean present")
    void autoConfig_registersDefaultSettingsService() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantSettingsService.class);
            assertThat(ctx.getBean(TenantSettingsService.class))
                    .isInstanceOf(DatabaseTenantSettingsService.class);
        });
    }

    // ── Back-off when custom bean present ─────────────────────────────────────

    @Test
    @DisplayName("auto-config backs off when a custom TenantSettingsService bean is declared")
    void autoConfig_backsOff_whenCustomSettingsServicePresent() {
        contextRunner
                .withUserConfiguration(CustomSettingsConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantSettingsService.class);
                    assertThat(ctx.getBean(TenantSettingsService.class))
                            .isInstanceOf(CustomSettingsConfig.CustomSettingsService.class);
                });
    }

    // ── Only one bean registered ──────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers exactly one TenantSettingsService bean")
    void autoConfig_registersSingleBean() {
        contextRunner.run(ctx ->
                assertThat(ctx.getBeansOfType(TenantSettingsService.class)).hasSize(1));
    }

    // ── Registered service is functional ─────────────────────────────────────

    @Test
    @DisplayName("auto-configured settings service returns global default for MAX_UPLOAD_MB")
    void autoConfig_service_returnsGlobalDefault() {
        contextRunner.run(ctx -> {
            TenantSettingsService service = ctx.getBean(TenantSettingsService.class);
            assertThat(service.get("any-tenant", TenantSettingKeys.MAX_UPLOAD_MB))
                    .hasValue("10");
        });
    }

    @Test
    @DisplayName("auto-configured settings service stores and retrieves tenant overrides")
    void autoConfig_service_storesAndRetrieves() {
        contextRunner.run(ctx -> {
            TenantSettingsService service = ctx.getBean(TenantSettingsService.class);
            service.set("acme", TenantSettingKeys.LOGO_URL, "https://logo.acme.com/logo.png");
            assertThat(service.get("acme", TenantSettingKeys.LOGO_URL))
                    .hasValue("https://logo.acme.com/logo.png");
        });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link TenantSettingOverrideDatastoreHelper}
     * and {@link TenantPlanSettingDefaultDatastoreHelper} (which normally require a Holon
     * {@code Datastore} bean) with in-memory-backed mocks, so the auto-configuration can be
     * exercised without a real database.
     */
    static class DatastoreHelperConfig {

        @Bean
        TenantSettingOverrideDatastoreHelper tenantSettingOverrideDatastoreHelper() {
            TenantSettingOverrideDatastoreHelper helper = mock(TenantSettingOverrideDatastoreHelper.class);
            Map<String, TenantSettingOverride> store = new ConcurrentHashMap<>();

            when(helper.save(any(TenantSettingOverride.class))).thenAnswer(inv -> {
                TenantSettingOverride override = inv.getArgument(0);
                store.put(override.getTenantId() + "::" + override.getSettingKey(), override);
                return override;
            });
            when(helper.findByTenantIdAndKey(anyString(), anyString())).thenAnswer(inv ->
                    Optional.ofNullable(store.get(inv.getArgument(0) + "::" + inv.getArgument(1))));
            when(helper.findAllByTenantId(anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0);
                return store.values().stream()
                        .filter(o -> o.getTenantId().equals(tenantId))
                        .toList();
            });
            when(helper.deleteByTenantIdAndKey(anyString(), anyString())).thenAnswer(inv -> {
                boolean removed = store.remove(inv.getArgument(0) + "::" + inv.getArgument(1)) != null;
                return removed ? 1L : 0L;
            });
            when(helper.deleteByTenantId(anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0);
                long count = store.keySet().stream().filter(k -> k.startsWith(tenantId + "::")).count();
                store.keySet().removeIf(k -> k.startsWith(tenantId + "::"));
                return count;
            });
            return helper;
        }

        @Bean
        TenantPlanSettingDefaultDatastoreHelper tenantPlanSettingDefaultDatastoreHelper() {
            TenantPlanSettingDefaultDatastoreHelper helper = mock(TenantPlanSettingDefaultDatastoreHelper.class);
            Map<String, TenantPlanSettingDefault> store = new ConcurrentHashMap<>();

            when(helper.save(any(TenantPlanSettingDefault.class))).thenAnswer(inv -> {
                TenantPlanSettingDefault planDefault = inv.getArgument(0);
                store.put(planDefault.getPlan().name() + "::" + planDefault.getSettingKey(), planDefault);
                return planDefault;
            });
            when(helper.findByPlanAndKey(any(TenantPlan.class), anyString())).thenAnswer(inv ->
                    Optional.ofNullable(store.get(inv.getArgument(0) + "::" + inv.getArgument(1))));
            when(helper.findAllByPlan(any(TenantPlan.class))).thenAnswer(inv -> {
                TenantPlan plan = inv.getArgument(0);
                return store.values().stream()
                        .filter(p -> p.getPlan() == plan)
                        .toList();
            });
            when(helper.deleteByPlanAndKey(any(TenantPlan.class), anyString())).thenAnswer(inv -> {
                boolean removed = store.remove(inv.getArgument(0) + "::" + inv.getArgument(1)) != null;
                return removed ? 1L : 0L;
            });
            return helper;
        }
    }

    static class CustomSettingsConfig {

        @Bean
        TenantSettingsService tenantSettingsService() {
            return new CustomSettingsService();
        }

        static class CustomSettingsService implements TenantSettingsService {

            @Override
            public Optional<String> get(String tenantId, TenantSetting setting) {
                return setting.getDefaultValue();
            }

            @Override
            public Map<String, String> getAll(String tenantId) {
                return Map.of();
            }

            @Override
            public Map<String, String> getPlanDefaults(TenantPlan plan) {
                return Map.of();
            }

            @Override
            public void set(String tenantId, String key, String value) {
            }

            @Override
            public void setPlanDefault(TenantPlan plan, String key, String value) {
            }

            @Override
            public void clearAll(String tenantId) {
            }
        }
    }
}
