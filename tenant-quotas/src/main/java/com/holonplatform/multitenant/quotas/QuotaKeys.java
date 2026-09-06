package com.holonplatform.multitenant.quotas;

/**
 * Built-in quota keys provided by the framework.
 *
 * <p>Use these for standard CRM/SaaS resource limits. Add your own by
 * implementing {@link QuotaKey} as an enum.</p>
 */
public enum QuotaKeys implements QuotaKey {

    MAX_USERS(        "max_users",         "Maximum number of active users per tenant"),
    MAX_CONTACTS(     "max_contacts",      "Maximum number of CRM contacts"),
    MAX_ACTIVE_DEALS( "max_active_deals",  "Maximum number of open/active deals"),
    MAX_STORAGE_MB(   "max_storage_mb",    "Maximum file storage in megabytes"),
    MAX_API_KEYS(     "max_api_keys",      "Maximum number of API keys"),
    MAX_WEBHOOKS(     "max_webhooks",      "Maximum number of webhook endpoints"),
    MAX_CUSTOM_FIELDS("max_custom_fields", "Maximum number of custom fields");

    private final String key;
    private final String description;

    QuotaKeys(String key, String description) {
        this.key         = key;
        this.description = description;
    }

    @Override public String key()         { return key; }
    @Override public String description() { return description; }
}

