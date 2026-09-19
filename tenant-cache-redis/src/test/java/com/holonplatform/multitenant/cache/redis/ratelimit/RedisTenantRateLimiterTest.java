package com.holonplatform.multitenant.cache.redis.ratelimit;

import com.holonplatform.multitenant.limits.TenantRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Real Testcontainers-backed regression test proving {@link RedisTenantRateLimiter} actually
 * enforces a shared limit through a real Redis server — including the specific scenario this class
 * exists to fix: two independent limiter instances (simulating two application instances behind a
 * load balancer) sharing one Redis-enforced limit instead of each getting their own.
 *
 * <p>Requires a running Docker daemon (via Testcontainers) —
 * {@code @Testcontainers(disabledWithoutDocker = true)} automatically skips this entire class (not
 * a failure) when Docker isn't available, so {@code mvn install} still succeeds on machines/CI
 * runners without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisTenantRateLimiterTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void tryAcquire_allowsUpToConfiguredCapacity() {
        RedisTenantRateLimiter limiter = newLimiter();
        String tenantId = uniqueTenantId();
        limiter.registerDefinition("test.api", TenantRateLimiter.LimitDefinition.perMinute(3));

        assertThat(limiter.tryAcquire(tenantId, "test.api")).isTrue();
        assertThat(limiter.tryAcquire(tenantId, "test.api")).isTrue();
        assertThat(limiter.tryAcquire(tenantId, "test.api")).isTrue();
        assertThat(limiter.tryAcquire(tenantId, "test.api")).isFalse();
    }

    @Test
    void multipleLimiterInstances_shareTheSameEnforcedLimit() {
        // Two independent RedisTenantRateLimiter instances, both talking to the same Redis
        // server, simulate two application instances behind a load balancer.
        RedisTenantRateLimiter instanceA = newLimiter();
        RedisTenantRateLimiter instanceB = newLimiter();
        String tenantId = uniqueTenantId();
        instanceA.registerDefinition("test.api", TenantRateLimiter.LimitDefinition.perMinute(3));
        instanceB.registerDefinition("test.api", TenantRateLimiter.LimitDefinition.perMinute(3));

        assertThat(instanceA.tryAcquire(tenantId, "test.api")).isTrue();
        assertThat(instanceB.tryAcquire(tenantId, "test.api")).isTrue();
        assertThat(instanceA.tryAcquire(tenantId, "test.api")).isTrue();
        // The 4th request, regardless of which instance serves it, must be denied: the limit of
        // 3 is enforced once across both instances, not 3-per-instance (6 total).
        assertThat(instanceB.tryAcquire(tenantId, "test.api")).isFalse();
    }

    @Test
    void tenants_haveIndependentCounters() {
        RedisTenantRateLimiter limiter = newLimiter();
        limiter.registerDefinition("test.api", TenantRateLimiter.LimitDefinition.perMinute(1));
        String tenantA = uniqueTenantId();
        String tenantB = uniqueTenantId();

        assertThat(limiter.tryAcquire(tenantA, "test.api")).isTrue();
        assertThat(limiter.tryAcquire(tenantA, "test.api")).isFalse();
        // tenantB's counter is independent of tenantA's.
        assertThat(limiter.tryAcquire(tenantB, "test.api")).isTrue();
    }

    @Test
    void remaining_decrementsOnAcquireAndIsFullForUntouchedTenant() {
        RedisTenantRateLimiter limiter = newLimiter();
        limiter.registerDefinition("test.api", TenantRateLimiter.LimitDefinition.perMinute(3));
        String tenantId = uniqueTenantId();

        assertThat(limiter.remaining(tenantId, "test.api")).isEqualTo(3);
        limiter.tryAcquire(tenantId, "test.api");
        assertThat(limiter.remaining(tenantId, "test.api")).isEqualTo(2);
    }

    @Test
    void reset_restoresFullCapacity() {
        RedisTenantRateLimiter limiter = newLimiter();
        limiter.registerDefinition("test.api", TenantRateLimiter.LimitDefinition.perMinute(1));
        String tenantId = uniqueTenantId();

        assertThat(limiter.tryAcquire(tenantId, "test.api")).isTrue();
        assertThat(limiter.tryAcquire(tenantId, "test.api")).isFalse();

        limiter.reset(tenantId, "test.api");

        assertThat(limiter.tryAcquire(tenantId, "test.api")).isTrue();
    }

    @Test
    void window_expiresAndRefillsCounter() throws InterruptedException {
        RedisTenantRateLimiter limiter = newLimiter();
        limiter.registerDefinition("test.fast", TenantRateLimiter.LimitDefinition.of(1, Duration.ofMillis(200)));
        String tenantId = uniqueTenantId();

        assertThat(limiter.tryAcquire(tenantId, "test.fast")).isTrue();
        assertThat(limiter.tryAcquire(tenantId, "test.fast")).isFalse();

        Thread.sleep(400);

        assertThat(limiter.tryAcquire(tenantId, "test.fast")).isTrue();
    }

    @Test
    void getDefinition_throwsForUnknownKey() {
        RedisTenantRateLimiter limiter = newLimiter();

        assertThatIllegalArgumentException().isThrownBy(() -> limiter.getDefinition("unknown.key"));
    }

    private RedisTenantRateLimiter newLimiter() {
        return new RedisTenantRateLimiter(redisTemplate);
    }

    private static String uniqueTenantId() {
        return "tenant-" + UUID.randomUUID();
    }
}
