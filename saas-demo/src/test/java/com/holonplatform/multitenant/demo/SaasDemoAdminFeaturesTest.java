package com.holonplatform.multitenant.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;

import com.holonplatform.multitenant.TenantContextTemplate;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.billing.Invoice;
import com.holonplatform.multitenant.billing.Subscription;
import com.holonplatform.multitenant.billing.SubscriptionStatus;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.demo.service.DemoTenantProvisioner;
import com.holonplatform.multitenant.demo.service.PlatformConsoleService;
import com.holonplatform.multitenant.demo.service.TenantBinding;
import com.holonplatform.multitenant.demo.support.TestWorkspaceIds;
import com.holonplatform.multitenant.permissions.view.RoleDefinition;
import com.holonplatform.multitenant.permissions.view.RoleMatrixItem;
import com.holonplatform.multitenant.permissions.view.TenantRoleManagementService;
import com.holonplatform.multitenant.permissions.view.ViewAction;
import com.holonplatform.multitenant.permissions.view.ViewRegistry;

/**
 * Batch three of the demo: cross-tenant administration, suspension, billing, custom roles and
 * the view registry (F13–F17).
 *
 * <p>Two workspaces are created for every test, because most of what this batch claims is only
 * meaningful as a comparison: suspending one workspace must leave the other untouched, and an
 * override written for one tenant's SALESMAN must not follow the role into the other tenant.
 * A single-tenant test would pass for all the wrong reasons.
 */
@SpringBootTest
class SaasDemoAdminFeaturesTest {

    @Autowired private DemoTenantProvisioner       provisioner;
    @Autowired private PlatformConsoleService      console;
    @Autowired private TenantRoleManagementService roleService;
    @Autowired private ViewRegistry                viewRegistry;

    private TenantDetails first;
    private TenantDetails second;

    @BeforeEach
    void signUpTwoWorkspaces() {
        String firstId  = TestWorkspaceIds.uniqueWorkspaceId("b3a");
        String secondId = TestWorkspaceIds.uniqueWorkspaceId("b3b");
        first  = provisioner.signUpWorkspace(firstId, "Acme Ltd",
                TestWorkspaceIds.ownerEmail(firstId), "owner-secret");
        second = provisioner.signUpWorkspace(secondId, "Globex Ltd",
                TestWorkspaceIds.ownerEmail(secondId), "owner-secret");
    }

    @AfterEach
    void clearState() {
        SecurityContextHolder.clearContext();
    }

    // ── F13: cross-tenant listing and search ───────────────────────────────────────────

    @Test
    void theOperatorSeesEveryWorkspaceAndCanSearchAcrossThem() {
        assertThat(console.count("")).isGreaterThanOrEqualTo(2);

        // The search term matches on id, and must reach a workspace the caller has no
        // membership of — which is the whole point of a control-plane query.
        List<String> found = console.list(0, 50, second.tenantId()).stream()
                .map(TenantDetails::tenantId)
                .toList();
        assertThat(found).contains(second.tenantId()).doesNotContain(first.tenantId());
    }

    @Test
    void theSliceApiPagesWithoutNeedingATotal() {
        List<TenantDetails> page = console.list(0, 1, "");
        assertThat(page).hasSize(1);

        // Counting is a separate, explicit call — the grid never forces it.
        assertThat(console.count("")).isGreaterThan(page.size());
    }

    @Test
    void statusAndPlanTilesCountAcrossTenants() {
        long activeBefore = console.countByStatus(TenantStatus.ACTIVE);
        console.suspend(first.tenantId());

        assertThat(console.countByStatus(TenantStatus.ACTIVE)).isEqualTo(activeBefore - 1);
        assertThat(console.countByStatus(TenantStatus.SUSPENDED)).isGreaterThanOrEqualTo(1);
        assertThat(console.countByPlan(TenantPlan.FREE)).isGreaterThanOrEqualTo(2);
    }

    // ── F14: suspension ────────────────────────────────────────────────────────────────

