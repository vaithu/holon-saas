package com.holonplatform.multitenant.limits;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.limits.annotation.RateLimit;
import com.holonplatform.multitenant.limits.internal.RateLimitAspect;
import com.holonplatform.multitenant.limits.internal.TokenBucketRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link RateLimitAspect}.
 *
 * <p>Bootstraps a minimal Spring context with AOP enabled to verify the
 * aspect intercepts {@link RateLimit}-annotated methods correctly.
 */
@SpringBootTest(classes = RateLimitAspectTest.TestConfig.class)
class RateLimitAspectTest {

    static final String KEY = "test.api";

    @Autowired TokenBucketRateLimiter rateLimiter;
    @Autowired RateLimitedService      target;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        rateLimiter.reset("acme", KEY);
        rateLimiter.reset("beta", KEY);
    }

    // ── allowed ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("@RateLimit allows call when token is available")
    void allowsCallWithToken() {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(TenantPlan.FREE).status(TenantStatus.ACTIVE).build());

        assertThatCode(() -> target.doWork()).doesNotThrowAnyException();
    }

    // ── denied ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("@RateLimit throws RateLimitExceededException when bucket is empty")
    void throwsWhenExhausted() {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(TenantPlan.FREE).status(TenantStatus.ACTIVE).build());

        // Consume the only token directly
        rateLimiter.tryAcquire("acme", KEY);

        assertThatExceptionOfType(RateLimitExceededException.class)
                .isThrownBy(() -> target.doWork())
                .satisfies(ex -> {
                    assertThat(ex.getTenantId()).isEqualTo("acme");
                    assertThat(ex.getLimitKey()).isEqualTo(KEY);
                });
    }

    // ── no tenant bypass ──────────────────────────────────────────────────────

    @Test
    @DisplayName("@RateLimit is bypassed when no tenant is in context (system call)")
    void bypassedWithNoTenant() {
        // No TenantContext.set() — simulates system / admin context
        // Even with an exhausted bucket for an irrelevant tenant, system calls are free
        assertThatCode(() -> target.doWork()).doesNotThrowAnyException();
    }

    // ── method still executes ─────────────────────────────────────────────────

    @Test
    @DisplayName("method body executes when rate limit is not exceeded")
    void methodBodyExecutes() {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(TenantPlan.FREE).status(TenantStatus.ACTIVE).build());

        assertThat(target.computeValue()).isEqualTo(42);
    }

    @Test
    @DisplayName("exception thrown by method propagates through the aspect unchanged")
    void methodExceptionPropagates() {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(TenantPlan.FREE).status(TenantStatus.ACTIVE).build());

        assertThatThrownBy(() -> target.throwingMethod())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("deliberate");
    }

    // ── inner helpers ─────────────────────────────────────────────────────────

    interface RateLimitedService {
        void doWork();
        int  computeValue();
        void throwingMethod();
    }

    static class RateLimitedServiceImpl implements RateLimitedService {

        @Override
        @RateLimit(key = KEY)
        public void doWork() { /* no-op */ }

        @Override
        @RateLimit(key = KEY)
        public int computeValue() { return 42; }

        @Override
        @RateLimit(key = KEY)
        public void throwingMethod() { throw new IllegalStateException("deliberate"); }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        TokenBucketRateLimiter tokenBucketRateLimiter() {
            TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
            // 1-token bucket so tests can exhaust it with a single extra acquire
            limiter.registerDefinition(KEY, TenantRateLimiter.LimitDefinition.perMinute(1));
            return limiter;
        }

        @Bean
        RateLimitAspect rateLimitAspect(TenantRateLimiter rateLimiter) {
            return new RateLimitAspect(rateLimiter);
        }

        @Bean
        RateLimitedService rateLimitedService() {
            return new RateLimitedServiceImpl();
        }
    }
}

