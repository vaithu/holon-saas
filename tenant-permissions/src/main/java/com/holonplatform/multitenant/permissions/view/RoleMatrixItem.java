package com.holonplatform.multitenant.permissions.view;

/**
 * Represents the permission state of a specific action on a view for a role in a tenant.
 *
 * @param viewId            identifier of the view (e.g. {@code "customer-details"})
 * @param viewDisplayName   display name of the view (e.g. {@code "Customer Details"})
 * @param category          module / category of the view (e.g. {@code "CRM"})
 * @param action            the action name (e.g. {@code "READ"}, {@code "CREATE"})
 * @param allowed           effective permission status for this role
 * @param customized        true if this state is explicitly customized/overridden by the tenant
 * @param inheritedDefault  the baseline system default value for this action
 */
public record RoleMatrixItem(
        String viewId,
        String viewDisplayName,
        String category,
        String action,
        boolean allowed,
        boolean customized,
        boolean inheritedDefault
) {
}
