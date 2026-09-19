package com.holonplatform.multitenant.permissions.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;
import com.holonplatform.multitenant.permissions.PermissionProperties;

import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for PermissionEntity persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations for role-based
 * permission management with tenant isolation.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.permissions.config.TenantPermissionAutoConfiguration}
 * rather than {@code @Component}-scanned — this class lives in an internal package outside
 * any consuming application's own base package, so component-scanning would never find it.
 */
public class TenantPermissionDatastoreHelper {

    private final BeanDatastoreHelper<PermissionEntity> helper;

    public TenantPermissionDatastoreHelper(Datastore datastore) {
        // Register the exclusion rule before Holon caches this entity's PropertySet, so
        // computed getters / synthetic Map "empty" sub-properties never leak into HQL.
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.helper = BeanDatastoreHelper.of(datastore, PermissionEntity.class);
    }

    /**
     * Save a permission entity to the database.
     *
     * @param permission the permission entity to save
     * @return the saved permission with generated ID
     */
    public PermissionEntity save(PermissionEntity permission) {
        return helper.insert(permission).getResult().orElse(permission);
    }

    /**
     * Find all permissions for a specific tenant and role.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name
     * @return list of permissions granted to this role
     */
    public List<PermissionEntity> findByTenantIdAndRoleName(String tenantId, String roleName) {
        return helper.findAll(
                QueryFilter.eq(PermissionProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(PermissionProperties.ROLE_NAME, roleName))
        ).toList();
    }

    /**
     * Find all permissions for a specific tenant, across all roles.
     *
     * @param tenantId the tenant identifier
     * @return list of all permission grants for this tenant
     */
    public List<PermissionEntity> findByTenantId(String tenantId) {
        return helper.findAll(QueryFilter.eq(PermissionProperties.TENANT_ID, tenantId)).toList();
    }

    /**
     * Find a specific permission by tenant, role, and permission name.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name
     * @param permission the permission name
     * @return the permission if it exists
     */
    public Optional<PermissionEntity> findByTenantIdAndRoleNameAndPermission(
            String tenantId, String roleName, String permission) {
        return helper.findTop(1,
                QueryFilter.eq(PermissionProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(PermissionProperties.ROLE_NAME, roleName))
                        .and(QueryFilter.eq(PermissionProperties.PERMISSION, permission)))
                .findFirst();
    }

    /**
     * Delete a specific permission by tenant, role, and permission name.
     *
     * @param tenantId the tenant identifier
     * @param roleName the role name
     * @param permission the permission name
     * @return number of permissions deleted
     */
    public long deleteByTenantIdAndRoleNameAndPermission(
            String tenantId, String roleName, String permission) {
        return helper.bulkDelete(QueryFilter.eq(PermissionProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(PermissionProperties.ROLE_NAME, roleName))
                        .and(QueryFilter.eq(PermissionProperties.PERMISSION, permission)))
                .getAffectedCount();
    }

    /**
     * Delete all permissions for a specific tenant.
     *
     * @param tenantId the tenant identifier
     * @return number of permissions deleted
     */
    public long deleteByTenantId(String tenantId) {
        return helper.bulkDelete(QueryFilter.eq(PermissionProperties.TENANT_ID, tenantId)).getAffectedCount();
    }
}



