package com.holonplatform.multitenant.quotas;

import com.holonplatform.multitenant.TenantPlan;

import java.util.Map;
import java.util.Objects;

/**
 * Defines the resource limit for a specific {@link QuotaKey} per {@link TenantPlan}.
 *
 * <p>Use {@code -1L} as the limit for a plan to mean <em>unlimited</em>.</p>
 *
 * <p>Example — registering a contact quota:
 * <pre>{@code
 * QuotaDefinition contactQuota = QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
 *     .limit(TenantPlan.FREE,         500L)
 *     .limit(TenantPlan.STARTER,     5_000L)
 *     .limit(TenantPlan.PROFESSIONAL,50_000L)
 *     .limit(TenantPlan.ENTERPRISE,  -1L)     // unlimited
 *     .build();
 *
 * quotaService.registerQuota(contactQuota);
 * }</pre>
 */
public record QuotaDefinition(QuotaKey key, Map<TenantPlan, Long> limits) {

    public QuotaDefinition {
        Objects.requireNonNull(key,    "key must not be null");
        Objects.requireNonNull(limits, "limits must not be null");
        limits = Map.copyOf(limits);
    }

    /**
     * Returns the limit for the given plan.
     * Returns {@code -1L} (unlimited) if no limit is defined for the plan.
     */
    public long getLimit(TenantPlan plan) {
        return limits.getOrDefault(plan, -1L);
    }

    /** Returns {@code true} if the plan has unlimited quota ({@code -1}). */
    public boolean isUnlimited(TenantPlan plan) {
        return getLimit(plan) == -1L;
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static Builder builder(QuotaKey key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final QuotaKey key;
        private final java.util.HashMap<TenantPlan, Long> limits = new java.util.HashMap<>();

        private Builder(QuotaKey key) {
            this.key = Objects.requireNonNull(key, "key must not be null");
        }

        public Builder limit(TenantPlan plan, long value) {
            limits.put(Objects.requireNonNull(plan), value);
            return this;
        }

        /** Sets the same limit for all plans (useful for uniform enforcement). */
        public Builder allPlans(long value) {
            for (TenantPlan plan : TenantPlan.values()) {
                limits.put(plan, value);
            }
            return this;
        }

        public QuotaDefinition build() {
            return new QuotaDefinition(key, Map.copyOf(limits));
        }
    }
}

