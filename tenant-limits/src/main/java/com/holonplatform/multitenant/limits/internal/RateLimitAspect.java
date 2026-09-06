package com.holonplatform.multitenant.limits.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.limits.TenantRateLimiter;
import com.holonplatform.multitenant.limits.annotation.RateLimit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * AOP aspect that enforces {@link RateLimit} on annotated methods.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>Peeks the current tenant via {@link TenantContext#peekTenantId()}.</li>
 *   <li>If no tenant is active the call is allowed through (system / admin contexts).</li>
 *   <li>Delegates to {@link TenantRateLimiter#acquireOrThrow(String, String)}.</li>
 * </ol>
 *
 * <p>The aspect is registered only when {@code org.aspectj.lang.annotation.Aspect}
 * is on the classpath; it is therefore safe to include the module as an optional
 * dependency.
 */
@Aspect
@Order(10)
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final TenantRateLimiter rateLimiter;

    public RateLimitAspect(TenantRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Around("@annotation(rateLimit)")
    public Object enforce(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String tenantId = TenantContext.peekTenantId();
        if (tenantId == null) {
            // No tenant → system call; bypass rate limiting
            log.trace("@RateLimit bypassed (no tenant context) on {}", pjp.getSignature().toShortString());
            return pjp.proceed();
        }
        String key = rateLimit.key();
        log.trace("@RateLimit check tenant='{}' key='{}'", tenantId, key);
        rateLimiter.acquireOrThrow(tenantId, key);
        return pjp.proceed();
    }
}

