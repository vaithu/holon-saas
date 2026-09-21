package com.holonplatform.multitenant.demo.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.CachingTenantDetailsLoader;
import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.admin.service.TenantAdminService;
import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.AuditSeverity;
import com.holonplatform.multitenant.audit.annotation.Audited;
import com.holonplatform.multitenant.permissions.annotation.RequiresPermission;

/**
 * Changes a workspace's plan, which is what makes the plan and feature gates in
 * {@link ReportService} flip from refusing to allowing (F12).
 *
 * <h2>Two things have to be got right</h2>
 * <p><strong>The write must not go to the tenant's own schema.</strong> Tenant records live in
 * the control plane, in the default schema, but during a signed-in request the tenant is bound
 * to {@link TenantContext} and every query is routed to that tenant's schema — where no
 * {@code tenant} table exists. The binding is therefore removed for the duration of the write
 * and restored afterwards, so the rest of the request still sees its tenant.
 *
 * <p><strong>The cached copy must be dropped.</strong> Tenant details are cached by
 * {@link CachingTenantDetailsLoader}, and it is that cached copy which the request filter binds
 * on every subsequent request. Without an explicit invalidation the workspace would keep
 * behaving as though it were still on the old plan until the entry expired.
 */
@Service
public class DemoPlanService {

    private static final Logger log = LoggerFactory.getLogger(DemoPlanService.class);

    private final TenantAdminService  adminService;
    private final TenantDetailsLoader tenantDetailsLoader;

    public DemoPlanService(TenantAdminService adminService, TenantDetailsLoader tenantDetailsLoader) {
        this.adminService        = Objects.requireNonNull(adminService, "adminService must not be null");
        this.tenantDetailsLoader = Objects.requireNonNull(tenantDetailsLoader,
                "tenantDetailsLoader must not be null");
    }

    /**
     * Moves the given workspace onto a different plan.
     *
     * @return the saved tenant
     */
    @RequiresPermission(value = DemoRoles.PLAN_MANAGE,
            message = "Only the workspace owner may change the plan.")
    @Audited(action = AuditAction.PLAN_CHANGED,
            resourceType = "Tenant",
            resourceIdExpression = "#tenantId",
            severity = AuditSeverity.WARNING,
            description = "Workspace plan changed")
    public TenantDetails changePlan(String tenantId, TenantPlan newPlan) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(newPlan, "newPlan must not be null");

        TenantDetails saved = TenantBinding.onControlPlane(() -> {
            TenantDetails current = adminService.findById(tenantId)
                    .orElseThrow(() -> new IllegalStateException("Unknown workspace: " + tenantId));
            return adminService.save(current.toBuilder().plan(newPlan).build());
        });

        invalidateCachedDetails(tenantId);
        log.info("Workspace '{}' moved to plan {}", tenantId, newPlan);
        return saved;
    }

    private void invalidateCachedDetails(String tenantId) {
        if (tenantDetailsLoader instanceof CachingTenantDetailsLoader caching) {
            caching.invalidate(tenantId);
        }
    }
}
