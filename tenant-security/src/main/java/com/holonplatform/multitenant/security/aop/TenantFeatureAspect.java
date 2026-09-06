package com.holonplatform.multitenant.security.aop;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.security.TenantFeatureNotEnabledException;
import com.holonplatform.multitenant.security.TenantPlanRequiredException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AOP aspect that enforces {@link CheckTenantFeature} and {@link RequiresPlan}
 * annotations at method invocation time.
 *
 * <h2>Registration</h2>
 * <p>This aspect is auto-configured when {@code aspectjweaver} is on the classpath.
 * To disable automatic registration, provide your own bean:
 * <pre>{@code
 * @Bean
 * public TenantFeatureAspect tenantFeatureAspect() {
 *     return new TenantFeatureAspect(); // customize if needed
 * }
 * }</pre>
 *
 * <p>Requires Spring AOP proxy creation ({@code @EnableAspectJAutoProxy} or
 * Spring Boot's auto-configured AOP support).
 *
 * <h2>Thread-safety</h2>
 * <p>The aspect is stateless and thread-safe.
 */
@Aspect
public class TenantFeatureAspect {

	private static final Logger log = LoggerFactory.getLogger(TenantFeatureAspect.class);

	/**
	 * Intercepts methods/classes annotated with {@link CheckTenantFeature} and
	 * verifies the current tenant has the required feature.
	 */
	@Before("@annotation(checkFeature)")
	public void enforceFeatureOnMethod(CheckTenantFeature checkFeature) {
		enforceFeature(checkFeature);
	}

	/**
	 * Intercepts methods on classes annotated with {@link CheckTenantFeature}.
	 */
	@Before("@within(checkFeature)")
	public void enforceFeatureOnClass(CheckTenantFeature checkFeature) {
		enforceFeature(checkFeature);
	}

	/**
	 * Intercepts methods/classes annotated with {@link RequiresPlan} and
	 * verifies the current tenant's plan meets the minimum requirement.
	 */
	@Before("@annotation(requiresPlan)")
	public void enforcePlanOnMethod(RequiresPlan requiresPlan) {
		enforcePlan(requiresPlan);
	}

	/**
	 * Intercepts methods on classes annotated with {@link RequiresPlan}.
	 */
	@Before("@within(requiresPlan)")
	public void enforcePlanOnClass(RequiresPlan requiresPlan) {
		enforcePlan(requiresPlan);
	}

	// -------------------------------------------------------------------------
	// Core enforcement logic
	// -------------------------------------------------------------------------

	private void enforceFeature(CheckTenantFeature annotation) {
		TenantDetails tenant = TenantContext.get().orElse(null);
		if (tenant == null) {
			log.warn("@CheckTenantFeature('{}') invoked with no tenant in context — access denied",
					annotation.value());
			throw new TenantFeatureNotEnabledException(annotation.value());
		}
		if (!tenant.hasFeature(annotation.value())) {
			throw new TenantFeatureNotEnabledException(annotation.value());
		}
	}

	private void enforcePlan(RequiresPlan annotation) {
		TenantDetails tenant = TenantContext.get().orElse(null);
		if (tenant == null) {
			log.warn("@RequiresPlan('{}') invoked with no tenant in context — access denied",
					annotation.minimum());
			throw new TenantPlanRequiredException(annotation.minimum(), null);
		}
		if (tenant.plan().ordinal() < annotation.minimum().ordinal()) {
			throw new TenantPlanRequiredException(annotation.minimum(), tenant.plan());
		}
	}
}

