package com.holonplatform.multitenant.settings;

/**
 * Catalogue of well-known setting keys used across the SaaS framework.
 *
 * <p>Applications may define their own keys in addition to these.
 */
public final class TenantSettingKeys {

    private TenantSettingKeys() {}

    // ── UI ────────────────────────────────────────────────────────────────────
    /** Maximum file upload size in megabytes. Default: {@code "10"} */
    public static final TenantSetting MAX_UPLOAD_MB =
            TenantSetting.of("ui.max_upload_mb", "10", "Maximum file upload size in MB");

    /** Default locale tag for UI rendering. Default: {@code "en"} */
    public static final TenantSetting DEFAULT_LOCALE =
            TenantSetting.of("ui.default_locale", "en", "Default UI locale (BCP-47 tag)");

    /** Custom logo URL for white-labelling. Default: null */
    public static final TenantSetting LOGO_URL =
            TenantSetting.of("ui.logo_url", null, "Custom logo URL (white-labelling)");

    /** Primary brand colour (CSS hex). Default: null */
    public static final TenantSetting PRIMARY_COLOR =
            TenantSetting.of("ui.primary_color", null, "Custom primary brand colour (hex)");

    // ── Rate limits ───────────────────────────────────────────────────────────
    /** Maximum API requests per minute. Default: {@code "60"} */
    public static final TenantSetting API_RATE_LIMIT_PER_MINUTE =
            TenantSetting.of("limits.api_rate_per_minute", "60", "Max API requests per minute");

    /** Maximum concurrent sessions. Default: {@code "5"} */
    public static final TenantSetting MAX_CONCURRENT_SESSIONS =
            TenantSetting.of("limits.max_concurrent_sessions", "5", "Max concurrent user sessions");

    // ── Email / notifications ─────────────────────────────────────────────────
    /** Custom SMTP host. Default: null (uses system SMTP) */
    public static final TenantSetting SMTP_HOST =
            TenantSetting.of("email.smtp_host", null, "Custom SMTP host");

    /** Sender display name for outgoing emails. Default: {@code "Notifications"} */
    public static final TenantSetting EMAIL_SENDER_NAME =
            TenantSetting.of("email.sender_name", "Notifications", "Email sender display name");

    // ── Data retention ────────────────────────────────────────────────────────
    /** Number of days to retain audit log entries. Default: {@code "365"} */
    public static final TenantSetting AUDIT_RETENTION_DAYS =
            TenantSetting.of("data.audit_retention_days", "365", "Audit log retention in days");

    /** Number of days to retain inactive sessions. Default: {@code "30"} */
    public static final TenantSetting SESSION_RETENTION_DAYS =
            TenantSetting.of("data.session_retention_days", "30", "Inactive session retention in days");
}

