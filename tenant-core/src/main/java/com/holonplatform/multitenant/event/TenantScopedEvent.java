package com.holonplatform.multitenant.event;

import com.holonplatform.multitenant.TenantDetails;

/**
 * Marker interface for Spring application events that carry a specific {@link TenantDetails}
 * and are dispatched <em>synchronously</em> to {@code @EventListener} methods without
 * {@link com.holonplatform.multitenant.TenantContext} necessarily being bound on the publishing
 * thread (Spring's {@code ApplicationEventPublisher} is synchronous by default, so listeners run
 * inline with whatever triggered the event — often outside a normal authenticated HTTP request,
 * e.g. mid-way through tenant onboarding, before any request-scoped {@code TenantContext}
 * binding could apply).
 *
 * <h2>What implementing this interface gets you</h2>
 * <p>{@code TenantScopedEventListenerAspect} (registered automatically by
 * {@code TenantCoreAutoConfiguration} when AspectJ is on the classpath) wraps every
 * {@code @EventListener} method invocation receiving an event of this type in
 * {@code TenantContext.set(event.getTenant())} / restore, so listener code can safely call any
 * tenant-scoped service without binding the context itself.
 *
 * <p>This is a safety net, not a replacement for explicit binding — it only activates when
 * Spring AOP auto-proxying is active (typically via {@code spring-boot-starter-aop}) and the
 * listener method's <em>first</em> parameter is the event itself (the standard
 * {@code @EventListener} shape). Listener code (and any {@code SignupService}-equivalent
 * orchestration code that is <em>not</em> a Spring event listener at all) should still bind
 * {@link com.holonplatform.multitenant.TenantContextTemplate} explicitly rather than relying on
 * this aspect alone — see the "TenantContext & transaction boundary rules" section of
 * {@code new-saas-app-checklist.md}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public final class MyTenantEvent extends ApplicationEvent implements TenantScopedEvent {
 *     private final TenantDetails tenant;
 *     public MyTenantEvent(Object source, TenantDetails tenant) {
 *         super(source);
 *         this.tenant = tenant;
 *     }
 *     @Override
 *     public TenantDetails getTenant() {
 *         return tenant;
 *     }
 * }
 * }</pre>
 */
public interface TenantScopedEvent {

	/**
	 * The tenant this event is scoped to — the tenant that must be bound to
	 * {@link com.holonplatform.multitenant.TenantContext} while listeners handle this event.
	 *
	 * @return the tenant; must not be null
	 */
	TenantDetails getTenant();
}
