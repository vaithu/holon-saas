package com.holonplatform.multitenant.permissions.config;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.permissions.internal.*;
import com.holonplatform.multitenant.permissions.view.SystemDefaultPermissionRegistry;
import com.holonplatform.multitenant.permissions.view.TenantRoleManagementService;
import com.holonplatform.multitenant.permissions.view.TenantViewAccessChecker;
import com.holonplatform.multitenant.permissions.view.UIPermissionHelper;
import com.holonplatform.multitenant.permissions.view.ViewRegistry;
import com.holonplatform.multitenant.permissions.view.internal.DefaultSystemDefaultPermissionRegistry;
import com.holonplatform.multitenant.permissions.view.internal.DefaultTenantRoleManagementService;
import com.holonplatform.multitenant.permissions.view.internal.DefaultViewRegistry;
import com.holonplatform.multitenant.users.TenantUserService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for the tenant permissions subsystem.
 *
 * <h2>What is registered</h2>
 * <ol>
 *   <li>A {@link VirtualThreadOptimizedTenantPermissionService} — database-backed
 *       implementation with Caffeine caching, optimized for Virtual Threads (Java 25+).
 *       Requires a {@link TenantPermissionDatastoreHelper} bean, which in turn requires
 *       a Holon {@code Datastore} bean (e.g. via {@code holon-datastore-jpa}).</li>
 *   <li>A {@link PermissionAspect} — enforces {@code @RequiresPermission} on Spring beans
 *       (only when AspectJ is on the classpath).</li>
 * </ol>
 *
 * <p>This module is UI-agnostic: permission checks are exposed only through
 * {@link TenantPermissionService} and the {@code @RequiresPermission} AOP aspect.
 * Any UI layer (Vaadin, React, REST clients, ...) is responsible for calling
 * {@link TenantPermissionService#currentUserHasPermission(String)} (or an
 * equivalent check) to guard its own navigation/routes. When given a
 * {@code "viewId:ACTION"}-shaped permission string, the default
 * {@link VirtualThreadOptimizedTenantPermissionService} delegates to
 * {@link TenantRoleManagementService} instead of the flat permission store, so
 * {@code @RequiresPermission} and the view/action permission matrix (managed by tenant
 * owners through {@code TenantRoleManagementService}) never disagree.</p>
 *
 * <h2>Virtual Thread Optimization (Java 21+)</h2>
 * <p>The database-backed service uses Caffeine cache instead of ThreadLocal for
 * per-request user resolution, making it more efficient with millions of Virtual Threads:</p>
 * <ul>
 *   <li>Auto-expiring cache entries (30 second TTL)</li>
 *   <li>Bounded cache size (10,000 entries)</li>
 *   <li>No ThreadLocal memory accumulation</li>
 *   <li>Cache statistics for monitoring</li>
 * </ul>
 *
 * <h2>Quick start — seed permissions after tenant onboarding</h2>
 * <pre>{@code
 * @EventListener
 * public void onTenantOnboarded(TenantOnboardingCompletedEvent e) {
 *     String tid = e.getTenantId();
 *     permissionService.setPermissions(tid, "ACCOUNTANT",
 *         Set.of("VIEW_FINANCIALS", "APPROVE_INVOICE", "VIEW_CONTACTS"));
 *     permissionService.setPermissions(tid, "DRIVER",
 *         Set.of("VIEW_OWN_TASKS", "MANAGE_DELIVERIES"));
 *     permissionService.setPermissions(tid, "ADMIN", Set.of("*")); // wildcard
 * }
 * }</pre>
 *
 * <h2>Assigning job roles to users</h2>
 * <p>Store the job role in the user's {@code attributes} map using the
 * {@link TenantPermissionService#JOB_ROLE_ATTRIBUTE} key:
 * <pre>{@code
 * TenantUser user = TenantUser.builder(userId, tenantId, email)
 *     .role(TenantRole.MEMBER)
 *     .attribute(TenantPermissionService.JOB_ROLE_ATTRIBUTE, "ACCOUNTANT")
 *     .build();
 * }</pre>
 */
@AutoConfiguration
@Import(TenantPermissionEntityScanRegistrar.class)
public class TenantPermissionAutoConfiguration {

    /**
     * Registers the {@link TenantPermissionDatastoreHelper} explicitly — it is not
     * {@code @Component}-scanned (it lives in an internal package outside any consuming
     * application's own base package), so this module must construct it itself.
     */
    @Bean
    @ConditionalOnBean(Datastore.class)
    @ConditionalOnMissingBean(TenantPermissionDatastoreHelper.class)
    public TenantPermissionDatastoreHelper tenantPermissionDatastoreHelper(Datastore datastore) {
        return new TenantPermissionDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnBean(Datastore.class)
    @ConditionalOnMissingBean(TenantRoleManagementDatastoreHelper.class)
    public TenantRoleManagementDatastoreHelper tenantRoleManagementDatastoreHelper(Datastore datastore) {
        return new TenantRoleManagementDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(ViewRegistry.class)
    public DefaultViewRegistry viewRegistry() {
        return new DefaultViewRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(SystemDefaultPermissionRegistry.class)
    public DefaultSystemDefaultPermissionRegistry systemDefaultPermissionRegistry() {
        return new DefaultSystemDefaultPermissionRegistry();
    }

    @Bean
    @ConditionalOnBean({TenantRoleManagementDatastoreHelper.class, TenantUserService.class})
    @ConditionalOnMissingBean(TenantRoleManagementService.class)
    public DefaultTenantRoleManagementService tenantRoleManagementService(
            TenantRoleManagementDatastoreHelper datastoreHelper,
            ViewRegistry viewRegistry,
            SystemDefaultPermissionRegistry defaultPermissionRegistry,
            TenantUserService tenantUserService) {
        return new DefaultTenantRoleManagementService(datastoreHelper, viewRegistry, defaultPermissionRegistry, tenantUserService);
    }

    @Bean
    @ConditionalOnBean(TenantRoleManagementService.class)
    @ConditionalOnMissingBean(TenantViewAccessChecker.class)
    public TenantViewAccessChecker tenantViewAccessChecker(TenantRoleManagementService roleManagementService) {
        return new TenantViewAccessChecker(roleManagementService);
    }

    @Bean
    @ConditionalOnBean(TenantRoleManagementService.class)
    @ConditionalOnMissingBean(UIPermissionHelper.class)
    public UIPermissionHelper uiPermissionHelper(TenantRoleManagementService roleManagementService) {
        return new UIPermissionHelper(roleManagementService);
    }

    @Bean
    @ConditionalOnBean({TenantPermissionDatastoreHelper.class, TenantUserService.class})
    @ConditionalOnMissingBean(TenantPermissionService.class)
    public VirtualThreadOptimizedTenantPermissionService virtualThreadOptimizedPermissionService(
            TenantPermissionDatastoreHelper datastoreHelper,
            TenantUserService tenantUserService,
            org.springframework.beans.factory.ObjectProvider<TenantRoleManagementService> roleManagementServiceProvider) {
        return new VirtualThreadOptimizedTenantPermissionService(datastoreHelper, tenantUserService, roleManagementServiceProvider);
    }

    @Bean
    @ConditionalOnMissingBean(PermissionAspect.class)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public PermissionAspect permissionAspect(TenantPermissionService tenantPermissionService) {
        return new PermissionAspect(tenantPermissionService);
    }
}



