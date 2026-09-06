package com.holonplatform.multitenant.onboarding;

import com.holonplatform.multitenant.TenantPlan;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validated request to onboard a new tenant into the system.
 *
 * <p>Use the static {@link #builder(String)} factory to construct instances.
 * All validation (non-null, non-blank tenantId; non-null plan) is enforced
 * at build time so callers receive a {@link IllegalArgumentException} early
 * rather than during the onboarding transaction.
 *
 * @param tenantId   URL-safe unique tenant identifier (e.g. {@code "acme"})
 * @param name       human-readable display name (nullable — defaults to tenantId)
 * @param plan       subscription plan (must not be null)
 * @param adminEmail optional admin user email for post-migration seeding (nullable)
 * @param locale     preferred IETF language tag, e.g. {@code "en-US"} (nullable)
 * @param timezone   preferred IANA timezone ID, e.g. {@code "America/New_York"} (nullable)
 * @param attributes arbitrary metadata for application-specific use (immutable copy)
 */
public record TenantOnboardingRequest(
        String tenantId,
        String name,
        TenantPlan plan,
        String adminEmail,
        String locale,
        String timezone,
        Map<String, String> attributes
) {

    /** Canonical constructor — defensive copy + validation. */
    public TenantOnboardingRequest {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(plan, "plan must not be null");
        attributes = attributes != null
                ? Map.copyOf(attributes)
                : Map.of();
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Creates a {@link Builder} for the given tenant ID.
     *
     * @param tenantId URL-safe unique tenant identifier; must not be null or blank
     */
    public static Builder builder(String tenantId) {
        return new Builder(tenantId);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Fluent builder for {@link TenantOnboardingRequest}. */
    public static final class Builder {

        private final String              tenantId;
        private       String              name;
        private       TenantPlan          plan     = TenantPlan.FREE;
        private       String              adminEmail;
        private       String              locale;
        private       String              timezone;
        private final Map<String, String> attributes = new HashMap<>();

        private Builder(String tenantId) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        }

        public Builder name(String name)             { this.name       = name;       return this; }
        public Builder plan(TenantPlan plan)         { this.plan        = Objects.requireNonNull(plan); return this; }
        public Builder adminEmail(String email)      { this.adminEmail  = email;     return this; }
        public Builder locale(String locale)         { this.locale      = locale;    return this; }
        public Builder timezone(String timezone)     { this.timezone    = timezone;  return this; }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, String> attrs) {
            if (attrs != null) this.attributes.putAll(attrs);
            return this;
        }

        /** Builds and validates the request. */
        public TenantOnboardingRequest build() {
            return new TenantOnboardingRequest(
                    tenantId, name, plan, adminEmail, locale, timezone, Map.copyOf(attributes));
        }
    }
}

