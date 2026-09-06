package com.holonplatform.multitenant.async;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * A {@link Callable} wrapper that propagates the caller's {@link TenantDetails}
 * to the worker thread for the duration of the call, then clears it.
 *
 * <p>Use this class when submitting tasks to an {@link java.util.concurrent.ExecutorService}
 * that doesn't have a {@link TenantAwareTaskDecorator} configured.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Future<Report> future = executor.submit(TenantContextCallable.of(() -> reportService.generate()));
 * }</pre>
 *
 * @param <V> the return type of the wrapped callable
 */
public final class TenantContextCallable<V> implements Callable<V> {

	private final Callable<V>   delegate;
	private final TenantDetails capturedTenant;

	private TenantContextCallable(Callable<V> delegate, TenantDetails capturedTenant) {
		this.delegate       = Objects.requireNonNull(delegate, "delegate must not be null");
		this.capturedTenant = capturedTenant;
	}

	/**
	 * Creates a {@code TenantContextCallable} that captures the tenant from the
	 * <em>calling</em> thread at this point in time.
	 *
	 * @param delegate the task to run; must not be null
	 * @param <V>      the return type
	 */
	public static <V> TenantContextCallable<V> of(Callable<V> delegate) {
		return new TenantContextCallable<>(delegate, TenantContext.get().orElse(null));
	}

	/**
	 * Creates a {@code TenantContextCallable} with an explicitly supplied tenant.
	 *
	 * @param delegate the task to run; must not be null
	 * @param tenant   the tenant to bind; may be null
	 * @param <V>      the return type
	 */
	public static <V> TenantContextCallable<V> of(Callable<V> delegate, TenantDetails tenant) {
		return new TenantContextCallable<>(delegate, tenant);
	}

	@Override
	public V call() throws Exception {
		try {
			if (capturedTenant != null) {
				TenantContext.set(capturedTenant);
			}
			return delegate.call();
		} finally {
			TenantContext.clear();
		}
	}
}

