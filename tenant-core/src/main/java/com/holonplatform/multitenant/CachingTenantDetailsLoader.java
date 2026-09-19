package com.holonplatform.multitenant;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;
import java.util.Objects;

/**
 * Decorates another {@link TenantDetailsLoader}, caching each successfully loaded
 * {@link TenantDetails} for a configurable time-to-live via Spring's {@link Cache} abstraction,
 * instead of a bespoke caching layer.
 *
 * <h2>Why this is needed</h2>
 * <p>{@link com.holonplatform.multitenant.filter.TenantContextFilter} calls
 * {@link TenantDetailsLoader#load(String)} on <strong>every</strong> request reaching a
 * non-ignored path — including every Vaadin Flow UIDL/heartbeat round-trip a single click
 * triggers, since those requests target the same URL as the visible route, not a path
 * excluded via {@code holon.tenant.ignored-paths}. Without this decorator, the default
 * {@code TenantDetailsLoader} registered by {@code tenant-onboarding} (a trivial delegate to
 * {@code TenantRepository}) re-issues a full database query for the tenant record on every
 * single one of those requests.
 *
 * <p>This mirrors {@link com.holonplatform.multitenant.resolver.SessionCachingTenantResolver},
 * which solves the equivalent problem for tenant <em>ID</em> resolution; this class solves it
 * for the subsequent tenant <em>details</em> load.
 *
 * <h2>Storage</h2>
 * <p>Defaults to a per-JVM Caffeine-backed {@link Cache} (via {@link CaffeineCache}) with
 * {@code expireAfterWrite} set to the configured TTL, when no {@link Cache} is supplied. For
 * multi-instance deployments, pass a shared {@link Cache} instead — e.g. the optional
 * {@code tenant-cache-redis} module's Redis-backed {@code Cache} bean — so an
 * {@link #invalidate(String)} on one instance is immediately visible to every other instance,
 * not just after the TTL elapses.
 *
 * <h2>Consistency</h2>
 * <p>Entries expire after the configured TTL, so administrative changes (suspend, plan change,
 * ...) are picked up within that window even without explicit invalidation. Callers that mutate
 * tenant data directly (e.g. {@code tenant-admin}'s admin service) should additionally call
 * {@link #invalidate(String)} right after a successful write, so the change is visible
 * immediately rather than after the TTL elapses.
 *
 * <p>Cached {@link TenantDetails} instances must be treated as read-only by callers: mutating a
 * cached instance in place would corrupt the value seen by every other request sharing it.
 */
public class CachingTenantDetailsLoader implements TenantDetailsLoader {

	/** Name shared by every {@link Cache} this class creates or is handed, for consistent metrics/logging. */
	public static final String CACHE_NAME = "tenantDetails";

	private final TenantDetailsLoader delegate;
	private final Cache cache;

	/**
	 * Creates a new caching decorator backed by a per-JVM Caffeine {@link Cache}.
	 *
	 * @param delegate  the loader to delegate cache-miss lookups to; must not be null
	 * @param ttlMillis how long a loaded {@link TenantDetails} stays cached, in milliseconds;
	 *                  must be strictly positive
	 */
	public CachingTenantDetailsLoader(TenantDetailsLoader delegate, long ttlMillis) {
		this(delegate, ttlMillis, defaultCache(ttlMillis));
	}

	/**
	 * Creates a new caching decorator backed by the given Spring {@link Cache}.
	 *
	 * <p>The {@code ttlMillis} parameter is retained only for constructor-level input validation
	 * (rejecting a non-positive value up front regardless of which cache backs this instance);
	 * the actual expiry is whatever {@code cache} itself is configured with.
	 *
	 * @param delegate  the loader to delegate cache-miss lookups to; must not be null
	 * @param ttlMillis must be strictly positive
	 * @param cache     the {@link Cache} to use for storage; must not be null
	 */
	public CachingTenantDetailsLoader(TenantDetailsLoader delegate, long ttlMillis, Cache cache) {
		this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
		if (ttlMillis <= 0) {
			throw new IllegalArgumentException("ttlMillis must be strictly positive");
		}
		this.cache = Objects.requireNonNull(cache, "cache must not be null");
	}

	private static Cache defaultCache(long ttlMillis) {
		if (ttlMillis <= 0) {
			throw new IllegalArgumentException("ttlMillis must be strictly positive");
		}
		return new CaffeineCache(CACHE_NAME,
				Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(ttlMillis)).build());
	}

	@Override
	public TenantDetails load(String tenantId) {
		Objects.requireNonNull(tenantId, "tenantId must not be null");
		try {
			// Cache.get(key, Callable) only stores the value once the callable returns
			// normally — an exception (e.g. TenantNotFoundException) is never cached, so a
			// transient failure doesn't stick around for the TTL.
			return cache.get(tenantId, () -> delegate.load(tenantId));
		} catch (Cache.ValueRetrievalException e) {
			// Spring's Cache.get(key, Callable) wraps any exception thrown by the loader in
			// this type; unwrap it so callers see the original exception, exactly as if no
			// caching decorator were involved.
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException re) {
				throw re;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw e;
		}
	}

	/**
	 * Evicts the cached {@link TenantDetails} for a single tenant, if present. Call this right
	 * after persisting a change to that tenant so the change is visible immediately.
	 *
	 * @param tenantId the tenant identifier to evict; must not be null
	 */
	public void invalidate(String tenantId) {
		Objects.requireNonNull(tenantId, "tenantId must not be null");
		cache.evict(tenantId);
	}

	/** Evicts every cached {@link TenantDetails} entry. */
	public void invalidateAll() {
		cache.clear();
	}

	/** Returns the wrapped loader. */
	public TenantDetailsLoader getDelegate() {
		return delegate;
	}

	/** Returns the underlying {@link Cache}. */
	public Cache getCache() {
		return cache;
	}
}
