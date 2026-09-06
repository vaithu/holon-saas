package com.holonplatform.multitenant.settings;

import jakarta.persistence.*;

import java.util.Objects;

/**
 * JPA entity representing a single per-tenant setting override.
 *
 * <p>Each row stores one {@code (tenantId, settingKey)} → {@code settingValue} mapping.
 * A row is only present when the tenant has explicitly overridden the setting; absence
 * of a row means the value falls back to the plan default or the {@link TenantSetting}'s
 * own built-in default.
 */
@Entity(name = "tenantsettingoverride")
@Table(name = "tenant_setting_override", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "setting_key"}, name = "uq_tenant_setting_override_key")
}, indexes = {
    @Index(name = "idx_tenant_setting_override_tenant_id", columnList = "tenant_id")
})
public class TenantSettingOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "setting_key", nullable = false, length = 255)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    // ── Constructors ─────────────────────────────────────────────────────────

    public TenantSettingOverride() {
        // JPA default constructor
    }

    public TenantSettingOverride(String tenantId, String settingKey, String settingValue) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.settingKey = Objects.requireNonNull(settingKey, "settingKey must not be null");
        this.settingValue = settingValue;
    }

    // ── Getters/Setters ──────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Starts a fluent {@link Builder} for a new {@code TenantSettingOverride}.
     */
    public static Builder builder(String tenantId, String settingKey) {
        return new Builder(tenantId, settingKey);
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private final TenantSettingOverride override;

        private Builder(String tenantId, String settingKey) {
            this.override = new TenantSettingOverride();
            this.override.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
            this.override.settingKey = Objects.requireNonNull(settingKey, "settingKey must not be null");
        }

        public Builder settingValue(String value) {
            this.override.settingValue = value;
            return this;
        }

        public TenantSettingOverride build() {
            return override;
        }
    }
}
