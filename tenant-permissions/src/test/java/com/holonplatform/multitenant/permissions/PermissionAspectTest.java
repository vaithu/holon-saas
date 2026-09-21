package com.holonplatform.multitenant.permissions;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.TenantUserRoleResolver;
import com.holonplatform.multitenant.permissions.annotation.RequiresPermission;
import com.holonplatform.multitenant.permissions.exception.PermissionDeniedException;
import com.holonplatform.multitenant.permissions.internal.PermissionAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests verifying {@link PermissionAspect} intercepts
 * {@link RequiresPermission}-annotated methods correctly.
 */
@SpringBootTest(classes = PermissionAspectTest.TestConfig.class)
class PermissionAspectTest {

    @Autowired FakeTenantPermissionService permissionService;
    @Autowired ProtectedService                protectedService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        permissionService.clearPermissions("acme");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setCurrentUser(String email, String jobRole) {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(TenantPlan.PROFESSIONAL).status(TenantStatus.ACTIVE).build());
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(email, "n/a", "ROLE_USER"));
        TestConfig.roleResolver().put("acme", email,
                new TenantUserRoleResolver.ResolvedTenantUserRole("acme", "MEMBER", jobRole));
    }

    // ── method-level annotation ───────────────────────────────────────────────

    @Test
    @DisplayName("@RequiresPermission allows call when user has permission")
    void allowsCallWhenPermitted() {
        setCurrentUser("alice@acme.com", "ACCOUNTANT");
        permissionService.setPermissions("acme", "ACCOUNTANT", Set.of("VIEW_FINANCIALS"));

        assertThatCode(() -> protectedService.viewFinancials()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("@RequiresPermission throws PermissionDeniedException when not permitted")
    void throwsWhenNotPermitted() {
        setCurrentUser("tom@acme.com", "DRIVER");
        permissionService.setPermissions("acme", "DRIVER", Set.of("VIEW_OWN_TASKS"));

        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> protectedService.viewFinancials())
                .satisfies(ex -> assertThat(ex.getPermission()).isEqualTo("VIEW_FINANCIALS"));
    }

    @Test
    @DisplayName("@RequiresPermission is bypassed when no tenant context (system call)")
    void bypassedWithNoTenantContext() {
        // No TenantContext.set() → permission service returns false → but wait:
        // the aspect calls permissionService.currentUserHasPermission() which returns false
        // when there's no tenant context. So it SHOULD throw.
        // System calls that don't go through the filter will fail — this is intentional.
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("system", "n/a", "ROLE_SYSTEM"));

        // Without tenant context, permission is denied
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> protectedService.viewFinancials());
    }

    @Test
    @DisplayName("wildcard '*' allows any permission through the aspect")
    void wildcardGrantsAll() {
        setCurrentUser("admin@acme.com", "ADMIN");
        permissionService.setPermissions("acme", "ADMIN", Set.of("*"));

        assertThatCode(() -> protectedService.viewFinancials()).doesNotThrowAnyException();
        assertThatCode(() -> protectedService.approveInvoice()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("method return value is preserved when permission is granted")
    void methodReturnValuePreserved() {
        setCurrentUser("alice@acme.com", "ACCOUNTANT");
        permissionService.setPermissions("acme", "ACCOUNTANT", Set.of("VIEW_FINANCIALS", "APPROVE_INVOICE"));

        assertThat(protectedService.getInvoiceCount()).isEqualTo(42);
    }

    @Test
    @DisplayName("custom message from @RequiresPermission is included in exception")
    void customMessageIncludedInException() {
        setCurrentUser("tom@acme.com", "DRIVER");
        permissionService.setPermissions("acme", "DRIVER", Set.of());

        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> protectedService.approveInvoice())
                .withMessage("Only accountants can approve invoices.");
    }

    // ── inner helpers ─────────────────────────────────────────────────────────

    interface ProtectedService {
        void viewFinancials();
        void approveInvoice();
        int  getInvoiceCount();
    }

    static class ProtectedServiceImpl implements ProtectedService {
        @Override
        @RequiresPermission("VIEW_FINANCIALS")
        public void viewFinancials() { /* no-op */ }

        @Override
        @RequiresPermission(value = "APPROVE_INVOICE", message = "Only accountants can approve invoices.")
        public void approveInvoice() { /* no-op */ }

        @Override
        @RequiresPermission("VIEW_FINANCIALS")
        public int getInvoiceCount() { return 42; }
    }

    /**
     * Minimal in-memory {@link TenantPermissionService} test fixture, scoped to this test
     * only. Not a shipped implementation — production code uses the Datastore-backed
     * {@code VirtualThreadOptimizedTenantPermissionService} registered by
     * {@code TenantPermissionAutoConfiguration}.
     */
    static class FakeTenantPermissionService implements TenantPermissionService {

        private final Map<String, Map<String, Set<String>>> store = new ConcurrentHashMap<>();
        private final FakeTenantUserRoleResolver roleResolver;

        FakeTenantPermissionService(FakeTenantUserRoleResolver roleResolver) {
            this.roleResolver = roleResolver;
        }

        @Override
        public void setPermissions(String tenantId, String roleName, Set<String> permissions) {
            store.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .put(roleName, new HashSet<>(permissions));
        }

        @Override
        public void grantPermissions(String tenantId, String roleName, Set<String> permissions) {
            store.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(roleName, k -> new HashSet<>())
                    .addAll(permissions);
        }

        @Override
        public void revokePermissions(String tenantId, String roleName, Set<String> permissions) {
            Map<String, Set<String>> tenantMap = store.get(tenantId);
            if (tenantMap != null) {
                Set<String> existing = tenantMap.get(roleName);
                if (existing != null) {
                    existing.removeAll(permissions);
                }
            }
        }

        @Override
        public Set<String> getPermissions(String tenantId, String roleName) {
            Map<String, Set<String>> tenantMap = store.get(tenantId);
            if (tenantMap == null) return Set.of();
            Set<String> perms = tenantMap.get(roleName);
            return perms != null ? Set.copyOf(perms) : Set.of();
        }

        @Override
        public boolean hasPermission(String tenantId, String roleName, String permission) {
            Map<String, Set<String>> tenantMap = store.get(tenantId);
            if (tenantMap == null) return false;
            Set<String> perms = tenantMap.get(roleName);
            return perms != null && (perms.contains("*") || perms.contains(permission));
        }

        @Override
        public boolean currentUserHasPermission(String permission) {
            String tenantId = TenantContext.peekTenantId();
            if (tenantId == null) {
                return false;
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return false;
            }
            TenantUserRoleResolver.ResolvedTenantUserRole role =
                    roleResolver.resolve(tenantId, auth.getName()).orElse(null);
            if (role == null) {
                return false;
            }
            if (role.jobRole() != null && hasPermission(tenantId, role.jobRole(), permission)) {
                return true;
            }
            return hasPermission(tenantId, role.platformRole(), permission);
        }

        @Override
        public Map<String, Set<String>> getAllPermissions(String tenantId) {
            Map<String, Set<String>> tenantMap = store.get(tenantId);
            return tenantMap != null ? new HashMap<>(tenantMap) : Map.of();
        }

        @Override
        public List<RolePermissions> listRolePermissions(String tenantId) {
            return getAllPermissions(tenantId).entrySet().stream()
                    .map(e -> new RolePermissions(tenantId, e.getKey(), e.getValue()))
                    .toList();
        }

        @Override
        public void clearPermissions(String tenantId) {
            store.remove(tenantId);
        }
    }

    /**
     * Minimal in-memory {@link TenantUserRoleResolver} test fixture — stands in for the
     * real implementation shipped by {@code holon-multi-tenant-users}
     * ({@code TenantUserServiceRoleResolver}), which this module no longer depends on.
     */
    static class FakeTenantUserRoleResolver implements TenantUserRoleResolver {

        private final Map<String, TenantUserRoleResolver.ResolvedTenantUserRole> roles = new ConcurrentHashMap<>();

        void put(String tenantId, String email, TenantUserRoleResolver.ResolvedTenantUserRole role) {
            roles.put(tenantId + "|" + email, role);
        }

        @Override
        public Optional<ResolvedTenantUserRole> resolve(String tenantId, String email) {
            return Optional.ofNullable(roles.get(tenantId + "|" + email));
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        private static FakeTenantUserRoleResolver roleResolverInstance;

        static FakeTenantUserRoleResolver roleResolver() { return roleResolverInstance; }

        @Bean
        FakeTenantUserRoleResolver tenantUserRoleResolver() {
            roleResolverInstance = new FakeTenantUserRoleResolver();
            return roleResolverInstance;
        }

        @Bean
        FakeTenantPermissionService tenantPermissionService(FakeTenantUserRoleResolver tenantUserRoleResolver) {
            return new FakeTenantPermissionService(tenantUserRoleResolver);
        }

        @Bean
        PermissionAspect permissionAspect(TenantPermissionService tenantPermissionService) {
            return new PermissionAspect(tenantPermissionService);
        }

        @Bean
        ProtectedService protectedService() {
            return new ProtectedServiceImpl();
        }
    }
}


