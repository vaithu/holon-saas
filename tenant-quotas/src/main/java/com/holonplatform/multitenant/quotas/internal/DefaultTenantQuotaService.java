package com.holonplatform.multitenant.quotas.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.quotas.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Default {@link TenantQuotaService} implementation.
 *
 * <p>Quota definitions are held in memory (registered at startup via {@link #registerQuota}).
 * Current usage is resolved by delegating to registered {@link QuotaUsageProvider} beans.
 * If no provider supports a key, usage is assumed to be 0 (no enforcement).</p>
 *
 * <h2>Usage cache (performance at 10k users)</h2>
 * <p>{@code @EnforceQuota} intercepts annotated methods on every call. Without caching,
 * each intercept triggers a {@code SELECT COUNT(*)} against the tenant DB — at 10k
 * concurrent users that can be thousands of count queries per second. A short-lived
 * in-process cache (default: {@value #DEFAULT_CACHE_TTL_MS} ms) dramatically reduces
 * that load. The trade-off is that a user may temporarily exceed a quota by the number
 * of concurrent requests that slip through before the first write invalidates the cache.
 * For strict enforcement, set {@code cacheEnabled = false} at construction time.</p>
 *
 * <p>The cache is explicitly invalidated via {@link #invalidateUsageCache(String, QuotaKey)}
 * whenever the quota service itself records a change (e.g. after a successful create call
 * in {@code QuotaEnforcementAspect}). Downstream code that bypasses the aspect and mutates
 * quota-counted resources should call {@code invalidateUsageCache} directly.</p>
 */
public class DefaultTenantQuotaService implements TenantQuotaService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantQuotaService.class);

    /** Default cache time-to-live in milliseconds (30 seconds). */
    public static final long DEFAULT_CACHE_TTL_MS = 30_000L;

    // ── Quota definitions ─────────────────────────────────────────────────────

    /** quotaKey.key() → QuotaDefinition */
    private final Map<String, QuotaDefinition> definitions = new ConcurrentHashMap<>();

    /** Providers indexed by supported quota key for O(1) lookup. Built lazily. */
    private final Map<String, QuotaUsageProvider> providerIndex = new ConcurrentHashMap<>();
    private final List<QuotaUsageProvider> usageProviders;

    // ── Usage cache ───────────────────────────────────────────────────────────

    /** Cache entry: holds the usage count and the wall-clock expiry time. */
    private record CacheEntry(long usage, long expiresAtMs) {
        boolean isExpired() { return System.currentTimeMillis() >= expiresAtMs; }
    }

    /** cacheKey = "{tenantId}:{quotaKey}" → CacheEntry */
    private final Map<String, CacheEntry> usageCache = new ConcurrentHashMap<>();

    private final boolean cacheEnabled;
    private final long    cacheTtlMs;

    /** Creates a service with the default 30-second usage cache. */
    public DefaultTenantQuotaService(List<QuotaUsageProvider> usageProviders) {
        this(usageProviders, true, DEFAULT_CACHE_TTL_MS);
    }

    /**
     * Creates a service with explicit cache configuration.
     *
     * @param usageProviders providers that compute live counts
     * @param cacheEnabled   {@code false} for strict per-call enforcement
     * @param cacheTtlMs     cache time-to-live in milliseconds (ignored when disabled)
     */
    public DefaultTenantQuotaService(List<QuotaUsageProvider> usageProviders,
                                     boolean cacheEnabled,
                                     long cacheTtlMs) {
        this.usageProviders = usageProviders != null ? List.copyOf(usageProviders) : List.of();
        this.cacheEnabled   = cacheEnabled;
        this.cacheTtlMs     = cacheTtlMs;
    }

    @Override
    public void registerQuota(QuotaDefinition definition) {
        Objects.requireNonNull(definition, "definition must not be null");
        definitions.put(definition.key().key(), definition);
        log.debug("Registered quota definition: key='{}' limits={}", definition.key().key(), definition.limits());
    }

    @Override
    public QuotaCheckResult checkQuota(String tenantId, QuotaKey key) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(key,      "key must not be null");

        QuotaDefinition def = definitions.get(key.key());
        if (def == null) {
            log.trace("No quota definition for '{}' — treating as unlimited", key.key());
            return QuotaCheckResult.unlimited(key, 0L);
        }

        // Zero-allocation tenant lookup via peekTenantId() first, then full get()
        // only once we know the tenant ID matches (avoids Optional creation on fast-path miss)
        String currentTenantId = TenantContext.peekTenantId();
        if (currentTenantId == null || !currentTenantId.equals(tenantId)) {
            log.debug("Tenant '{}' not in TenantContext during quota check for '{}' — treating as unlimited",
                      tenantId, key.key());
            return QuotaCheckResult.unlimited(key, 0L);
        }
        // Tenant ID matches — retrieve full details for plan lookup (one Optional, unavoidable)
        TenantDetails tenant = TenantContext.get().orElse(null);
        if (tenant == null) {
            return QuotaCheckResult.unlimited(key, 0L);
        }

        long limit = def.getLimit(tenant.plan());
        if (limit == -1L) {
            long usage = resolveUsageCached(tenantId, key);
            return QuotaCheckResult.unlimited(key, usage);
        }

        long usage = resolveUsageCached(tenantId, key);
        return QuotaCheckResult.of(key, limit, usage);
    }

    @Override
    public QuotaCheckResult checkQuota(String tenantId, String quotaKeyString) {
        QuotaDefinition def = definitions.get(quotaKeyString);
        if (def == null) {
            QuotaKey tempKey = new QuotaKey() {
                @Override public String key()         { return quotaKeyString; }
                @Override public String description() { return quotaKeyString; }
            };
            return QuotaCheckResult.unlimited(tempKey, 0L);
        }
        return checkQuota(tenantId, def.key());
    }

    @Override
    public long getLimit(String tenantId, QuotaKey key) {
        QuotaDefinition def = definitions.get(key.key());
        if (def == null) return -1L;
        // Zero-allocation: peek tenant ID, compare, then resolve full details only on match
        String currentTenantId = TenantContext.peekTenantId();
        if (currentTenantId == null || !currentTenantId.equals(tenantId)) return -1L;
        TenantDetails tenant = TenantContext.get().orElse(null);
        return tenant != null ? def.getLimit(tenant.plan()) : -1L;
    }

    @Override
    public long getRemaining(String tenantId, QuotaKey key) {
        return checkQuota(tenantId, key).remaining();
    }

    @Override
    public Map<String, QuotaCheckResult> getAllQuotas(String tenantId) {
        return definitions.values().stream()
                .collect(Collectors.toUnmodifiableMap(
                        d -> d.key().key(),
                        d -> checkQuota(tenantId, d.key())
                ));
    }

    @Override
    public List<QuotaDefinition> listDefinitions() {
        return List.copyOf(definitions.values());
    }

    // ── Cache management ──────────────────────────────────────────────────────

    /**
     * Explicitly evicts the cached usage count for {@code (tenantId, key)}.
     * Call this after any operation that changes the counted resource
     * (e.g. after successfully creating a contact, invite, etc.) so that the
     * next quota check reflects the new reality immediately.
     */
    public void invalidateUsageCache(String tenantId, QuotaKey key) {
        usageCache.remove(cacheKey(tenantId, key));
        log.trace("Evicted usage cache for quota '{}' in tenant '{}'", key.key(), tenantId);
    }

    /** Clears all cached usage values for a tenant (e.g. on tenant deletion). */
    public void invalidateUsageCacheForTenant(String tenantId) {
        String prefix = tenantId + ":";
        usageCache.keySet().removeIf(k -> k.startsWith(prefix));
        log.debug("Evicted all usage cache entries for tenant '{}'", tenantId);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Returns the current usage, serving from cache when possible.
     *
     * <h2>Fast path (cache hit)</h2>
     * <p>O(1) ConcurrentHashMap lookup — no DB round-trip, no locking.</p>
     *
     * <h2>Slow path (cache miss / expired)</h2>
     * <p>Uses {@link java.util.concurrent.ConcurrentHashMap#compute} to hold a per-bucket
     * lock while refreshing. This prevents a <em>cache stampede</em>: under the old
     * implementation, N threads could all see an expired entry simultaneously and all
     * call {@link #resolveUsage} in parallel, causing N redundant DB count queries.
     * With {@code compute()}, only ONE thread per {@code (tenantId, quotaKey)} calls
     * the provider at a time; other threads for the same key wait for the result.
     * Threads for DIFFERENT keys are unaffected (ConcurrentHashMap bucket granularity).</p>
     *
     * <p><strong>Caveat:</strong> if {@code resolveUsage()} is slow (e.g. remote DB call),
     * threads waiting for the same key are blocked for that duration. For strict
     * SLA requirements, replace this with Caffeine's async loading cache.</p>
     */
    private long resolveUsageCached(String tenantId, QuotaKey key) {
        if (!cacheEnabled) {
            return resolveUsage(tenantId, key);
        }

        String ck = cacheKey(tenantId, key);

        // ── Fast path: check without locking ─────────────────────────────────
        CacheEntry entry = usageCache.get(ck);
        if (entry != null && !entry.isExpired()) {
            log.trace("Usage cache HIT for quota '{}' in tenant '{}'", key.key(), tenantId);
            return entry.usage();
        }

        // ── Slow path: compute atomically — prevents stampede ─────────────────
        // compute() holds the bucket lock for this key, so only ONE thread per
        // (tenantId, quotaKey) pair calls resolveUsage() when the cache is cold/expired.
        long[] result = {0L};
        usageCache.compute(ck, (k, existing) -> {
            // Double-checked: another thread may have already refreshed the entry
            // while we were waiting for the bucket lock.
            if (existing != null && !existing.isExpired()) {
                result[0] = existing.usage();
                return existing; // reuse — no DB call needed
            }
            result[0] = resolveUsage(tenantId, key);
            log.trace("Usage cache MISS — fetched {} for quota '{}' in tenant '{}'",
                      result[0], key.key(), tenantId);
            return new CacheEntry(result[0], System.currentTimeMillis() + cacheTtlMs);
        });
        return result[0];
    }

    private long resolveUsage(String tenantId, QuotaKey key) {
        // O(1) provider lookup via index (built lazily)
        QuotaUsageProvider provider = providerIndex.computeIfAbsent(key.key(), k ->
            usageProviders.stream()
                .filter(p -> p.supports(key))
                .findFirst()
                .orElse(null));

        if (provider != null) {
            long usage = provider.getCurrentUsage(tenantId, key);
            log.trace("Usage for quota '{}' in tenant '{}': {}", key.key(), tenantId, usage);
            return usage;
        }
        log.debug("No QuotaUsageProvider registered for '{}' — assuming usage=0", key.key());
        return 0L;
    }

    private static String cacheKey(String tenantId, QuotaKey key) {
        return tenantId + ":" + key.key();
    }
}



