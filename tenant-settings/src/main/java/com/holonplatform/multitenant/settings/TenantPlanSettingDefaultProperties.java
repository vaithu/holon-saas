package com.holonplatform.multitenant.settings;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.PathProperty;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.PropertySet;
import com.holonplatform.multitenant.TenantPlan;

/**
 * Holon {@link PropertySet} interface for {@link TenantPlanSettingDefault}.
 *
 * <p>Defines metadata for plan-level setting defaults including field names, labels,
 * validation rules, and type information. Used by {@code BeanDatastoreHelper}'s
 * query filter and sort methods to key off of the entity's bean property names.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #ID} — Unique identifier (server-generated)</li>
 *   <li>{@link #PLAN} — Tenant plan name (required)</li>
 *   <li>{@link #SETTING_KEY} — Setting key (required, max 255 chars)</li>
 *   <li>{@link #SETTING_VALUE} — Stored plan default value (optional, arbitrary length)</li>
 * </ul>
 */
public interface TenantPlanSettingDefaultProperties {

    // ── Identifiers ────────────────────────────────────────────────────────────

    /**
     * Unique identifier for this plan setting default row (server-generated).
     */
    NumericProperty<Long> ID = NumericProperty.longType("id")
        .messageCode("setting.plan_default.id");

    /**
     * Tenant plan this default applies to. Required.
     *
     * <p>Typed as the enum rather than as a {@code String}, because the entity field is a
     * {@link TenantPlan} mapped with {@code @Enumerated(EnumType.STRING)}. Hibernate validates
     * a query parameter against the <em>field's</em> Java type, not the column type, so
     * declaring this as a {@code StringProperty} made every filter on it fail at runtime with
     * {@code QueryArgumentException: argument [FREE] is not assignable to TenantPlan} — the
     * column's contents look like a string, but the binding never gets that far.
     */
    PathProperty<TenantPlan> PLAN = PathProperty.create("plan", TenantPlan.class)
        .messageCode("setting.plan_default.plan")
        .withValidator(Validator.notNull());

    // ── Setting data ───────────────────────────────────────────────────────────

    /**
     * Dot-notation setting key (e.g. {@code "ui.max_upload_mb"}).
     * Required, max 255 characters.
     */
    StringProperty SETTING_KEY = StringProperty.create("settingKey")
        .messageCode("setting.plan_default.setting_key")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    /**
     * Stored plan-level default value. May be null when not yet set.
     */
    StringProperty SETTING_VALUE = StringProperty.create("settingValue")
        .messageCode("setting.plan_default.setting_value");

    // ── Validators ─────────────────────────────────────────────────────────────

    /**
     * Builds a validator rejecting {@link String} values longer than {@code max} characters.
     * Null values are considered valid (combine with {@link Validator#notBlank()} to also require a value).
     */
    private static Validator<String> maxLength(int max) {
        return Validator.create(value -> value == null || value.length() <= max,
            "must not exceed " + max + " characters");
    }
}
