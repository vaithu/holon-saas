package com.holonplatform.multitenant.quotas;

/**
 * Identifies a resource quota type.
 *
 * <p>Implement this interface (typically as an enum) to define your application's
 * quota set. Example:
 * <pre>{@code
 * public enum CrmQuota implements QuotaKey {
 *     MAX_CONTACTS("max_contacts", "Maximum number of CRM contacts"),
 *     MAX_PIPELINES("max_pipelines", "Maximum number of sales pipelines");
 *
 *     private final String key;
 *     private final String description;
 *     CrmQuota(String key, String description) { this.key = key; this.description = description; }
 *     public String key() { return key; }
 *     public String description() { return description; }
 * }
 * }</pre>
 */
public interface QuotaKey {

    /** Unique string identifier used in quota definitions and enforcement. */
    String key();

    /** Human-readable description shown in quota dashboards. */
    String description();
}

