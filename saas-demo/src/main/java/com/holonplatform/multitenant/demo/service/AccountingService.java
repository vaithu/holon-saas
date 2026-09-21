package com.holonplatform.multitenant.demo.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.annotation.Audited;
import com.holonplatform.multitenant.permissions.annotation.RequiresPermission;

/**
 * The mirror image of {@link SalesService}, and the second half of the method-level
 * authorization demo.
 *
 * <p>{@link #postLedgerEntry} requires {@link DemoRoles#ACCOUNTING_POST}, which the ACCOUNTANT
 * (Bob) holds and the SALESMAN (Alice) does not — so each of the two invited users can call
 * exactly one of these two services.
 */
@Service
public class AccountingService {

    private final DemoLedger ledger;

    public AccountingService(DemoLedger ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");
    }

    /**
     * Posts a ledger entry for the current tenant.
     *
     * @throws com.holonplatform.multitenant.permissions.exception.PermissionDeniedException
     *         if the caller's role does not grant {@code ACCOUNTING_POST}
     */
    @RequiresPermission(value = DemoRoles.ACCOUNTING_POST,
            message = "Only an ACCOUNTANT may post a ledger entry.")
    @Audited(action = AuditAction.CREATE,
            resourceType = "LedgerEntry",
            resourceIdExpression = "#account",
            description = "Ledger entry posted")
    public String postLedgerEntry(String account, long amount) {
        String tenantId = TenantContext.get()
                .map(tenant -> tenant.tenantId())
                .orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"));

        String entry = "LEDGER %s -> %d".formatted(account, amount);
        ledger.append(tenantId, entry);
        return entry;
    }
}
