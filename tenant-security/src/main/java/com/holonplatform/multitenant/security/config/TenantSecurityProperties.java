package com.holonplatform.multitenant.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the multi-tenant security layer.
 * All properties are prefixed with {@code holon.tenant.security}.
 *
 * <pre>{@code
 * holon:
 *   tenant:
 *     security:
 *       isolation-enforced: true      # 403 on cross-tenant attempt (true = enforce, false = warn only)
 *       isolation-filter-order: -50   # filter ordering (after tenant-core's -100)
 *       aop-enabled: true             # register TenantFeatureAspect
 *       principal-fallback-enabled: true   # bind TenantContext from the authenticated user
 *       principal-fallback-filter-order: -60  # after security (-100), before isolation (-50)
 *       auth-context-registration-enabled: true  # publish a global Holon AuthContext (Spring Security-backed)
 * }</pre>
 */
@ConfigurationProperties(prefix = "holon.tenant.security")
public class TenantSecurityProperties {

	/** Whether cross-tenant access causes HTTP 403 (true) or just a warning log (false). Default: true. */
	private boolean isolationEnforced = true;

	/** Servlet filter order for the isolation filter. Default: -50 (after tenant-core's -100). */
	private int isolationFilterOrder = -50;

	/** Whether to auto-register the {@link com.holonplatform.multitenant.security.aop.TenantFeatureAspect}. Default: true. */
	private boolean aopEnabled = true;

	/**
	 * Whether to register the
	 * {@link com.holonplatform.multitenant.security.filter.AuthenticatedPrincipalTenantContextFilter},
	 * which binds {@link com.holonplatform.multitenant.TenantContext} from the authenticated
	 * principal for requests that could not resolve a tenant from the request itself (most
	 * notably a single-page UI's internal round-trips, such as Vaadin Flow's UIDL/heartbeat
	 * POSTs to the servlet root).
	 *
	 * <p>The binding is additive and safe: it only fires when no tenant is already bound and a
	 * user is authenticated, and {@link com.holonplatform.multitenant.security.filter.TenantIsolationFilter}
	 * still validates the result. Default: {@code true}.
	 */
	private boolean principalFallbackEnabled = true;

	/**
	 * Servlet filter order for the principal-based tenant binding filter. Must be greater than
	 * Spring Security's filter chain order ({@code -100}) so the {@code SecurityContextHolder} is
	 * populated, and less than {@link #isolationFilterOrder} so the isolation filter validates the
	 * binding. Default: {@code -60}.
	 */
	private int principalFallbackFilterOrder = -60;

	/**
	 * Whether to publish a global Holon {@link com.holonplatform.auth.AuthContext}, backed by Spring
	 * Security's {@code SecurityContextHolder}, into the Holon {@code Context} classloader scope so
	 * that {@link com.holonplatform.auth.AuthContext#getCurrent()} / {@code require()} resolve on every
	 * thread that carries an authenticated Spring Security context.
	 *
	 * <p>This lets Holon-aware UIs whose route/authorization guards call {@code AuthContext.require()}
	 * (e.g. the Holon Vaadin Flow navigator) work with a Spring Security-managed app without any
	 * hand-written binding code.
	 *
	 * <p>Registration is non-destructive: it uses {@code putIfAbsent}, so an application that already
	 * publishes its own {@code AuthContext} (in this or any higher-priority scope, e.g. a Vaadin
	 * session scope) keeps precedence. Default: {@code true}.
	 */
	private boolean authContextRegistrationEnabled = true;

	public boolean isIsolationEnforced()               { return isolationEnforced; }
	public void setIsolationEnforced(boolean v)        { this.isolationEnforced = v; }

	public int getIsolationFilterOrder()               { return isolationFilterOrder; }
	public void setIsolationFilterOrder(int v)         { this.isolationFilterOrder = v; }

	public boolean isAopEnabled()                      { return aopEnabled; }
	public void setAopEnabled(boolean v)               { this.aopEnabled = v; }

	public boolean isPrincipalFallbackEnabled()        { return principalFallbackEnabled; }
	public void setPrincipalFallbackEnabled(boolean v) { this.principalFallbackEnabled = v; }

	public int getPrincipalFallbackFilterOrder()       { return principalFallbackFilterOrder; }
	public void setPrincipalFallbackFilterOrder(int v) { this.principalFallbackFilterOrder = v; }
	public boolean isAuthContextRegistrationEnabled()        { return authContextRegistrationEnabled; }
	public void setAuthContextRegistrationEnabled(boolean v) { this.authContextRegistrationEnabled = v; }}

