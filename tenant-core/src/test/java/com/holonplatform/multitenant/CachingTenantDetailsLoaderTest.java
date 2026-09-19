package com.holonplatform.multitenant;

import com.holonplatform.multitenant.exception.TenantNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CachingTenantDetailsLoader}.
 */
class CachingTenantDetailsLoaderTest {

	@Test
	void load_cachesResultWithinTtl() {
		AtomicInteger loads = new AtomicInteger();
		TenantDetailsLoader delegate = tenantId -> {
			loads.incrementAndGet();
			return TenantDetails.builder(tenantId).build();
		};
		CachingTenantDetailsLoader cache = new CachingTenantDetailsLoader(delegate, 60_000L);

		cache.load("acme");
		cache.load("acme");
		cache.load("acme");

		assertThat(loads.get()).isEqualTo(1);
	}

	@Test
	void load_reloadsAfterTtlExpires() throws InterruptedException {
		AtomicInteger loads = new AtomicInteger();
		TenantDetailsLoader delegate = tenantId -> {
			loads.incrementAndGet();
			return TenantDetails.builder(tenantId).build();
		};
		CachingTenantDetailsLoader cache = new CachingTenantDetailsLoader(delegate, 5L);

		cache.load("acme");
		Thread.sleep(20L);
		cache.load("acme");

		assertThat(loads.get()).isEqualTo(2);
	}

	@Test
	void invalidate_forcesReloadOnNextCall() {
		AtomicInteger loads = new AtomicInteger();
		TenantDetailsLoader delegate = tenantId -> {
			loads.incrementAndGet();
			return TenantDetails.builder(tenantId).build();
		};
		CachingTenantDetailsLoader cache = new CachingTenantDetailsLoader(delegate, 60_000L);

		cache.load("acme");
		cache.invalidate("acme");
		cache.load("acme");

		assertThat(loads.get()).isEqualTo(2);
	}

	@Test
	void invalidateAll_clearsEveryEntry() {
		AtomicInteger loads = new AtomicInteger();
		TenantDetailsLoader delegate = tenantId -> {
			loads.incrementAndGet();
			return TenantDetails.builder(tenantId).build();
		};
		CachingTenantDetailsLoader cache = new CachingTenantDetailsLoader(delegate, 60_000L);

		cache.load("acme");
		cache.load("umbrella");
		cache.invalidateAll();
		cache.load("acme");
		cache.load("umbrella");

		assertThat(loads.get()).isEqualTo(4);
	}

	@Test
	void load_doesNotCacheFailures() {
		AtomicInteger loads = new AtomicInteger();
		TenantDetailsLoader delegate = tenantId -> {
			loads.incrementAndGet();
			throw new TenantNotFoundException(tenantId);
		};
		CachingTenantDetailsLoader cache = new CachingTenantDetailsLoader(delegate, 60_000L);

		assertThatThrownBy(() -> cache.load("missing")).isInstanceOf(TenantNotFoundException.class);
		assertThatThrownBy(() -> cache.load("missing")).isInstanceOf(TenantNotFoundException.class);

		assertThat(loads.get()).isEqualTo(2);
	}

	@Test
	void constructor_rejectsNonPositiveTtl() {
		TenantDetailsLoader delegate = tenantId -> TenantDetails.builder(tenantId).build();
		assertThatThrownBy(() -> new CachingTenantDetailsLoader(delegate, 0L))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
