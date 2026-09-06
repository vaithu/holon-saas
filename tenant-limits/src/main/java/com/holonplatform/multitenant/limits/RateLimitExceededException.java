package com.holonplatform.multitenant.limits;

/**
 * Thrown when a tenant has exhausted its rate-limit quota.
 *
 * <p>Callers should translate this to HTTP {@code 429 Too Many Requests}.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String tenantId;
    private final String limitKey;
    private final long   limitPerWindow;
    private final long   windowSeconds;

    public RateLimitExceededException(String tenantId, String limitKey,
            long limitPerWindow, long windowSeconds) {
        super(String.format(
                "Rate limit exceeded for tenant '%s' on '%s': limit=%d per %ds",
                tenantId, limitKey, limitPerWindow, windowSeconds));
        this.tenantId       = tenantId;
        this.limitKey       = limitKey;
        this.limitPerWindow = limitPerWindow;
        this.windowSeconds  = windowSeconds;
    }

    public String getTenantId()       { return tenantId; }
    public String getLimitKey()       { return limitKey; }
    public long   getLimitPerWindow() { return limitPerWindow; }
    public long   getWindowSeconds()  { return windowSeconds; }
}

