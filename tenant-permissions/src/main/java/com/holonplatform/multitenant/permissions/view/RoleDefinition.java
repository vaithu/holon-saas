package com.holonplatform.multitenant.permissions.view;

/**
 * Descriptor of an available role in a tenant (either system baseline role or tenant custom role).
 *
 * @param roleName     unique role key (e.g. {@code "SALESMAN"}, {@code "JUNIOR_SALES"})
 * @param displayName  human-readable label
 * @param description  description of role responsibilities
 * @param isSystemRole true if this is a platform-defined baseline role
 * @param baseRole     the template role this custom role was cloned from (or null if system/scratch)
 */
public record RoleDefinition(
        String roleName,
        String displayName,
        String description,
        boolean isSystemRole,
        String baseRole
) {
}
