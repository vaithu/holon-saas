package com.holonplatform.multitenant.data.datasource;

import com.holonplatform.jdbc.TenantDataSourceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link BoundedTenantDataSourceRegistry}.
 */
class BoundedTenantDataSourceRegistryTest {

	private static DataSource mockDs(String id) {
		// Use a simple non-closeable stub
		return new SingleConnectionDataSource("jdbc:h2:mem:" + id + ";DB_CLOSE_DELAY=-1", "sa", "", false);
	}

	// -------------------------------------------------------------------------
	// Positive cases
	// -------------------------------------------------------------------------

	@Test
	void getDataSource_createsOnFirstAccess() {
		AtomicInteger callCount = new AtomicInteger();
		TenantDataSourceProvider provider = id -> {
			callCount.incrementAndGet();
			return mockDs(id);
		};
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(provider, 10);

		DataSource ds1 = registry.getDataSource("acme");
		DataSource ds2 = registry.getDataSource("acme"); // second call → same instance

		assertThat(ds1).isSameAs(ds2);
		assertThat(callCount.get()).isEqualTo(1); // provider called once
	}

	@Test
	void getDataSource_differentTenants_returnsDifferentInstances() {
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(
				id -> mockDs(id), 10);

		DataSource acme  = registry.getDataSource("acme");
		DataSource beta  = registry.getDataSource("beta");

		assertThat(acme).isNotSameAs(beta);
	}

	@Test
	void size_tracksRegisteredDataSources() {
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> mockDs(id), 10);

		registry.getDataSource("a");
		registry.getDataSource("b");
		registry.getDataSource("c");

		assertThat(registry.size()).isEqualTo(3);
	}

	@Test
	void activeTenantIds_returnsSnapshot() {
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> mockDs(id), 10);

		registry.getDataSource("alpha");
		registry.getDataSource("beta");

		assertThat(registry.activeTenantIds()).containsExactlyInAnyOrder("alpha", "beta");
	}

	@Test
	void evict_removesFromCache() {
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> mockDs(id), 10);

		registry.getDataSource("acme");
		registry.evict("acme");

		assertThat(registry.size()).isZero();
	}

	@Test
	void evict_isNoopForNonExistentTenant() {
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> mockDs(id), 10);

		assertThatCode(() -> registry.evict("nonexistent")).doesNotThrowAnyException();
	}

	// -------------------------------------------------------------------------
	// Least-recently-used eviction
	//
	// The pool worth keeping is the one still receiving traffic, so eviction is
	// driven by last use rather than by creation order. Re-accessing a tenant
	// protects it; going untouched the longest is what gets a pool evicted.
	// -------------------------------------------------------------------------

	@Test
	void lruEviction_evictsLeastRecentlyUsed_whenCapacityExceeded() {
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> mockDs(id), 3);

		registry.getDataSource("a");
		registry.getDataSource("b");
		registry.getDataSource("c");
		// Nothing has been re-accessed, so "a" is both the oldest created and the least
		// recently used — adding "d" evicts it.
		registry.getDataSource("d");

		assertThat(registry.size()).isEqualTo(3);
		assertThat(registry.activeTenantIds()).containsExactlyInAnyOrder("b", "c", "d");
	}

	@Test
	void lruEviction_reAccessProtectsAPoolFromEviction() {
		// The case that matters in production: the earliest-created tenant is usually the
		// busiest. Under creation-order eviction it would be evicted and immediately
		// re-created on the next request, thrashing its pool precisely at capacity.
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> mockDs(id), 3);

		registry.getDataSource("a"); // created first...
		registry.getDataSource("b");
		registry.getDataSource("c");
		registry.getDataSource("a"); // ...but still in active use

		registry.getDataSource("d");

		// "b" has now gone longest without use, so it is the victim — not "a".
		assertThat(registry.activeTenantIds()).containsExactlyInAnyOrder("a", "c", "d");
	}

	@Test
	void lruEviction_survivesRepeatedPressureOnAHotTenant() {
		AtomicInteger creations = new AtomicInteger();
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> {
			creations.incrementAndGet();
			return mockDs(id);
		}, 3);

		registry.getDataSource("hot");
		// A stream of one-shot tenants keeps the cache permanently at capacity.
		for (int i = 0; i < 20; i++) {
			registry.getDataSource("hot");        // keep it the most recently used
			registry.getDataSource("cold-" + i);
		}

		// The hot tenant's pool must have been created exactly once, never evicted.
		assertThat(registry.activeTenantIds()).contains("hot");
		assertThat(creations.get()).isEqualTo(21); // "hot" + 20 cold tenants
	}

	// -------------------------------------------------------------------------
	// Concurrency
	// -------------------------------------------------------------------------

	@Test
	void concurrentAccess_samesTenant_createsProviderOnlyOnce() throws InterruptedException {
		AtomicInteger creationCount = new AtomicInteger();
		TenantDataSourceProvider provider = id -> {
			creationCount.incrementAndGet();
			return mockDs(id + creationCount.get());
		};
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(provider, 10);

		int threads = 20;
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done  = new CountDownLatch(threads);
		ExecutorService exec = Executors.newFixedThreadPool(threads);

		for (int i = 0; i < threads; i++) {
			exec.submit(() -> {
				try {
					start.await();
					registry.getDataSource("shared-tenant");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
		}

		start.countDown();
		assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
		exec.shutdown();

		// Provider must be called exactly once for the same tenant
		assertThat(creationCount.get()).isEqualTo(1);
	}

	// -------------------------------------------------------------------------
	// Negative cases
	// -------------------------------------------------------------------------

	@Test
	void constructor_throwsOnNullProvider() {
		assertThatThrownBy(() -> new BoundedTenantDataSourceRegistry(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void constructor_throwsOnZeroMaxSize() {
		assertThatThrownBy(() -> new BoundedTenantDataSourceRegistry(id -> mockDs(id), 0))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void getDataSource_nullTenantId_returnsBootstrapConnection() {
		AtomicInteger callCount = new AtomicInteger();
		TenantDataSourceProvider provider = id -> {
			callCount.incrementAndGet();
			return mockDs(id == null ? "bootstrap" : id);
		};
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(provider);

		DataSource boot1 = registry.getDataSource(null);
		DataSource boot2 = registry.getDataSource(null); // second call → same cached instance
		DataSource tenant = registry.getDataSource("acme");

		assertThat(boot1).isSameAs(boot2);
		assertThat(boot1).isNotSameAs(tenant);
		assertThat(callCount.get()).isEqualTo(2); // once for bootstrap, once for "acme"
		// The bootstrap connection is never surfaced as an active tenant id
		assertThat(registry.activeTenantIds()).containsExactly("acme");
	}
}

