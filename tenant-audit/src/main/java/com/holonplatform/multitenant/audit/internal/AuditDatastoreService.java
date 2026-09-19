package com.holonplatform.multitenant.audit.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.audit.AuditEntry;
import com.holonplatform.multitenant.audit.AuditEntryProperties;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Holon Datastore-backed service for audit entries.
 *
 * Provides database-backed audit operations using Holon's type-safe Datastore API.
 * Integrates with Holon PropertySet metadata for UI rendering and validation.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Type-safe queries via QueryExpression</li>
 *   <li>Pagination and sorting support</li>
 *   <li>Tenant-aware filtering</li>
 *   <li>PropertySet metadata integration</li>
 * </ul>
 */
@Service
public class AuditDatastoreService {

    private final BeanDatastoreHelper<AuditEntry> beanDatastoreHelper;

    public AuditDatastoreService(Datastore dataStore) {
        // Register the exclusion rule before Holon caches this entity's PropertySet, so
        // computed getters / synthetic Map "empty" sub-properties never leak into HQL.
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.beanDatastoreHelper = BeanDatastoreHelper.of(dataStore, AuditEntry.class);
    }

    /**
     * Record an audit entry in the database.
     * @param entry the audit entry to persist
     * @return the persisted entry with ID
     */
    @Transactional
    public AuditEntry record(AuditEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("audit entry must not be null");
        }
        beanDatastoreHelper.insert(entry);
        return entry;
    }

    /**
     * Find all audit entries for a specific tenant.
     * @param tenantId the tenant identifier
     * @return list of audit entries, empty if none found
     */
    public List<AuditEntry> findByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        return beanDatastoreHelper.findSlice(Integer.MAX_VALUE, 0,
                QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId),
                AuditEntryProperties.CREATED_AT.desc())
            .toList();
    }

    /**
     * Find audit entries for a tenant with pagination and sorting.
     * @param tenantId the tenant identifier
     * @param offset the offset (number of entries to skip)
     * @param limit the maximum number of entries to return
     * @return list of audit entries for the requested page
     */
    public List<AuditEntry> findByTenant(String tenantId, int offset, int limit) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }

        QueryFilter filter = QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId);

        return beanDatastoreHelper.findSlice(limit, offset, filter, AuditEntryProperties.CREATED_AT.desc())
            .toList();
    }

    /**
     * Find a specific audit entry by ID.
     * @param id the audit entry ID
     * @return the audit entry, or empty if not found
     */
    public AuditEntry findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        return beanDatastoreHelper.findTop(1, QueryFilter.eq(AuditEntryProperties.ID, id),
                AuditEntryProperties.CREATED_AT.desc())
            .findFirst()
            .orElse(null);
    }

    /**
     * Count total audit entries for a tenant.
     * @param tenantId the tenant identifier
     * @return count of audit entries
     */
    public long countByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        return beanDatastoreHelper.count(QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId));
    }

    /**
     * Find audit entries by action.
     * @param tenantId the tenant identifier
     * @param action the action to filter by
     * @return list of matching audit entries
     */
    public List<AuditEntry> findByAction(String tenantId, String action) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be null or blank");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be null or blank");
        }

        QueryFilter filter = QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId)
            .and(QueryFilter.eq(AuditEntryProperties.ACTION, action));

        return beanDatastoreHelper.findSlice(Integer.MAX_VALUE, 0, filter, AuditEntryProperties.CREATED_AT.desc())
            .toList();
    }

    /**
     * Get PropertySet interface for PropertyListing/PropertyForm support.
     * @return property set class for AuditEntry
     */
    public Class<?> getPropertySetClass() {
        return AuditEntryProperties.class;
    }
}

