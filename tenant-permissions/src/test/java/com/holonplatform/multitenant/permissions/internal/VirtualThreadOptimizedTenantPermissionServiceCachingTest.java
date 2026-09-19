package com.holonplatform.multitenant.permissions.internal;

import com.holonplatform.auth.AuthContext;
import com.holonplatform.core.Context;
import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantUserRoleResolver;
import com.holonplatform.multitenant.TenantUserRoleResolver.ResolvedTenantUserRole;
import com.holonplatform.multitenant.permissions.view.TenantRoleManagementService;
import com.holonplatform.spring.security.SpringSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VirtualThreadOptimizedTenantPermissionService}'s user resolution
 * caching, now backed by Spring's {@link Cache} abstraction instead of a raw Caffeine field.
 *
 * <p>{@link VirtualThreadOptimizedTenantPermissionService} resolves the current user through
 * Holon's {@link AuthContext}, not directly through Spring Security's {@code SecurityContextHolder}.
 * These tests publish a {@code SpringSecurity}-backed {@code AuthContext} into the Holon
 * classloader {@link Context} scope — the same bridge production applications register via
 * {@code tenant-security}'s {@code SpringSecurityAuthContextRegistrar} — so that authenticating
 * through {@code SecurityContextHolder} is visible to {@code AuthContext.getCurrent()}.
 */
class VirtualThreadOptimizedTenantPermissionServiceCachingTest {

    @BeforeEach
    void bindAuthContext() {
        Context.get().classLoaderScope()
                .orElseThrow()
                .put(AuthContext.CONTEXT_KEY, SpringSecurity.authContext());
    }

