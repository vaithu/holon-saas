package com.holonplatform.multitenant.limits.internal;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.holonplatform.multitenant.limits.TenantRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory {@link TenantRateLimiter} backed by per-(tenant, key) token buckets.
 *
 * <h2>Algorithm</h2>
 * <p>Each bucket uses a continuous <em>token-bucket</em> model:
 * <ul>
 *   <li>Capacity = {@code LimitDefinition.maxRequests()}</li>
 *   <li>Tokens are added at a rate of {@code capacity / windowNanos} tokens per nanosecond.</li>
 *   <li>Each {@link #tryAcquire} call atomically refills then tries to consume one token.</li>
 *   <li>Partial tokens are accumulated; the floor is taken before consumption.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>The bucket store is a Caffeine {@link com.github.benmanes.caffeine.cache.Cache}.
 * Each {@code TokenBucket} instance is guarded internally with {@code synchronized}
 * to keep the refill + consume operation atomic without external locking.
 *
 * <h2>Memory bound</h2>
 * <p>Buckets are evicted after a period of inactivity (default 1 hour) and the total
 * number of tracked buckets is capped (default 100,000), so long-lived JVMs with many
 * tenants/keys don't accumulate memory unboundedly. This is safe because an idle-evicted
 * bucket is semantically equivalent to "not yet seen" — {@link #remaining} already treats
 * a missing bucket as full capacity, and a bucket idle longer than its window would have
 * refilled to full capacity anyway.
 *
 * <h2>Plan-awareness</h2>
 * <p>Register custom {@link TenantRateLimiter.LimitDefinition}s per key via
 * {@link #registerDefinition(String, TenantRateLimiter.LimitDefinition)}.
 * If no custom definition is registered for a key a {@link IllegalArgumentException}
 * is thrown at acquire time.
 */
public class TokenBucketRateLimiter implements TenantRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    /** Default idle-eviction period for buckets with no recent activity. */
    public static final Duration DEFAULT_IDLE_EVICTION = Duration.ofHours(1);

    /** Default hard cap on the total number of tracked buckets across all tenants/keys. */
    public static final long DEFAULT_MAX_BUCKETS = 100_000;

    /** key → limit definition */
    private final ConcurrentHashMap<String, LimitDefinition> definitions = new ConcurrentHashMap<>();

    /** (tenantId + "\0" + key) → bucket, evicted after inactivity and capped in size. */
    private final com.github.benmanes.caffeine.cache.Cache<String, TokenBucket> buckets;

    /**
     * Creates a rate limiter with the default idle-eviction period ({@link #DEFAULT_IDLE_EVICTION})
     * and bucket cap ({@link #DEFAULT_MAX_BUCKETS}).
     */
    public TokenBucketRateLimiter() {
        this(DEFAULT_IDLE_EVICTION, DEFAULT_MAX_BUCKETS);
    }

    /**
     * Creates a rate limiter with a custom idle-eviction period and bucket cap.
     *
     * @param idleEviction how long a bucket may go untouched before it is evicted; must not be null
     * @param maxBuckets   hard cap on the total number of tracked buckets across all tenants/keys
     */
    public TokenBucketRateLimiter(Duration idleEviction, long maxBuckets) {
        Objects.requireNonNull(idleEviction, "idleEviction must not be null");
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(idleEviction)
                .maximumSize(maxBuckets)
                .build();
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /**
     * Registers (or replaces) the limit definition for the given key.
     *
     * @param key        rate-limit key; must not be null
     * @param definition bucket capacity and window; must not be null
     */
    public void registerDefinition(String key, LimitDefinition definition) {
        Objects.requireNonNull(key,        "key must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        definitions.put(key, definition);
    }

    /**
     * Bulk-registers definitions from a map (e.g. loaded from config).
     *
     * @param defs map of key → definition; must not be null
     */
    public void registerDefinitions(Map<String, LimitDefinition> defs) {
        Objects.requireNonNull(defs, "defs must not be null");
        defs.forEach(this::registerDefinition);
    }

    // ── TenantRateLimiter ─────────────────────────────────────────────────────

    @Override
    public boolean tryAcquire(String tenantId, String key) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(key,      "key must not be null");
        LimitDefinition def = requireDefinition(key);
        TokenBucket bucket = buckets.get(bucketId(tenantId, key),
                id -> new TokenBucket(def.maxRequests(), def.window().toNanos()));
        boolean allowed = bucket.tryConsume();
        if (!allowed) {
            log.debug("Rate limit exceeded for tenant='{}' key='{}'", tenantId, key);
        }
        return allowed;
    }

    @Override
    public LimitDefinition getDefinition(String key) {
        Objects.requireNonNull(key, "key must not be null");
        return requireDefinition(key);
    }

    @Override
    public void reset(String tenantId, String key) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(key,      "key must not be null");
        buckets.invalidate(bucketId(tenantId, key));
        log.debug("Reset bucket for tenant='{}' key='{}'", tenantId, key);
    }

    @Override
    public long remaining(String tenantId, String key) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(key,      "key must not be null");
        TokenBucket bucket = buckets.getIfPresent(bucketId(tenantId, key));
        if (bucket == null) {
            LimitDefinition def = requireDefinition(key);
            return def.maxRequests(); // untouched (or evicted-while-idle) bucket is full
        }
        return bucket.getTokens();
    }

    /**
     * Returns the current number of tracked buckets across all tenants/keys — useful for
     * monitoring the memory footprint of this limiter.
     */
    public long trackedBucketCount() {
        return buckets.estimatedSize();
    }

    /**
     * Forces pending Caffeine maintenance (size-based and idle-based eviction) to run
     * synchronously. Caffeine normally performs this lazily during reads/writes, so this is
     * mainly useful for tests and diagnostics that need deterministic, immediate eviction.
     */
    public void cleanUp() {
        buckets.cleanUp();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private LimitDefinition requireDefinition(String key) {
        LimitDefinition def = definitions.get(key);
        if (def == null) {
            throw new IllegalArgumentException(
                    "No LimitDefinition registered for key '" + key + "'. "
                    + "Call registerDefinition() or use TenantRateLimitAutoConfiguration.");
        }
        return def;
    }

    private static String bucketId(String tenantId, String key) {
        return tenantId + '\u0000' + key;
    }

    // ── TokenBucket ───────────────────────────────────────────────────────────

    /**
     * Continuous token-bucket implementation.
     *
     * <p>Tokens are accumulated as a {@code double} so that sub-token refill
     * rates work correctly for small windows (e.g. 1 req/s). The consumption
     * step takes the floor before decrementing.
     */
    static final class TokenBucket {

        private final double capacity;
        private final double tokensPerNano;

        private double tokens;
        private long   lastRefillNanos;

        TokenBucket(long capacity, long windowNanos) {
            this.capacity       = capacity;
            this.tokensPerNano  = (double) capacity / windowNanos;
            this.tokens         = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized long getTokens() {
            refill();
            return (long) tokens;
        }

        private void refill() {
            long now     = System.nanoTime();
            long elapsed = now - lastRefillNanos;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
                lastRefillNanos = now;
            }
        }
    }
}
