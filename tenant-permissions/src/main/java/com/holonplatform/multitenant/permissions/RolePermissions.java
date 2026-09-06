package com.holonplatform.multitenant.permissions;

import java.util.Collections;
import java.util.Set;

/**
 * Represents the set of permissions granted to a named role within a specific tenant.
 *
 * @param tenantId   the tenant this mapping belongs to
 * @param roleName   the role name (e.g. "ACCOUNTANT", "DRIVER", "ADMIN")
 * @param permissions set of permission strings granted to this role
 */
public record RolePermissions(String tenantId, String roleName, Set<String> permissions) {

    public RolePermissions {
        permissions = Collections.unmodifiableSet(permissions);
    }

    /** Returns true if this role has been granted the given permission. */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /** Returns true if this role has been granted all of the given permissions. */
    public boolean hasAllPermissions(Set<String> required) {
        return permissions.containsAll(required);
    }

    /** Returns true if this role has been granted any of the given permissions. */
    public boolean hasAnyPermission(Set<String> required) {
        return required.stream().anyMatch(permissions::contains);
    }
}