    @AfterEach
    void cleanup() {
        Context.get().classLoaderScope().ifPresent(scope -> scope.remove(AuthContext.CONTEXT_KEY));
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<TenantRoleManagementService> emptyProvider() {
        ObjectProvider<TenantRoleManagementService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private void authenticateAs(String tenantId, String email) {
        TenantContext.set(TenantDetails.builder(tenantId).build());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(email, "n/a", "ROLE_USER"));
    }

    @Test
    @DisplayName("default Caffeine-backed cache avoids re-resolving the same user within the TTL")
    void defaultCache_avoidsRedundantResolution() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);
        AtomicInteger resolutions = new AtomicInteger();
        when(roleResolver.resolve("acme", "alice@acme.com")).thenAnswer(inv -> {
            resolutions.incrementAndGet();
            return Optional.of(new ResolvedTenantUserRole("acme", "MEMBER", "ADMIN"));
        });
        when(datastoreHelper.findByTenantIdAndRoleNameAndPermission("acme", "ADMIN", "*"))
                .thenReturn(Optional.empty());
        when(datastoreHelper.findByTenantIdAndRoleNameAndPermission("acme", "ADMIN", "VIEW_FINANCIALS"))
                .thenReturn(Optional.of(new PermissionEntity("acme", "ADMIN", "VIEW_FINANCIALS")));

        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, roleResolver, emptyProvider());

        authenticateAs("acme", "alice@acme.com");

        assertThat(service.currentUserHasPermission("VIEW_FINANCIALS")).isTrue();
        assertThat(service.currentUserHasPermission("VIEW_FINANCIALS")).isTrue();
        assertThat(service.currentUserHasPermission("VIEW_FINANCIALS")).isTrue();

        assertThat(resolutions.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("getCacheStats() reports real Caffeine stats for the default cache")
    void getCacheStats_reportsRealStatsForDefaultCache() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);
        when(roleResolver.resolve("acme", "alice@acme.com"))
                .thenReturn(Optional.of(new ResolvedTenantUserRole("acme", "MEMBER", "ADMIN")));
        when(datastoreHelper.findByTenantIdAndRoleNameAndPermission("acme", "ADMIN", "*"))
                .thenReturn(Optional.empty());
        when(datastoreHelper.findByTenantIdAndRoleNameAndPermission("acme", "ADMIN", "X"))
                .thenReturn(Optional.empty());

        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, roleResolver, emptyProvider());

        authenticateAs("acme", "alice@acme.com");
        service.currentUserHasPermission("X");
        service.currentUserHasPermission("X");

        assertThat(service.getCacheStats().requestCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("getCacheStats() returns an empty snapshot for a non-Caffeine Cache")
    void getCacheStats_isEmptyForNonCaffeineCache() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);

        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, roleResolver, emptyProvider(), new ConcurrentMapCache("userResolution"));

        assertThat(service.getCacheStats().requestCount()).isZero();
    }

    @Test
    @DisplayName("clearPermissions evicts only the affected tenant's cached users (Caffeine-backed cache)")
    void clearPermissions_evictsOnlyAffectedTenant() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);
        AtomicInteger acmeResolutions = new AtomicInteger();
        AtomicInteger umbrellaResolutions = new AtomicInteger();
        when(roleResolver.resolve("acme", "alice@acme.com")).thenAnswer(inv -> {
            acmeResolutions.incrementAndGet();
            return Optional.of(new ResolvedTenantUserRole("acme", "MEMBER", "ADMIN"));
        });
        when(roleResolver.resolve("umbrella", "bob@umbrella.com")).thenAnswer(inv -> {
            umbrellaResolutions.incrementAndGet();
            return Optional.of(new ResolvedTenantUserRole("umbrella", "MEMBER", "ADMIN"));
        });
        when(datastoreHelper.findByTenantIdAndRoleNameAndPermission(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("ADMIN"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, roleResolver, emptyProvider());

        authenticateAs("acme", "alice@acme.com");
        service.currentUserHasPermission("X");
        authenticateAs("umbrella", "bob@umbrella.com");
        service.currentUserHasPermission("X");

        service.clearPermissions("acme");

        authenticateAs("acme", "alice@acme.com");
        service.currentUserHasPermission("X");
        authenticateAs("umbrella", "bob@umbrella.com");
        service.currentUserHasPermission("X");

        assertThat(acmeResolutions.get()).isEqualTo(2); // evicted -> re-resolved
        assertThat(umbrellaResolutions.get()).isEqualTo(1); // untouched -> still cached
    }

    @Test
    @DisplayName("clearCache() empties the cache regardless of tenant")
    void clearCache_emptiesEverything() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);
        AtomicInteger resolutions = new AtomicInteger();
        when(roleResolver.resolve("acme", "alice@acme.com")).thenAnswer(inv -> {
            resolutions.incrementAndGet();
            return Optional.of(new ResolvedTenantUserRole("acme", "MEMBER", "ADMIN"));
        });
        when(datastoreHelper.findByTenantIdAndRoleNameAndPermission(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("ADMIN"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, roleResolver, emptyProvider());

        authenticateAs("acme", "alice@acme.com");
        service.currentUserHasPermission("X");
        service.clearCache();
        service.currentUserHasPermission("X");

        assertThat(resolutions.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("a custom Cache supplied via the 4-arg constructor is used instead of the default")
    void customCache_isUsedWhenSupplied() {
        TenantPermissionDatastoreHelper datastoreHelper = mock(TenantPermissionDatastoreHelper.class);
        TenantUserRoleResolver roleResolver = mock(TenantUserRoleResolver.class);
        when(roleResolver.resolve("acme", "alice@acme.com"))
                .thenReturn(Optional.of(new ResolvedTenantUserRole("acme", "MEMBER", "ADMIN")));
        when(datastoreHelper.findByTenantIdAndRoleNameAndPermission("acme", "ADMIN", "*"))
                .thenReturn(Optional.empty());
        when(datastoreHelper.findByTenantIdAndRoleNameAndPermission("acme", "ADMIN", "X"))
                .thenReturn(Optional.empty());

        Cache customCache = new ConcurrentMapCache("custom");
        VirtualThreadOptimizedTenantPermissionService service = new VirtualThreadOptimizedTenantPermissionService(
                datastoreHelper, roleResolver, emptyProvider(), customCache);

        authenticateAs("acme", "alice@acme.com");
        service.currentUserHasPermission("X");

        assertThat(customCache.get("acme|alice@acme.com")).isNotNull();
    }
}