    @Test
    void suspendingOneWorkspaceLeavesEveryOtherWorkspaceUntouched() {
        console.suspend(first.tenantId());

        assertThat(statusOf(first.tenantId())).isEqualTo(TenantStatus.SUSPENDED);
        assertThat(statusOf(second.tenantId())).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void suspensionIsReversible() {
        console.suspend(first.tenantId());
        assertThat(statusOf(first.tenantId())).isEqualTo(TenantStatus.SUSPENDED);

        console.activate(first.tenantId());
        assertThat(statusOf(first.tenantId())).isEqualTo(TenantStatus.ACTIVE);
    }

    // ── F15: subscriptions and invoices ────────────────────────────────────────────────

    @Test
    void aSubscriptionIsOpenedOnFirstSightAndSeededFromTheTenantPlan() {
        Subscription subscription = console.subscriptionOf(first.tenantId());

        assertThat(subscription.tenantId()).isEqualTo(first.tenantId());
        assertThat(subscription.plan()).isEqualTo(first.plan());
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);

        // Asking twice must not open a second subscription. Compare at millisecond precision:
        // the first call returns the freshly built instance (nanoseconds), the second reads it
        // back from H2, which stores microseconds.
        assertThat(console.subscriptionOf(first.tenantId()).periodStart().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(subscription.periodStart().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void changingPlanRaisesAnInvoiceAndUpdatesTheTenantRegistry() {
        console.changePlan(first.tenantId(), TenantPlan.PROFESSIONAL);

        assertThat(console.subscriptionOf(first.tenantId()).plan()).isEqualTo(TenantPlan.PROFESSIONAL);

        // The registry copy is what @RequiresPlan consults, so it has to agree with billing.
        assertThat(planOf(first.tenantId())).isEqualTo(TenantPlan.PROFESSIONAL);

        List<Invoice> invoices = console.invoicesOf(first.tenantId(), 0, 10);
        assertThat(invoices).hasSize(1);
        assertThat(invoices.getFirst().amountDue())
                .isEqualByComparingTo(PlatformConsoleService.priceOf(TenantPlan.PROFESSIONAL));
        assertThat(console.countInvoices(first.tenantId())).isEqualTo(1);
    }

    @Test
    void invoicesAreHeldPerWorkspaceNotInASharedTable() {
        console.changePlan(first.tenantId(), TenantPlan.STARTER);

        // Billing tables live inside each tenant's own schema, so the second workspace must be
        // entirely unaware of the first one's invoice.
        assertThat(console.countInvoices(first.tenantId())).isEqualTo(1);
        assertThat(console.countInvoices(second.tenantId())).isZero();
    }

    @Test
    void cancellingLeavesTheWorkspaceRunningUntilThePeriodEnds() {
        console.subscriptionOf(first.tenantId());
        Subscription cancelled = console.cancelSubscription(first.tenantId());

        assertThat(cancelled.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        // Cancelling bills; it does not evict. The tenant stays active.
        assertThat(statusOf(first.tenantId())).isEqualTo(TenantStatus.ACTIVE);
    }

    // ── F16: custom roles and the permission matrix ────────────────────────────────────
    //
    // The role service reads and writes tenant_permission_overrides and tenant_custom_roles,
    // which live inside the tenant's own schema — so every call below has to run with that
    // tenant bound. In the application this is automatic (the request filter binds it); in a
    // test it must be stated. Without it the queries reach the default schema and fail with
    // "Table TENANT_PERMISSION_OVERRIDES not found".

    @Test
    void theBaselineDeniesSalesmenExportUntilTheWorkspaceGrantsIt() {
        TenantContextTemplate.run(first, () -> {
            String tenantId = first.tenantId();

            assertThat(roleService.isActionPermitted(tenantId, DemoRoles.SALESMAN, "sales", ViewAction.EXPORT))
                    .isFalse();

            roleService.setActionOverride(tenantId, DemoRoles.SALESMAN, "sales", ViewAction.EXPORT, true);

            assertThat(roleService.isActionPermitted(tenantId, DemoRoles.SALESMAN, "sales", ViewAction.EXPORT))
                    .isTrue();
        });
    }

    @Test
    void anOverrideAppliesToOneWorkspaceOnly() {
        TenantContextTemplate.run(first, () ->
                roleService.setActionOverride(first.tenantId(), DemoRoles.SALESMAN, "sales",
                        ViewAction.EXPORT, true));

        TenantContextTemplate.run(first, () ->
                assertThat(roleService.isActionPermitted(first.tenantId(), DemoRoles.SALESMAN,
                        "sales", ViewAction.EXPORT)).isTrue());

        // The role name is shared; the meaning of the role is not.
        TenantContextTemplate.run(second, () ->
                assertThat(roleService.isActionPermitted(second.tenantId(), DemoRoles.SALESMAN,
                        "sales", ViewAction.EXPORT)).isFalse());
    }

    @Test
    void resettingARoleRestoresTheBaseline() {
        TenantContextTemplate.run(first, () -> {
            String tenantId = first.tenantId();
            roleService.setActionOverride(tenantId, DemoRoles.SALESMAN, "sales", ViewAction.EXPORT, true);

            roleService.resetRoleToDefault(tenantId, DemoRoles.SALESMAN);

            assertThat(roleService.isActionPermitted(tenantId, DemoRoles.SALESMAN, "sales", ViewAction.EXPORT))
                    .isFalse();
            // What the baseline does grant must survive the reset.
            assertThat(roleService.isActionPermitted(tenantId, DemoRoles.SALESMAN, "sales", ViewAction.READ))
                    .isTrue();
        });
    }

    @Test
    void aCustomRoleInheritsFromItsBaseAndCannotShadowASystemRole() {
        String tenantId = first.tenantId();

        TenantContextTemplate.run(first, () -> {
            RoleDefinition junior = roleService.createCustomRole(tenantId, "junior_salesman",
                    "Junior salesman", "Learning the ropes", DemoRoles.SALESMAN);

            assertThat(junior.roleName()).isEqualTo("JUNIOR_SALESMAN");
            assertThat(junior.isSystemRole()).isFalse();
            assertThat(roleService.getRoles(tenantId)).extracting(RoleDefinition::roleName)
                    .contains("JUNIOR_SALESMAN");

            // Inherited from SALESMAN, without a single override of its own.
            assertThat(roleService.isActionPermitted(tenantId, "JUNIOR_SALESMAN", "sales", ViewAction.READ))
                    .isTrue();
            assertThat(roleService.isActionPermitted(tenantId, "JUNIOR_SALESMAN", "sales", ViewAction.EXPORT))
                    .isFalse();

            assertThatThrownBy(() -> roleService.createCustomRole(tenantId, DemoRoles.SALESMAN,
                    "Clash", "Should be refused", null))
                    .isInstanceOf(IllegalArgumentException.class);
        });

        // And the custom role stays local to the workspace that created it.
        TenantContextTemplate.run(second, () ->
                assertThat(roleService.getRoles(second.tenantId())).extracting(RoleDefinition::roleName)
                        .doesNotContain("JUNIOR_SALESMAN"));
    }

    // ── F17: the view registry drives the matrix ───────────────────────────────────────

    @Test
    void everyDemoViewIsRegisteredWithItsDeclaredActions() {
        assertThat(viewRegistry.getAllViews()).extracting("viewId")
                .contains("dashboard", "team", "sales", "accounting", "settings", "audit", "plan", "roles");

        assertThat(viewRegistry.getView("sales")).isPresent().get()
                .extracting("supportedActions")
                .isEqualTo(java.util.Set.of(ViewAction.READ, ViewAction.CREATE, ViewAction.EXPORT));
    }

    @Test
    void theMatrixIsBuiltFromTheRegisteredViews() {
        TenantContextTemplate.run(first, () -> {
            List<RoleMatrixItem> matrix = roleService.getRoleMatrix(first.tenantId(), DemoRoles.SALESMAN);

            // One row per registered view/action pair — so the registry defines the vocabulary
            // the matrix can even express.
            int expectedRows = viewRegistry.getAllViews().stream()
                    .mapToInt(view -> view.supportedActions().size())
                    .sum();
            assertThat(matrix).hasSize(expectedRows);

            assertThat(matrix).anySatisfy(item -> {
                assertThat(item.viewId()).isEqualTo("sales");
                assertThat(item.action()).isEqualTo(ViewAction.READ);
                assertThat(item.allowed()).isTrue();
                assertThat(item.customized()).isFalse();
            });
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────

    private TenantStatus statusOf(String tenantId) {
        return TenantBinding.onControlPlane(() -> console.list(0, 50, tenantId)).stream()
                .filter(tenant -> tenant.tenantId().equals(tenantId))
                .findFirst()
                .orElseThrow()
                .status();
    }

    private TenantPlan planOf(String tenantId) {
        return TenantBinding.onControlPlane(() -> console.list(0, 50, tenantId)).stream()
                .filter(tenant -> tenant.tenantId().equals(tenantId))
                .findFirst()
                .orElseThrow()
                .plan();
    }
}
