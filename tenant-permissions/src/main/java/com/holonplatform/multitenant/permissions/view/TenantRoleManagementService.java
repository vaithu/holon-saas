package com.holonplatform.multitenant.permissions.view;

import java.util.List;
import java.util.Set;

/**
 * High-level dynamic authorization and role management service for tenants.
 *
 * <p>Allows tenant administrators to customize permissions per view and per action for any role,
 * create custom roles, inspect the full permission matrix, and reset overrides back to system baseline defaults.</p>
 */
public interface TenantRoleManagementService {

    /**
     * Returns all roles available in the tenant, including system baseline roles and tenant-created custom roles.
     *
     * @param tenantId the tenant identifier
     */
    List<RoleDefinition> getRoles(String tenantId);

    /**
     * Creates a new custom role for a tenant, optionally cloning baseline permissions from an existing role.
     *
     * @param tenantId    the tenant identifier
     * @param roleName    the unique identifier for the custom role (e.g. "JUNIOR_SALESMAN")
     * @param displayName friendly display name
     * @param description brief description
     * @param baseRole    optional template role to clone default permissions from
     */
    RoleDefinition createCustomRole(String tenantId, String roleName, String displayName, String description, String baseRole);

    /**
     * Deletes a custom role and all of its associated permission overrides in the tenant.
     *
     * @param tenantId the tenant identifier
     * @param roleName the custom role identifier
     */
    void deleteCustomRole(String tenantId, String roleName);

    /**
     * Returns the permission matrix for a specific role across all registered views and actions,
     * showing effective permissions and whether each item is inherited or customized.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name to inspect
     */
    List<RoleMatrixItem> getRoleMatrix(String tenantId, String roleName);

    /**
     * Overrides (grants or revokes) permission for a specific view action on a role.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name
     * @param viewId   the view identifier
     * @param action   the action name (e.g. {@code "CREATE"})
     * @param allowed  true to grant, false to deny/revoke
     */
    void setActionOverride(String tenantId, String roleName, String viewId, String action, boolean allowed);

    /**
     * Resets all custom overrides on a specific view back to system defaults for the given role.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name
     * @param viewId   the view identifier
     */
    void resetViewToDefault(String tenantId, String roleName, String viewId);

    /**
     * Resets all custom overrides for a role back to system defaults.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name
     */
    void resetRoleToDefault(String tenantId, String roleName);

    /**
     * Checks if a role in a tenant is permitted to perform a specific action on a view.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name
     * @param viewId   the view identifier
     * @param action   the action name
     * @return true if permitted by explicit override or inherited system default
     */
    boolean isActionPermitted(String tenantId, String roleName, String viewId, String action);

    /**
     * Checks if a role in a tenant is permitted to view/navigate to a specific view.
     * Equivalent to {@code isActionPermitted(tenantId, roleName, viewId, ViewAction.READ)}.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name
     * @param viewId   the view identifier
     * @return true if navigation to the view is allowed
     */
    boolean isViewPermitted(String tenantId, String roleName, String viewId);

    /**
     * Checks whether the current authenticated user has permission to navigate to the specified view.
     *
     * @param viewId the view identifier
     * @return true if the current user can access the view
     */
    boolean currentUserCanAccessView(String viewId);

    /**
     * Checks whether the current authenticated user has permission to perform the specified action on a view.
     *
     * @param viewId the view identifier
     * @param action the action name (e.g. {@code "CREATE"}, {@code "EXPORT"})
     * @return true if the current user can perform the action
     */
    boolean currentUserCanPerformAction(String viewId, String action);

    /**
     * Returns all permitted action names for the current authenticated user on a specific view.
     *
     * @param viewId the view identifier
     * @return set of permitted actions (e.g. {@code ["READ", "UPDATE"]})
     */
    Set<String> getCurrentUserPermittedActions(String viewId);
}
