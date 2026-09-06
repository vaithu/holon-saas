package com.holonplatform.multitenant.onboarding.event;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.event.TenantScopedEvent;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Spring application event published by
 * {@link com.holonplatform.multitenant.onboarding.TenantOnboardingService}
 * after a tenant's schema has been provisioned, all Flyway migrations run, and the
 * tenant status set to {@code ACTIVE}.
 *
 * <h2>Typical listeners</h2>
 * <ul>
 *   <li>Seed initial data (default admin user, lookup tables, demo data)</li>
 *   <li>Send welcome email to the admin address</li>
 *   <li>Register the tenant in an external billing system</li>
 *   <li>Warm up the tenant's DataSource pool</li>
 * </ul>
 *
 * <pre>{@code
 * @Component
 * public class WelcomeEmailSender {
 *
 *     @EventListener
 *     @Async
 *     public void onOnboardingCompleted(TenantOnboardingCompletedEvent event) {
 *         mailer.sendWelcome(event.getTenant().attributes().get("adminEmail"));
 *     }
 * }
 * }</pre>
 *
 * <p>Implements {@link TenantScopedEvent} — any {@code @EventListener} receiving this event has
 * {@code TenantContext} bound to {@link #getTenant()} automatically for the duration of the
 * listener call, when {@code TenantScopedEventListenerAspect} is active (AspectJ on the
 * classpath). See {@link TenantScopedEvent}'s Javadoc for the important caveats — this is a
 * safety net, not a substitute for explicit {@code TenantContextTemplate} binding.
 */
public final class TenantOnboardingCompletedEvent extends ApplicationEvent implements TenantScopedEvent {

    private static final long serialVersionUID = 1L;

    private final TenantDetails tenant;

    public TenantOnboardingCompletedEvent(Object source, TenantDetails tenant) {
        super(source);
        this.tenant = Objects.requireNonNull(tenant, "tenant must not be null");
    }

    /** The fully provisioned and activated tenant. */
    @Override
    public TenantDetails getTenant() {
        return tenant;
    }

    /** Convenience shortcut — equivalent to {@code getTenant().tenantId()}. */
    public String getTenantId() {
        return tenant.tenantId();
    }
}

