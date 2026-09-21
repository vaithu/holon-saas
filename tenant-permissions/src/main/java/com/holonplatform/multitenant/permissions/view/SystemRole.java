package com.holonplatform.multitenant.permissions.view;

/**
 * Standard system role identifiers provided out-of-the-box by the platform.
 */
public final class SystemRole {

    /**
     * Tenant owner — the highest tenant role ({@code TenantRole.OWNER}), holding every
     * administrator right plus tenant deletion and ownership transfer.
     *
     * <p>Like {@link #ADMIN}, this role short-circuits to full access in
     * {@code TenantRoleManagementService#isActionPermitted}. It must: the baseline
     * {@code SystemDefaultPermissionRegistry} registers no defaults under the name
     * {@code "OWNER"}, so without the short-circuit a tenant owner resolves to "not
     * allowed" for every view and action and is locked out of the entire dynamic
     * permission matrix — strictly less access than the ADMIN they outrank.
     */
    public static final String OWNER = "OWNER";

    /** Full administrator with wildcard access to all views and actions. */
    public static final String ADMIN = "ADMIN";

    /** Sales representative with access to CRM, leads, and customer viewing/editing. */
    public static final String SALESMAN = "SALESMAN";

    /** Financial and accounting personnel with access to invoicing, payments, and reports. */
    public static final String ACCOUNTANT = "ACCOUNTANT";

    /** Standard system operator with operational and task execution access. */
    public static final String OPERATOR = "OPERATOR";

    /** Standard member with basic view and collaboration access. */
    public static final String MEMBER = "MEMBER";

    /** Read-only viewer across allowed views. */
    public static final String VIEWER = "VIEWER";

    private SystemRole() {
    }
}
