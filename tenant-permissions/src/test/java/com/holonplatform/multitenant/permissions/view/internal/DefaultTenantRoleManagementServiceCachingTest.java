package com.holonplatform.multitenant.permissions.view.internal;

import com.holonplatform.multitenant.TenantUserRoleResolver;
import com.holonplatform.multitenant.permissions.internal.TenantCustomRoleEntity;
import com.holonplatform.multitenant.permissions.internal.TenantPermissionOverrideEntity;
import com.holonplatform.multitenant.permissions.internal.TenantRoleManagementDatastoreHelper;
import com.holonplatform.multitenant.permissions.view.SystemDefaultPermissionRegistry;
import com.holonplatform.multitenant.permissions.view.ViewRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultTenantRoleManagementService}'s permission-evaluation caching,
 * now backed by Spring's {@link Cache} abstraction instead of a raw Caffeine field.
 */
class DefaultTenantRoleManagementServiceCachingTest {

    private static final String TENANT_ID = "acme";
    private static final String ROLE = "SALESMAN";
    private static final String VIEW_ID = "customer-details";
    private static final String ACTION = "READ";

    @Test
    void isActionPermitted_cachesRepeatedEvaluations() {
        TenantRoleManagementDatastoreHelper datastoreHelper = mock(TenantRoleManagementDatastoreHelper.class);
        ViewRegistry viewRegistry = mock(ViewRegistry.class);
        SystemDefaultPermissionRegistry defaultPermissionRegistry = mock(SystemDefaultPermissionRegistry.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);

        when(datastoreHelper.findOverride(TENANT_ID, ROLE, VIEW_ID, ACTION)).thenReturn(Optional.empty());
        when(datastoreHelper.findCustomRole(TENANT_ID, ROLE)).thenReturn(Optional.empty());
        when(defaultPermissionRegistry.isDefaultAllowed(ROLE, VIEW_ID, ACTION)).thenReturn(true);

        DefaultTenantRoleManagementService service = new DefaultTenantRoleManagementService(
                datastoreHelper, viewRegistry, defaultPermissionRegistry, roleResolver);

        assertThat(service.isActionPermitted(TENANT_ID, ROLE, VIEW_ID, ACTION)).isTrue();
        assertThat(service.isActionPermitted(TENANT_ID, ROLE, VIEW_ID, ACTION)).isTrue();
        assertThat(service.isActionPermitted(TENANT_ID, ROLE, VIEW_ID, ACTION)).isTrue();

        // Only the first call should have actually hit the datastore; the rest are cache hits.
        verify(datastoreHelper, times(1)).findOverride(TENANT_ID, ROLE, VIEW_ID, ACTION);
        verify(datastoreHelper, times(1)).findCustomRole(TENANT_ID, ROLE);
    }

