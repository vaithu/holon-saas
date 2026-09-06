package com.holonplatform.multitenant.limits.config;

import com.holonplatform.multitenant.limits.TenantLimitKeys;
import com.holonplatform.multitenant.limits.TenantRateLimiter;
import com.holonplatform.multitenant.limits.internal.RateLimitAspect;
import com.holonplatform.multitenant.limits.internal.TokenBucketRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TenantRateLimitAutoConfiguration}.
 */
class TenantRateLimitAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantRateLimitAutoConfiguration.class));

    // ── TenantRateLimiter ─────────────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers TokenBucketRateLimiter as TenantRateLimiter")
    void autoConfig_registersTokenBucketRateLimiter() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantRateLimiter.class);
            assertThat(ctx.getBean(TenantRateLimiter.class))
                    .isInstanceOf(TokenBucketRateLimiter.class);
        });
    }

    @Test
    @DisplayName("auto-configured limiter has API_REQUESTS definition pre-loaded")
    void autoConfig_limiter_hasApiRequestsDefinition() {
        contextRunner.run(ctx -> {
            TenantRateLimiter limiter = ctx.getBean(TenantRateLimiter.class);
            TenantRateLimiter.LimitDefinition def = limiter.getDefinition(TenantLimitKeys.API_REQUESTS);
            assertThat(def).isNotNull();
            assertThat(def.maxRequests())
                    .isEqualTo(TenantLimitKeys.API_REQUESTS_DEFAULT.maxRequests());
        });
    }

    @Test
    @DisplayName("auto-configured limiter has all default keys registered")
    void autoConfig_limiter_hasAllDefaultKeys() {
        contextRunner.run(ctx -> {
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

    // ── RateLimitAspect ───────────────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers RateLimitAspect when AspectJ is on classpath")
    void autoConfig_registersRateLimitAspect() {
        contextRunner.run(ctx ->
                assertThat(ctx).hasSingleBean(RateLimitAspect.class));
    }

    @Test
    @DisplayName("auto-config backs off RateLimitAspect when custom one is present")
    void autoConfig_backsOff_whenCustomAspectPresent() {
        contextRunner
                .withUserConfiguration(CustomAspectConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(RateLimitAspect.class);
                    assertThat(ctx.getBean(RateLimitAspect.class))
                            .isInstanceOf(CustomAspectConfig.CustomAspect.class);
                });
    }

    // ── Back-off for TenantRateLimiter ────────────────────────────────────────

    @Test
    @DisplayName("auto-config backs off TenantRateLimiter when custom bean is present")
    void autoConfig_backsOff_whenCustomLimiterPresent() {
        contextRunner
                .withUserConfiguration(CustomLimiterConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantRateLimiter.class);
                    assertThat(ctx.getBean(TenantRateLimiter.class))
                            .isInstanceOf(CustomLimiterConfig.CustomLimiter.class);
                });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    static class CustomAspectConfig {

        @org.springframework.context.annotation.Bean
        RateLimitAspect rateLimitAspect(TenantRateLimiter limiter) {
            return new CustomAspect(limiter);
        }

        static class CustomAspect extends RateLimitAspect {
            CustomAspect(TenantRateLimiter limiter) { super(limiter); }
        }
    }

    static class CustomLimiterConfig {

        @org.springframework.context.annotation.Bean
        TenantRateLimiter tenantRateLimiter() {
            return new CustomLimiter();
        }

        static class CustomLimiter extends TokenBucketRateLimiter {
            // marker subclass
        }
    }
}

