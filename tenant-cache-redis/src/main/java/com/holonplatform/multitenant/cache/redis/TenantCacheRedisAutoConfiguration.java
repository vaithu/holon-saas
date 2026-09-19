package com.holonplatform.multitenant.cache.redis;

import com.holonplatform.multitenant.CachingTenantDetailsLoader;
import com.holonplatform.multitenant.config.TenantProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Spring Boot auto-configuration for a Redis-backed {@code tenantDetailsCache} {@link Cache}
 * bean, shared across every application instance.
 *
 * <p>Built on Spring Data Redis's {@link RedisCacheManager} — the standard {@link Cache}
 * abstraction backend for Redis — instead of a bespoke {@code TenantDetailsCacheStore}
 * implementation. TTL is applied natively via Redis {@code EXPIRE} (through
 * {@link RedisCacheConfiguration#entryTtl(Duration)}), so expired entries are reclaimed by Redis
 * itself without any application-side sweeping, and {@link Cache#evict(Object)} called on any
 * instance is visible to every other instance immediately (it is the same physical cache entry).
 *
 * <p>Activates automatically whenever a {@code RedisConnectionFactory} bean is present (e.g. via
 * {@code spring-boot-starter-data-redis} + {@code spring.data.redis.*} properties) and no
 * application-supplied {@link Cache} bean named {@code tenantDetailsCache} already exists. Runs
 * before {@code TenantOnboardingAutoConfiguration}, so its default {@code TenantDetailsLoader}
 * bean picks up this cache instead of falling back to the per-JVM Caffeine one.
 *
 * <p>Without a {@code RedisConnectionFactory} on the classpath/context, this auto-configuration is
 * entirely inert — the per-JVM Caffeine cache remains the default.
 */
@AutoConfiguration
@org.springframework.boot.autoconfigure.AutoConfigureBefore(
		name = "com.holonplatform.multitenant.onboarding.config.TenantOnboardingAutoConfiguration")
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableConfigurationProperties(TenantProperties.class)
public class TenantCacheRedisAutoConfiguration {

	@Bean("tenantDetailsCache")
	@ConditionalOnBean(RedisConnectionFactory.class)
	@ConditionalOnMissingBean(name = "tenantDetailsCache")
	public Cache tenantDetailsCache(RedisConnectionFactory connectionFactory, TenantProperties properties) {
		RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
				.entryTtl(Duration.ofMillis(properties.getDetailsCacheTtlMs()))
				.serializeValuesWith(RedisSerializationContext.SerializationPair
						.fromSerializer(new GenericJackson2JsonRedisSerializer()));
		RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(configuration)
				.withCacheConfiguration(CachingTenantDetailsLoader.CACHE_NAME, configuration)
				.build();
		cacheManager.afterPropertiesSet();
		return cacheManager.getCache(CachingTenantDetailsLoader.CACHE_NAME);
	}
}
