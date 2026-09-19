package com.holonplatform.multitenant.users.internal;

import com.holonplatform.multitenant.TenantUserRoleResolver;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserService;

import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link TenantUserRoleResolver} implementation, adapting {@link TenantUserService}
 * so that other modules (e.g. {@code holon-multi-tenant-permissions}) can resolve a user's
 * roles without depending on this module's concrete {@link TenantUser} / {@link TenantUserService}
 * types.
 */
public class TenantUserServiceRoleResolver implements TenantUserRoleResolver {

    private final TenantUserService tenantUserService;

    public TenantUserServiceRoleResolver(TenantUserService tenantUserService) {
        this.tenantUserService = Objects.requireNonNull(tenantUserService, "tenantUserService must not be null");
    }

    @Override
    public Optional<ResolvedTenantUserRole> resolve(String tenantId, String email) {
        return tenantUserService.findByEmail(tenantId, email)
                .map(this::toResolvedRole);
    }

    private ResolvedTenantUserRole toResolvedRole(TenantUser user) {
        String jobRole = user.getAttributes() != null ? user.getAttributes().get(JOB_ROLE_ATTRIBUTE) : null;
        return new ResolvedTenantUserRole(user.getTenantId(), user.getRole().name(), jobRole);
    }
}
