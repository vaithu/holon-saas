package com.holonplatform.multitenant.permissions.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore helper for managing {@link TenantPermissionOverrideEntity} and {@link TenantCustomRoleEntity}.
 */
public class TenantRoleManagementDatastoreHelper {

    private final BeanDatastoreHelper<TenantPermissionOverrideEntity> overrideHelper;
    private final BeanDatastoreHelper<TenantCustomRoleEntity> customRoleHelper;

    public TenantRoleManagementDatastoreHelper(Datastore datastore) {
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.overrideHelper = BeanDatastoreHelper.of(datastore, TenantPermissionOverrideEntity.class);
        this.customRoleHelper = BeanDatastoreHelper.of(datastore, TenantCustomRoleEntity.class);
    }

    // ── Overrides ────────────────────────────────────────────────────────────

    public TenantPermissionOverrideEntity saveOverride(TenantPermissionOverrideEntity entity) {
        Optional<TenantPermissionOverrideEntity> existing = findOverride(
                entity.getTenantId(), entity.getRoleName(), entity.getViewId(), entity.getAction());

        if (existing.isPresent()) {
            TenantPermissionOverrideEntity toUpdate = existing.get();
            toUpdate.setAllowed(entity.isAllowed());
            toUpdate.setUpdatedAt(Instant.now());
            overrideHelper.update(toUpdate);
            return toUpdate;
        } else {
            return overrideHelper.insert(entity).getResult().orElse(entity);
        }
    }

    public Optional<TenantPermissionOverrideEntity> findOverride(
            String tenantId, String roleName, String viewId, String action) {
        return overrideHelper.findTop(1,
                QueryFilter.eq(TenantPermissionOverrideProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.ROLE_NAME, roleName))
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.VIEW_ID, viewId))
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.ACTION, action)))
                .findFirst();
    }

    public List<TenantPermissionOverrideEntity> findOverridesByTenantAndRole(String tenantId, String roleName) {
        return overrideHelper.findAll(
                QueryFilter.eq(TenantPermissionOverrideProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.ROLE_NAME, roleName)))
                .toList();
    }

    public List<TenantPermissionOverrideEntity> findOverridesByTenant(String tenantId) {
        return overrideHelper.findAll(
                QueryFilter.eq(TenantPermissionOverrideProperties.TENANT_ID, tenantId))
                .toList();
    }

    public long deleteOverride(String tenantId, String roleName, String viewId, String action) {
        return overrideHelper.bulkDelete(
                QueryFilter.eq(TenantPermissionOverrideProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.ROLE_NAME, roleName))
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.VIEW_ID, viewId))
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.ACTION, action)))
                .getAffectedCount();
    }

    public long deleteOverridesByView(String tenantId, String roleName, String viewId) {
        return overrideHelper.bulkDelete(
                QueryFilter.eq(TenantPermissionOverrideProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.ROLE_NAME, roleName))
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.VIEW_ID, viewId)))
                .getAffectedCount();
    }

    public long deleteOverridesByRole(String tenantId, String roleName) {
        return overrideHelper.bulkDelete(
                QueryFilter.eq(TenantPermissionOverrideProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantPermissionOverrideProperties.ROLE_NAME, roleName)))
                .getAffectedCount();
    }

    public long deleteOverridesByTenant(String tenantId) {
        return overrideHelper.bulkDelete(
                QueryFilter.eq(TenantPermissionOverrideProperties.TENANT_ID, tenantId))
                .getAffectedCount();
    }

    // ── Custom Roles ─────────────────────────────────────────────────────────

    public TenantCustomRoleEntity saveCustomRole(TenantCustomRoleEntity role) {
        Optional<TenantCustomRoleEntity> existing = findCustomRole(role.getTenantId(), role.getRoleName());
        if (existing.isPresent()) {
            TenantCustomRoleEntity toUpdate = existing.get();
            toUpdate.setDisplayName(role.getDisplayName());
            toUpdate.setDescription(role.getDescription());
            toUpdate.setBaseRole(role.getBaseRole());
            customRoleHelper.update(toUpdate);
            return toUpdate;
        } else {
            return customRoleHelper.insert(role).getResult().orElse(role);
        }
    }

    public Optional<TenantCustomRoleEntity> findCustomRole(String tenantId, String roleName) {
        return customRoleHelper.findTop(1,
                QueryFilter.eq(TenantCustomRoleProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantCustomRoleProperties.ROLE_NAME, roleName)))
                .findFirst();
    }

    public List<TenantCustomRoleEntity> findCustomRolesByTenant(String tenantId) {
        return customRoleHelper.findAll(
                QueryFilter.eq(TenantCustomRoleProperties.TENANT_ID, tenantId))
                .toList();
    }

    public long deleteCustomRole(String tenantId, String roleName) {
        deleteOverridesByRole(tenantId, roleName);
        return customRoleHelper.bulkDelete(
                QueryFilter.eq(TenantCustomRoleProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantCustomRoleProperties.ROLE_NAME, roleName)))
                .getAffectedCount();
    }
}
