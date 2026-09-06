package com.holonplatform.multitenant.limits.config;

import com.holonplatform.multitenant.limits.TenantLimitKeys;
import com.holonplatform.multitenant.limits.TenantRateLimiter;
import com.holonplatform.multitenant.limits.internal.RateLimitAspect;
import com.holonplatform.multitenant.limits.internal.TokenBucketRateLimiter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the tenant rate-limiting subsystem.
 *
 * <h2>What is registered</h2>
 * <ol>
 *   <li>A {@link TokenBucketRateLimiter} pre-loaded with the {@link TenantLimitKeys}
 *       default {@link TenantRateLimiter.LimitDefinition}s.</li>
 *   <li>A {@link RateLimitAspect} bean — only when AspectJ is on the classpath and
 *       no custom {@link RateLimitAspect} bean is present.</li>
 * </ol>
 *
 * <p>Override any of these beans in your application context to customise per-plan
 * limits or swap the storage backend.
 */
@AutoConfiguration
public class TenantRateLimitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TenantRateLimiter.class)
    public TokenBucketRateLimiter tenantRateLimiter() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        // Register well-known defaults
        limiter.registerDefinition(TenantLimitKeys.API_REQUESTS,       TenantLimitKeys.API_REQUESTS_DEFAULT);
        limiter.registerDefinition(TenantLimitKeys.EMAIL_SENDS,        TenantLimitKeys.EMAIL_SENDS_DEFAULT);
        limiter.registerDefinition(TenantLimitKeys.FILE_UPLOADS,       TenantLimitKeys.FILE_UPLOADS_DEFAULT);
        limiter.registerDefinition(TenantLimitKeys.REPORT_EXPORTS,     TenantLimitKeys.REPORT_EXPORTS_DEFAULT);
        limiter.registerDefinition(TenantLimitKeys.WEBHOOK_DELIVERIES, TenantLimitKeys.WEBHOOK_DELIVERIES_DEFAULT);
        return limiter;
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitAspect.class)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public RateLimitAspect rateLimitAspect(TenantRateLimiter rateLimiter) {
        return new RateLimitAspect(rateLimiter);
    }
}

