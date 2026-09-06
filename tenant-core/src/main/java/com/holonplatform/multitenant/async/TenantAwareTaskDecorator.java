package com.holonplatform.multitenant.async;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import org.springframework.core.task.TaskDecorator;

/**
 * Spring {@link TaskDecorator} that captures the {@link TenantDetails} from the
 * submitting thread and reinstates it on the worker thread before executing the task.
 *
 * <p>Register this bean in any {@code Executor} or {@code ThreadPoolTaskExecutor} to
 * ensure that background tasks started from a request-scoped thread inherit the correct
 * tenant context.
 *
 * <h2>Why not InheritableThreadLocal?</h2>
 * <p>{@code InheritableThreadLocal} causes context to bleed between pooled threads when
 * a thread is reused for a different request. This decorator is the correct, safe
 * alternative: it captures the context at submission time and restores it precisely
 * for the duration of the task.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Bean
 * public ThreadPoolTaskExecutor applicationTaskExecutor(TenantAwareTaskDecorator decorator) {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(decorator);
 *     executor.setCorePoolSize(4);
 *     executor.initialize();
 *     return executor;
 * }
 * }</pre>
 *
 * <p>Also see {@link TenantContextRunnable} and {@link TenantContextCallable} for manual
 * wrapping without a full executor configuration.
 */
public class TenantAwareTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		// Capture the context from the submitting thread
		TenantDetails capturedTenant = TenantContext.get().orElse(null);

		return () -> {
			try {
				if (capturedTenant != null) {
					TenantContext.set(capturedTenant);
				}
				runnable.run();
			} finally {
				TenantContext.clear();
			}
		};
	}
}

