package com.holonplatform.multitenant;

import java.util.Optional;

/**
 * Resolves the platform role and job-specific role a user holds within a tenant.
 *
 * <p>This abstraction lives in {@code tenant-core} — the module every other module already
 * depends on — precisely so that permission-checking modules (such as
 * {@code holon-multi-tenant-permissions}) do not need a hard compile dependency on the
 * concrete user-management module ({@code holon-multi-tenant-users}). Without this
 * indirection, {@code holon-multi-tenant-users} could never depend back on
 * {@code holon-multi-tenant-permissions} (e.g. to use {@code @RequiresPermission}) without
 * creating a circular module dependency.
 *
 * <p>{@code holon-multi-tenant-users} auto-registers an implementation of this interface
 * (adapting its own {@code TenantUserService}) whenever it is on the classpath, so any
 * module that only depends on {@code tenant-core} can still resolve "what role does this
 * user hold in this tenant" without knowing about the concrete user module at all.
 */
public interface TenantUserRoleResolver {

    /**
     * Standard key used to store a user's job-specific role in a tenant user's attributes
     * (e.g. {@code "ACCOUNTANT"}, {@code "DRIVER"}), distinct from the coarser platform role
     * (e.g. {@code "ADMIN"}, {@code "MEMBER"}).
     */
    String JOB_ROLE_ATTRIBUTE = "job_role";

    /**
     * Resolves the roles held by the given user within the given tenant.
     *
     * @param tenantId the tenant identifier
     * @param email    the user's email / principal name
     * @return the resolved role information, or empty if the user is unknown in this tenant
     */
    Optional<ResolvedTenantUserRole> resolve(String tenantId, String email);

    /**
     * The platform role and optional job-specific role held by a user within a tenant.
     *
     * @param tenantId     the tenant identifier
     * @param platformRole the platform role name (e.g. {@code "ADMIN"}, {@code "MEMBER"}) — never {@code null}
     * @param jobRole      the job-specific role attribute, if assigned (e.g. {@code "ACCOUNTANT"}), or {@code null}
     */
    record ResolvedTenantUserRole(String tenantId, String platformRole, String jobRole) {

        /**
         * Returns the job role if present and non-blank, otherwise the platform role.
         * This mirrors the "job role first, platform role fallback" convention used
         * across the permission-checking modules.
         */
        public String effectiveRole() {
            return (jobRole != null && !jobRole.isBlank()) ? jobRole : platformRole;
        }
    }
}
