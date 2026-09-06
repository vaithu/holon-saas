package com.holonplatform.multitenant.settings;

import com.holonplatform.multitenant.TenantPlan;
import jakarta.persistence.*;

import java.util.Objects;

/**
 * JPA entity representing a single plan-level default setting value.
 *
 * <p>Each row stores one {@code (plan, settingKey)} → {@code settingValue} mapping,
 * applied to all tenants on that {@link TenantPlan} unless overridden at the tenant
 * level (see {@link TenantSettingOverride}).
 */
@Entity(name = "tenantplansettingdefault")
@Table(name = "tenant_plan_setting_default", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"plan", "setting_key"}, name = "uq_tenant_plan_setting_default_key")
})
public class TenantPlanSettingDefault {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private TenantPlan plan;

    @Column(name = "setting_key", nullable = false, length = 255)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    // ── Constructors ─────────────────────────────────────────────────────────

    public TenantPlanSettingDefault() {
        // JPA default constructor
    }

    public TenantPlanSettingDefault(TenantPlan plan, String settingKey, String settingValue) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
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

    public TenantPlan getPlan() {
        return plan;
    }

    public void setPlan(TenantPlan plan) {
        this.plan = plan;
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
     * Starts a fluent {@link Builder} for a new {@code TenantPlanSettingDefault}.
     */
    public static Builder builder(TenantPlan plan, String settingKey) {
        return new Builder(plan, settingKey);
    }

    // ── Builder ──────────────────────────────────────────────────────────────

    public static final class Builder {
        private final TenantPlanSettingDefault planSettingDefault;

        private Builder(TenantPlan plan, String settingKey) {
            this.planSettingDefault = new TenantPlanSettingDefault();
            this.planSettingDefault.plan = Objects.requireNonNull(plan, "plan must not be null");
            this.planSettingDefault.settingKey = Objects.requireNonNull(settingKey, "settingKey must not be null");
        }

        public Builder settingValue(String value) {
            this.planSettingDefault.settingValue = value;
            return this;
        }

        public TenantPlanSettingDefault build() {
            return planSettingDefault;
        }
    }
}
