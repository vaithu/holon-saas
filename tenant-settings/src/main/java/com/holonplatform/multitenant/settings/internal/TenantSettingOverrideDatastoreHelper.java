package com.holonplatform.multitenant.settings.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;
import com.holonplatform.multitenant.settings.TenantSettingOverride;
import com.holonplatform.multitenant.settings.TenantSettingOverrideProperties;

import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link TenantSettingOverride} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on per-tenant
 * setting overrides using Holon's Properties Model.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.settings.config.TenantSettingsAutoConfiguration}
 * rather than {@code @Component}-scanned — this class lives in an internal package outside
 * any consuming application's own base package, so component-scanning would never find it.
 */
public class TenantSettingOverrideDatastoreHelper {

    private final BeanDatastoreHelper<TenantSettingOverride> helper;

    public TenantSettingOverrideDatastoreHelper(Datastore datastore) {
        // Register the exclusion rule before Holon caches this entity's PropertySet, so
        // computed getters / synthetic Map "empty" sub-properties never leak into HQL.
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.helper = BeanDatastoreHelper.of(datastore, TenantSettingOverride.class);
    }

    /**
     * Inserts or updates the given override (upsert, keyed by the entity identifier).
     */
    public TenantSettingOverride save(TenantSettingOverride override) {
        return helper.save(override).getResult().orElse(override);
    }

    public Optional<TenantSettingOverride> findByTenantIdAndKey(String tenantId, String key) {
        return helper.findTop(1,
                QueryFilter.eq(TenantSettingOverrideProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantSettingOverrideProperties.SETTING_KEY, key)))
                .findFirst();
    }

    public List<TenantSettingOverride> findAllByTenantId(String tenantId) {
        return helper.findAll(QueryFilter.eq(TenantSettingOverrideProperties.TENANT_ID, tenantId)).toList();
    }

    /**
     * Deletes the override for the given tenant and key, if present.
     *
     * @return number of affected rows (0 or 1)
     */
    public long deleteByTenantIdAndKey(String tenantId, String key) {
        return helper.bulkDelete(
                QueryFilter.eq(TenantSettingOverrideProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantSettingOverrideProperties.SETTING_KEY, key)))
                .getAffectedCount();
    }

    /**
     * Deletes all overrides for the given tenant.
     *
     * @return number of affected rows
     */
    public long deleteByTenantId(String tenantId) {
        return helper.bulkDelete(QueryFilter.eq(TenantSettingOverrideProperties.TENANT_ID, tenantId))
                .getAffectedCount();
    }
}
