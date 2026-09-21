package com.holonplatform.multitenant.demo.service;

import java.time.Duration;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.limits.TenantRateLimiter;
import com.holonplatform.multitenant.quotas.QuotaDefinition;
import com.holonplatform.multitenant.quotas.QuotaKeys;
import com.holonplatform.multitenant.quotas.TenantQuotaService;

import jakarta.annotation.PostConstruct;

/**
 * Registers the demo's quota limits (F9) and rate limits (F10) at startup.
 *
 * <h2>Quotas versus rate limits</h2>
 * <p>They answer different questions. A <strong>quota</strong> caps how much of a resource a
 * workspace may hold at once and is derived from its plan — here, how many members it may have.
 * A <strong>rate limit</strong> caps how often an operation may be invoked over a window,
 * regardless of plan — here, how many sales may be recorded per minute.
 *
 * <p>Both are registries that the framework's aspects consult, so they have to be populated
 * before the first request. Registering them from {@code @PostConstruct} keeps that ordering
 * guaranteed without an event listener.
 */
@Component
public class DemoLimits {

    private static final Logger log = LoggerFactory.getLogger(DemoLimits.class);

    /**
     * Rate-limit key for recording a sale.
     *
     * <p>Deliberately not one of the framework's {@code TenantLimitKeys} constants: those name
     * generic platform concerns (API calls, e-mail sends), whereas this limits one specific
     * business operation.
     */
    public static final String SALES_RECORD_LIMIT_KEY = "sales.records";

    /** Small enough that the limit can be reached by hand during a demo. */
    public static final long SALES_RECORDS_PER_MINUTE = 3;

    private final TenantQuotaService quotaService;
    private final TenantRateLimiter  rateLimiter;

    public DemoLimits(TenantQuotaService quotaService, TenantRateLimiter rateLimiter) {
        this.quotaService = Objects.requireNonNull(quotaService, "quotaService must not be null");
        this.rateLimiter  = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
    }

    @PostConstruct
    void register() {
        // A FREE workspace holds three members: the owner plus the two colleagues the
        // walkthrough invites. Inviting a fourth is what makes the quota visible.
        quotaService.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_USERS)
                .limit(TenantPlan.FREE, 3L)
                .limit(TenantPlan.STARTER, 10L)
                .limit(TenantPlan.PROFESSIONAL, 50L)
                .limit(TenantPlan.ENTERPRISE, -1L)
                .build());

        // The rate limiter refuses any key it has no definition for, so this registration is
        // required rather than optional tuning. It goes through the TenantRateLimiter interface
        // so that it keeps working whichever implementation is in play — the in-memory token
        // bucket, or the Redis fixed-window limiter that takes over once Redis is present (F23).
        rateLimiter.registerDefinition(SALES_RECORD_LIMIT_KEY,
                TenantRateLimiter.LimitDefinition.of(SALES_RECORDS_PER_MINUTE, Duration.ofMinutes(1)));

        log.info("Registered demo quotas and the '{}' rate limit on {}",
                SALES_RECORD_LIMIT_KEY, rateLimiter.getClass().getSimpleName());
    }
}
