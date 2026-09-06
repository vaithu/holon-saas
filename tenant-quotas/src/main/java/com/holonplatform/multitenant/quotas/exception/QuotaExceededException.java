package com.holonplatform.multitenant.quotas.exception;

import com.holonplatform.multitenant.quotas.QuotaKey;

/**
 * Thrown by {@link com.holonplatform.multitenant.quotas.internal.QuotaAspect}
 * when a tenant has exceeded their plan's resource quota.
 */
public class QuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final QuotaKey quotaKey;
    private final long     limit;
    private final long     currentUsage;

    public QuotaExceededException(QuotaKey quotaKey, long limit, long currentUsage, String message) {
        super(message);
        this.quotaKey     = quotaKey;
        this.limit        = limit;
        this.currentUsage = currentUsage;
    }

    public QuotaExceededException(QuotaKey quotaKey, long limit, long currentUsage) {
        this(quotaKey, limit, currentUsage,
             "Quota exceeded for '" + quotaKey.key() + "': limit=" + limit + ", current=" + currentUsage);
    }

    /** The quota key that was exceeded. */
    public QuotaKey getQuotaKey() { return quotaKey; }

    /** The plan's configured limit for this quota. */
    public long getLimit() { return limit; }

    /** The usage count at the time the exception was thrown. */
    public long getCurrentUsage() { return currentUsage; }
}

