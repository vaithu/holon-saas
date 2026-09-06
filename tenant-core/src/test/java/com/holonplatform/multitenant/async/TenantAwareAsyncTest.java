package com.holonplatform.multitenant.async;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantAwareTaskDecorator}, {@link TenantContextRunnable},
 * and {@link TenantContextCallable}.
 */
class TenantAwareAsyncTest {

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	// -------------------------------------------------------------------------
	// TenantAwareTaskDecorator
	// -------------------------------------------------------------------------

	@Test
	void taskDecorator_propagatesTenantToWorkerThread() throws Exception {
		TenantDetails tenant = TenantDetails.builder("acme").build();
		TenantContext.set(tenant);

		TenantAwareTaskDecorator decorator = new TenantAwareTaskDecorator();
		AtomicReference<String> capturedId = new AtomicReference<>();

		Runnable decorated = decorator.decorate(() ->
				capturedId.set(TenantContext.currentTenantId().orElse(null)));

		Thread worker = new Thread(decorated);
		worker.start();
		worker.join();

		assertThat(capturedId.get()).isEqualTo("acme");
	}

	@Test
	void taskDecorator_clearsContextAfterTask() throws Exception {
		TenantContext.set(TenantDetails.builder("acme").build());
		TenantAwareTaskDecorator decorator = new TenantAwareTaskDecorator();

		Runnable decorated = decorator.decorate(() -> {
			// no-op
		});

		// Run in a thread that we can inspect after
		AtomicReference<Boolean> contextAfterRun = new AtomicReference<>();
		Thread worker = new Thread(() -> {
			decorated.run();
			contextAfterRun.set(TenantContext.isSet());
		});
		worker.start();
		worker.join();

		assertThat(contextAfterRun.get()).isFalse();
	}

	@Test
	void taskDecorator_worksWithNoTenantInCallingThread() throws Exception {
		// No tenant set on calling thread
		TenantAwareTaskDecorator decorator = new TenantAwareTaskDecorator();
		AtomicReference<Boolean> wasSet = new AtomicReference<>();

		Runnable decorated = decorator.decorate(() -> wasSet.set(TenantContext.isSet()));
		Thread worker = new Thread(decorated);
		worker.start();
		worker.join();

		assertThat(wasSet.get()).isFalse();
	}

	// -------------------------------------------------------------------------
	// TenantContextRunnable
	// -------------------------------------------------------------------------

	@Test
	void tenantContextRunnable_propagatesTenant() throws Exception {
		TenantDetails tenant = TenantDetails.builder("runnable-tenant").build();
		TenantContext.set(tenant);

		AtomicReference<String> captured = new AtomicReference<>();
		TenantContextRunnable wrapped = TenantContextRunnable.of(
				() -> captured.set(TenantContext.currentTenantId().orElse(null)));

		Thread worker = new Thread(wrapped);
		worker.start();
		worker.join();

		assertThat(captured.get()).isEqualTo("runnable-tenant");
	}

	@Test
	void tenantContextRunnable_explicitTenant() throws Exception {
		TenantDetails tenant = TenantDetails.builder("explicit").build();
		AtomicReference<String> captured = new AtomicReference<>();

		TenantContextRunnable wrapped = TenantContextRunnable.of(
				() -> captured.set(TenantContext.currentTenantId().orElse(null)),
				tenant);

		Thread worker = new Thread(wrapped);
		worker.start();
		worker.join();

		assertThat(captured.get()).isEqualTo("explicit");
	}

	// -------------------------------------------------------------------------
	// TenantContextCallable
	// -------------------------------------------------------------------------

	@Test
	void tenantContextCallable_propagatesTenantAndReturnsResult() throws Exception {
		TenantDetails tenant = TenantDetails.builder("callable-tenant").build();
		TenantContext.set(tenant);

		TenantContextCallable<String> callable = TenantContextCallable.of(
				() -> TenantContext.currentTenantId().orElse("none"));

		String result = CompletableFuture.supplyAsync(() -> {
			try {
				return callable.call();
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).get();

		assertThat(result).isEqualTo("callable-tenant");
	}

	@Test
	void tenantContextCallable_clearsContextAfterCall() throws Exception {
		TenantContext.set(TenantDetails.builder("acme").build());
		TenantContextCallable<Void> callable = TenantContextCallable.of(() -> null);

		AtomicReference<Boolean> contextAfterCall = new AtomicReference<>();
		Thread worker = new Thread(() -> {
			try {
				callable.call();
				contextAfterCall.set(TenantContext.isSet());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		worker.start();
		worker.join();

		assertThat(contextAfterCall.get()).isFalse();
	}
}

