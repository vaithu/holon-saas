package com.holonplatform.multitenant.security;

import com.holonplatform.multitenant.TenantPlan;

/**
 * Thrown by the AOP aspect when a method annotated with
 * {@link com.holonplatform.multitenant.security.aop.RequiresPlan}
 * is invoked by a tenant whose plan does not meet the minimum requirement.
 */
public class TenantPlanRequiredException extends TenantSecurityException {

	private static final long serialVersionUID = 1L;

	private final TenantPlan requiredPlan;
	private final TenantPlan actualPlan;

	public TenantPlanRequiredException(TenantPlan requiredPlan, TenantPlan actualPlan) {
		super("Plan '" + requiredPlan + "' or higher is required; current plan: '" + actualPlan + "'");
		this.requiredPlan = requiredPlan;
		this.actualPlan   = actualPlan;
	}

	/** The minimum plan that was required. */
	public TenantPlan getRequiredPlan() { return requiredPlan; }

	/** The actual plan of the current tenant. */
	public TenantPlan getActualPlan()   { return actualPlan; }
}

