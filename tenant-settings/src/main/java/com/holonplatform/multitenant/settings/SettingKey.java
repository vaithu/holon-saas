package com.holonplatform.multitenant.settings;

import java.util.Objects;

/**
 * Typed key that identifies a tenant setting.
 *
 * <p>A {@code SettingKey} carries the setting name, its Java type, and a default
 * value used when no explicit value has been stored at any inheritance level.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Declare constants in a settings catalogue class:
 * public final class AppSettings {
 *     public static final SettingKey<Integer> MAX_USERS =
 *             SettingKey.of("max_users", Integer.class, 5);
 *     public static final SettingKey<Boolean> MFA_REQUIRED =
 *             SettingKey.of("mfa_required", Boolean.class, false);
 * }
 *
 * // Read via TenantSettingsService:
 * int limit = settingsService.get("acme", AppSettings.MAX_USERS);
 * }</pre>
 *
 * @param <T> the Java type of the setting value
 */
public record SettingKey<T>(String name, Class<T> type, T defaultValue) {

    public SettingKey {
        Objects.requireNonNull(name,  "SettingKey.name must not be null");
        Objects.requireNonNull(type,  "SettingKey.type must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("SettingKey.name must not be blank");
        }
    }

    /**
     * Creates a {@code SettingKey} with a non-null default value.
     *
     * @param name         unique setting name
     * @param type         value Java type
     * @param defaultValue value returned when no setting is stored
     * @param <T>          value type
     * @return new key
     */
    public static <T> SettingKey<T> of(String name, Class<T> type, T defaultValue) {
        return new SettingKey<>(name, type, defaultValue);
    }

    /**
     * Creates a {@code SettingKey} with no default value ({@code null}).
     *
     * @param name unique setting name
     * @param type value Java type
     * @param <T>  value type
     * @return new key
     */
    public static <T> SettingKey<T> of(String name, Class<T> type) {
        return new SettingKey<>(name, type, null);
    }
}

