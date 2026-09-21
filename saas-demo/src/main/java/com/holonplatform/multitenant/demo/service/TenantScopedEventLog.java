package com.holonplatform.multitenant.demo.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.demo.event.DemoSaleRecordedEvent;

/**
 * F21 — proof that a listener runs with the event's tenant bound, whatever the publisher had.
 *
 * <h2>What the recorded rows show</h2>
 * <p>Each entry stores the tenant the event declared next to the tenant the listener actually
 * observed on {@link TenantContext} while it ran. When they match on a publication made from a
 * thread with nothing bound, {@code TenantScopedEventListenerAspect} is the only thing that can
 * have put it there.
 *
 * <p>The listener is intentionally as ordinary as possible — a plain {@code @EventListener} with
 * no tenant handling of its own — because that is the claim being demonstrated: a listener does
 * not have to know it is multi-tenant.
 */
@Component
public class TenantScopedEventLog {

    private static final int MAX_ENTRIES = 50;

    private final Deque<Observed> observed = new ArrayDeque<>();

    /**
     * @param at             when the listener ran
     * @param description    what the event carried
     * @param tenantOnEvent  the tenant declared by the event
     * @param tenantInContext the tenant bound while the listener ran
     */
    public record Observed(Instant at, String description, String tenantOnEvent,
                           String tenantInContext) {
        public boolean bound() {
            return tenantOnEvent.equals(tenantInContext);
        }
    }

    @EventListener
    public void onSaleRecorded(DemoSaleRecordedEvent event) {
        record(event.description() + " (" + event.amount() + ")",
                event.getTenant().tenantId(),
                TenantContext.currentTenantId().orElse("(none)"));
    }

    private void record(String description, String tenantOnEvent, String tenantInContext) {
        synchronized (observed) {
            observed.addFirst(new Observed(Instant.now(), description, tenantOnEvent, tenantInContext));
            while (observed.size() > MAX_ENTRIES) {
                observed.removeLast();
            }
        }
    }

    /**
     * The observations belonging to the workspace currently bound.
     *
     * <p>A single in-memory log holds every tenant's events, so it has to be filtered before it
     * is shown to one of them — a demo of tenant scoping that leaked across tenants in its own
     * reporting would be arguing against itself.
     */
    public List<Observed> recent() {
        String current = TenantContext.currentTenantId().orElse(null);
        synchronized (observed) {
            if (current == null) {
                return List.copyOf(observed);
            }
            return observed.stream()
                    .filter(entry -> current.equals(entry.tenantOnEvent()))
                    .toList();
        }
    }

    /**
     * Publishes the event from a thread that has <em>no</em> tenant bound, which is the only
     * version of this demonstration that proves anything. Publishing from a request thread that
     * already has the right tenant bound would look identical whether the aspect existed or not.
     */
    public static void publishFromAnUnboundThread(ApplicationEventPublisher publisher,
                                                  TenantDetails tenant,
                                                  String description, long amount) {
        Thread worker = new Thread(() -> publisher.publishEvent(
                new DemoSaleRecordedEvent(TenantScopedEventLog.class, tenant, description, amount)),
                "demo-unbound-publisher");
        worker.start();
        try {
            worker.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
