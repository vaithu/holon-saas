package com.holonplatform.multitenant.demo.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.CachingTenantDetailsLoader;
import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.limits.TenantRateLimiter;

/**
 * F23 — reports on the Redis-backed tenant details cache and distributed rate limiter.
 *
 * <h2>What this is really demonstrating</h2>
 * <p>Nothing in the application asked for Redis. {@code DemoLimits} calls
 * {@code rateLimiter.registerDefinition(...)}, {@code SalesService} calls
 * {@code acquireOrThrow(...)}, and the onboarding module asks for a {@code TenantDetailsLoader}.
 * Putting {@code holon-multi-tenant-cache-redis} and a {@code RedisConnectionFactory} on the
 * classpath replaces the per-JVM implementations of both underneath that code, which is what
 * makes the difference between a demo that works on one node and a service that survives being
 * run on four.
 *
 * <p>So the useful thing to show is not "a limiter exists" but <em>where the state lives</em>:
 * this reads the raw Redis keys back, which is the only evidence that a second application
 * instance would see the same counter.
 */
@Service
public class RedisFeatureReport {

    /** Mirrors the prefix {@code RedisTenantRateLimiter} builds its keys from. */
    private static final String RATE_LIMIT_KEY_PREFIX = "holon:ratelimit:";

    private final TenantRateLimiter                 rateLimiter;
    private final ObjectProvider<StringRedisTemplate> redisTemplate;
    private final ObjectProvider<Cache>               detailsCache;

    public RedisFeatureReport(TenantRateLimiter rateLimiter,
                              ObjectProvider<StringRedisTemplate> redisTemplate,
                              @org.springframework.beans.factory.annotation.Qualifier("tenantDetailsCache")
                              ObjectProvider<Cache> detailsCache) {
        this.rateLimiter   = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.redisTemplate = redisTemplate;
        this.detailsCache  = detailsCache;
    }

    /** True when Redis actually answered, as opposed to merely being on the classpath. */
    public boolean redisAvailable() {
        String version = serverVersion();
        return version != null && !version.startsWith("unreachable");
    }

    /**
     * The Redis server version, or a description of why it could not be read. Reaching the real
     * server is the point: a connection factory bean can exist without anything listening behind
     * it, and that difference only shows up on first use — so the failure is reported rather than
     * swallowed.
     *
     * <p>The raw {@code INFO} reply is parsed here instead of using
     * {@code RedisServerCommands.info()}, which feeds the reply to {@link Properties#load} and so
     * interprets backslashes as escape sequences. A Redis running on Windows reports its
     * executable and config paths in {@code INFO}, and those contain backslashes — enough to make
     * the standard call fail outright with "Malformed \\uxxxx encoding".
     */
    public String serverVersion() {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        if (template == null) {
            return "unreachable: no StringRedisTemplate bean";
        }
        try {
            Object raw = template.execute(
                    (org.springframework.data.redis.core.RedisCallback<Object>)
                            connection -> connection.execute("INFO"));
            // Drivers differ on whether a custom command comes back as bytes or as a String.
            String info = switch (raw) {
                case null -> null;
                case byte[] bytes -> new String(bytes, StandardCharsets.UTF_8);
                default -> raw.toString();
            };
            if (info == null) {
                return "unreachable: INFO returned nothing";
            }
            return info.lines()
                    .filter(line -> line.startsWith("redis_version:"))
                    .map(line -> line.substring("redis_version:".length()).trim())
                    .findFirst()
                    .orElse("unreachable: INFO carried no version");
        } catch (RuntimeException e) {
            return "unreachable: " + e.getClass().getSimpleName() + " " + e.getMessage();
        }
    }

    /** Simple class name of the rate limiter Spring actually injected. */
    public String rateLimiterImplementation() {
        return rateLimiter.getClass().getSimpleName();
    }

    /**
     * Describes the {@code tenantDetailsCache}. The bean is contributed by
     * {@code TenantCacheRedisAutoConfiguration} only when a connection factory exists; otherwise
     * the onboarding module's per-JVM Caffeine cache is used and no such bean is present.
     */
    public String detailsCacheImplementation() {
        Cache cache = detailsCache.getIfAvailable();
        if (cache == null) {
            return "none (per-JVM Caffeine cache inside the onboarding module)";
        }
        return cache instanceof RedisCache
                ? cache.getClass().getSimpleName() + " '" + cache.getName() + "' (shared, in Redis)"
                : cache.getClass().getSimpleName() + " '" + cache.getName() + "' (local to this JVM)";
    }

    /** Permits left in the current window for the bound tenant, or -1 if no tenant is bound. */
    public long remainingSalesPermits() {
        return TenantContext.currentTenantId()
                .map(tenantId -> rateLimiter.remaining(tenantId, DemoLimits.SALES_RECORD_LIMIT_KEY))
                .orElse(-1L);
    }

    /**
     * The raw Redis keys holding rate-limit counters for the bound tenant, with their values.
     *
     * <p>Scoped to the bound tenant's prefix rather than scanning everything: one workspace has
     * no business seeing how heavily another is using the platform, and the key name embeds the
     * tenant id precisely so that this scoping is possible.
     */
    public List<String> rateLimitKeysForCurrentTenant() {
        StringRedisTemplate template = redisTemplate.getIfAvailable();
        String tenantId = TenantContext.currentTenantId().orElse(null);
        if (template == null || tenantId == null) {
            return List.of();
        }
        List<String> rows = new ArrayList<>();
        try {
            // Sorted so the display is stable between refreshes and between runs.
            Set<String> keys = new TreeSet<>(
                    template.keys(RATE_LIMIT_KEY_PREFIX + tenantId + ":*"));
            for (String key : keys) {
                Long ttl = template.getExpire(key);
                rows.add(key + " = " + template.opsForValue().get(key)
                        + "   (expires in " + ttl + "s)");
            }
        } catch (RuntimeException e) {
            return List.of("Could not read Redis: " + e.getMessage());
        }
        return rows;
    }

    /** The Redis key the details cache would use for a tenant, for display alongside the above. */
    public String detailsCacheKeyFor(String tenantId) {
        return CachingTenantDetailsLoader.CACHE_NAME + "::" + tenantId;
    }
}
