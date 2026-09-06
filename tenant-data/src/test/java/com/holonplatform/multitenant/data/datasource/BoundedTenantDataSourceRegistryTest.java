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
	// Insertion-order eviction
	//
	// The registry uses insertion-order (not LRU) eviction to keep the hot
	// read path completely lock-free. Re-accessing an existing tenant does NOT
	// move it to the "recently used" position — the oldest-inserted pool is
	// always evicted first.
	// -------------------------------------------------------------------------

	@Test
	void insertionOrderEviction_evictsOldestInserted_whenCapacityExceeded() {
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> mockDs(id), 3);

		registry.getDataSource("a");
		registry.getDataSource("b");
		registry.getDataSource("c");
		// Adding "d" evicts "a" — the first-inserted tenant
		registry.getDataSource("d");

		assertThat(registry.size()).isEqualTo(3);
		assertThat(registry.activeTenantIds()).doesNotContain("a");
		assertThat(registry.activeTenantIds()).containsExactlyInAnyOrder("b", "c", "d");
	}

	@Test
	void insertionOrderEviction_reAccessDoesNotChangeEvictionOrder() {
		// Design note: the hot-path (ConcurrentHashMap.get) is entirely lock-free.
		// Tracking access order would require acquiring the createLock on every
		// cache hit — defeating the entire purpose of the two-tier design.
		// Insertion-order is the deliberate tradeoff.
		BoundedTenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(id -> mockDs(id), 3);

		registry.getDataSource("a"); // inserted first
		registry.getDataSource("b");
		registry.getDataSource("c");
		registry.getDataSource("a"); // re-access → does NOT promote "a"
		// "a" was inserted first, so it is still evicted next
		registry.getDataSource("d");

		assertThat(registry.activeTenantIds()).doesNotContain("a"); // evicted as oldest insertion
		assertThat(registry.activeTenantIds()).containsExactlyInAnyOrder("b", "c", "d");
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

