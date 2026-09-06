package com.holonplatform.multitenant.quotas;

/**
 * Result of a quota check for a specific tenant and quota key.
 *
 * @param key          the quota that was checked
 * @param limit        the maximum allowed value (-1 = unlimited)
 * @param currentUsage the current usage count
 * @param remaining    how many units are left (Long.MAX_VALUE when unlimited)
 * @param unlimited    true if this plan has no limit
 * @param exceeded     true if currentUsage >= limit (and not unlimited)
 */
public record QuotaCheckResult(
        QuotaKey key,
        long limit,
        long currentUsage,
        long remaining,
        boolean unlimited,
        boolean exceeded
) {
    public static QuotaCheckResult unlimited(QuotaKey key, long currentUsage) {
        return new QuotaCheckResult(key, -1L, currentUsage, Long.MAX_VALUE, true, false);
    }

    public static QuotaCheckResult of(QuotaKey key, long limit, long currentUsage) {
        long remaining = Math.max(0, limit - currentUsage);
        boolean exceeded = currentUsage >= limit;
        return new QuotaCheckResult(key, limit, currentUsage, remaining, false, exceeded);
    }

    /** Returns a percentage used (0–100), or 0 when unlimited. */
    public int percentUsed() {
        if (unlimited || limit <= 0) return 0;
        return (int) Math.min(100, (currentUsage * 100) / limit);
    }
}

