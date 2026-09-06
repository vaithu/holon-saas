package com.holonplatform.multitenant.quotas.internal;

import com.holonplatform.multitenant.quotas.QuotaKey;
import com.holonplatform.multitenant.quotas.QuotaKeys;
import com.holonplatform.multitenant.quotas.QuotaUsageProvider;
import com.holonplatform.multitenant.users.TenantUserService;

/**
 * Built-in {@link QuotaUsageProvider} for {@link QuotaKeys#MAX_USERS}.
 *
 * <p>Uses {@link TenantUserService#count(String)} to report the current
 * number of active users in the tenant. Registered automatically by
 * {@link com.holonplatform.multitenant.quotas.config.TenantQuotaAutoConfiguration}.</p>
 */
public class UserCountQuotaProvider implements QuotaUsageProvider {

    private final TenantUserService tenantUserService;

    public UserCountQuotaProvider(TenantUserService tenantUserService) {
        this.tenantUserService = tenantUserService;
    }

    @Override
    public boolean supports(QuotaKey key) {
        return QuotaKeys.MAX_USERS.key().equals(key.key());
    }

    @Override
    public long getCurrentUsage(String tenantId, QuotaKey key) {
        return tenantUserService.count(tenantId);
    }
}

