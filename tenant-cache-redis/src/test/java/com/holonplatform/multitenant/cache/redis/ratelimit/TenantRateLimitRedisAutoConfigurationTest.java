package com.holonplatform.multitenant.cache.redis.ratelimit;

import com.holonplatform.multitenant.limits.TenantLimitKeys;
import com.holonplatform.multitenant.limits.TenantRateLimiter;
import com.holonplatform.multitenant.limits.config.TenantRateLimitAutoConfiguration;
import com.holonplatform.multitenant.limits.internal.TokenBucketRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for {@link TenantRateLimitRedisAutoConfiguration}, using a mocked
 * {@link StringRedisTemplate} to verify Spring wiring/back-off behavior in isolation from a real
 * Redis server (round-trip correctness against a real server is covered separately by
 * {@link RedisTenantRateLimiterTest}, which requires Docker).
 */
class TenantRateLimitRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantRateLimitRedisAutoConfiguration.class));

    @Test
    @DisplayName("registers RedisTenantRateLimiter when a StringRedisTemplate bean is present")
    void registersRedisLimiter_whenStringRedisTemplatePresent() {
        contextRunner
                .withUserConfiguration(StringRedisTemplateConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantRateLimiter.class);
                    assertThat(ctx.getBean(TenantRateLimiter.class))
                            .isInstanceOf(RedisTenantRateLimiter.class);
                });
    }

    @Test
    @DisplayName("does not register any TenantRateLimiter when no StringRedisTemplate bean is present")
    void doesNotRegister_whenNoStringRedisTemplate() {
        contextRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(TenantRateLimiter.class));
    }

    @Test
    @DisplayName("auto-configured Redis limiter has all default keys registered")
    void autoConfig_limiter_hasAllDefaultKeys() {
        contextRunner
                .withUserConfiguration(StringRedisTemplateConfig.class)
                .run(ctx -> {
                    TenantRateLimiter limiter = ctx.getBean(TenantRateLimiter.class);
                    assertThatCode(() -> limiter.getDefinition(TenantLimitKeys.API_REQUESTS))
                            .doesNotThrowAnyException();
                    assertThatCode(() -> limiter.getDefinition(TenantLimitKeys.EMAIL_SENDS))
                            .doesNotThrowAnyException();
                    assertThatCode(() -> limiter.getDefinition(TenantLimitKeys.FILE_UPLOADS))
                            .doesNotThrowAnyException();
                    assertThatCode(() -> limiter.getDefinition(TenantLimitKeys.REPORT_EXPORTS))
                            .doesNotThrowAnyException();
                    assertThatCode(() -> limiter.getDefinition(TenantLimitKeys.WEBHOOK_DELIVERIES))
                            .doesNotThrowAnyException();
                });
    }

    @Test
    @DisplayName("backs off in favor of an application-supplied TenantRateLimiter bean")
    void backsOff_whenCustomLimiterPresent() {
        contextRunner
                .withUserConfiguration(StringRedisTemplateConfig.class, CustomLimiterConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantRateLimiter.class);
                    assertThat(ctx.getBean(TenantRateLimiter.class))
                            .isInstanceOf(CustomLimiterConfig.CustomLimiter.class);
                });
    }

    @Test
    @DisplayName("when combined with the local auto-config, Redis wins and the local one backs off")
    void combinedWithLocalAutoConfig_redisWins() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        TenantRateLimitRedisAutoConfiguration.class,
                        TenantRateLimitAutoConfiguration.class))
                .withUserConfiguration(StringRedisTemplateConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantRateLimiter.class);
                    assertThat(ctx.getBean(TenantRateLimiter.class))
                            .isInstanceOf(RedisTenantRateLimiter.class)
                            .isNotInstanceOf(TokenBucketRateLimiter.class);
                });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    static class StringRedisTemplateConfig {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    static class CustomLimiterConfig {

        @Bean
        TenantRateLimiter tenantRateLimiter() {
            return new CustomLimiter();
        }

        static class CustomLimiter implements TenantRateLimiter {
            @Override
            public boolean tryAcquire(String tenantId, String key) { return true; }
            @Override
            public LimitDefinition getDefinition(String key) { return LimitDefinition.perMinute(1); }
            @Override
            public void reset(String tenantId, String key) { }
            @Override
            public long remaining(String tenantId, String key) { return 1; }
        }
    }
}
