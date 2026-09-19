package com.holonplatform.multitenant.permissions.view;

import java.util.Map;
import java.util.Set;

/**
 * Registry holding the platform's immutable system baseline permissions for standard roles.
 *
 * <p>When a tenant has not explicitly customized a permission for a system role, the authorization
 * engine falls back to these system defaults.</p>
 */
public interface SystemDefaultPermissionRegistry {

    /**
     * Registers default allowed actions for a system role on a specific view.
     *
     * @param roleName the system role (e.g. {@code "SALESMAN"})
     * @param viewId   the view ID or {@code "*"} for all views
     * @param actions  allowed actions (e.g. {@code "READ"}, {@code "UPDATE"}) or {@code "*"}
     */
    void registerDefault(String roleName, String viewId, String... actions);

    /**
     * Checks if a role is permitted to perform an action on a view by system default.
     *
     * @param roleName the role name
     * @param viewId   the view identifier
     * @param action   the action name
     * @return true if granted by default
     */
    boolean isDefaultAllowed(String roleName, String viewId, String action);

    /**
     * Returns the complete default permission mapping for a role (viewId -&gt; Set&lt;actions&gt;).
     *
     * @param roleName the role name
     */
    Map<String, Set<String>> getDefaultRolePermissions(String roleName);

    /**
     * Returns the set of all known system roles defined in this baseline registry.
     */
    Set<String> getKnownSystemRoles();

    /**
     * Returns true if the given role is a standard system role.
     */
    boolean isSystemRole(String roleName);
}
