package com.holonplatform.multitenant.demo.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantFeature;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.AuditEntry;
import com.holonplatform.multitenant.audit.TenantAuditService;
import com.holonplatform.multitenant.security.aop.CheckTenantFeature;
import com.holonplatform.multitenant.security.aop.RequiresPlan;

/**
 * Demonstrates gating on the workspace's commercial position rather than on the caller's role
 * (F12).
 *
 * <h2>Two different questions</h2>
 * <p>{@code @RequiresPermission} asks <em>"is this person allowed?"</em>.
 * {@code @RequiresPlan} and {@code @CheckTenantFeature} ask <em>"has this workspace paid for
 * it?"</em> — the answer is the same for every member of the workspace, and changes when the
 * plan changes rather than when a role changes.
 *
 * <p>The two guards differ from each other too. {@link #exportSalesReport} needs a plan of at
 * least {@code PROFESSIONAL}, which is an ordered comparison. {@link #recentAuditEntries} needs
 * the {@code AUDIT_LOG} feature, which a tenant may hold either because its plan includes it or
 * because it was granted individually — useful for enabling something for one customer without
 * moving them up a tier.
 */
@Service
public class ReportService {

    private final TenantAuditService auditService;

    public ReportService(TenantAuditService auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
    }

    /**
     * Produces a (pretend) sales export.
     *
     * @throws com.holonplatform.multitenant.security.TenantPlanRequiredException
     *         if the workspace's plan is below {@code PROFESSIONAL}
     */
    @RequiresPlan(minimum = TenantPlan.PROFESSIONAL,
            message = "Exporting reports requires the PROFESSIONAL plan.")
    public String exportSalesReport(String tenantId) {
        return "Sales report for " + tenantId + " exported.";
    }

    /**
     * Reads the workspace's most recent audit entries.
     *
     * @throws com.holonplatform.multitenant.security.TenantFeatureNotEnabledException
     *         if the workspace does not have the {@code AUDIT_LOG} feature
     * @throws com.holonplatform.multitenant.permissions.exception.PermissionDeniedException
     *         if the caller does not hold {@code AUDIT_VIEW} — the framework's audit service
     *         applies that check itself
     */
    @CheckTenantFeature(value = TenantFeature.AUDIT_LOG,
            message = "The audit log is available from the PROFESSIONAL plan upwards.")
    public List<AuditEntry> recentAuditEntries(String tenantId, int limit) {
        return auditService.findByTenant(tenantId, 0, limit);
    }

    /**
     * Reads the workspace's recent audit entries for one kind of action.
     *
     * <p>Filtering happens in the query rather than in memory, which is what makes this worth
     * having as a separate method: the audit log is the one table in a workspace that only ever
     * grows.
     */
    @CheckTenantFeature(value = TenantFeature.AUDIT_LOG,
            message = "The audit log is available from the PROFESSIONAL plan upwards.")
    public List<AuditEntry> auditEntriesByAction(String tenantId, AuditAction action, int limit) {
        return auditService.findByTenantAndAction(tenantId, action, 0, limit);
    }
}
