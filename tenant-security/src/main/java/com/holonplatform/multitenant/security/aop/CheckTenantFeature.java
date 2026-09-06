package com.holonplatform.multitenant.security.aop;

import com.holonplatform.multitenant.TenantFeature;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares that the annotated method or class requires a specific
 * {@link TenantFeature} to be enabled for the current tenant.
 *
 * <p>When placed on a class, applies to all methods in the class.
 * Method-level annotations override class-level ones.
 *
 * <p>Throws {@link com.holonplatform.multitenant.security.TenantFeatureNotEnabledException}
 * if the feature is not available.
 *
 * <p>Requires {@code TenantFeatureAspect} to be registered as a Spring bean
 * and {@code @EnableAspectJAutoProxy} to be active.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @Service
 * public class ReportService {
 *
 *     @CheckTenantFeature(TenantFeature.ADVANCED_REPORTS)
 *     public List<Report> generateReports() {
 *         // Only tenants with ADVANCED_REPORTS can reach here
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface CheckTenantFeature {

	/** The feature that must be enabled for the current tenant. */
	TenantFeature value();

	/** Optional custom message for the exception. Defaults to an auto-generated message. */
	String message() default "";
}

