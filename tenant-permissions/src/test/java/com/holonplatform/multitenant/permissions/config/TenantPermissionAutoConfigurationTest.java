package com.holonplatform.multitenant.permissions.config;

import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.permissions.internal.PermissionAspect;
import com.holonplatform.multitenant.permissions.internal.PermissionEntity;
import com.holonplatform.multitenant.permissions.internal.TenantPermissionDatastoreHelper;
import com.holonplatform.multitenant.permissions.internal.VirtualThreadOptimizedTenantPermissionService;
import com.holonplatform.multitenant.users.TenantUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link TenantPermissionAutoConfiguration} registers expected beans.
 *
 * <p>A mocked {@link TenantPermissionDatastoreHelper} stands in for a real Holon
 * Datastore so the context can be exercised without a database.
 */
class TenantPermissionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantPermissionAutoConfiguration.class))
            .withBean(TenantUserService.class, () -> mock(TenantUserService.class))
            .withUserConfiguration(DatastoreHelperConfig.class);

    @Test
    @DisplayName("auto-config registers TenantPermissionService bean")
    void registersPermissionService() {
        contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(TenantPermissionService.class));
    }

    @Test
    @DisplayName("auto-config registers VirtualThreadOptimizedTenantPermissionService by default")
    void registersDatabaseBackedImplementation() {
        contextRunner.run(ctx -> {
            TenantPermissionService service = ctx.getBean(TenantPermissionService.class);
            assertThat(service).isInstanceOf(VirtualThreadOptimizedTenantPermissionService.class);
        });
    }

    @Test
    @DisplayName("auto-config registers PermissionAspect bean")
    void registersPermissionAspect() {
        contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(PermissionAspect.class));
    }

    @Test
    @DisplayName("custom TenantPermissionService bean overrides default")
    void customBeanOverridesDefault() {
        TenantPermissionService custom = mock(TenantPermissionService.class);

        contextRunner.withBean(TenantPermissionService.class, () -> custom)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantPermissionService.class);
                    assertThat(ctx.getBean(TenantPermissionService.class)).isSameAs(custom);
                    assertThat(ctx).doesNotHaveBean(VirtualThreadOptimizedTenantPermissionService.class);
                });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link TenantPermissionDatastoreHelper}
     * (which normally requires a Holon {@code Datastore} bean) with an in-memory-backed
     * mock, so the auto-configuration can be exercised without a real database.
     */
    static class DatastoreHelperConfig {

        @org.springframework.context.annotation.Bean
        TenantPermissionDatastoreHelper tenantPermissionDatastoreHelper() {
            TenantPermissionDatastoreHelper helper = mock(TenantPermissionDatastoreHelper.class);
            Map<String, PermissionEntity> store = new ConcurrentHashMap<>();

            when(helper.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity e = inv.getArgument(0);
                store.put(e.getTenantId() + "::" + e.getRoleName() + "::" + e.getPermission(), e);
                return e;
            });
            when(helper.findByTenantIdAndRoleName(anyString(), anyString())).thenAnswer(inv -> {
                String prefix = inv.getArgument(0) + "::" + inv.getArgument(1) + "::";
                List<PermissionEntity> result = new ArrayList<>();
                store.forEach((k, v) -> { if (k.startsWith(prefix)) result.add(v); });
                return result;
            });
            when(helper.findByTenantIdAndRoleNameAndPermission(anyString(), anyString(), anyString()))
                    .thenAnswer(inv -> Optional.ofNullable(store.get(
                            inv.getArgument(0) + "::" + inv.getArgument(1) + "::" + inv.getArgument(2))));
            return helper;
        }
    }
}
