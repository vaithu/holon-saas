package com.holonplatform.multitenant;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runs a block of code with a given {@link TenantDetails} bound to {@link TenantContext} for the
 * current thread, guaranteeing the previous context (if any) is restored afterwards — even if the
 * block throws.
 *
 * <h2>Why this exists</h2>
 * <p>Binding {@link TenantContext} around a handful of calls is a recurring pattern in code that
 * runs outside the normal request path — signup orchestration, {@code @EventListener} handlers
 * reacting to onboarding events, scheduled jobs, admin tooling — anywhere {@code TenantContext}
 * hasn't already been bound by {@code TenantContextFilter}. The pattern is easy to get subtly
 * wrong by hand:
 * <pre>{@code
 * TenantContext.set(tenant);
 * try {
 *     doSomething();
 * } finally {
 *     TenantContext.clear(); // wrong if this call is nested inside an already-bound context!
 * }
 * }</pre>
 * The naive {@code finally { TenantContext.clear() }} above is a real bug in a nested-call
 * scenario: if this block runs on a thread that already has a <em>different</em> tenant bound
 * (e.g. an admin operation temporarily acting on another tenant's behalf), an unconditional
 * {@code clear()} wipes out that outer context instead of restoring it. This class captures
 * whatever was bound beforehand and restores exactly that, rather than always clearing.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TenantContextTemplate.run(tenant, () -> {
 *     tenantUserService.invite(tenantId, userId, email, TenantRole.OWNER);
 *     tenantUserService.activate(tenantId, userId);
 * });
 *
 * String result = TenantContextTemplate.call(tenant, () -> someService.compute());
 * }</pre>
 *
 * <p>For propagating tenant context to a <em>different</em> thread (e.g. an
 * {@code ExecutorService} task), use
 * {@link com.holonplatform.multitenant.async.TenantContextRunnable} /
 * {@link com.holonplatform.multitenant.async.TenantContextCallable} instead — this class is for
 * temporarily (re)binding the context on the <em>current</em> thread.
 */
public final class TenantContextTemplate {

	private TenantContextTemplate() {
	}

	/**
	 * Runs {@code action} with {@code tenant} bound to {@link TenantContext}, restoring whatever
	 * was previously bound (or clearing, if nothing was) once {@code action} completes or throws.
	 *
	 * @param tenant the tenant to bind for the duration of {@code action}; must not be null
	 * @param action the code to run; must not be null
	 */
	public static void run(TenantDetails tenant, Runnable action) {
		Objects.requireNonNull(tenant, "tenant must not be null");
		Objects.requireNonNull(action, "action must not be null");
		TenantDetails previous = TenantContext.get().orElse(null);
		TenantContext.set(tenant);
		try {
			action.run();
		} finally {
			restore(previous);
		}
	}

	/**
	 * Calls {@code action} with {@code tenant} bound to {@link TenantContext}, restoring whatever
	 * was previously bound (or clearing, if nothing was) once {@code action} completes or throws.
	 *
	 * @param tenant the tenant to bind for the duration of {@code action}; must not be null
	 * @param action the code to call; must not be null
	 * @param <T>    the return type
	 * @return the value returned by {@code action}
	 */
	public static <T> T call(TenantDetails tenant, Supplier<T> action) {
		Objects.requireNonNull(tenant, "tenant must not be null");
		Objects.requireNonNull(action, "action must not be null");
		TenantDetails previous = TenantContext.get().orElse(null);
		TenantContext.set(tenant);
		try {
			return action.get();
		} finally {
			restore(previous);
		}
	}

	private static void restore(TenantDetails previous) {
		bindRestore(previous);
	}

	// -------------------------------------------------------------------------
	// Low-level primitives
	// -------------------------------------------------------------------------

	/**
	 * Low-level primitive underlying {@link #run(TenantDetails, Runnable)} /
	 * {@link #call(TenantDetails, Supplier)}, for callers who cannot express their action as a
	 * plain {@link Runnable}/{@link Supplier} — e.g. Spring AOP advice wrapping
	 * {@code ProceedingJoinPoint.proceed()}, which throws {@code Throwable} rather than just
	 * unchecked exceptions (see {@code TenantScopedEventListenerAspect}). Binds {@code tenant}
	 * and returns whatever was previously bound, to be restored via
	 * {@link #restoreBound(TenantDetails)} in a {@code finally} block:
	 * <pre>{@code
	 * TenantDetails previous = TenantContextTemplate.bind(tenant);
	 * try {
	 *     return joinPoint.proceed();
	 * } finally {
	 *     TenantContextTemplate.restoreBound(previous);
	 * }
	 * }</pre>
	 * Prefer {@link #run(TenantDetails, Runnable)} / {@link #call(TenantDetails, Supplier)}
	 * wherever the action doesn't need to propagate a checked/{@code Throwable} exception —
	 * unlike this primitive, they cannot be misused (no risk of forgetting the {@code finally}).
	 *
	 * @param tenant the tenant to bind; must not be null
	 * @return whatever {@link TenantDetails} was bound before this call, or {@code null} if none
	 */
	public static TenantDetails bind(TenantDetails tenant) {
		Objects.requireNonNull(tenant, "tenant must not be null");
		TenantDetails previous = TenantContext.get().orElse(null);
		TenantContext.set(tenant);
		return previous;
	}

	/**
	 * Restores whatever {@link TenantDetails} {@link #bind(TenantDetails)} reported as
	 * previously bound (or clears the context, if {@code previous} is {@code null}).
	 *
	 * @param previous the value returned by the matching {@link #bind(TenantDetails)} call
	 */
	public static void restoreBound(TenantDetails previous) {
		bindRestore(previous);
	}

	private static void bindRestore(TenantDetails previous) {
		if (previous != null) {
			TenantContext.set(previous);
		} else {
			TenantContext.clear();
		}
	}
}
