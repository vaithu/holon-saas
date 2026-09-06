package com.holonplatform.multitenant.security.aop;

import com.holonplatform.multitenant.TenantPlan;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated method or class requires the current tenant's
 * subscription plan to be at least the specified minimum level.
 *
 * <p>The plan hierarchy (ascending) is:
 * {@link TenantPlan#FREE} &lt; {@link TenantPlan#STARTER} &lt;
 * {@link TenantPlan#PROFESSIONAL} &lt; {@link TenantPlan#ENTERPRISE}.
 *
 * <p>Throws {@link com.holonplatform.multitenant.security.TenantPlanRequiredException}
 * if the tenant's plan is below the minimum.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Service
 * public class IntegrationService {
 *
 *     @RequiresPlan(TenantPlan.PROFESSIONAL)
 *     public void syncWithExternalSystem() { ... }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface RequiresPlan {

	/** The minimum {@link TenantPlan} required to invoke this method. */
	TenantPlan minimum();

	/** Optional custom message for the exception. Defaults to an auto-generated message. */
	String message() default "";
}

