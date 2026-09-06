package com.holonplatform.multitenant.limits;

/**
 * Result of a single rate-limit acquisition attempt.
 *
 * @param allowed        {@code true} when the request was permitted and a token was consumed
 * @param remaining      tokens still available in the current window (always {@code 0} when denied)
 * @param resetAtEpochMs epoch-millisecond timestamp when the bucket will next be refilled
 *                       (for denied results: earliest moment a token becomes available again;
 *                       for allowed results: end of the current rolling window)
 */
public record RateLimitResult(boolean allowed, long remaining, long resetAtEpochMs) {

    /**
     * Creates an <em>allowed</em> result with the given remaining-token count.
     *
     * @param remaining      tokens remaining after this acquisition (≥ 0)
     * @param resetAtEpochMs window-reset timestamp in epoch milliseconds
     */
    public static RateLimitResult allowed(long remaining, long resetAtEpochMs) {
        return new RateLimitResult(true, remaining, resetAtEpochMs);
    }

    /**
     * Creates an <em>allowed</em> result for unlimited plans (remaining = {@link Long#MAX_VALUE}).
     *
     * @param resetAtEpochMs window-reset timestamp in epoch milliseconds
     */
    public static RateLimitResult unlimited(long resetAtEpochMs) {
        return new RateLimitResult(true, Long.MAX_VALUE, resetAtEpochMs);
    }

    /**
     * Creates a <em>denied</em> result.
     *
     * @param resetAtEpochMs earliest epoch-millisecond when a token becomes available again
     */
    public static RateLimitResult denied(long resetAtEpochMs) {
        return new RateLimitResult(false, 0L, resetAtEpochMs);
    }

    /**
     * Returns the number of milliseconds until the bucket is reset.
     * Returns {@code 0} if the reset time is already in the past.
     *
     * @param nowMs current epoch milliseconds
     */
    public long retryAfterMs(long nowMs) {
        return Math.max(0L, resetAtEpochMs - nowMs);
    }
}
