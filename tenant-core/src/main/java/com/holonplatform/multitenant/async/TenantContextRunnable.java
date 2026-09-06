package com.holonplatform.multitenant.async;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;

import java.util.Objects;

/**
 * A {@link Runnable} wrapper that propagates the caller's {@link TenantDetails}
 * to the worker thread for the duration of the task, then clears it.
 *
 * <p>Use this class when you need to propagate tenant context to a background
 * thread and don't have access to a {@link TenantAwareTaskDecorator}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * executor.submit(TenantContextRunnable.of(myRunnable));
 * }</pre>
 *
 * <p>If no tenant is set on the calling thread the delegate is still executed —
 * the worker thread simply has no tenant context either.
 */
public final class TenantContextRunnable implements Runnable {

	private final Runnable      delegate;
	private final TenantDetails capturedTenant;

	private TenantContextRunnable(Runnable delegate, TenantDetails capturedTenant) {
		this.delegate       = Objects.requireNonNull(delegate, "delegate must not be null");
		this.capturedTenant = capturedTenant;
	}

	/**
	 * Creates a {@code TenantContextRunnable} that captures the tenant from the
	 * <em>calling</em> thread at this point in time.
	 *
	 * @param delegate the task to run; must not be null
	 */
	public static TenantContextRunnable of(Runnable delegate) {
		return new TenantContextRunnable(delegate, TenantContext.get().orElse(null));
	}

	/**
	 * Creates a {@code TenantContextRunnable} that uses the explicitly supplied tenant.
	 * Useful in tests or when constructing tasks in a context-free environment.
	 *
	 * @param delegate the task to run; must not be null
	 * @param tenant   the tenant to bind; may be null (no context will be set)
	 */
	public static TenantContextRunnable of(Runnable delegate, TenantDetails tenant) {
		return new TenantContextRunnable(delegate, tenant);
	}

	@Override
	public void run() {
		try {
			if (capturedTenant != null) {
				TenantContext.set(capturedTenant);
			}
			delegate.run();
		} finally {
			TenantContext.clear();
		}
	}
}

