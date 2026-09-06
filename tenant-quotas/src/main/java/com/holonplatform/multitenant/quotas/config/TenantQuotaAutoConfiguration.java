package com.holonplatform.multitenant.quotas.config;

import com.holonplatform.multitenant.quotas.QuotaUsageProvider;
import com.holonplatform.multitenant.quotas.TenantQuotaService;
import com.holonplatform.multitenant.quotas.internal.DefaultTenantQuotaService;
import com.holonplatform.multitenant.quotas.internal.QuotaAspect;
import com.holonplatform.multitenant.quotas.internal.UserCountQuotaProvider;
import com.holonplatform.multitenant.users.TenantUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Spring Boot auto-configuration for the tenant quota subsystem.
 *
 * <h2>What is registered</h2>
 * <ul>
 *   <li>A {@link DefaultTenantQuotaService} wiring all discovered {@link QuotaUsageProvider} beans.</li>
 *   <li>A {@link UserCountQuotaProvider} for the built-in {@code max_users} quota.</li>
 *   <li>A {@link QuotaAspect} for {@code @EnforceQuota} (when AspectJ is present).</li>
 * </ul>
 *
 * <h2>Registering quota definitions</h2>
 * <pre>{@code
 * @Autowired TenantQuotaService quotaService;
 *
 * @PostConstruct
 * void setupQuotas() {
 *     quotaService.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_USERS)
 *         .limit(TenantPlan.FREE, 3).limit(TenantPlan.STARTER, 10)
 *         .limit(TenantPlan.PROFESSIONAL, 50).limit(TenantPlan.ENTERPRISE, -1)
 *         .build());
 *
 *     quotaService.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
 *         .limit(TenantPlan.FREE, 500).limit(TenantPlan.STARTER, 5_000)
 *         .limit(TenantPlan.PROFESSIONAL, 50_000).limit(TenantPlan.ENTERPRISE, -1)
 *         .build());
 * }
 * }</pre>
 *
 * <h2>Providing custom usage counts</h2>
 * <pre>{@code
 * @Component
 * public class ContactCountProvider implements QuotaUsageProvider {
 *     @Autowired ContactRepository repo;
 *     @Override public boolean supports(QuotaKey key) {
 *         return QuotaKeys.MAX_CONTACTS.key().equals(key.key());
 *     }
 *     @Override public long getCurrentUsage(String tenantId, QuotaKey key) {
 *         return repo.countByTenantId(tenantId);
 *     }
 * }
 * }</pre>
 */
@AutoConfiguration
public class TenantQuotaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserCountQuotaProvider.class)
    public UserCountQuotaProvider userCountQuotaProvider(TenantUserService tenantUserService) {
        return new UserCountQuotaProvider(tenantUserService);
    }

    @Bean
    @ConditionalOnMissingBean(TenantQuotaService.class)
    public DefaultTenantQuotaService tenantQuotaService(
            @Autowired(required = false) List<QuotaUsageProvider> usageProviders) {
        return new DefaultTenantQuotaService(usageProviders);
    }

    @Bean
    @ConditionalOnMissingBean(QuotaAspect.class)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public QuotaAspect quotaAspect(TenantQuotaService tenantQuotaService) {
        return new QuotaAspect(tenantQuotaService);
    }
}

