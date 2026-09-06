package com.holonplatform.multitenant.quotas.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.quotas.QuotaCheckResult;
import com.holonplatform.multitenant.quotas.QuotaKey;
import com.holonplatform.multitenant.quotas.TenantQuotaService;
import com.holonplatform.multitenant.quotas.annotation.EnforceQuota;
import com.holonplatform.multitenant.quotas.exception.QuotaExceededException;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * AOP aspect that enforces {@link EnforceQuota} before annotated methods execute.
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

    @Before("@annotation(enforceQuota)")
    public void enforce(EnforceQuota enforceQuota) {
        String tenantId = TenantContext.peekTenantId();
        if (tenantId == null) {
            log.trace("@EnforceQuota('{}') bypassed — no tenant context", enforceQuota.value());
            return;
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
    }
}

