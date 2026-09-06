package com.holonplatform.multitenant.settings;

import com.holonplatform.multitenant.TenantPlan;

import java.util.Map;
import java.util.Optional;

/**
 * Service for reading and writing per-tenant settings with three-tier inheritance:
 * <pre>
 *   global default  →  plan override  →  tenant override  →  resolved value
 * </pre>
 *
 * <h2>Reading</h2>
 * <p>Use {@link #get(String, TenantSetting)} to read a typed value with automatic
 * fallback through the inheritance chain.
 *
 * <h2>Writing</h2>
 * <p>Use {@link #set(String, String, String)} to store a tenant-level override.
 * Plan-level defaults are managed separately via {@link #setPlanDefault(TenantPlan, String, String)}.
 *
 * <h2>Implementations</h2>
 * <p>The in-memory default is suitable for tests. Production apps should provide
 * a JPA-backed implementation using the {@code tenant_settings} table created by
 * the Flyway baseline.
 */
public interface TenantSettingsService {

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Resolves the value for {@code setting} for the given tenant.
     * Follows the inheritance chain: tenant override → plan default → global default.
     *
     * @param tenantId the tenant; must not be null
     * @param setting  the setting definition; must not be null
     * @return resolved value, or {@link Optional#empty()} if no value anywhere in the chain
     */
    Optional<String> get(String tenantId, TenantSetting setting);

    /**
     * Short-form read — returns the resolved value or {@code fallback} if absent.
     *
     * @param tenantId the tenant
     * @param setting  the setting definition
     * @param fallback value returned when no value is found
     * @return resolved value, never null if fallback is non-null
     */
    default String getOrDefault(String tenantId, TenantSetting setting, String fallback) {
        return get(tenantId, setting).orElse(fallback);
    }

    /**
     * Returns all settings explicitly set for the given tenant (no inheritance applied).
     *
     * @param tenantId the tenant; must not be null
     * @return mutable snapshot map; never null
     */
    Map<String, String> getAll(String tenantId);

    /**
     * Returns all plan-level defaults for the given plan.
     *
     * @param plan the plan; must not be null
     * @return mutable snapshot map; never null
     */
    Map<String, String> getPlanDefaults(TenantPlan plan);

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Stores a tenant-level setting override.
     *
     * @param tenantId the tenant; must not be null
     * @param key      setting key; must not be null
     * @param value    setting value; null removes the override (falls back to plan/global)
     */
    void set(String tenantId, String key, String value);

    /**
     * Convenience overload using a {@link TenantSetting} definition.
     *
     * @param tenantId the tenant; must not be null
     * @param setting  setting definition; must not be null
     * @param value    value; null removes the override
     */
    default void set(String tenantId, TenantSetting setting, String value) {
        set(tenantId, setting.key(), value);
    }

    /**
     * Stores a plan-level default. Applies to all tenants on that plan
     * unless overridden at the tenant level.
     *
     * @param plan  the plan; must not be null
     * @param key   setting key; must not be null
     * @param value value; null removes the plan-level default
     */
    void setPlanDefault(TenantPlan plan, String key, String value);

    /**
     * Removes all explicit settings for the given tenant.
     * Tenant reverts to plan/global defaults.
     *
     * @param tenantId the tenant; must not be null
     */
    void clearAll(String tenantId);
}

