package com.holonplatform.multitenant.limits;

import java.time.Duration;

/**
 * Service for managing per-tenant rate limits using a sliding-window token-bucket algorithm.
 *
 * <h2>Algorithm</h2>
 * <p>Each tenant + key combination owns a token bucket refilled at a fixed rate.
 * {@link #tryAcquire} atomically checks and decrements the bucket; if empty it
 * returns {@code false} (caller decides whether to reject or block).
 *
 * <h2>Key naming</h2>
 * <p>Use dot-notation keys that correspond to {@link TenantLimitKeys} constants,
 * e.g. {@code "api.requests"}, {@code "email.sends"}.
 */
public interface TenantRateLimiter {

    /**
     * Attempts to acquire one permit for the given tenant and key.
     *
     * @param tenantId  the tenant; must not be null
     * @param key       the rate-limit key (e.g. {@code "api.requests"}); must not be null
     * @return {@code true} if the permit was granted; {@code false} if the limit is exceeded
     */
    boolean tryAcquire(String tenantId, String key);

    /**
     * Acquires one permit or throws {@link RateLimitExceededException}.
     *
     * @param tenantId  the tenant; must not be null
     * @param key       the rate-limit key; must not be null
     * @throws RateLimitExceededException if the limit is exceeded
     */
    default void acquireOrThrow(String tenantId, String key) {
        if (!tryAcquire(tenantId, key)) {
            LimitDefinition def = getDefinition(key);
            throw new RateLimitExceededException(tenantId, key,
                    def.maxRequests(), def.window().toSeconds());
        }
    }

    /**
     * Returns the {@link LimitDefinition} configured for the given key.
     *
     * @param key the rate-limit key; must not be null
     * @return the limit definition; never null
     */
    LimitDefinition getDefinition(String key);

    /**
     * Registers (or replaces) the limit definition for a key.
     *
     * <p>An application that defines its own limit keys — as opposed to the
     * {@link TenantLimitKeys} defaults the auto-configurations register — has to call this at
     * start-up, otherwise {@link #tryAcquire} has no bucket to consult for that key.
     *
     * <p>This is declared here, rather than only on the individual implementations, so that
     * callers do not have to know which implementation they were given. Before it existed,
     * application code had to write {@code if (limiter instanceof TokenBucketRateLimiter b)}
     * to register anything, which meant that merely putting Redis on the classpath — and so
     * swapping in a different implementation — made those registrations silently disappear and
     * left the corresponding limits unenforced.
     *
     * <p>The default implementation throws, so that an implementation which genuinely cannot
     * accept definitions at runtime (a fixed-configuration or remote-policy limiter) fails
     * loudly instead of quietly ignoring the call.
     *
     * @param key        the rate-limit key; must not be null
     * @param definition the definition to apply; must not be null
     * @throws UnsupportedOperationException if this implementation does not support runtime
     *         registration
     */
    default void registerDefinition(String key, LimitDefinition definition) {
        throw new UnsupportedOperationException(getClass().getName()
                + " does not support registering limit definitions at runtime");
    }

    /**
     * Registers (or replaces) several limit definitions at once.
     *
     * @param definitions definitions by key; must not be null
     * @throws UnsupportedOperationException if this implementation does not support runtime
     *         registration
     */
    default void registerDefinitions(java.util.Map<String, LimitDefinition> definitions) {
        definitions.forEach(this::registerDefinition);
    }

    /**
     * Resets the bucket for the given tenant and key.
     * Useful after a plan upgrade.
     *
     * @param tenantId the tenant; must not be null
     * @param key      the rate-limit key; must not be null
     */
    void reset(String tenantId, String key);

    /**
     * Returns the number of remaining permits in the current window.
     *
     * @param tenantId the tenant; must not be null
     * @param key      the rate-limit key; must not be null
     * @return remaining permits (0 = bucket empty)
     */
    long remaining(String tenantId, String key);

    // ── Nested type ───────────────────────────────────────────────────────────

    /**
     * Immutable definition of a rate-limit bucket.
     *
     * @param maxRequests maximum number of requests allowed in the window
     * @param window      refill period (e.g. {@link Duration#ofMinutes(1)})
     */
    record LimitDefinition(long maxRequests, Duration window) {

        public static LimitDefinition of(long maxRequests, Duration window) {
            return new LimitDefinition(maxRequests, window);
        }

        /** Shorthand: N requests per minute. */
        public static LimitDefinition perMinute(long n) {
            return of(n, Duration.ofMinutes(1));
        }

        /** Shorthand: N requests per second. */
        public static LimitDefinition perSecond(long n) {
            return of(n, Duration.ofSeconds(1));
        }

        /** Shorthand: N requests per hour. */
        public static LimitDefinition perHour(long n) {
            return of(n, Duration.ofHours(1));
        }
    }
}

