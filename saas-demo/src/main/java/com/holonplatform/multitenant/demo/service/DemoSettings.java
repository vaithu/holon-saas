package com.holonplatform.multitenant.demo.service;

import java.util.List;

import com.holonplatform.multitenant.settings.TenantSetting;

/**
 * The settings this demo makes configurable per workspace (F8).
 *
 * <h2>Three tiers, one resolved value</h2>
 * <p>A setting resolves through {@code tenant override → plan default → global default}. The
 * global default is the one declared here, on the {@link TenantSetting} itself; the plan default
 * is stored per plan; the tenant override is stored per workspace. The Settings view shows all
 * three side by side so it is obvious which tier a value came from.
 *
 * <p>Both keys below are ordinary application settings rather than framework ones — the point is
 * that an application defines its own vocabulary and the framework only supplies the resolution
 * and storage.
 */
public final class DemoSettings {

    private DemoSettings() {
    }

    /** Currency label shown next to sale amounts. */
    public static final TenantSetting SALES_CURRENCY = TenantSetting.of(
            "sales.currency", "USD",
            "Currency that sale amounts are recorded in");

    /** Largest discount a salesman may apply, as a whole percentage. */
    public static final TenantSetting SALES_MAX_DISCOUNT_PCT = TenantSetting.of(
            "sales.max_discount_pct", "5",
            "Maximum discount percentage a salesman may apply to a sale");

    /** Every setting the demo knows about, in display order. */
    public static final List<TenantSetting> ALL = List.of(SALES_CURRENCY, SALES_MAX_DISCOUNT_PCT);
}
