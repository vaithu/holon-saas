package com.holonplatform.multitenant.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.holonplatform.multitenant.CachingTenantDetailsLoader;
import com.holonplatform.multitenant.TenantContextTemplate;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.cache.redis.ratelimit.RedisTenantRateLimiter;
import com.holonplatform.multitenant.demo.service.DemoLimits;
import com.holonplatform.multitenant.demo.service.DemoTenantProvisioner;
import com.holonplatform.multitenant.demo.service.RedisFeatureReport;
import com.holonplatform.multitenant.demo.support.TestWorkspaceIds;
import com.holonplatform.multitenant.limits.RateLimitExceededException;
import com.holonplatform.multitenant.limits.TenantRateLimiter;

/**
 * Batch five, F23: the tenant details cache and the rate limiter, once Redis is present.
 *
 * <h2>What is worth asserting here</h2>
 * <p>Not that rate limiting works — {@code SaasDemoPlatformFeaturesTest} already covers that
 * against the in-memory limiter, and the behaviour is supposed to be identical. What matters is
 * that the behaviour <em>stayed</em> identical after the implementation underneath it was
 * swapped, and that the state moved out of the JVM. A rate limit that silently stops being
 * enforced when you scale out is worse than no rate limit, because the dashboard still shows one.
 *
 * <p>Redis here is a real {@code redis-server}, started in-process by
 * {@code EmbeddedRedisEnvironmentPostProcessor}, so these assertions exercise the actual Lua
 * script and the actual {@code EXPIRE} semantics rather than a fake.
 */
@SpringBootTest
class SaasDemoRedisTest {

    @Autowired private DemoTenantProvisioner provisioner;
    @Autowired private TenantRateLimiter     rateLimiter;
    @Autowired private RedisFeatureReport    report;
    @Autowired private StringRedisTemplate   redis;
    @Autowired private TenantDetailsLoader   detailsLoader;

    @Autowired(required = false)
    @Qualifier("tenantDetailsCache")
    private Cache detailsCache;

    private TenantDetails workspace;

    @BeforeEach
    void signUpAWorkspace() {
        String workspaceId = TestWorkspaceIds.uniqueWorkspaceId("b5");
        workspace = provisioner.signUpWorkspace(workspaceId, "Redis Ltd",
                TestWorkspaceIds.ownerEmail(workspaceId), "owner-secret");
    }

    // ── The swap itself ────────────────────────────────────────────────────────────────

    @Test
    void redisReplacesBothTheLimiterAndTheDetailsCache() {
        // Neither the demo nor the framework's calling code names Redis anywhere. The
        // substitution is done purely by what is on the classpath, which is the property that
        // makes it possible to develop against the in-memory versions and deploy on Redis.
        assertThat(rateLimiter).isInstanceOf(RedisTenantRateLimiter.class);
        assertThat(detailsCache).isInstanceOf(RedisCache.class);
        assertThat(report.redisAvailable()).isTrue();
    }

    @Test
    void theEmbeddedServerIsARealRedis() {
        // Guards against the report quietly reporting success from a stubbed connection: this
        // is the version string the actual server process announced.
        assertThat(report.serverVersion()).startsWith("5.");
    }

    // ── The limit still behaves the same ───────────────────────────────────────────────

    @Test
    void theDemosOwnLimitKeySurvivedTheSwap() {
        // The regression this exists to catch: registerDefinition used to be reachable only by
        // casting to TokenBucketRateLimiter, so adding Redis left this key undefined and the
        // sales limit unenforced. An undefined key is refused outright by the limiter, so
        // asking for its definition is enough to prove the registration landed.
        assertThat(rateLimiter.getDefinition(DemoLimits.SALES_RECORD_LIMIT_KEY).maxRequests())
                .isEqualTo(DemoLimits.SALES_RECORDS_PER_MINUTE);
    }

