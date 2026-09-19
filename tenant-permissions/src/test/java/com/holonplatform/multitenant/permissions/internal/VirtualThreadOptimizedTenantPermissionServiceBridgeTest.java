package com.holonplatform.multitenant.permissions.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.permissions.view.TenantRoleManagementService;
import com.holonplatform.multitenant.users.TenantUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies that {@link VirtualThreadOptimizedTenantPermissionService} bridges
 * {@code "viewId:ACTION"}-shaped permission keys to {@link TenantRoleManagementService}
 * instead of the flat permission store, so {@code @RequiresPermission} and the tenant-owner
 * view/action permission matrix never disagree (the conflict this bridge was added to fix).
 */
class VirtualThreadOptimizedTenantPermissionServiceBridgeTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<TenantRoleManagementService> providerReturning(TenantRoleManagementService service) {
        ObjectProvider<TenantRoleManagementService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }

    private void authenticateAs(String tenantId, String email) {
        TenantContext.set(TenantDetails.builder(tenantId).build());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(email, "n/a", "ROLE_USER"));
    }

    @Test
    @DisplayName("'viewId:ACTION' key delegates to TenantRoleManagementService when available")
    void delegatesViewActionKeyToRoleManagementService() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserService tenantUserService = mock(TenantUserService.class);
        TenantRoleManagementService roleManagementService = mock(TenantRoleManagementService.class);
        when(roleManagementService.currentUserCanPerformAction("customer-details", "CREATE")).thenReturn(true);

        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, tenantUserService, providerReturning(roleManagementService));

        authenticateAs("acme", "alice@acme.com");

        assertThat(service.currentUserHasPermission("customer-details:CREATE")).isTrue();
        verify(roleManagementService).currentUserCanPerformAction("customer-details", "CREATE");
        verifyNoInteractions(datastoreHelper);
    }

    @Test
    @DisplayName("'viewId:ACTION' key falls back to the flat store when no TenantRoleManagementService bean exists")
    void fallsBackToFlatStoreWhenRoleManagementServiceMissing() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserService tenantUserService = mock(TenantUserService.class);
        when(tenantUserService.findByEmail(anyString(), anyString())).thenReturn(Optional.empty());

        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, tenantUserService, providerReturning(null));

        authenticateAs("acme", "alice@acme.com");

        // No user resolved (no TenantUser record) -> flat-store path returns false, never throws
        assertThat(service.currentUserHasPermission("customer-details:CREATE")).isFalse();
        verifyNoInteractions(datastoreHelper);
    }

    @Test
    @DisplayName("plain keys with no colon always use the flat permission store, never the view matrix")
    void plainKeyUsesFlatStore() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserService tenantUserService = mock(TenantUserService.class);
        when(tenantUserService.findByEmail(anyString(), anyString())).thenReturn(Optional.empty());
        TenantRoleManagementService roleManagementService = mock(TenantRoleManagementService.class);

        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, tenantUserService, providerReturning(roleManagementService));

        authenticateAs("acme", "alice@acme.com");

        assertThat(service.currentUserHasPermission("VIEW_FINANCIALS")).isFalse();
        verifyNoInteractions(roleManagementService);
    }
}
