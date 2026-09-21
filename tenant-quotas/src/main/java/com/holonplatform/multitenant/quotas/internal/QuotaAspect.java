package com.holonplatform.multitenant.quotas.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.quotas.QuotaCheckResult;
import com.holonplatform.multitenant.quotas.QuotaKey;
import com.holonplatform.multitenant.quotas.TenantQuotaService;
import com.holonplatform.multitenant.quotas.annotation.EnforceQuota;
import com.holonplatform.multitenant.quotas.exception.QuotaExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * AOP aspect that enforces {@link EnforceQuota} around annotated methods.
 *
 * <p>Bypassed when no tenant is present in the current context (system/admin calls).</p>
 */
@Aspect
@Order(20)
public class QuotaAspect {

    private static final Logger log = LoggerFactory.getLogger(QuotaAspect.class);

    private final TenantQuotaService quotaService;

    public QuotaAspect(TenantQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    /**
     * Checks the quota, runs the method, and then drops the cached usage count.
     *
     * <p>The eviction is what makes consecutive calls see each other. Usage is cached for a
     * short interval to keep {@code @EnforceQuota} from issuing a {@code COUNT(*)} on every
     * invocation; without evicting after a successful call, a tenant sitting one below its
     * limit could pass the check repeatedly for the whole cache lifetime and overshoot by as
     * many calls as fit into it.
     */
    @Around("@annotation(enforceQuota)")
    public Object enforce(ProceedingJoinPoint joinPoint, EnforceQuota enforceQuota) throws Throwable {
        String tenantId = TenantContext.peekTenantId();
        if (tenantId == null) {
            log.trace("@EnforceQuota('{}') bypassed — no tenant context", enforceQuota.value());
            return joinPoint.proceed();
        }

        QuotaCheckResult result = quotaService.checkQuota(tenantId, enforceQuota.value());
        if (result.exceeded()) {
            log.debug("@EnforceQuota('{}') exceeded for tenant '{}': limit={}, usage={}",
                      enforceQuota.value(), tenantId, result.limit(), result.currentUsage());

            // Find the actual QuotaKey for a well-typed exception
            QuotaKey key = result.key();
            throw new QuotaExceededException(key, result.limit(), result.currentUsage(),
                                             enforceQuota.message());
        }

        Object returned = joinPoint.proceed();

        // Only on success: a method that threw consumed nothing.
        quotaService.invalidateUsageCache(tenantId, result.key());
        return returned;
    }
}

