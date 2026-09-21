package com.holonplatform.multitenant.cache.redis;

import com.holonplatform.multitenant.CachingTenantDetailsLoader;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.config.TenantProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
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
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
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
 * <p>Values are serialised with a type-specific {@link Jackson2JsonRedisSerializer} rather than
 * the generic one. The generic serializer embeds the runtime class of every value in the JSON,
 * and the value here is a JPA entity: its collections are Hibernate {@code PersistentSet}s while
 * it is managed, so the runtime type written to Redis is a Hibernate collection that cannot be
 * reconstructed on the way back out — the entry is written successfully and then fails on every
 * subsequent read. Pinning the serializer to {@link TenantDetails} drops the embedded types
 * altogether, which is both correct and smaller on the wire; this cache only ever holds one type,
 * so nothing is lost.
 *
 * <p>Without a {@code RedisConnectionFactory} on the classpath/context, this auto-configuration is
 * entirely inert — the per-JVM Caffeine cache remains the default.
 */
@AutoConfiguration
@org.springframework.boot.autoconfigure.AutoConfigureBefore(
		name = "com.holonplatform.multitenant.onboarding.config.TenantOnboardingAutoConfiguration")
// @ConditionalOnBean is evaluated in auto-configuration order, so without this the condition is
// assessed before Spring Boot has contributed the RedisConnectionFactory and always sees nothing
// — leaving the per-JVM cache in place in exactly the deployments that needed the shared one.
// Referenced by name, and under both the Boot 3 and Boot 4 package names, so that this module
// does not take a hard dependency on which Boot version supplies the connection factory.
@org.springframework.boot.autoconfigure.AutoConfigureAfter(name = {
		"org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
		"org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration" })
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
						.fromSerializer(new Jackson2JsonRedisSerializer<>(tenantDetailsMapper(),
								TenantDetails.class)));
		RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
				.cacheDefaults(configuration)
				.withCacheConfiguration(CachingTenantDetailsLoader.CACHE_NAME, configuration)
				.build();
		cacheManager.afterPropertiesSet();
		return cacheManager.getCache(CachingTenantDetailsLoader.CACHE_NAME);
	}

	/**
	 * The mapper used for cache values, deliberately tolerant of properties it cannot set.
	 *
	 * <p>{@link TenantDetails} exposes derived read-only getters such as {@code isActive()}.
	 * Jackson writes those out and then refuses to read them back, because there is no
	 * corresponding setter — so a strict mapper writes entries that can never be read again.
	 *
	 * <p>Tolerance is also what makes a rolling deployment survivable: during one, two versions
	 * of the application share this cache, and the newer one will be writing properties the older
	 * one has never heard of. Failing on those would turn every deployment into a cache-wide
	 * outage for the older instances.
	 */
	private static ObjectMapper tenantDetailsMapper() {
		return JsonMapper.builder()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.build();
	}
}
