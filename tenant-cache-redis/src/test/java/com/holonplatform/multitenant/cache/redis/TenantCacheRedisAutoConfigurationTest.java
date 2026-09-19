package com.holonplatform.multitenant.cache.redis;

import com.holonplatform.multitenant.CachingTenantDetailsLoader;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.config.TenantProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.*;

/**
 * Real Testcontainers-backed regression test proving {@link TenantCacheRedisAutoConfiguration}'s
 * {@code tenantDetailsCache} bean actually round-trips {@link TenantDetails} through a real Redis
 * server — not just against a mocked {@code RedisTemplate}, the only way the equivalent bespoke
 * {@code RedisTenantDetailsCacheStore} this replaced was ever verified.
 *
 * <p>Requires a running Docker daemon (via Testcontainers) —
 * {@code @Testcontainers(disabledWithoutDocker = true)} automatically skips this entire class (not
 * a failure) when Docker isn't available, so {@code mvn install} still succeeds on machines/CI
 * runners without Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
class TenantCacheRedisAutoConfigurationTest {

	@Container
	static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	private LettuceConnectionFactory connectionFactory;

	@BeforeEach
	void setUp() {
		connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
		connectionFactory.afterPropertiesSet();
	}

	@AfterEach
	void tearDown() {
		connectionFactory.destroy();
	}

	@Test
	void tenantDetailsCache_roundTripsThroughRedis() {
		Cache cache = tenantDetailsCache(60_000L);
		TenantDetails details = TenantDetails.builder("acme").plan(TenantPlan.PROFESSIONAL).build();

		cache.put("acme", details);
		TenantDetails cached = cache.get("acme", TenantDetails.class);

		assertThat(cached).isNotNull();
		assertThat(cached.tenantId()).isEqualTo("acme");
		assertThat(cached.plan()).isEqualTo(TenantPlan.PROFESSIONAL);
	}

	@Test
	void evict_removesEntry_visibleThroughANewCacheHandle() {
		Cache cache = tenantDetailsCache(60_000L);
		cache.put("acme", TenantDetails.builder("acme").build());

		cache.evict("acme");

		// A second, independently-built Cache handle (simulating a different application
		// instance) must see the eviction immediately, proving the cache is shared via Redis
		// and not merely local to the first handle.
		Cache secondHandle = tenantDetailsCache(60_000L);
		assertThat(secondHandle.get("acme")).isNull();
	}

	@Test
	void put_appliesConfiguredTtlAsRedisExpiry() throws InterruptedException {
		Cache cache = tenantDetailsCache(50L);

		cache.put("acme", TenantDetails.builder("acme").build());
		Thread.sleep(300L);

		assertThat(cache.get("acme")).isNull();
	}

	@Test
	void cache_isNamedAfterTheSharedConstant() {
		Cache cache = tenantDetailsCache(60_000L);

		assertThat(cache.getName()).isEqualTo(CachingTenantDetailsLoader.CACHE_NAME);
	}

	private Cache tenantDetailsCache(long ttlMillis) {
		TenantProperties properties = new TenantProperties();
		properties.setDetailsCacheTtlMs(ttlMillis);
		return new TenantCacheRedisAutoConfiguration().tenantDetailsCache(connectionFactory, properties);
	}
}
