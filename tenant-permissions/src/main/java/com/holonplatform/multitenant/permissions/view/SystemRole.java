package com.holonplatform.multitenant.permissions.view;

/**
 * Standard system role identifiers provided out-of-the-box by the platform.
 */
public final class SystemRole {

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
