package com.holonplatform.multitenant.limits;

import java.time.Duration;

/**
 * Catalogue of well-known rate-limit keys used across the SaaS framework.
 *
 * <p>Applications register these definitions with {@link TenantRateLimiter}
 * implementations at startup. Each constant is a {@link TenantRateLimiter.LimitDefinition}
 * that describes the bucket capacity and refill window.
 *
 * <h2>Keys</h2>
 * <p>All keys follow dot-notation: {@code "<domain>.<resource>"}.
 */
public final class TenantLimitKeys {

    private TenantLimitKeys() {}

    // ── API ───────────────────────────────────────────────────────────────────

    /** Maximum REST / RPC API calls per minute. */
    public static final String API_REQUESTS = "api.requests";

    /** Default limit definition for {@link #API_REQUESTS}: 60 rpm. */
    public static final TenantRateLimiter.LimitDefinition API_REQUESTS_DEFAULT =
            TenantRateLimiter.LimitDefinition.perMinute(60);

    // ── Email ─────────────────────────────────────────────────────────────────

    /** Outgoing email sends per hour. */
    public static final String EMAIL_SENDS = "email.sends";

    /** Default limit definition for {@link #EMAIL_SENDS}: 100 per hour. */
    public static final TenantRateLimiter.LimitDefinition EMAIL_SENDS_DEFAULT =
            TenantRateLimiter.LimitDefinition.of(100, Duration.ofHours(1));

    // ── File uploads ──────────────────────────────────────────────────────────

    /** File upload operations per minute. */
    public static final String FILE_UPLOADS = "storage.uploads";

    /** Default limit definition for {@link #FILE_UPLOADS}: 20 per minute. */
    public static final TenantRateLimiter.LimitDefinition FILE_UPLOADS_DEFAULT =
            TenantRateLimiter.LimitDefinition.perMinute(20);

    // ── Reports ───────────────────────────────────────────────────────────────

    /** Report generation requests per hour. */
    public static final String REPORT_EXPORTS = "reports.exports";

    /** Default limit definition for {@link #REPORT_EXPORTS}: 10 per hour. */
    public static final TenantRateLimiter.LimitDefinition REPORT_EXPORTS_DEFAULT =
            TenantRateLimiter.LimitDefinition.of(10, Duration.ofHours(1));

    // ── Webhooks ──────────────────────────────────────────────────────────────

    /** Webhook deliveries per minute. */
    public static final String WEBHOOK_DELIVERIES = "webhooks.deliveries";

    /** Default limit definition for {@link #WEBHOOK_DELIVERIES}: 30 per minute. */
    public static final TenantRateLimiter.LimitDefinition WEBHOOK_DELIVERIES_DEFAULT =
            TenantRateLimiter.LimitDefinition.perMinute(30);
}

