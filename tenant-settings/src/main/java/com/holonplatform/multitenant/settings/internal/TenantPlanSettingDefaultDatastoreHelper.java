package com.holonplatform.multitenant.settings.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.settings.TenantPlanSettingDefault;
import com.holonplatform.multitenant.settings.TenantPlanSettingDefaultProperties;

import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link TenantPlanSettingDefault} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on plan-level
 * setting defaults using Holon's Properties Model.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.settings.config.TenantSettingsAutoConfiguration}
 * rather than {@code @Component}-scanned — this class lives in an internal package outside
 * any consuming application's own base package, so component-scanning would never find it.
 */
public class TenantPlanSettingDefaultDatastoreHelper {

    private final BeanDatastoreHelper<TenantPlanSettingDefault> helper;

    public TenantPlanSettingDefaultDatastoreHelper(Datastore datastore) {
        this.helper = BeanDatastoreHelper.of(datastore, TenantPlanSettingDefault.class);
    }

    /**
     * Inserts or updates the given plan default (upsert, keyed by the entity identifier).
     */
    public TenantPlanSettingDefault save(TenantPlanSettingDefault planSettingDefault) {
        return helper.save(planSettingDefault).getResult().orElse(planSettingDefault);
    }

    public Optional<TenantPlanSettingDefault> findByPlanAndKey(String plan, String key) {
        return helper.findTop(1,
                QueryFilter.eq(TenantPlanSettingDefaultProperties.PLAN, plan)
                        .and(QueryFilter.eq(TenantPlanSettingDefaultProperties.SETTING_KEY, key)))
                .findFirst();
    }

    public List<TenantPlanSettingDefault> findAllByPlan(String plan) {
        return helper.findAll(QueryFilter.eq(TenantPlanSettingDefaultProperties.PLAN, plan)).toList();
    }

    /**
     * Deletes the plan default for the given plan and key, if present.
     *
     * @return number of affected rows (0 or 1)
     */
    public long deleteByPlanAndKey(String plan, String key) {
        return helper.bulkDelete(
                QueryFilter.eq(TenantPlanSettingDefaultProperties.PLAN, plan)
                        .and(QueryFilter.eq(TenantPlanSettingDefaultProperties.SETTING_KEY, key)))
                .getAffectedCount();
    }
}
