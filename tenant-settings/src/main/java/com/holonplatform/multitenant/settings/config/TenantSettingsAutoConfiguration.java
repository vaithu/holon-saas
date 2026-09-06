package com.holonplatform.multitenant.settings.config;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.settings.TenantSettingsService;
import com.holonplatform.multitenant.settings.internal.DatabaseTenantSettingsService;
import com.holonplatform.multitenant.settings.internal.TenantPlanSettingDefaultDatastoreHelper;
import com.holonplatform.multitenant.settings.internal.TenantSettingOverrideDatastoreHelper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for the tenant settings subsystem.
 *
 * <p>Registers a single {@link TenantSettingsService} bean when none is already present:
 * {@link DatabaseTenantSettingsService}, backed by Holon Datastore via
 * {@link TenantSettingOverrideDatastoreHelper} and {@link TenantPlanSettingDefaultDatastoreHelper}.
 * A {@code Datastore} bean must be configured (e.g. via {@code holon-datastore-jpa}) for this
 * module to start.
 */
@AutoConfiguration
@Import(TenantSettingsEntityScanRegistrar.class)
public class TenantSettingsAutoConfiguration {

    /**
     * Registers {@link TenantSettingOverrideDatastoreHelper} and
     * {@link TenantPlanSettingDefaultDatastoreHelper} explicitly — neither is
     * {@code @Component}-scanned (they live in an internal package outside any consuming
     * application's own base package), so this module must construct them itself.
     */
    @Bean
    @ConditionalOnMissingBean(TenantSettingOverrideDatastoreHelper.class)
    public TenantSettingOverrideDatastoreHelper tenantSettingOverrideDatastoreHelper(Datastore datastore) {
        return new TenantSettingOverrideDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(TenantPlanSettingDefaultDatastoreHelper.class)
    public TenantPlanSettingDefaultDatastoreHelper tenantPlanSettingDefaultDatastoreHelper(Datastore datastore) {
        return new TenantPlanSettingDefaultDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(TenantSettingsService.class)
    public TenantSettingsService databaseTenantSettingsService(TenantSettingOverrideDatastoreHelper overrideHelper,
            TenantPlanSettingDefaultDatastoreHelper planDefaultHelper) {
        return new DatabaseTenantSettingsService(overrideHelper, planDefaultHelper);
    }
}

