package com.holonplatform.multitenant.settings;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable definition of a tenant setting key with its metadata.
 *
 * <p>Settings follow a three-tier inheritance model:
 * <ol>
 *   <li><strong>Global defaults</strong> — hard-coded or loaded from {@code application.yml}</li>
 *   <li><strong>Plan defaults</strong> — values set at the {@link com.holonplatform.multitenant.TenantPlan} level</li>
 *   <li><strong>Tenant overrides</strong> — per-tenant values that take highest precedence</li>
 * </ol>
 *
 * <p>Pre-defined keys live in {@link TenantSettingKeys}. Applications add custom
 * keys by calling {@code TenantSetting.of("my.key", "default", "Description")}.
 *
 * @param key          dot-notation key (e.g. {@code "ui.max_upload_mb"})
 * @param defaultValue fallback value when no override is present; may be null
 * @param description  human-readable documentation
 */
public record TenantSetting(String key, String defaultValue, String description) {

    public TenantSetting {
        Objects.requireNonNull(key, "key must not be null");
        if (key.isBlank()) throw new IllegalArgumentException("key must not be blank");
    }

    /**
     * Factory shorthand.
     *
     * @param key          dot-notation key
     * @param defaultValue default (may be null)
     * @param description  documentation
     */
    public static TenantSetting of(String key, String defaultValue, String description) {
        return new TenantSetting(key, defaultValue, description);
    }

    /** Returns the defaultValue wrapped in Optional. */
    public Optional<String> getDefaultValue() {
        return Optional.ofNullable(defaultValue);
    }
}

