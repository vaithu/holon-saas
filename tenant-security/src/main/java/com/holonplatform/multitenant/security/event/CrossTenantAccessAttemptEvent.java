package com.holonplatform.multitenant.security.event;

import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Spring application event published by
 * {@link com.holonplatform.multitenant.security.filter.TenantIsolationFilter}
 * when an authenticated user attempts to access a tenant that differs from their
 * own authenticated tenant — a potential account-takeover or misconfiguration.
 *
 * <p>Listen for this event to implement security alerting or audit logging:
 * <pre>{@code
 * @Component
 * public class SecurityAuditListener {
 *
 *     @EventListener
 *     public void onCrossTenantAttempt(CrossTenantAccessAttemptEvent event) {
 *         alertService.recordSecurityIncident(event);
 *     }
 * }
 * }</pre>
 */
public class CrossTenantAccessAttemptEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	private final String userTenantId;
	private final String requestTenantId;
	private final String requestUri;
	private final String username;

	public CrossTenantAccessAttemptEvent(Object source,
			String userTenantId,
			String requestTenantId,
			String requestUri,
			String username) {
		super(source);
		this.userTenantId    = Objects.requireNonNull(userTenantId);
		this.requestTenantId = Objects.requireNonNull(requestTenantId);
		this.requestUri      = Objects.requireNonNull(requestUri);
		this.username        = username;
	}

	/** The tenant the user is authenticated for. */
	public String getUserTenantId()    { return userTenantId; }

	/** The tenant resolved for the request that was blocked. */
	public String getRequestTenantId() { return requestTenantId; }

	/** The URI of the blocked request. */
	public String getRequestUri()      { return requestUri; }

	/** The username of the user who made the attempt (may be null for anonymous). */
	public String getUsername()        { return username; }
}

