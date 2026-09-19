package com.holonplatform.multitenant.permissions.view;

/**
 * Standard actions for fine-grained view permissions.
 *
 * <p>Custom action strings are also supported wherever action strings are accepted.</p>
 */
public final class ViewAction {

    /** Action to view / navigate to a view (Read access). */
    public static final String READ = "READ";

    /** Action to create new records / resources within a view. */
    public static final String CREATE = "CREATE";

    /** Action to modify or update records within a view. */
    public static final String UPDATE = "UPDATE";

    /** Action to delete records within a view. */
    public static final String DELETE = "DELETE";

    /** Action to export or download data from a view. */
    public static final String EXPORT = "EXPORT";

    /** Action to trigger execution, approval, or batch operations in a view. */
    public static final String EXECUTE = "EXECUTE";

    private ViewAction() {
    }
}
