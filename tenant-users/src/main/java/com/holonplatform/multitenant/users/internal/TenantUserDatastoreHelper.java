package com.holonplatform.multitenant.users.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserProperties;

import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link TenantUser} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on tenant users with
 * role and status filtering using Holon's Properties Model.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.users.config.TenantUserAutoConfiguration} rather than
 * {@code @Component}-scanned — this class lives in an internal package outside any consuming
 * application's own base package, so component-scanning would never find it.
 */
public class TenantUserDatastoreHelper {

    private final BeanDatastoreHelper<TenantUser> helper;

    public TenantUserDatastoreHelper(Datastore datastore) {
        this.helper = BeanDatastoreHelper.of(datastore, TenantUser.class);
    }

    public TenantUser save(TenantUser user) {
        // Deliberately BeanDatastoreHelper#save (upsert: insert if new, update if the entity
        // already has an assigned id), NOT #insert. invite() calls this with a brand-new
        // TenantUser (no id yet — insert is correct), but activate()/suspend()/remove()/
        // changeRole()/recordLogin() all first load an EXISTING TenantUser (already has a
        // real, DB-assigned id) then mutate one field and call this same method — #insert()
        // on an already-persisted, detached entity throws
        // "jakarta.persistence.EntityExistsException: Detached entity passed to persist".
        return helper.save(user).getResult().orElse(user);
    }

    public Optional<TenantUser> findById(String userId, String tenantId) {
        return helper.findTop(1,
                QueryFilter.eq(TenantUserProperties.USER_ID, userId)
                        .and(QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)))
                .findFirst();
    }

    public Optional<TenantUser> findByTenantIdAndEmailIgnoreCase(String tenantId, String email) {
        return helper.findTop(1,
                QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantUserProperties.EMAIL, email)))
                .findFirst();
    }

    public List<TenantUser> findByTenantId(String tenantId) {
        return helper.findAll(QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId),
                TenantUserProperties.CREATED_AT.asc()).toList();
    }

    public List<TenantUser> findByTenantIdAndRole(String tenantId, String role) {
        return helper.findAll(
                QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantUserProperties.ROLE, role)),
                TenantUserProperties.CREATED_AT.asc()
        ).toList();
    }

    public List<TenantUser> findByTenantIdAndStatus(String tenantId, String status) {
        return helper.findAll(
                QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantUserProperties.STATUS, status)),
                TenantUserProperties.CREATED_AT.asc()
        ).toList();
    }

    public long countByTenantId(String tenantId) {
        return helper.count(QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId));
    }

    public long countByTenantIdAndStatus(String tenantId, String status) {
        return helper.count(
                QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantUserProperties.STATUS, status))
        );
    }

    public long countByTenantIdAndRole(String tenantId, String role) {
        return helper.count(
                QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(TenantUserProperties.ROLE, role))
        );
    }

    public long deleteByTenantId(String tenantId) {
        Optional<TenantUser> tenantUser = helper.findTop(1, QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId))
                .findFirst();
        return helper.delete(tenantUser.orElse(new TenantUser())).getAffectedCount();
    }
}

