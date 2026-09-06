package com.holonplatform.multitenant.event;

import com.holonplatform.multitenant.TenantDetails;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Spring application event published by
 * {@link com.holonplatform.multitenant.filter.TenantContextFilter} after a
 * {@link TenantDetails} has been successfully resolved and bound to the request thread.
 *
 * <p>Listen for this event to implement per-tenant request tracking, audit logging,
 * or rate-limiting.
 *
 * <p>Example listener:
 * <pre>{@code
 * @Component
 * public class TenantAuditListener {
 *
 *     @EventListener
 *     public void onTenantResolved(TenantResolvedEvent event) {
 *         auditLog.record(event.getTenant().tenantId(), event.getRequestUri());
 *     }
 * }
 * }</pre>
 */
public class TenantResolvedEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	private final TenantDetails tenant;
	private final String        requestUri;

	/**
	 * Creates a new {@code TenantResolvedEvent}.
	 *
	 * @param source     the filter that emitted this event
	 * @param tenant     the fully resolved tenant; must not be null
	 * @param requestUri the request URI that triggered resolution; must not be null
	 */
	public TenantResolvedEvent(Object source, TenantDetails tenant, String requestUri) {
		super(source);
		this.tenant     = Objects.requireNonNull(tenant, "tenant must not be null");
		this.requestUri = Objects.requireNonNull(requestUri, "requestUri must not be null");
	}

	/** Returns the fully resolved {@link TenantDetails}. */
	public TenantDetails getTenant() {
		return tenant;
	}

	/** Returns the request URI that triggered tenant resolution. */
	public String getRequestUri() {
		return requestUri;
	}
}

