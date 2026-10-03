package com.holonplatform.multitenant.demo.account;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.demo.config.PlatformAdmin;
import com.holonplatform.multitenant.demo.service.TenantBinding;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserStatus;
import com.holonplatform.multitenant.users.internal.TenantUserDatastoreHelper;

/**
 * Answers "which workspaces can this address sign in to?" — the lookup that replaced the
 * Workspace field on the login form.
 *
 * <h2>Why this needs three reads per workspace</h2>
 * <p>The three facts the chooser shows live in three different places, and two of them are in
 * different schemas:
 * <ul>
 *   <li>the <em>credential</em> ({@code demo_account}) is in the control plane, and is the only
 *       place that knows an address has an account in a workspace at all;</li>
 *   <li>the <em>workspace name and status</em> come from the {@code tenant} registry, also in
 *       the control plane, via the {@link TenantDetailsLoader} (so they are cached); and</li>
 *   <li>the <em>member's name and role</em> come from {@code tenant_users}, which the
 *       onboarding migration creates <strong>inside each tenant's schema</strong>. Reading it
 *       therefore means binding that tenant first — hence
 *       {@link TenantBinding#asTenant}.</li>
 * </ul>
 *
 * <p>That last read goes through the ungated {@link TenantUserDatastoreHelper} rather than
 * {@code TenantUserService}, whose {@code findByEmail} is
 * {@code @RequiresPermission({"USER_VIEW", "ADMIN"})}-gated. There is by definition no
 * authenticated user yet when this runs — the whole point is to work out who is signing in —
 * so the permission check could only ever fail. This is the same escape hatch the framework's
 * own {@code TenantUserServiceRoleResolver} takes, and for the same reason.
 *
 * <p>Nothing here checks the password. The login view verifies credentials separately and only
 * ever shows the caller the workspaces the submitted password actually opens, so this class
 * being able to enumerate an address's workspaces is not itself a disclosure.
 */
@Service
public class WorkspaceDirectory {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceDirectory.class);

    private final PlatformAccountStore        accountStore;
    private final TenantDetailsLoader         tenantDetailsLoader;
    private final TenantUserDatastoreHelper   tenantUsers;

    public WorkspaceDirectory(PlatformAccountStore accountStore,
                              TenantDetailsLoader tenantDetailsLoader,
                              TenantUserDatastoreHelper tenantUsers) {
        this.accountStore        = Objects.requireNonNull(accountStore, "accountStore must not be null");
        this.tenantDetailsLoader = Objects.requireNonNull(tenantDetailsLoader,
                "tenantDetailsLoader must not be null");
        this.tenantUsers         = Objects.requireNonNull(tenantUsers, "tenantUsers must not be null");
    }

    /**
     * Returns every workspace the address holds an account in, newest registry state first read
     * fresh so a suspension made a moment ago is reflected.
     *
     * @param email the address typed into the login form
     * @return the memberships, possibly empty; never null
     */
    public List<WorkspaceMembership> membershipsOf(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        List<WorkspaceMembership> memberships = new ArrayList<>();
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        for (String workspaceId : accountStore.findWorkspaceIds(normalized)) {
            describe(workspaceId, normalized).ifPresent(memberships::add);
        }
        return memberships;
    }

    private Optional<WorkspaceMembership> describe(String workspaceId, String email) {
        if (PlatformAdmin.WORKSPACE.equals(workspaceId)) {
            // The operator is deliberately not a tenant member, so there is no registry entry
            // and no tenant_users row to read — the console is described by hand instead.
            return Optional.of(new WorkspaceMembership(PlatformAdmin.WORKSPACE, "Platform console",
                    email, PlatformAdmin.ROLE, true));
        }

        TenantDetails tenant;
        try {
            tenant = tenantDetailsLoader.load(workspaceId);
        } catch (RuntimeException e) {
            // A credential left behind for a workspace that no longer exists is not an error
            // worth failing the whole sign-in over; it simply is not offered.
            log.debug("Skipping workspace '{}' for '{}': registry lookup failed", workspaceId, email, e);
            return Optional.empty();
        }
        if (tenant == null) {
            return Optional.empty();
        }

        String name = email;
        String role = "MEMBER";
        try {
            Optional<TenantUser> member = TenantBinding.asTenant(tenant,
                    () -> tenantUsers.findByTenantIdAndEmailIgnoreCase(workspaceId, email)
                            .filter(user -> user.getStatus() != TenantUserStatus.REMOVED));
            if (member.isPresent()) {
                name = member.get().effectiveName();
                role = member.get().getRole().name();
            }
        } catch (RuntimeException e) {
            // The membership row is presentation detail only — the credential above is what
            // decides whether this workspace can be entered — so fall back to the defaults.
            log.debug("Could not read the membership of '{}' in '{}'", email, workspaceId, e);
        }

        return Optional.of(new WorkspaceMembership(workspaceId,
                tenant.name() != null ? tenant.name() : workspaceId,
                name, role, tenant.isActive()));
    }
}
