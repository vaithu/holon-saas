package com.holonplatform.multitenant.permissions;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantUserRoleResolver;
import com.holonplatform.multitenant.permissions.internal.TenantRoleManagementDatastoreHelper;
import com.holonplatform.multitenant.permissions.view.*;
import com.holonplatform.multitenant.permissions.view.internal.DefaultSystemDefaultPermissionRegistry;
import com.holonplatform.multitenant.permissions.view.internal.DefaultTenantRoleManagementService;
import com.holonplatform.multitenant.permissions.view.internal.DefaultViewRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TenantDynamicAuthorizationTest {

    private Datastore datastore;
    private TenantRoleManagementDatastoreHelper datastoreHelper;
    private DefaultViewRegistry viewRegistry;
    private DefaultSystemDefaultPermissionRegistry systemDefaultRegistry;
    private TenantUserRoleResolver tenantUserRoleResolver;
    private DefaultTenantRoleManagementService roleManagementService;
    private TenantViewAccessChecker accessChecker;
    private UIPermissionHelper uiPermissionHelper;

    private static final String TENANT_ID = "tenant-acme";
    private static final String USER_EMAIL = "john.sales@acme.com";

    @BeforeEach
    void setUp() {
        datastore = Mockito.mock(Datastore.class, Mockito.RETURNS_DEEP_STUBS);
        datastoreHelper = Mockito.mock(TenantRoleManagementDatastoreHelper.class);
        viewRegistry = new DefaultViewRegistry();
        systemDefaultRegistry = new DefaultSystemDefaultPermissionRegistry();
        tenantUserRoleResolver = Mockito.mock(TenantUserRoleResolver.class);

        // Register sample views (100+ simulated)
        viewRegistry.register(ViewResource.builder("customer-details")
                .displayName("Customer Details")
                .category("CRM")
                .supportedActions(ViewAction.READ, ViewAction.UPDATE, ViewAction.EXPORT)
                .build());

        viewRegistry.register(ViewResource.builder("customer-creation")
                .displayName("Customer Creation")
                .category("CRM")
                .supportedActions(ViewAction.READ, ViewAction.CREATE)
                .build());

        viewRegistry.register(ViewResource.builder("invoices")
                .displayName("Invoices")
                .category("Billing")
                .supportedActions(ViewAction.READ, ViewAction.CREATE, ViewAction.UPDATE, ViewAction.EXPORT)
                .build());

        roleManagementService = new DefaultTenantRoleManagementService(
                datastoreHelper, viewRegistry, systemDefaultRegistry, tenantUserRoleResolver);
        accessChecker = new TenantViewAccessChecker(roleManagementService);
        uiPermissionHelper = new UIPermissionHelper(roleManagementService);

        // Setup mock user & security context
        TenantContext.set(TenantDetails.builder(TENANT_ID).name("ACME Corp").build());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_EMAIL, "password", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        TenantUserRoleResolver.ResolvedTenantUserRole mockRole = new TenantUserRoleResolver.ResolvedTenantUserRole(
                TENANT_ID, "MEMBER", SystemRole.SALESMAN);
        when(tenantUserRoleResolver.resolve(TENANT_ID, USER_EMAIL))
                .thenReturn(Optional.of(mockRole));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Default System Baseline: Salesman can view customer details but cannot access customer creation")
    void testSystemDefaultsForSalesman() {
        // Salesman can read customer details by default
        assertTrue(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.SALESMAN, "customer-details", ViewAction.READ));
        assertTrue(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.SALESMAN, "customer-details", ViewAction.UPDATE));

        // Salesman cannot create customer by default
        assertFalse(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.SALESMAN, "customer-creation", ViewAction.CREATE));
        assertFalse(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.SALESMAN, "customer-creation", ViewAction.READ));

        // Current user check
        assertTrue(roleManagementService.currentUserCanAccessView("customer-details"));
        assertFalse(roleManagementService.currentUserCanAccessView("customer-creation"));
        assertFalse(roleManagementService.currentUserCanPerformAction("customer-creation", ViewAction.CREATE));
    }

    @Test
    @DisplayName("Admin has full wildcard access across all views and actions")
    void testAdminWildcardAccess() {
        assertTrue(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.ADMIN, "customer-details", ViewAction.READ));
        assertTrue(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.ADMIN, "customer-creation", ViewAction.CREATE));
        assertTrue(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.ADMIN, "invoices", ViewAction.DELETE));
        assertTrue(roleManagementService.isViewPermitted(TENANT_ID, SystemRole.ADMIN, "any-view-id"));
    }

    @Test
    @DisplayName("Tenant Owner Customization: Enable customer-creation for Salesman via Override")
    void testTenantOwnerOverrideGrant() {
        // Simulate override in datastore
        when(datastoreHelper.findOverride(TENANT_ID, SystemRole.SALESMAN, "customer-creation", ViewAction.CREATE))
                .thenReturn(Optional.of(new com.holonplatform.multitenant.permissions.internal.TenantPermissionOverrideEntity(
                        TENANT_ID, SystemRole.SALESMAN, "customer-creation", ViewAction.CREATE, true)));
        when(datastoreHelper.findOverride(TENANT_ID, SystemRole.SALESMAN, "customer-creation", ViewAction.READ))
                .thenReturn(Optional.of(new com.holonplatform.multitenant.permissions.internal.TenantPermissionOverrideEntity(
                        TENANT_ID, SystemRole.SALESMAN, "customer-creation", ViewAction.READ, true)));

        assertTrue(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.SALESMAN, "customer-creation", ViewAction.CREATE));
        assertTrue(roleManagementService.currentUserCanPerformAction("customer-creation", ViewAction.CREATE));
        assertTrue(roleManagementService.currentUserCanAccessView("customer-creation"));
        assertTrue(accessChecker.checkAccess("customer-creation"));
    }

    @Test
    @DisplayName("Tenant Owner Customization: Disable customer details update for Salesman via Override")
    void testTenantOwnerOverrideDeny() {
        // Explicitly deny update
        when(datastoreHelper.findOverride(TENANT_ID, SystemRole.SALESMAN, "customer-details", ViewAction.UPDATE))
                .thenReturn(Optional.of(new com.holonplatform.multitenant.permissions.internal.TenantPermissionOverrideEntity(
                        TENANT_ID, SystemRole.SALESMAN, "customer-details", ViewAction.UPDATE, false)));

        assertFalse(roleManagementService.isActionPermitted(TENANT_ID, SystemRole.SALESMAN, "customer-details", ViewAction.UPDATE));
        assertFalse(roleManagementService.currentUserCanPerformAction("customer-details", ViewAction.UPDATE));
        // Read is still allowed because only update was overridden
        assertTrue(roleManagementService.currentUserCanPerformAction("customer-details", ViewAction.READ));
    }

    @Test
    @DisplayName("Role Matrix: Correctly displays customized vs inherited default status")
    void testRoleMatrixGeneration() {
        when(datastoreHelper.findOverridesByTenantAndRole(TENANT_ID, SystemRole.SALESMAN))
                .thenReturn(List.of(
                        new com.holonplatform.multitenant.permissions.internal.TenantPermissionOverrideEntity(
                                TENANT_ID, SystemRole.SALESMAN, "customer-details", ViewAction.UPDATE, false)
                ));

        List<RoleMatrixItem> matrix = roleManagementService.getRoleMatrix(TENANT_ID, SystemRole.SALESMAN);
        assertNotNull(matrix);
        assertFalse(matrix.isEmpty());

        // Find customer-details UPDATE item
        RoleMatrixItem updateItem = matrix.stream()
                .filter(i -> "customer-details".equals(i.viewId()) && ViewAction.UPDATE.equals(i.action()))
                .findFirst()
                .orElseThrow();

        assertFalse(updateItem.allowed(), "Should be denied per override");
        assertTrue(updateItem.customized(), "Should be marked customized");
        assertTrue(updateItem.inheritedDefault(), "Default was allowed");

        // Find customer-details READ item (not customized)
        RoleMatrixItem readItem = matrix.stream()
                .filter(i -> "customer-details".equals(i.viewId()) && ViewAction.READ.equals(i.action()))
                .findFirst()
                .orElseThrow();

        assertTrue(readItem.allowed());
        assertFalse(readItem.customized(), "Should be marked inherited default");
        assertTrue(readItem.inheritedDefault());
    }

    @Test
    @DisplayName("UI Permission Helper: Conditionally executes callbacks based on permissions")
    void testUIPermissionHelper() {
        boolean[] executed = new boolean[]{false, false};

        uiPermissionHelper.ifAllowed("customer-details", ViewAction.READ, () -> executed[0] = true);
        uiPermissionHelper.ifAllowed("customer-creation", ViewAction.CREATE, () -> executed[1] = true);

        assertTrue(executed[0], "Read callback should execute");
        assertFalse(executed[1], "Create callback should not execute without permission");
    }
}
