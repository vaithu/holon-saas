package com.holonplatform.multitenant.users.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;
import com.holonplatform.multitenant.users.TenantRole;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserProperties;
import com.holonplatform.multitenant.users.TenantUserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-backed service for tenant users.
 *
 * Provides database-backed user operations using Holon's type-safe Datastore API.
 * Integrates with Holon PropertySet metadata for UI rendering and validation.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Type-safe queries via QueryExpression</li>
 *   <li>Pagination and sorting support</li>
 *   <li>Tenant-aware filtering</li>
 *   <li>Role and status filtering</li>
 *   <li>PropertySet metadata integration</li>
 * </ul>
 */
@Service
public class UserDatastoreService {

    private final BeanDatastoreHelper<TenantUser> beanDatastoreHelper;

    public UserDatastoreService(Datastore dataStore) {
        // See TenantUserDatastoreHelper: register the exclusion rule before Holon caches
        // TenantUser's PropertySet, so the computed "active" getter and synthetic
        // "attributes.empty" sub-property never leak into generated HQL.
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.beanDatastoreHelper = BeanDatastoreHelper.of(dataStore, TenantUser.class);
    }

    /**
     * Create or update a tenant user.
     * @param user the user to persist
     * @return the persisted user with ID
     */
    @Transactional
    public TenantUser save(TenantUser user) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        return beanDatastoreHelper.save(user).getResult().orElse(user);
    }

    /**
     * Find a user by ID.
     * @param id the user ID
     * @return the user, or empty if not found
     */
    public Optional<TenantUser> findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return beanDatastoreHelper.findTop(1, QueryFilter.eq(TenantUserProperties.ID, id),
                TenantUserProperties.ID.asc())
            .findFirst();
    }

    /**
     * Find all users in a tenant.
     * @param tenantId the tenant identifier
     * @return list of users in tenant
     */
    public List<TenantUser> findByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        return beanDatastoreHelper.findSlice(Integer.MAX_VALUE, 0,
                QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId),
                TenantUserProperties.EMAIL.asc())
            .toList();
    }

    /**
     * Find users in a tenant with pagination and sorting.
     * @param tenantId the tenant identifier
     * @param offset the offset (number of users to skip)
     * @param limit the maximum number of users to return
     * @return list of users for the requested page
     */
    public List<TenantUser> findByTenant(String tenantId, int offset, int limit) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }

        QueryFilter filter = QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId);

        return beanDatastoreHelper.findSlice(limit, offset, filter, TenantUserProperties.EMAIL.asc())
            .toList();
    }

    /**
     * Find a specific user by email in a tenant.
     * @param tenantId the tenant identifier
     * @param email the user email
     * @return the user, or empty if not found
     */
    public Optional<TenantUser> findByEmail(String tenantId, String email) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be null or blank");
        }

        QueryFilter filter = QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
            .and(QueryFilter.eq(TenantUserProperties.EMAIL, email));

        return beanDatastoreHelper.findTop(1, filter, TenantUserProperties.EMAIL.asc())
            .findFirst();
    }

    /**
     * Find all users with a specific role in a tenant.
     * @param tenantId the tenant identifier
     * @param role the tenant role
     * @return list of users with that role
     */
    public List<TenantUser> findByRole(String tenantId, TenantRole role) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }

        QueryFilter filter = QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
            .and(QueryFilter.eq(TenantUserProperties.ROLE, role.name()));

        return beanDatastoreHelper.findSlice(Integer.MAX_VALUE, 0, filter, TenantUserProperties.EMAIL.asc())
            .toList();
    }

    /**
     * Find all active users in a tenant.
     * @param tenantId the tenant identifier
     * @return list of active users
     */
    public List<TenantUser> findActive(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }

        QueryFilter filter = QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
            .and(QueryFilter.eq(TenantUserProperties.STATUS, TenantUserStatus.ACTIVE.name()));

        return beanDatastoreHelper.findSlice(Integer.MAX_VALUE, 0, filter, TenantUserProperties.EMAIL.asc())
            .toList();
    }

    /**
     * Find all pending invitations in a tenant.
     * @param tenantId the tenant identifier
     * @return list of invited users
     */
    public List<TenantUser> findPending(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }

        QueryFilter filter = QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId)
            .and(QueryFilter.eq(TenantUserProperties.STATUS, TenantUserStatus.INVITED.name()));

        return beanDatastoreHelper.findSlice(Integer.MAX_VALUE, 0, filter, TenantUserProperties.EMAIL.asc())
            .toList();
    }

    /**
     * Count total users in a tenant.
     * @param tenantId the tenant identifier
     * @return count of users
     */
    public long countByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        return beanDatastoreHelper.count(QueryFilter.eq(TenantUserProperties.TENANT_ID, tenantId));
    }

    /**
     * Delete a user.
     * @param id the user ID
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean delete(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        Optional<TenantUser> existing = findById(id);
        existing.ifPresent(beanDatastoreHelper::delete);
        return existing.isPresent();
    }

    /**
     * Get PropertySet interface for PropertyListing/PropertyForm support.
     * @return property set class for TenantUser
     */
    public Class<?> getPropertySetClass() {
        return TenantUserProperties.class;
    }
}

