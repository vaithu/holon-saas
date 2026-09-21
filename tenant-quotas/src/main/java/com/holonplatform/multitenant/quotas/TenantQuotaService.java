package com.holonplatform.multitenant.quotas;

import java.util.List;
import java.util.Map;

/**
 * Service for registering quota definitions and checking resource usage against plan limits.
 *
 * <h2>Setup</h2>
 * <p>Register quota definitions at startup (or in a {@code @PostConstruct} method):
 * <pre>{@code
 * @Autowired TenantQuotaService quotaService;
 *
 * @PostConstruct
 * void configureQuotas() {
 *     quotaService.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_USERS)
 *         .limit(TenantPlan.FREE,         3L)
 *         .limit(TenantPlan.STARTER,     10L)
 *         .limit(TenantPlan.PROFESSIONAL,50L)
 *         .limit(TenantPlan.ENTERPRISE,  -1L)
 *         .build());
 *
 *     quotaService.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
 *         .limit(TenantPlan.FREE,          500L)
 *         .limit(TenantPlan.STARTER,      5_000L)
 *         .limit(TenantPlan.PROFESSIONAL, 50_000L)
 *         .limit(TenantPlan.ENTERPRISE,   -1L)
 *         .build());
 * }
 * }</pre>
 *
 * <h2>Enforcement</h2>
 * <p>Either annotate service methods:
 * <pre>{@code
 * @EnforceQuota("max_contacts")
 * public Contact createContact(ContactDto dto) { ... }
 * }</pre>
 * or check programmatically:
 * <pre>{@code
 * QuotaCheckResult result = quotaService.checkQuota(tenantId, QuotaKeys.MAX_CONTACTS);
 * if (result.exceeded()) {
 *     throw new QuotaExceededException(QuotaKeys.MAX_CONTACTS, result.limit(), result.currentUsage());
 * }
 * }</pre>
 */
public interface TenantQuotaService {

    /**
     * Registers a quota definition. Replaces any existing definition for the same key.
     *
     * @param definition quota definition — must not be null
     */
    void registerQuota(QuotaDefinition definition);

    /**
     * Checks the current usage against the tenant's plan limit for the given quota key.
     *
     * @param tenantId the tenant to check
     * @param key      the quota key
     * @return check result — never null
     */
    QuotaCheckResult checkQuota(String tenantId, QuotaKey key);

    /**
     * Convenience overload accepting a key string (matched against registered definitions).
     */
    QuotaCheckResult checkQuota(String tenantId, String quotaKeyString);

    /**
     * Returns the configured limit for the tenant's plan.
     * Returns {@code -1} if unlimited or no definition is registered.
     *
     * @param tenantId the tenant
     * @param key      the quota key
     * @return the limit, or -1 for unlimited
     */
    long getLimit(String tenantId, QuotaKey key);

    /**
     * Returns remaining units for the tenant's quota.
     * Returns {@link Long#MAX_VALUE} if unlimited.
     *
     * @param tenantId the tenant
     * @param key      the quota key
     * @return remaining units
     */
    long getRemaining(String tenantId, QuotaKey key);

    /**
     * Returns a snapshot of all registered quotas for a tenant,
     * including current usage and remaining capacity.
     * Useful for quota dashboards.
     *
     * @param tenantId the tenant
     * @return map from quota key string → check result
     */
    Map<String, QuotaCheckResult> getAllQuotas(String tenantId);

    /**
     * Returns all registered quota definitions.
     */
    List<QuotaDefinition> listDefinitions();

    // ── Cache management ──────────────────────────────────────────────────────

    /**
     * Evicts any cached usage count for {@code (tenantId, key)}, so the next check re-reads it.
     *
     * <p>Implementations that cache usage do so on a short timer, which means a resource
     * created or removed by a path that does not go through {@link annotation.EnforceQuota} —
     * accepting an invitation, an administrative import, a cascading delete — leaves the
     * cached count stale and the quota briefly wrong in either direction. Such code should
     * call this afterwards.
     *
     * <p>The default implementation does nothing, which is correct for implementations that
     * do not cache.
     *
     * @param tenantId the tenant whose cached usage should be dropped
     * @param key      the quota to drop
     */
    default void invalidateUsageCache(String tenantId, QuotaKey key) {
        // No cache to invalidate.
    }

    /**
     * Evicts every cached usage count for a tenant — for instance after a bulk import, or when
     * the tenant is deleted.
     *
     * @param tenantId the tenant whose cached usage should be dropped
     */
    default void invalidateUsageCacheForTenant(String tenantId) {
        // No cache to invalidate.
    }
}

