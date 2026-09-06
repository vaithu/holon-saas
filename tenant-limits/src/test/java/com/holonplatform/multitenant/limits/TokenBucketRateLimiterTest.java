package com.holonplatform.multitenant.limits;

import com.holonplatform.multitenant.limits.internal.TokenBucketRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TokenBucketRateLimiter}.
 */
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new TokenBucketRateLimiter();
        // 3 requests per minute — small capacity for easy assertion
        limiter.registerDefinition("test.api", TenantRateLimiter.LimitDefinition.perMinute(3));
        // 1 req per second
        limiter.registerDefinition("test.fast", TenantRateLimiter.LimitDefinition.perSecond(1));
    }

    // ── Basic allow / deny ────────────────────────────────────────────────────

    @Test
    @DisplayName("tryAcquire allows requests up to capacity")
    void tryAcquire_allowsUpToCapacity() {
        assertThat(limiter.tryAcquire("acme", "test.api")).isTrue();
        assertThat(limiter.tryAcquire("acme", "test.api")).isTrue();
        assertThat(limiter.tryAcquire("acme", "test.api")).isTrue();
    }

    @Test
    @DisplayName("tryAcquire denies when bucket is exhausted")
    void tryAcquire_deniesWhenExhausted() {
        limiter.tryAcquire("acme", "test.api");
        limiter.tryAcquire("acme", "test.api");
        limiter.tryAcquire("acme", "test.api");
        // 4th should be denied
        assertThat(limiter.tryAcquire("acme", "test.api")).isFalse();
    }

    @Test
    @DisplayName("acquireOrThrow throws RateLimitExceededException when exhausted")
    void acquireOrThrow_throwsWhenExhausted() {
        limiter.tryAcquire("acme", "test.api");
        limiter.tryAcquire("acme", "test.api");
        limiter.tryAcquire("acme", "test.api");
        assertThatExceptionOfType(RateLimitExceededException.class)
                .isThrownBy(() -> limiter.acquireOrThrow("acme", "test.api"))
                .satisfies(ex -> {
                    assertThat(ex.getTenantId()).isEqualTo("acme");
                    assertThat(ex.getLimitKey()).isEqualTo("test.api");
                    assertThat(ex.getLimitPerWindow()).isEqualTo(3);
                });
    }

    // ── Tenant isolation ─────────────────────────────���────────────────────────

    @Test
    @DisplayName("tenants have independent buckets")
    void tenants_haveIndependentBuckets() {
        limiter.tryAcquire("acme", "test.api");
        limiter.tryAcquire("acme", "test.api");
        limiter.tryAcquire("acme", "test.api");
        // acme bucket is empty; beta bucket is untouched
        assertThat(limiter.tryAcquire("beta", "test.api")).isTrue();
    }

    // ── remaining ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("remaining decrements on each acquire")
    void remaining_decrementsOnAcquire() {
        assertThat(limiter.remaining("acme", "test.api")).isEqualTo(3);
        limiter.tryAcquire("acme", "test.api");
        assertThat(limiter.remaining("acme", "test.api")).isEqualTo(2);
    }

    @Test
    @DisplayName("remaining returns full capacity for new tenant")
    void remaining_fullCapacityForNewTenant() {
        assertThat(limiter.remaining("new-tenant", "test.api")).isEqualTo(3);
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("reset restores full bucket capacity")
    void reset_restoresBucket() {
        limiter.tryAcquire("acme", "test.api");
        limiter.tryAcquire("acme", "test.api");
        limiter.tryAcquire("acme", "test.api");
        assertThat(limiter.tryAcquire("acme", "test.api")).isFalse();
        limiter.reset("acme", "test.api");
        assertThat(limiter.tryAcquire("acme", "test.api")).isTrue();
    }

    // ── getDefinition ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDefinition returns registered definition")
    void getDefinition_returnsRegistered() {
        TenantRateLimiter.LimitDefinition def = limiter.getDefinition("test.api");
        assertThat(def.maxRequests()).isEqualTo(3);
    }

    @Test
    @DisplayName("getDefinition throws for unknown key")
    void getDefinition_throwsForUnknownKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> limiter.getDefinition("unknown.key"));
    }

    // ── Null guards ─��─────────────────────────────────────────────────────────

    @Test
    @DisplayName("tryAcquire throws NPE for null tenantId")
    void tryAcquire_nullTenantId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> limiter.tryAcquire(null, "test.api"));
    }

    @Test
    @DisplayName("tryAcquire throws NPE for null key")
    void tryAcquire_nullKey_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> limiter.tryAcquire("acme", null));
    }

    // ── Refill (fast window) ──────────────────────────────────────────────────

    @Test
    @DisplayName("bucket refills after window elapses")
    void bucket_refillsAfterWindow() throws InterruptedException {
        // Use 1-per-second limit; exhaust, wait 1.1s, try again
        limiter.tryAcquire("acme", "test.fast");
        assertThat(limiter.tryAcquire("acme", "test.fast")).isFalse();
        Thread.sleep(1100);
        assertThat(limiter.tryAcquire("acme", "test.fast")).isTrue();
    }
}

