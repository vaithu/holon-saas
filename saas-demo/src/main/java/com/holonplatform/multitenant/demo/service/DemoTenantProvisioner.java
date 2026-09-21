package com.holonplatform.multitenant.demo.service;

import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantContextTemplate;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.signup.SignupRequest;
import com.holonplatform.multitenant.signup.SignupService;
import com.holonplatform.multitenant.users.TenantRole;

/**
 * Creates a workspace and gives its roles their permissions.
 *
 * <h2>Why the permissions must be seeded</h2>
 * <p>The flat permission store is empty for a brand-new tenant, and the framework's permission
 * service grants nothing by default — not even to an OWNER. Without seeding, the very first
 * thing the new owner tries to do (open the Team view and invite someone, which goes through
 * {@code @RequiresPermission({"USER_INVITE", "ADMIN"})}) would be denied.
 *
 * <p>Permissions are stored <em>inside the tenant's own schema</em>, so every write here has to
 * happen with the tenant bound to the {@code TenantContext}; that is what
 * {@link TenantContextTemplate} does below. Seeding directly after sign-up, rather than from a
 * {@code TenantOnboardingCompletedEvent} listener, keeps the ordering obvious: by the time
 * {@link #signUpWorkspace} returns, the workspace is ready to log into.
 */
@Service
public class DemoTenantProvisioner {

    private static final Logger log = LoggerFactory.getLogger(DemoTenantProvisioner.class);

    /**
     * The owner and admin roles get a wildcard so the workspace is usable out of the box.
     * The two job roles get strictly disjoint permissions — that disjointness is the whole
     * point of the authorization demo.
     */
    private static final Set<String> SALESMAN_PERMISSIONS =
            Set.of(DemoRoles.SALES_VIEW, DemoRoles.SALES_RECORD);
    private static final Set<String> ACCOUNTANT_PERMISSIONS =
            Set.of(DemoRoles.ACCOUNTING_VIEW, DemoRoles.ACCOUNTING_POST);
    private final SignupService           signupService;
    private final TenantPermissionService permissionService;

    public DemoTenantProvisioner(SignupService signupService, TenantPermissionService permissionService) {
        this.signupService     = Objects.requireNonNull(signupService, "signupService must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
    }

    /**
     * Runs the framework sign-up flow (validate → provision schema → create the owner's
     * membership → store credentials) and then seeds the new tenant's role permissions.
     *
     * @return the provisioned tenant
     */
    public TenantDetails signUpWorkspace(String workspaceId, String companyName,
                                         String ownerEmail, String rawPassword) {

        TenantDetails tenant = signupService.signup(SignupRequest
                .builder(workspaceId, ownerEmail, rawPassword)
                .companyName(companyName)
                .build());

        seedRolePermissions(tenant);
        return tenant;
    }

    /**
     * Writes the demo's role → permission mapping into the tenant's own schema.
     *
     * <p>Role names are matched against the user's job role first and their platform role
     * second, so {@code OWNER}/{@code MEMBER} here are {@link TenantRole} names while
     * {@code SALESMAN}/{@code ACCOUNTANT} are job roles carried on the user's
     * {@code job_role} attribute.
     */
    public void seedRolePermissions(TenantDetails tenant) {
        TenantContextTemplate.run(tenant, () -> {
            String tenantId = tenant.tenantId();

            // The owner (and any future admin) can do everything in their own workspace.
            permissionService.setPermissions(tenantId, TenantRole.OWNER.name(), Set.of("*"));
            permissionService.setPermissions(tenantId, TenantRole.ADMIN.name(), Set.of("*"));

            // A plain MEMBER has no permissions of its own: everything an invited user may do
            // comes from their job role, which keeps the two job roles cleanly separated.
            permissionService.setPermissions(tenantId, TenantRole.MEMBER.name(), Set.of());

            permissionService.setPermissions(tenantId, DemoRoles.SALESMAN, SALESMAN_PERMISSIONS);
            permissionService.setPermissions(tenantId, DemoRoles.ACCOUNTANT, ACCOUNTANT_PERMISSIONS);

            log.info("Seeded demo role permissions for tenant '{}'", tenantId);
        });
    }
}
