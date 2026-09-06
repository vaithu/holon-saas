package com.holonplatform.multitenant.settings;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.PropertySet;

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
     * Tenant plan name this default applies to (stored as the enum's {@code name()}).
     * Required.
     */
    StringProperty PLAN = StringProperty.create("plan")
        .messageCode("setting.plan_default.plan")
        .withValidator(Validator.notBlank());

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
