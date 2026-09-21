package com.holonplatform.multitenant.users.internal;

import com.holonplatform.multitenant.TenantUserRoleResolver;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserStatus;

import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link TenantUserRoleResolver} implementation, exposing a user's platform and job
 * roles so that other modules (e.g. {@code holon-multi-tenant-permissions}) can resolve them
 * without depending on this module's concrete {@link TenantUser} type.
 *
 * <h2>Why this reads through the datastore helper rather than {@code TenantUserService}</h2>
 * <p>This resolver is infrastructure <em>for</em> authorization, so it cannot itself require
 * authorization. {@code TenantUserService.findByEmail(...)} is
 * {@code @RequiresPermission({"USER_VIEW", "ADMIN"})}-gated, and evaluating that permission
 * requires resolving the very roles this method is being asked to produce. Going through the
 * service therefore made every permission check re-enter itself, surfacing as
 * {@code IllegalStateException: Recursive update} from the permission cache and making the
 * first authorization attempt for any user fail outright.
 *
 * <p>The ungated {@link TenantUserDatastoreHelper} breaks that cycle. Reading a membership row
 * to determine what its owner is allowed to do is a privileged internal operation, not a
 * user-facing query, so it is correct for it not to be permission-checked.
 */
public class TenantUserServiceRoleResolver implements TenantUserRoleResolver {

    private final TenantUserDatastoreHelper datastoreHelper;

    public TenantUserServiceRoleResolver(TenantUserDatastoreHelper datastoreHelper) {
        this.datastoreHelper = Objects.requireNonNull(datastoreHelper, "datastoreHelper must not be null");
    }

    @Override
    public Optional<ResolvedTenantUserRole> resolve(String tenantId, String email) {
        return datastoreHelper.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                .filter(user -> user.getStatus() != TenantUserStatus.REMOVED)
                .map(this::toResolvedRole);
    }

    private ResolvedTenantUserRole toResolvedRole(TenantUser user) {
        String jobRole = user.getAttributes() != null ? user.getAttributes().get(JOB_ROLE_ATTRIBUTE) : null;
        return new ResolvedTenantUserRole(user.getTenantId(), user.getRole().name(), jobRole);
    }
}