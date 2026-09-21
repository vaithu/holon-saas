package com.holonplatform.multitenant.demo.service;

import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.AuditSeverity;
import com.holonplatform.multitenant.audit.annotation.Audited;
import com.holonplatform.multitenant.demo.event.DemoSaleRecordedEvent;
import com.holonplatform.multitenant.limits.annotation.RateLimit;
import com.holonplatform.multitenant.permissions.annotation.RequiresPermission;

/**
 * Demonstrates <strong>method-level</strong> authorization, rate limiting and auditing.
 *
 * <p>{@link #recordSale} is reachable only by a user whose role grants
 * {@link DemoRoles#SALES_RECORD}. In the demo that is the SALESMAN (Alice) and the workspace
 * owner; the ACCOUNTANT (Bob) is refused with a {@code PermissionDeniedException} thrown by
 * the framework's aspect before the method body ever runs.
 *
 * <p>The same method carries two further framework guards, each applied by its own aspect:
 * {@code @RateLimit} consumes a permit from the workspace's per-minute bucket (F10), and
 * {@code @Audited} writes an entry into the workspace's audit log (F11) whether the call
 * succeeds or fails.
 */
@Service
public class SalesService {

    private final DemoLedger ledger;

    private final ApplicationEventPublisher events;

    public SalesService(DemoLedger ledger, ApplicationEventPublisher events) {
        this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
    }

    /**
     * Records a sale for the current tenant.
     *
     * @throws com.holonplatform.multitenant.permissions.exception.PermissionDeniedException
     *         if the caller's role does not grant {@code SALES_RECORD}
     * @throws com.holonplatform.multitenant.limits.RateLimitExceededException
     *         if the workspace has already recorded its allowance of sales this minute
     */
    @RequiresPermission(value = DemoRoles.SALES_RECORD,
            message = "Only a SALESMAN may record a sale.")
    @RateLimit(key = DemoLimits.SALES_RECORD_LIMIT_KEY)
    @Audited(action = AuditAction.CREATE,
            resourceType = "Sale",
            resourceIdExpression = "#customer",
            severity = AuditSeverity.INFO,
            description = "Sale recorded")
    public String recordSale(String customer, long amount) {
        TenantDetails tenant = TenantContext.get()
                .orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"));

        String entry = "SALE   %s -> %d".formatted(customer, amount);
        ledger.append(tenant.tenantId(), entry);

        // Announced as a tenant-scoped event (F21): the tenant rides along on the event, so a
        // listener reached from any thread still runs with the right workspace bound.
        events.publishEvent(new DemoSaleRecordedEvent(this, tenant, customer, amount));
        return entry;
    }
}
