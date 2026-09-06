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

	public boolean isIsolationEnforced()               { return isolationEnforced; }
	public void setIsolationEnforced(boolean v)        { this.isolationEnforced = v; }

	public int getIsolationFilterOrder()               { return isolationFilterOrder; }
	public void setIsolationFilterOrder(int v)         { this.isolationFilterOrder = v; }

	public boolean isAopEnabled()                      { return aopEnabled; }
	public void setAopEnabled(boolean v)               { this.aopEnabled = v; }
}