    @Test
    void setActionOverride_invalidatesOnlyTheAffectedTenantAndRole() {
        TenantRoleManagementDatastoreHelper datastoreHelper = mock(TenantRoleManagementDatastoreHelper.class);
        ViewRegistry viewRegistry = mock(ViewRegistry.class);
        SystemDefaultPermissionRegistry defaultPermissionRegistry = mock(SystemDefaultPermissionRegistry.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);

        when(datastoreHelper.findOverride(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(datastoreHelper.findCustomRole(anyString(), anyString())).thenReturn(Optional.empty());
        when(defaultPermissionRegistry.isDefaultAllowed(anyString(), anyString(), anyString())).thenReturn(false);

        DefaultTenantRoleManagementService service = new DefaultTenantRoleManagementService(
                datastoreHelper, viewRegistry, defaultPermissionRegistry, roleResolver);

        // Warm the cache for two different (tenant, role) pairs.
        service.isActionPermitted(TENANT_ID, ROLE, VIEW_ID, ACTION);
        service.isActionPermitted("umbrella", ROLE, VIEW_ID, ACTION);

        // Overriding acme/SALESMAN evicts only that entry.
        when(datastoreHelper.findOverride(TENANT_ID, ROLE, VIEW_ID, ACTION))
                .thenReturn(Optional.of(new TenantPermissionOverrideEntity(TENANT_ID, ROLE, VIEW_ID, ACTION, true)));
        service.setActionOverride(TENANT_ID, ROLE, VIEW_ID, ACTION, true);

        assertThat(service.isActionPermitted(TENANT_ID, ROLE, VIEW_ID, ACTION)).isTrue();
        assertThat(service.isActionPermitted("umbrella", ROLE, VIEW_ID, ACTION)).isFalse();

        // acme/SALESMAN was re-evaluated (evicted); umbrella/SALESMAN stayed cached (1 lookup only).
        verify(datastoreHelper, times(2)).findOverride(TENANT_ID, ROLE, VIEW_ID, ACTION);
        verify(datastoreHelper, times(1)).findOverride("umbrella", ROLE, VIEW_ID, ACTION);
    }

    @Test
    void createCustomRole_invalidatesCacheForThatRole() {
        TenantRoleManagementDatastoreHelper datastoreHelper = mock(TenantRoleManagementDatastoreHelper.class);
        ViewRegistry viewRegistry = mock(ViewRegistry.class);
        SystemDefaultPermissionRegistry defaultPermissionRegistry = mock(SystemDefaultPermissionRegistry.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);

        String customRole = "SUPPORT_LEAD";
        when(datastoreHelper.findOverride(TENANT_ID, customRole, VIEW_ID, ACTION)).thenReturn(Optional.empty());
        when(datastoreHelper.findCustomRole(TENANT_ID, customRole)).thenReturn(Optional.empty());
        when(defaultPermissionRegistry.isDefaultAllowed(customRole, VIEW_ID, ACTION)).thenReturn(false);
        when(defaultPermissionRegistry.isSystemRole(customRole)).thenReturn(false);

        DefaultTenantRoleManagementService service = new DefaultTenantRoleManagementService(
                datastoreHelper, viewRegistry, defaultPermissionRegistry, roleResolver);

        assertThat(service.isActionPermitted(TENANT_ID, customRole, VIEW_ID, ACTION)).isFalse();

        // Custom role now gets a base role granting the action.
        when(datastoreHelper.findCustomRole(TENANT_ID, customRole))
                .thenReturn(Optional.of(new TenantCustomRoleEntity(TENANT_ID, customRole, "Support Lead", "desc", "ADMIN")));
        when(datastoreHelper.findOverride(TENANT_ID, "ADMIN", VIEW_ID, ACTION)).thenReturn(Optional.empty());
        when(defaultPermissionRegistry.isDefaultAllowed("ADMIN", VIEW_ID, ACTION)).thenReturn(true);

        service.createCustomRole(TENANT_ID, customRole, "Support Lead", "desc", "ADMIN");

        assertThat(service.isActionPermitted(TENANT_ID, customRole, VIEW_ID, ACTION)).isTrue();
    }

    @Test
    void customCache_isUsedInsteadOfDefault() {
        TenantRoleManagementDatastoreHelper datastoreHelper = mock(TenantRoleManagementDatastoreHelper.class);
        ViewRegistry viewRegistry = mock(ViewRegistry.class);
        SystemDefaultPermissionRegistry defaultPermissionRegistry = mock(SystemDefaultPermissionRegistry.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);

        when(datastoreHelper.findOverride(TENANT_ID, ROLE, VIEW_ID, ACTION)).thenReturn(Optional.empty());
        when(datastoreHelper.findCustomRole(TENANT_ID, ROLE)).thenReturn(Optional.empty());
        when(defaultPermissionRegistry.isDefaultAllowed(ROLE, VIEW_ID, ACTION)).thenReturn(true);

        Cache customCache = new ConcurrentMapCache("custom-permission-eval");
        DefaultTenantRoleManagementService service = new DefaultTenantRoleManagementService(
                datastoreHelper, viewRegistry, defaultPermissionRegistry, roleResolver, customCache);

        assertThat(service.isActionPermitted(TENANT_ID, ROLE, VIEW_ID, ACTION)).isTrue();

        assertThat(customCache.get(TENANT_ID + ":" + ROLE + ":" + VIEW_ID + ":" + ACTION)).isNotNull();
    }

    @Test
    void adminRole_shortCircuitsWithoutTouchingCacheOrDatastore() {
        TenantRoleManagementDatastoreHelper datastoreHelper = mock(TenantRoleManagementDatastoreHelper.class);
        ViewRegistry viewRegistry = mock(ViewRegistry.class);
        SystemDefaultPermissionRegistry defaultPermissionRegistry = mock(SystemDefaultPermissionRegistry.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);

        DefaultTenantRoleManagementService service = new DefaultTenantRoleManagementService(
                datastoreHelper, viewRegistry, defaultPermissionRegistry, roleResolver);

        assertThat(service.isActionPermitted(TENANT_ID, "ADMIN", VIEW_ID, ACTION)).isTrue();

        verifyNoInteractions(datastoreHelper);
    }
}
