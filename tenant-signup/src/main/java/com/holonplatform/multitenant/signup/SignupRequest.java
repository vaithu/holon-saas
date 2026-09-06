package com.holonplatform.multitenant.signup;

import com.holonplatform.multitenant.TenantPlan;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validated input for {@link SignupService#signup(SignupRequest)}: creates a
 * brand-new tenant ("workspace") together with its owner account.
 *
 * @param tenantId     URL-safe workspace slug, e.g. {@code "acme-corp"}
 * @param companyName  human-readable display name for the tenant (nullable — defaults to tenantId)
 * @param ownerEmail   the future owner's email address
 * @param rawPassword  the future owner's chosen password, in plain text (hashed internally, never stored as-is)
 * @param plan         initial subscription plan (default {@link TenantPlan#FREE})
 * @param attributes   arbitrary onboarding metadata, passed through to {@code TenantOnboardingRequest}
 */
public record SignupRequest(
        String tenantId,
        String companyName,
        String ownerEmail,
        String rawPassword,
        TenantPlan plan,
        Map<String, String> attributes
) {

    public SignupRequest {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(ownerEmail, "ownerEmail must not be null");
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        plan = plan != null ? plan : TenantPlan.FREE;
        attributes = sanitizeAttributes(attributes);
    }

    /**
     * Normalizes the given attribute map into an immutable copy safe to pass to
     * {@link Map#copyOf(Map)}, which otherwise throws an uninformative
     * {@link NullPointerException} if any value is {@code null} — a likely mistake for
     * callers that treat "attribute left blank" as {@code null} (e.g. an unset optional
     * form field) rather than an empty string. Null values are coerced to {@code ""};
     * null keys still fail fast with a clear message.
     */
    private static Map<String, String> sanitizeAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
        	return Map.of();
        }
        Map<String, String> sanitized = new HashMap<>();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
        	Objects.requireNonNull(entry.getKey(), "attribute key must not be null");
        	sanitized.put(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
        }
        return Map.copyOf(sanitized);
    }

    public static Builder builder(String tenantId, String ownerEmail, String rawPassword) {
        return new Builder(tenantId, ownerEmail, rawPassword);
    }

    public static final class Builder {
        private final String tenantId;
        private final String ownerEmail;
        private final String rawPassword;
        private String companyName;
        private TenantPlan plan = TenantPlan.FREE;
        private final Map<String, String> attributes = new HashMap<>();

        private Builder(String tenantId, String ownerEmail, String rawPassword) {
            this.tenantId = tenantId;
            this.ownerEmail = ownerEmail;
            this.rawPassword = rawPassword;
        }

        public Builder companyName(String companyName) { this.companyName = companyName; return this; }
        public Builder plan(TenantPlan plan) { this.plan = plan; return this; }
        public Builder attribute(String key, String value) {
            Objects.requireNonNull(key, "attribute key must not be null");
            this.attributes.put(key, value != null ? value : "");
            return this;
        }

        public SignupRequest build() {
            return new SignupRequest(tenantId, companyName, ownerEmail, rawPassword, plan, Map.copyOf(attributes));
        }
    }
}