    @Test
    void theWindowStillClosesAfterTheConfiguredNumberOfCalls() {
        String key = DemoLimits.SALES_RECORD_LIMIT_KEY;
        for (int i = 0; i < DemoLimits.SALES_RECORDS_PER_MINUTE; i++) {
            assertThat(rateLimiter.tryAcquire(workspace.tenantId(), key))
                    .as("permit %d of %d", i + 1, DemoLimits.SALES_RECORDS_PER_MINUTE)
                    .isTrue();
        }
        assertThat(rateLimiter.tryAcquire(workspace.tenantId(), key)).isFalse();
        assertThatThrownBy(() -> rateLimiter.acquireOrThrow(workspace.tenantId(), key))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void oneWorkspaceExhaustingItsWindowDoesNotAffectAnother() {
        TenantDetails other = provisioner.signUpWorkspace(TestWorkspaceIds.uniqueWorkspaceId("b5-other"),
                "Other Ltd", "owner@b5-other.test", "owner-secret");
        String key = DemoLimits.SALES_RECORD_LIMIT_KEY;

        for (int i = 0; i < DemoLimits.SALES_RECORDS_PER_MINUTE; i++) {
            rateLimiter.tryAcquire(workspace.tenantId(), key);
        }
        assertThat(rateLimiter.tryAcquire(workspace.tenantId(), key)).isFalse();
        assertThat(rateLimiter.tryAcquire(other.tenantId(), key)).isTrue();
    }

    // ── The state is genuinely outside this JVM ────────────────────────────────────────

    @Test
    void theCounterIsHeldInRedisUnderATenantScopedKey() {
        String key = DemoLimits.SALES_RECORD_LIMIT_KEY;
        rateLimiter.tryAcquire(workspace.tenantId(), key);

        String redisKey = "holon:ratelimit:" + workspace.tenantId() + ":" + key;
        // Read with a plain template rather than through the limiter: going around the API is
        // the only way to show that a second process would find the same counter.
        assertThat(redis.opsForValue().get(redisKey)).isEqualTo("1");
        assertThat(redis.getExpire(redisKey)).isPositive();
    }

    @Test
    void theReportOnlyShowsKeysBelongingToTheBoundWorkspace() {
        String key = DemoLimits.SALES_RECORD_LIMIT_KEY;
        rateLimiter.tryAcquire(workspace.tenantId(), key);

        TenantDetails other = provisioner.signUpWorkspace(TestWorkspaceIds.uniqueWorkspaceId("b5-peek"),
                "Peek Ltd", "owner@b5-peek.test", "owner-secret");
        rateLimiter.tryAcquire(other.tenantId(), key);

        TenantContextTemplate.run(workspace, () ->
                assertThat(report.rateLimitKeysForCurrentTenant())
                        .isNotEmpty()
                        .allSatisfy(row -> assertThat(row).contains(workspace.tenantId()))
                        // Redis is shared by every workspace, so the read has to be scoped
                        // explicitly; a bare KEYS * would leak one tenant's usage to another.
                        .noneSatisfy(row -> assertThat(row).contains(other.tenantId())));
    }

    @Test
    void aResetClearsTheCounterInRedisNotJustLocally() {
        String key = DemoLimits.SALES_RECORD_LIMIT_KEY;
        rateLimiter.tryAcquire(workspace.tenantId(), key);
        rateLimiter.reset(workspace.tenantId(), key);

        assertThat(redis.opsForValue().get("holon:ratelimit:" + workspace.tenantId() + ":" + key))
                .isNull();
        assertThat(rateLimiter.remaining(workspace.tenantId(), key))
                .isEqualTo(DemoLimits.SALES_RECORDS_PER_MINUTE);
    }

    // ── The details cache ──────────────────────────────────────────────────────────────

    @Test
    void loadedTenantDetailsAreCachedInRedisForEveryInstanceToShare() {
        detailsLoader.load(workspace.tenantId());

        // The Spring Cache abstraction names Redis keys "<cache>::<key>", so this is the entry
        // another application instance would hit instead of going back to the database.
        assertThat(redis.hasKey(report.detailsCacheKeyFor(workspace.tenantId()))).isTrue();
    }

    @Test
    void evictingOnOneInstanceRemovesTheSharedEntry() {
        detailsLoader.load(workspace.tenantId());
        assertThat(redis.hasKey(report.detailsCacheKeyFor(workspace.tenantId()))).isTrue();

        // Through the framework's own invalidate(), because that is what PlatformConsoleService
        // calls when a workspace is suspended. Spring's Cache.evict() is permitted to complete
        // asynchronously and the Redis implementation does exactly that, so this asserts the
        // stronger guarantee the caller actually depends on: gone by the time we return.
        ((CachingTenantDetailsLoader) detailsLoader).invalidate(workspace.tenantId());

        // The eviction is a delete against shared state, not a local invalidation that leaves
        // other nodes serving a tenant that has been switched off.
        assertThat(redis.hasKey(report.detailsCacheKeyFor(workspace.tenantId()))).isFalse();
    }

    @Test
    void theReportNamesWhatIsActuallyWired() {
        assertThat(report.rateLimiterImplementation()).isEqualTo("RedisTenantRateLimiter");
        assertThat(report.detailsCacheImplementation()).contains("shared, in Redis");
    }
}
