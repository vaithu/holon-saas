package com.holonplatform.multitenant.demo.event;

import org.springframework.context.ApplicationEvent;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.event.TenantScopedEvent;

/**
 * F21 — a sale, announced with the workspace it belongs to attached.
 *
 * <h2>Why the tenant travels on the event</h2>
 * <p>A listener is not guaranteed to run on the thread that published, and even when it does,
 * the publisher may have already unbound the tenant — a background job, a scheduled sweep, or
 * anything reached through an executor has no tenant of its own. Carrying {@link TenantDetails}
 * on the event means the listener never has to guess, and
 * {@code TenantScopedEventListenerAspect} binds it to the {@code TenantContext} for the duration
 * of the call. A listener can therefore write to the tenant's own schema without doing anything
 * tenant-aware itself.
 */
public class DemoSaleRecordedEvent extends ApplicationEvent implements TenantScopedEvent {

    private final transient TenantDetails tenant;
    private final String                  description;
    private final long                    amount;

    public DemoSaleRecordedEvent(Object source, TenantDetails tenant, String description, long amount) {
        super(source);
        this.tenant      = tenant;
        this.description = description;
        this.amount      = amount;
    }

    @Override
    public TenantDetails getTenant() {
        return tenant;
    }

    public String description() {
        return description;
    }

    public long amount() {
        return amount;
    }
}
