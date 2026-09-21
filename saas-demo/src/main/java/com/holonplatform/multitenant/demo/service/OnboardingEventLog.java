package com.holonplatform.multitenant.demo.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.holonplatform.multitenant.onboarding.event.TenantOnboardingCompletedEvent;
import com.holonplatform.multitenant.onboarding.event.TenantOnboardingFailedEvent;

/**
 * F26 — a record of what onboarding announced, kept so it can be looked at.
 *
 * <h2>Why onboarding publishes events at all</h2>
 * <p>Provisioning a tenant is the moment other parts of a system want to act: seed reference
 * data, open a billing account, tell an ops channel, start a welcome sequence. None of those
 * belong inside the onboarding transaction, and none of them should be able to fail a signup.
 * Publishing {@code TenantOnboardingCompletedEvent} and {@code TenantOnboardingFailedEvent}
 * lets them happen without onboarding knowing they exist.
 *
 * <h2>The part that is easy to miss</h2>
 * <p>Onboarding publishes through a {@code publishSafely} wrapper that catches everything a
 * listener throws and logs it at {@code DEBUG}. That is the right call for the publisher — a
 * broken welcome email must not roll back a paid signup — but it means a listener doing real
 * work fails invisibly by default. Anything that must not be lost needs its own error handling
 * and its own retry; the event alone is not a delivery guarantee.
 *
 * <p>This log is platform-wide, not tenant-scoped: onboarding events are published before any
 * tenant is bound, and describe workspaces rather than belonging to one. It is only ever shown
 * in the operator console.
 */
@Component
public class OnboardingEventLog {

    /** What happened to one onboarding attempt. */
    public record Observed(Instant at, String tenantId, String outcome, String detail) {
    }

    private static final int MAX_ENTRIES = 50;

    private final Deque<Observed> observed = new ArrayDeque<>();

    @EventListener
    public synchronized void onCompleted(TenantOnboardingCompletedEvent event) {
        record(new Observed(Instant.now(), event.getTenant().tenantId(), "COMPLETED",
                "status " + event.getTenant().status()));
    }

    @EventListener
    public synchronized void onFailed(TenantOnboardingFailedEvent event) {
        Throwable cause = rootCause(event.getCause());
        record(new Observed(Instant.now(), event.getTenantId(), "FAILED",
                cause == null ? "(no cause reported)"
                              : cause.getClass().getSimpleName() + ": " + summarise(cause.getMessage())));
    }

    public synchronized List<Observed> recent() {
        return List.copyOf(observed);
    }

    private void record(Observed entry) {
        observed.addFirst(entry);
        while (observed.size() > MAX_ENTRIES) {
            observed.removeLast();
        }
    }

    /**
     * Onboarding wraps failures several layers deep — a Flyway error arrives inside a
     * {@code TenantMigrationException} inside the cause chain — and the outermost message says
     * only that onboarding failed. The innermost one is the only one that says why.
     */
    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String summarise(String message) {
        if (message == null) {
            return "(no message)";
        }
        String firstLine = message.lines().findFirst().orElse(message).trim();
        return firstLine.length() > 160 ? firstLine.substring(0, 160) + "…" : firstLine;
    }
}
