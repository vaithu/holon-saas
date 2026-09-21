package com.holonplatform.multitenant.demo.service;

/**
 * The job roles and permission keys used by the demo.
 *
 * <h2>How a permission check resolves</h2>
 * <p>{@code @RequiresPermission("SALES_RECORD")} is intercepted by the framework's
 * {@code PermissionAspect}, which asks the permission service whether the current user holds
 * that permission. The service resolves the caller's email from the Holon {@code AuthContext}
 * (populated from Spring Security), maps it to a tenant user, and then checks the user's
 * <em>job role</em> first and their <em>platform role</em> second, honouring a {@code "*"}
 * wildcard in either.
 *
 * <p>Permission keys must not contain a colon: the service treats a {@code "viewId:ACTION"}
 * shape as a request for the separate view/action matrix rather than the flat permission store
 * that this demo seeds.
 */
public final class DemoRoles {

    private DemoRoles() {
    }

    // ── Job roles (stored on the tenant user as the "job_role" attribute) ───────────────

    public static final String SALESMAN   = "SALESMAN";
    public static final String ACCOUNTANT = "ACCOUNTANT";

    // ── Permission keys ────────────────────────────────────────────────────────────────

    /** Grants entry to the Sales view. */
    public static final String SALES_VIEW = "SALES_VIEW";
    /** Grants the right to call {@code SalesService#recordSale}. */
    public static final String SALES_RECORD = "SALES_RECORD";

    /** Grants entry to the Accounting view. */
    public static final String ACCOUNTING_VIEW = "ACCOUNTING_VIEW";
    /** Grants the right to call {@code AccountingService#postLedgerEntry}. */
    public static final String ACCOUNTING_POST = "ACCOUNTING_POST";

    /** Grants entry to the Team view, where the owner invites people. */
    public static final String USER_INVITE = "USER_INVITE";

    /** Grants entry to the Settings view and the right to change a setting. */
    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

    /**
     * Grants entry to the Audit log view.
     *
     * <p>The framework's own {@code TenantAuditService} read methods are gated on
     * {@code {"AUDIT_VIEW", "ADMIN"}}, so this key deliberately matches the name the framework
     * expects rather than inventing a demo-specific one.
     */
    public static final String AUDIT_VIEW = "AUDIT_VIEW";

    /** Grants entry to the Plan view, where the owner changes the workspace's plan. */
    public static final String PLAN_MANAGE = "PLAN_MANAGE";

    /** Grants entry to the Roles view, where the owner edits the view/action matrix. */
    public static final String ROLES_MANAGE = "ROLES_MANAGE";

    /**
     * Permission for exporting from the Sales view — and the one key in the demo that is
     * deliberately <em>not</em> a flat permission.
     *
     * <p>Its {@code viewId:ACTION} shape makes the permission service delegate it to the
     * per-tenant role matrix instead of the flat store, so whether Alice may export is decided
     * by what her workspace's owner has granted SALESMAN on the {@code sales} view — not by
     * anything seeded at sign-up. That is the distinction F16 exists to show.
     */
    public static final String SALES_EXPORT_ACTION = "sales:EXPORT";
}
