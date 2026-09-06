package com.holonplatform.multitenant.event;

import com.holonplatform.multitenant.TenantContextTemplate;
import com.holonplatform.multitenant.TenantDetails;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring AOP aspect that automatically binds {@link com.holonplatform.multitenant.TenantContext}
 * around any {@code @EventListener} method invocation whose event argument implements
 * {@link TenantScopedEvent} — see that interface's Javadoc for the full rationale and usage
 * guidance.
 *
 * <p>Registered by {@code TenantCoreAutoConfiguration} only when AspectJ is on the classpath
 * (optional dependency) and Spring AOP auto-proxying is active in the application (typically via
 * {@code spring-boot-starter-aop}, already required by several other {@code holon-saas} modules
 * for their own {@code @Aspect} features — e.g. {@code tenant-audit}'s {@code AuditAspect}).
 */
@Aspect
public class TenantScopedEventListenerAspect {

	private static final Logger log = LoggerFactory.getLogger(TenantScopedEventListenerAspect.class);

	@Around("@annotation(org.springframework.context.event.EventListener) && args(event,..)")
	public Object bindTenantContextForScopedEvent(ProceedingJoinPoint joinPoint, Object event) throws Throwable {
		if (!(event instanceof TenantScopedEvent scopedEvent)) {
			return joinPoint.proceed();
		}

		TenantDetails tenant = scopedEvent.getTenant();
		if (tenant == null) {
			log.warn("TenantScopedEvent {} returned a null tenant — TenantContext left unchanged for listener {}",
					event.getClass().getName(), joinPoint.getSignature().toShortString());
			return joinPoint.proceed();
		}

		TenantDetails previous = TenantContextTemplate.bind(tenant);
		try {
			return joinPoint.proceed();
		} finally {
			TenantContextTemplate.restoreBound(previous);
		}
	}
}
