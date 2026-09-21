package com.holonplatform.multitenant.demo.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.security.event.CrossTenantAccessAttemptEvent;

/**
 * F19 — a record of every cross-tenant access the isolation filter refused.
 *
 * <h2>Why the event matters as much as the 403</h2>
 * <p>{@code TenantIsolationFilter} already stops the request, so nothing here is load-bearing
 * for security. What the event adds is the ability to <em>notice</em>: a member repeatedly
 * reaching for another workspace's data is a signal worth surfacing, and it is invisible if the
 * only response is a status code the attacker sees and the operator does not. A plain
 * {@code @EventListener} is all it takes to subscribe.
 *
 * <p>The attempts are kept in memory and capped, because this is a demo — a real deployment
 * would forward them to whatever already receives its security telemetry.
 */
@Component
public class CrossTenantAttemptLog {

    private static final int MAX_ENTRIES = 50;

    private final Deque<Attempt> attempts = new ArrayDeque<>();

    /**
     * One refused attempt.
     *
     * @param at            when the filter refused it
     * @param username      the authenticated user who tried
     * @param userTenant    the workspace that user actually belongs to
     * @param requestTenant the workspace the request was aimed at
     * @param uri           the request that was refused
     */
    public record Attempt(Instant at, String username, String userTenant,
                          String requestTenant, String uri) {
    }

    @EventListener
    public void onCrossTenantAccessAttempt(CrossTenantAccessAttemptEvent event) {
        synchronized (attempts) {
            attempts.addFirst(new Attempt(Instant.now(), event.getUsername(),
                    event.getUserTenantId(), event.getRequestTenantId(), event.getRequestUri()));
            while (attempts.size() > MAX_ENTRIES) {
                attempts.removeLast();
            }
        }
    }

    /**
     * The attempts that concern the workspace currently bound — either made by one of its
     * members, or aimed at it.
     *
     * <p>Filtering here matters more than it looks: an unfiltered in-memory log surfaced in a
     * tenant-facing view would itself leak one workspace's activity to another, which is
     * precisely the failure the feature exists to prevent. A platform operator's console would
     * legitimately show all of them.
     */
    public List<Attempt> recent() {
        String current = TenantContext.currentTenantId().orElse(null);
        synchronized (attempts) {
            if (current == null) {
                return List.copyOf(attempts);
            }
            return attempts.stream()
                    .filter(a -> current.equals(a.userTenant()) || current.equals(a.requestTenant()))
                    .toList();
        }
    }
}
