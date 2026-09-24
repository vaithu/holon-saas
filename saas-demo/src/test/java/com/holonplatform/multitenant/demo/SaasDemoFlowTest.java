package com.holonplatform.multitenant.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.holonplatform.multitenant.TenantContextTemplate;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.demo.service.AccountingService;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.demo.service.DemoTenantProvisioner;
import com.holonplatform.multitenant.demo.service.SalesService;
import com.holonplatform.multitenant.demo.support.TestWorkspaceIds;
import com.holonplatform.multitenant.invitations.InvitationRequest;
import com.holonplatform.multitenant.invitations.TenantInvitationService;
import com.holonplatform.multitenant.invitations.UserInvitation;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.permissions.exception.PermissionDeniedException;
import com.holonplatform.multitenant.users.TenantRole;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserService;

/**
 * Walks the seven scenarios the demo exists to show, against a real H2 database and the real
 * framework auto-configuration — sign-up, login, invite, accept, and the two directions of
 * permission asymmetry.
 *
 * <p>The Vaadin views are deliberately not exercised here. They are thin adapters over the
 * services asserted below, so testing at this level verifies the behaviour that actually matters
 * without the cost and flakiness of driving a browser.
 */
@SpringBootTest
class SaasDemoFlowTest {

    @Autowired private DemoTenantProvisioner   provisioner;
    @Autowired private TenantInvitationService invitationService;
    @Autowired private TenantUserService       userService;
    @Autowired private TenantPermissionService permissionService;
    @Autowired private SalesService            salesService;
    @Autowired private AccountingService       accountingService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerInvitesTwoUsersWhoThenSeeDifferentViewsAndMethods() {

        // 1. Tenant sign-up: provisions the tenant_<id> schema and the owner's account.
        // Only the workspace ID has to be unique: accounts are scoped to a workspace, so a fresh
        // workspace always starts out with no members and the e-mails below can stay fixed.
        String workspace = TestWorkspaceIds.uniqueWorkspaceId("acme");
        TenantDetails tenant = provisioner.signUpWorkspace(
                workspace, "Acme Corp", "owner@acme.test", "owner-secret");

        assertThat(tenant.tenantId()).isEqualTo(workspace);

        // 2. The owner is a real member of the workspace and holds the wildcard permission.
        authenticateAs("owner@acme.test");
        TenantContextTemplate.run(tenant, () -> {
            assertThat(permissionService.currentUserHasPermission(DemoRoles.USER_INVITE)).isTrue();

            // 3. The owner invites a salesman and an accountant.
            invite(workspace, "alice@acme.test", DemoRoles.SALESMAN);
            invite(workspace, "bob@acme.test", DemoRoles.ACCOUNTANT);
        });

        // 4. Both invitees accept, choosing their own password.
        String aliceToken = pendingTokenFor(tenant, "alice@acme.test");
        String bobToken   = pendingTokenFor(tenant, "bob@acme.test");

        SecurityContextHolder.clearContext();
        TenantContextTemplate.run(tenant, () -> {
            invitationService.accept(aliceToken, "alice-secret");
            invitationService.accept(bobToken, "bob-secret");
        });

        TenantContextTemplate.run(tenant, () -> {
            List<TenantUser> members = authenticatedAs("owner@acme.test",
                    () -> userService.findByTenant(workspace, 0, 100));
            assertThat(members).extracting(TenantUser::getEmail)
                    .contains("owner@acme.test", "alice@acme.test", "bob@acme.test");
        });

        // 5/6. Alice sees Sales only; her job role grants SALES_* and nothing else.
        authenticateAs("alice@acme.test");
        TenantContextTemplate.run(tenant, () -> {
            assertThat(permissionService.currentUserHasPermission(DemoRoles.SALES_VIEW)).isTrue();
            assertThat(permissionService.currentUserHasPermission(DemoRoles.ACCOUNTING_VIEW)).isFalse();

            // 7. Method-level: Alice may record a sale but may not post to the ledger.
            assertThatCode(() -> salesService.recordSale("Globex", 100)).doesNotThrowAnyException();
            assertThatThrownBy(() -> accountingService.postLedgerEntry("4000", 100))
                    .isInstanceOf(PermissionDeniedException.class);
        });

        // The mirror image for Bob, proving the asymmetry runs in both directions.
        SecurityContextHolder.clearContext();
        authenticateAs("bob@acme.test");
        TenantContextTemplate.run(tenant, () -> {
            assertThat(permissionService.currentUserHasPermission(DemoRoles.ACCOUNTING_VIEW)).isTrue();
            assertThat(permissionService.currentUserHasPermission(DemoRoles.SALES_VIEW)).isFalse();

            assertThatCode(() -> accountingService.postLedgerEntry("4000", 250)).doesNotThrowAnyException();
            assertThatThrownBy(() -> salesService.recordSale("Globex", 250))
                    .isInstanceOf(PermissionDeniedException.class);
        });
    }

    private void invite(String tenantId, String email, String jobRole) {
        invitationService.invite(InvitationRequest.builder(tenantId, email)
                .platformRole(TenantRole.MEMBER)
                .jobRole(jobRole)
                .build());
    }

    private String pendingTokenFor(TenantDetails tenant, String email) {
        return authenticatedAs("owner@acme.test", () -> TenantContextTemplate.call(tenant,
                () -> invitationService.findByEmail(tenant.tenantId(), email).stream()
                        .filter(UserInvitation::isPending)
                        .map(UserInvitation::token)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No pending invitation for " + email))));
    }

    private static void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
    }

    private static <T> T authenticatedAs(String email, java.util.function.Supplier<T> action) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        authenticateAs(email);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
