package com.holonplatform.multitenant.permissions.config;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.permissions.internal.*;
import com.holonplatform.multitenant.users.TenantUserService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
 * equivalent check) to guard its own navigation/routes.</p>
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
    @ConditionalOnMissingBean(TenantPermissionDatastoreHelper.class)
    public TenantPermissionDatastoreHelper tenantPermissionDatastoreHelper(Datastore datastore) {
        return new TenantPermissionDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(TenantPermissionService.class)
    public VirtualThreadOptimizedTenantPermissionService virtualThreadOptimizedPermissionService(
            TenantPermissionDatastoreHelper datastoreHelper,
            TenantUserService tenantUserService) {
        return new VirtualThreadOptimizedTenantPermissionService(datastoreHelper, tenantUserService);
    }

    @Bean
    @ConditionalOnMissingBean(PermissionAspect.class)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public PermissionAspect permissionAspect(TenantPermissionService tenantPermissionService) {
        return new PermissionAspect(tenantPermissionService);
    }
}



