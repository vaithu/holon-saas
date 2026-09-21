package com.holonplatform.multitenant.users.config;

import com.holonplatform.multitenant.users.TenantRole;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserService;
import com.holonplatform.multitenant.users.internal.DatabaseTenantUserService;
import com.holonplatform.multitenant.users.internal.TenantUserDatastoreHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link TenantUserAutoConfiguration}.
 *
 * <p>A mocked {@link TenantUserDatastoreHelper} stands in for a real Holon Datastore
 * so the context can be exercised without a database.
 */
class TenantUserAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantUserAutoConfiguration.class))
            .withUserConfiguration(DatastoreHelperConfig.class);

    // ── Default bean registration ─────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers DatabaseTenantUserService when no custom bean present")
    void autoConfig_registersDefaultUserService() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantUserService.class);
            assertThat(ctx.getBean(TenantUserService.class))
                    .isInstanceOf(DatabaseTenantUserService.class);
        });
    }

    // ── Back-off when custom bean present ─────────────────────────────────────

    @Test
    @DisplayName("auto-config backs off when a custom TenantUserService bean is declared")
    void autoConfig_backsOff_whenCustomUserServicePresent() {
        contextRunner
                .withUserConfiguration(CustomUserServiceConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantUserService.class);
                    assertThat(ctx.getBean(TenantUserService.class))
                            .isInstanceOf(CustomUserServiceConfig.CustomUserService.class);
                });
    }

    // ── Only one bean registered ──────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers exactly one TenantUserService bean")
    void autoConfig_registersSingleBean() {
        contextRunner.run(ctx ->
                assertThat(ctx.getBeansOfType(TenantUserService.class)).hasSize(1));
    }

    // ── Registered service is functional ─────────────────────────────────────

    @Test
    @DisplayName("auto-configured service can invite and retrieve users")
    void autoConfig_service_isFunctional() {
        contextRunner.run(ctx -> {
            TenantUserService service = ctx.getBean(TenantUserService.class);
            service.invite("acme", "u1", "user@example.com", TenantRole.MEMBER);
            assertThat(service.findById("acme", "u1")).isPresent();
        });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link TenantUserDatastoreHelper}
     * (which normally requires a Holon {@code Datastore} bean) with an in-memory-backed
     * mock, so the auto-configuration can be exercised without a real database.
     */
    static class DatastoreHelperConfig {

        @Bean
        TenantUserDatastoreHelper tenantUserDatastoreHelper() {
            TenantUserDatastoreHelper helper = mock(TenantUserDatastoreHelper.class);
            java.util.Map<String, TenantUser> store = new java.util.concurrent.ConcurrentHashMap<>();

            when(helper.save(any(TenantUser.class))).thenAnswer(inv -> {
                TenantUser user = inv.getArgument(0);
                store.put(user.getTenantId() + "::" + user.getUserId(), user);
                return user;
            });
            when(helper.findById(anyString(), anyString())).thenAnswer(inv ->
                    Optional.ofNullable(store.get(inv.getArgument(1) + "::" + inv.getArgument(0))));
            return helper;
        }
    }

    static class CustomUserServiceConfig {

        @Bean
        TenantUserService tenantUserService() {
            return new CustomUserService();
        }

        static class CustomUserService implements TenantUserService {
            @Override
            public TenantUser invite(String tenantId, String userId, String email, TenantRole role) {
                return TenantUser.builder(userId, tenantId, email).role(role).build();
            }

            @Override
            public TenantUser acceptInvitation(String tenantId, String userId, String email, TenantRole role,
                    java.util.Map<String, String> attributes) {
                return TenantUser.builder(userId, tenantId, email).role(role).build();
            }

            @Override
            public TenantUser activate(String tenantId, String userId) {
                return null;
            }

            @Override
            public TenantUser suspend(String tenantId, String userId) {
                return null;
            }

            @Override
            public void remove(String tenantId, String userId) {
            }

            @Override
            public TenantUser changeRole(String tenantId, String userId, TenantRole newRole) {
                return null;
            }

            @Override
            public void recordLogin(String tenantId, String userId) {
            }

            @Override
            public Optional<TenantUser> findById(String tenantId, String userId) {
                return Optional.empty();
            }

            @Override
            public Optional<TenantUser> findByEmail(String tenantId, String email) {
                return Optional.empty();
            }

            @Override
            public java.util.List<TenantUser> findByTenant(String tenantId, int offset, int limit) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<TenantUser> findByRole(String tenantId, TenantRole role, int offset, int limit) {
                return java.util.List.of();
            }

            @Override
            public java.util.List<TenantUser> findByStatus(String tenantId,
                    com.holonplatform.multitenant.users.TenantUserStatus status, int offset, int limit) {
                return java.util.List.of();
            }

            @Override
            public long count(String tenantId) {
                return 0;
            }

            @Override
            public long countByStatus(String tenantId, com.holonplatform.multitenant.users.TenantUserStatus status) {
                return 0;
            }

            @Override
            public void deleteAll(String tenantId) {
            }
        }
    }
}
