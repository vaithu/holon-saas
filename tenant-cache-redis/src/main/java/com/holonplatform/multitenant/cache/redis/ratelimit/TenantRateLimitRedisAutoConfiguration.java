package com.holonplatform.multitenant.cache.redis.ratelimit;

import com.holonplatform.multitenant.limits.TenantLimitKeys;
import com.holonplatform.multitenant.limits.TenantRateLimiter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spring Boot auto-configuration for a Redis-backed {@link TenantRateLimiter}, shared across
 * every application instance.
 *
 * <p>Activates automatically whenever a {@link StringRedisTemplate} bean is present (Spring
 * Boot's own {@code RedisAutoConfiguration} creates one as soon as a {@code RedisConnectionFactory}
 * exists, e.g. via {@code spring-boot-starter-data-redis} + {@code spring.data.redis.*}
 * properties) — and only when {@code holon-multi-tenant-limits} is also on the classpath, and no
 * application-supplied {@link TenantRateLimiter} bean already exists.
 *
 * <p>Runs before {@code TenantRateLimitAutoConfiguration}, so that module's default (per-JVM,
 * Caffeine-backed) {@code TokenBucketRateLimiter} backs off via {@code @ConditionalOnMissingBean}
 * in favor of this distributed implementation — the same "Redis wins if present, otherwise fall
 * back to the local default" pattern used for {@code tenantDetailsCache}.
 *
 * <p>Without a {@link StringRedisTemplate} on the classpath/context, this auto-configuration is
 * entirely inert — the per-JVM {@code TokenBucketRateLimiter} remains the default.
 */
@AutoConfiguration
@AutoConfigureBefore(name = "com.holonplatform.multitenant.limits.config.TenantRateLimitAutoConfiguration")
@ConditionalOnClass({RedisConnectionFactory.class, TenantRateLimiter.class})
public class TenantRateLimitRedisAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(TenantRateLimiter.class)
    public RedisTenantRateLimiter tenantRateLimiter(StringRedisTemplate redisTemplate) {
        RedisTenantRateLimiter limiter = new RedisTenantRateLimiter(redisTemplate);
        // Same well-known defaults as TenantRateLimitAutoConfiguration's local implementation.
        limiter.registerDefinition(TenantLimitKeys.API_REQUESTS,       TenantLimitKeys.API_REQUESTS_DEFAULT);
        limiter.registerDefinition(TenantLimitKeys.EMAIL_SENDS,        TenantLimitKeys.EMAIL_SENDS_DEFAULT);
        limiter.registerDefinition(TenantLimitKeys.FILE_UPLOADS,       TenantLimitKeys.FILE_UPLOADS_DEFAULT);
        limiter.registerDefinition(TenantLimitKeys.REPORT_EXPORTS,     TenantLimitKeys.REPORT_EXPORTS_DEFAULT);
        limiter.registerDefinition(TenantLimitKeys.WEBHOOK_DELIVERIES, TenantLimitKeys.WEBHOOK_DELIVERIES_DEFAULT);
        return limiter;
    }
}
