package com.holonplatform.multitenant.users.config;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.TenantUserRoleResolver;
import com.holonplatform.multitenant.users.TenantUserService;
import com.holonplatform.multitenant.users.internal.DatabaseTenantUserService;
import com.holonplatform.multitenant.users.internal.TenantUserDatastoreHelper;
import com.holonplatform.multitenant.users.internal.TenantUserServiceRoleResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for the tenant user management subsystem.
 *
 * <p>Registers a single {@link TenantUserService} bean when none is already present:
 * {@link DatabaseTenantUserService}, backed by Holon Datastore via
 * {@link TenantUserDatastoreHelper}. A {@code Datastore} bean must be configured
 * (e.g. via {@code holon-datastore-jpa}) for this module to start.
 *
 * <p>Also registers a {@link TenantUserRoleResolver} bean adapting {@link TenantUserService},
 * so other modules (e.g. {@code holon-multi-tenant-permissions}) can resolve a user's roles
 * without depending on this module's concrete types.
 */
@AutoConfiguration
@Import(TenantUserEntityScanRegistrar.class)
public class TenantUserAutoConfiguration {

    /**
     * Registers the {@link TenantUserDatastoreHelper} explicitly — it is not
     * {@code @Component}-scanned (it lives in an internal package outside any consuming
     * application's own base package), so this module must construct it itself.
     */
    @Bean
    @ConditionalOnMissingBean(TenantUserDatastoreHelper.class)
    public TenantUserDatastoreHelper tenantUserDatastoreHelper(Datastore datastore) {
        return new TenantUserDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(TenantUserService.class)
    public TenantUserService databaseTenantUserService(TenantUserDatastoreHelper datastoreHelper) {
        return new DatabaseTenantUserService(datastoreHelper);
    }

    @Bean
    @ConditionalOnMissingBean(TenantUserRoleResolver.class)
    public TenantUserRoleResolver tenantUserServiceRoleResolver(TenantUserService tenantUserService) {
        return new TenantUserServiceRoleResolver(tenantUserService);
    }
}

