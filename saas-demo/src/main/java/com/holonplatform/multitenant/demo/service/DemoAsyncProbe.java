package com.holonplatform.multitenant.demo.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.async.TenantAwareTaskDecorator;

import jakarta.annotation.PreDestroy;

/**
 * F20 — what a worker thread knows about the tenant, with and without the decorator.
 *
 * <h2>The defect this exists to make visible</h2>
 * <p>{@link TenantContext} is backed by a plain {@link ThreadLocal}. That is the right choice —
 * an {@code InheritableThreadLocal} would leak a tenant into every thread a request happens to
 * spawn, and would be actively wrong for virtual threads, which are created per task. The
 * consequence is that handing work to an executor loses the tenant silently: the worker sees no
 * tenant, the query runs against whatever schema is default, and nothing throws.
 *
 * <p>{@link TenantAwareTaskDecorator} captures the tenant at submit time and rebinds it on the
 * worker, clearing it afterwards so the pooled thread does not carry it into the next task.
 *
 * <h2>The framework does not attach it for you</h2>
 * <p>{@code TenantCoreAutoConfiguration} publishes a {@code TenantAwareTaskDecorator} bean but
 * wires it onto nothing. Spring has no mechanism for a library to retrofit a {@link TaskDecorator}
 * onto executors the application defines, so the application must do it — which is exactly the
 * step that gets forgotten. Both executors below are otherwise identical.
 */
@Service
public class DemoAsyncProbe {

    private final ThreadPoolTaskExecutor decorated   = executor("demo-decorated-",
            new TenantAwareTaskDecorator());
    private final ThreadPoolTaskExecutor undecorated = executor("demo-plain-", null);

    /** What a worker thread reports seeing when the work is submitted through each executor. */
    public record Observation(String submittedBy, String seenByDecoratedWorker,
                              String seenByUndecoratedWorker) {
    }

    /**
     * Submits the same trivial task — "tell me the current tenant" — to both executors and
     * collects what each worker saw.
     */
    public Observation observe() {
        String here = TenantContext.currentTenantId().orElse("(none)");
        return new Observation(here, tenantSeenBy(decorated), tenantSeenBy(undecorated));
    }

    private static String tenantSeenBy(ThreadPoolTaskExecutor executor) {
        try {
            return executor.submit(() -> TenantContext.currentTenantId().orElse("(none)"))
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "(interrupted)";
        } catch (ExecutionException | TimeoutException e) {
            return "(failed: " + e.getMessage() + ")";
        }
    }

    private static ThreadPoolTaskExecutor executor(String prefix, TaskDecorator decorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix(prefix);
        if (decorator != null) {
            executor.setTaskDecorator(decorator);
        }
        // A pool of exactly one thread, reused between calls, so the "cleared afterwards" half
        // of the decorator's contract is exercised too: a leak would show up as the previous
        // call's tenant appearing in the undecorated result.
        executor.initialize();
        return executor;
    }

    /**
     * A virtual thread sees no tenant either, and cannot be fixed by inheritance — which is
     * worth stating separately, because the usual intuition is that a thread started inline
     * inherits its creator's context.
     */
    public String tenantSeenByAVirtualThread() {
        try {
            return Executors.newVirtualThreadPerTaskExecutor()
                    .submit(() -> TenantContext.currentTenantId().orElse("(none)"))
                    .get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "(interrupted)";
        } catch (ExecutionException | TimeoutException e) {
            return "(failed)";
        }
    }

    @PreDestroy
    void shutdown() {
        decorated.shutdown();
        undecorated.shutdown();
    }
}
