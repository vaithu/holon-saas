package com.holonplatform.multitenant.quotas;

/**
 * SPI for reporting the current resource usage for a specific {@link QuotaKey}.
 *
 * <p>Declare one Spring bean per quota key you want to enforce:
 * <pre>{@code
 * @Component
 * public class ContactCountProvider implements QuotaUsageProvider {
 *
 *     @Autowired ContactRepository contactRepo;
 *
 *     @Override
 *     public boolean supports(QuotaKey key) {
 *         return QuotaKeys.MAX_CONTACTS.key().equals(key.key());
 *     }
 *
 *     @Override
 *     public long getCurrentUsage(String tenantId, QuotaKey key) {
 *         return contactRepo.countByTenantId(tenantId);
 *     }
 * }
 * }</pre>
 *
 * <p>If no provider is registered for a key, usage is assumed to be 0
 * (no blocking enforcement).</p>
 */
public interface QuotaUsageProvider {

    /**
     * Returns {@code true} if this provider can report usage for the given quota key.
     */
    boolean supports(QuotaKey key);

    /**
     * Returns the current resource usage count for the given tenant and quota key.
     *
     * @param tenantId the tenant
     * @param key      the quota key
     * @return current usage (≥ 0)
     */
    long getCurrentUsage(String tenantId, QuotaKey key);
}

