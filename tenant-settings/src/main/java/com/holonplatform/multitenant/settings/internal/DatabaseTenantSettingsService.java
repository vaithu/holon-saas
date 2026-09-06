package com.holonplatform.multitenant.settings.internal;

import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.settings.TenantPlanSettingDefault;
import com.holonplatform.multitenant.settings.TenantSetting;
import com.holonplatform.multitenant.settings.TenantSettingOverride;
import com.holonplatform.multitenant.settings.TenantSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Database-backed {@link TenantSettingsService} implementation using Holon Datastore.
 *
 * <p>Persists tenant-level overrides via {@link TenantSettingOverrideDatastoreHelper} and
 * plan-level defaults via {@link TenantPlanSettingDefaultDatastoreHelper}, replicating the
 * three-tier inheritance chain (tenant override → plan default → global default) previously
 * implemented in-memory.
 */
@Service
public class DatabaseTenantSettingsService implements TenantSettingsService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTenantSettingsService.class);

    private final TenantSettingOverrideDatastoreHelper overrideHelper;
    private final TenantPlanSettingDefaultDatastoreHelper planDefaultHelper;

    public DatabaseTenantSettingsService(TenantSettingOverrideDatastoreHelper overrideHelper,
            TenantPlanSettingDefaultDatastoreHelper planDefaultHelper) {
        this.overrideHelper = Objects.requireNonNull(overrideHelper, "overrideHelper must not be null");
        this.planDefaultHelper = Objects.requireNonNull(planDefaultHelper, "planDefaultHelper must not be null");
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<String> get(String tenantId, TenantSetting setting) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(setting, "setting must not be null");

        // 1. Tenant override
        Optional<String> tenantValue = overrideHelper.findByTenantIdAndKey(tenantId, setting.key())
                .map(TenantSettingOverride::getSettingValue);
        if (tenantValue.isPresent()) {
            return tenantValue;
        }

        // 2. Plan default (we don't know the plan here without a tenant lookup —
        //    callers who have the plan should use get(tenantId, plan, setting) directly).
        // Plan lookup is intentionally not done here to keep this method tenant-plan-agnostic.

        // 3. Global default from the TenantSetting record
        return setting.getDefaultValue();
    }

    /**
     * Plan-aware resolution — resolves tenant override → plan default → global default.
     *
     * @param tenantId the tenant
     * @param plan     the tenant's current plan (pass null to skip plan tier)
     * @param setting  setting definition
     * @return resolved value, or empty if nowhere found
     */
    @Transactional(readOnly = true)
    public Optional<String> get(String tenantId, TenantPlan plan, TenantSetting setting) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(setting, "setting must not be null");

        // 1. Tenant override
        Optional<String> tenantValue = overrideHelper.findByTenantIdAndKey(tenantId, setting.key())
                .map(TenantSettingOverride::getSettingValue);
        if (tenantValue.isPresent()) {
            return tenantValue;
        }

        // 2. Plan default
        if (plan != null) {
            Optional<String> planValue = planDefaultHelper.findByPlanAndKey(plan.name(), setting.key())
                    .map(TenantPlanSettingDefault::getSettingValue);
            if (planValue.isPresent()) {
                return planValue;
            }
        }

        // 3. Global default
        return setting.getDefaultValue();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getAll(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Map<String, String> result = new HashMap<>();
        for (TenantSettingOverride override : overrideHelper.findAllByTenantId(tenantId)) {
            result.put(override.getSettingKey(), override.getSettingValue());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getPlanDefaults(TenantPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        Map<String, String> result = new HashMap<>();
        for (TenantPlanSettingDefault planDefault : planDefaultHelper.findAllByPlan(plan.name())) {
            result.put(planDefault.getSettingKey(), planDefault.getSettingValue());
        }
        return result;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void set(String tenantId, String key, String value) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (value == null) {
            overrideHelper.deleteByTenantIdAndKey(tenantId, key);
            log.debug("Removed setting '{}' for tenant '{}'", key, tenantId);
        } else {
            TenantSettingOverride override = overrideHelper.findByTenantIdAndKey(tenantId, key)
                    .orElseGet(() -> TenantSettingOverride.builder(tenantId, key).build());
            override.setSettingValue(value);
            overrideHelper.save(override);
            log.debug("Set '{}' = '{}' for tenant '{}'", key, value, tenantId);
        }
    }

    @Override
    @Transactional
    public void setPlanDefault(TenantPlan plan, String key, String value) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(key, "key must not be null");
        if (value == null) {
            planDefaultHelper.deleteByPlanAndKey(plan.name(), key);
        } else {
            TenantPlanSettingDefault planDefault = planDefaultHelper.findByPlanAndKey(plan.name(), key)
                    .orElseGet(() -> TenantPlanSettingDefault.builder(plan, key).build());
            planDefault.setSettingValue(value);
            planDefaultHelper.save(planDefault);
        }
    }

    @Override
    @Transactional
    public void clearAll(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        overrideHelper.deleteByTenantId(tenantId);
        log.info("Cleared all settings for tenant '{}'", tenantId);
    }
}
