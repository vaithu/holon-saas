package com.holonplatform.multitenant.permissions;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing fine-grained role-based permissions within a tenant.
 *
 * <p>Each tenant maintains its own role→permission mapping. A "role" here refers
 * to the {@code customRole} string on a {@code TenantUser} (e.g. "ACCOUNTANT",
 * "DRIVER"), or a platform role name (e.g. "ADMIN", "MEMBER").</p>
 *
 * <p>Typical setup at application startup (or in a {@code TenantOnboardedEvent} listener):</p>
 * <pre>
 * permissionService.setPermissions("acme-corp", "ACCOUNTANT",
 *     Set.of("VIEW_FINANCIALS", "APPROVE_INVOICE", "VIEW_CONTACTS"));
 * permissionService.setPermissions("acme-corp", "DRIVER",
 *     Set.of("VIEW_OWN_TASKS", "MANAGE_DELIVERIES"));
 * permissionService.setPermissions("acme-corp", "ADMIN",
 *     Set.of("*"));   // wildcard = all permissions
 * </pre>
 */
public interface TenantPermissionService {

    /**
     * Standard key used in {@code TenantUser.attributes()} to store a user's job-specific role.
     *
     * <p>Example — assigning a job role when inviting a user:
     * <pre>{@code
     * TenantUser user = TenantUser.builder(userId, tenantId, email)
     *     .role(TenantRole.MEMBER)
     *     .attribute(TenantPermissionService.JOB_ROLE_ATTRIBUTE, "ACCOUNTANT")
     *     .build();
     * }</pre>
     *
     * <p>The permission service checks this attribute first when resolving
     * {@link #currentUserHasPermission(String)}.
     */
    String JOB_ROLE_ATTRIBUTE = "job_role";

    /**
     * Replaces the full permission set for a role in a tenant.
     * Use {@code "*"} as a permission to grant all permissions (wildcard).
     */
    void setPermissions(String tenantId, String roleName, Set<String> permissions);

    /**
     * Adds the given permissions to a role's existing permission set.
     */
    void grantPermissions(String tenantId, String roleName, Set<String> permissions);

    /**
     * Removes the given permissions from a role's permission set.
     */
    void revokePermissions(String tenantId, String roleName, Set<String> permissions);

    /**
     * Returns all permissions granted to a role in a tenant.
     */
    Set<String> getPermissions(String tenantId, String roleName);

    /**
     * Returns true if the given role has been granted the specified permission in the tenant.
     * Also returns true if the role has the wildcard {@code "*"} permission.
     */
    boolean hasPermission(String tenantId, String roleName, String permission);

    /**
     * Overload accepting a holon-core {@link com.holonplatform.auth.Permission} object.
     * The permission string is resolved via {@link com.holonplatform.auth.Permission#getPermission()}.
     */
    default boolean hasPermission(String tenantId, String roleName, com.holonplatform.auth.Permission permission) {
        return permission.getPermission()
                .map(p -> hasPermission(tenantId, roleName, p))
                .orElse(false);
    }

    /**
     * Checks whether the <strong>current authenticated user</strong> (resolved from the
     * Spring {@code SecurityContextHolder}) has the specified permission within the
     * current tenant ({@code TenantContext.require()}).
     *
     * <p>The check is performed against both the user's {@code customRole} and their
     * platform {@code TenantRole} name.</p>
     */
    boolean currentUserHasPermission(String permission);

    /**
     * Overload accepting a holon-core {@link com.holonplatform.auth.Permission} object.
     * The permission string is resolved via {@link com.holonplatform.auth.Permission#getPermission()}.
     */
    default boolean currentUserHasPermission(com.holonplatform.auth.Permission permission) {
        return permission.getPermission()
                .map(this::currentUserHasPermission)
                .orElse(false);
    }

    /**
     * Returns the full permission map for a tenant: roleName → Set&lt;permission&gt;.
     */
    Map<String, Set<String>> getAllPermissions(String tenantId);

    /**
     * Returns all {@link RolePermissions} records for a tenant.
     */
    List<RolePermissions> listRolePermissions(String tenantId);

    /**
     * Removes all permission mappings for a tenant (e.g. on tenant deletion).
     */
    void clearPermissions(String tenantId);
}




