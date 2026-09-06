package com.holonplatform.multitenant.settings;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.PropertySet;

/**
 * Holon {@link PropertySet} interface for {@link TenantSettingOverride}.
 *
 * <p>Defines metadata for tenant setting overrides including field names, labels,
 * validation rules, and type information. Used by {@code BeanDatastoreHelper}'s
 * query filter and sort methods to key off of the entity's bean property names.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #ID} — Unique identifier (server-generated)</li>
 *   <li>{@link #TENANT_ID} — Tenant identifier (required, max 255 chars)</li>
 *   <li>{@link #SETTING_KEY} — Setting key (required, max 255 chars)</li>
 *   <li>{@link #SETTING_VALUE} — Stored override value (optional, arbitrary length)</li>
 * </ul>
 */
public interface TenantSettingOverrideProperties {

    // ── Identifiers ────────────────────────────────────────────────────────────

    /**
     * Unique identifier for this tenant setting override row (server-generated).
     */
    NumericProperty<Long> ID = NumericProperty.longType("id")
        .messageCode("setting.override.id");

    /**
     * Tenant identifier this override belongs to.
     * Required, max 255 characters.
     */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .messageCode("setting.override.tenant_id")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    // ── Setting data ───────────────────────────────────────────────────────────

    /**
     * Dot-notation setting key (e.g. {@code "ui.max_upload_mb"}).
     * Required, max 255 characters.
     */
    StringProperty SETTING_KEY = StringProperty.create("settingKey")
        .messageCode("setting.override.setting_key")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    /**
     * Stored tenant-level override value. May be null when not yet set.
     */
    StringProperty SETTING_VALUE = StringProperty.create("settingValue")
        .messageCode("setting.override.setting_value");

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
