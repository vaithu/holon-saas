package com.holonplatform.multitenant.cache.redis.ratelimit;

import com.holonplatform.multitenant.limits.TenantRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-backed {@link TenantRateLimiter}, shared across every application instance.
 *
 * <h2>Algorithm — fixed window counter, not the continuous token bucket</h2>
 * <p>Unlike {@code TokenBucketRateLimiter} (the default, per-JVM implementation), this
 * implementation uses a <em>fixed window counter</em>: the first {@link #tryAcquire} for a given
 * (tenant, key) atomically increments a Redis counter and sets its TTL to the definition's
 * {@code window}; every subsequent call within that window increments the same counter and is
 * allowed only while the count stays at or below {@code maxRequests}. When the TTL expires, Redis
 * itself reclaims the key and the next call starts a fresh window.
 *
 * <p>This trade-off is deliberate: a true continuous token bucket needs to read-modify-write
 * fractional token state, which across a distributed store either requires optimistic-locking
 * retries or a more complex Lua script maintaining floating-point state in a Redis hash. A fixed
 * window counter needs only a single atomic {@code INCR}, made all-or-nothing together with the
 * conditional {@code PEXPIRE} via one small Lua script — simple, fast, and exactly as consistent
 * across instances as any other Redis-backed counter. The only observable behavioral difference
 * from the token bucket is at window boundaries, where up to {@code 2 * maxRequests} requests can
 * land in a short straddle of two windows; the enforced <em>average</em> rate is identical.
 *
 * <h2>Why this matters for multi-instance deployments</h2>
 * <p>{@code TokenBucketRateLimiter}'s buckets are plain in-memory state: with {@code N} application
 * instances behind a load balancer, each instance enforces the configured limit independently, so
 * a tenant can end up with up to {@code N} times its intended quota. Because every instance here
 * increments the same Redis key, the configured limit is enforced <strong>once</strong>, regardless
 * of how many instances are running.
 *
 * <p>Limit definitions themselves ({@link #registerDefinition}) are still local, in-memory
 * configuration — exactly like {@code TokenBucketRateLimiter} — since they're static app config,
 * not runtime state; every instance is expected to register the same definitions at startup.
 */
public class RedisTenantRateLimiter implements TenantRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisTenantRateLimiter.class);

    /** Redis key prefix for all buckets managed by this limiter. */
    static final String KEY_PREFIX = "holon:ratelimit:";

    /**
     * Atomically increments the counter and, only on the very first increment (i.e. the key was
     * just created), sets its expiry to the window length in milliseconds — so the counter and
     * its TTL can never diverge, even under concurrent access from multiple instances.
     */
    private static final RedisScript<Long> INCREMENT_AND_EXPIRE_IF_NEW = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if tonumber(current) == 1 then "
                    + "  redis.call('PEXPIRE', KEYS[1], ARGV[1]) "
                    + "end "
                    + "return current",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /** key → limit definition (local, in-memory configuration — see class Javadoc). */
    private final ConcurrentHashMap<String, LimitDefinition> definitions = new ConcurrentHashMap<>();

    public RedisTenantRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /**
     * Registers (or replaces) the limit definition for the given key.
     *
     * @param key        rate-limit key; must not be null
     * @param definition bucket capacity and window; must not be null
     */
    @Override
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
    @Override
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
        String redisKey = redisKey(tenantId, key);
        Long current = redisTemplate.execute(INCREMENT_AND_EXPIRE_IF_NEW,
                Collections.singletonList(redisKey), String.valueOf(def.window().toMillis()));
        boolean allowed = current != null && current <= def.maxRequests();
        if (!allowed) {
            log.debug("Rate limit exceeded for tenant='{}' key='{}' (Redis-backed, count={}, limit={})",
                    tenantId, key, current, def.maxRequests());
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
        redisTemplate.delete(redisKey(tenantId, key));
        log.debug("Reset counter for tenant='{}' key='{}'", tenantId, key);
    }

    @Override
    public long remaining(String tenantId, String key) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(key,      "key must not be null");
        LimitDefinition def = requireDefinition(key);
        String value = redisTemplate.opsForValue().get(redisKey(tenantId, key));
        if (value == null) {
            return def.maxRequests(); // untouched (or expired) window is full
        }
        long current = Long.parseLong(value);
        return Math.max(0, def.maxRequests() - current);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private LimitDefinition requireDefinition(String key) {
        LimitDefinition def = definitions.get(key);
        if (def == null) {
            throw new IllegalArgumentException(
                    "No LimitDefinition registered for key '" + key + "'. "
                    + "Call registerDefinition() or use the auto-configuration's default keys.");
        }
        return def;
    }

    private static String redisKey(String tenantId, String key) {
        return KEY_PREFIX + tenantId + ':' + key;
    }
}
