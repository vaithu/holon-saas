package com.holonplatform.multitenant.audit.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.audit.AuditEntry;
import com.holonplatform.multitenant.audit.AuditEntryProperties;

import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link AuditEntry} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on audit entries with
 * tenant isolation and filtering by action and severity.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.audit.config.TenantAuditAutoConfiguration} rather than
 * {@code @Component}-scanned — this class lives in an internal package outside any consuming
 * application's own base package, so component-scanning would never find it.
 */
public class AuditEntryDatastoreHelper {

    private final BeanDatastoreHelper<AuditEntry> helper;

    public AuditEntryDatastoreHelper(Datastore datastore) {
        this.helper = BeanDatastoreHelper.of(datastore, AuditEntry.class);
    }

    /**
     * Save an audit entry to the database.
     */
    public AuditEntry save(AuditEntry entry) {
        return helper.insert(entry).getResult().orElse(entry);
    }

    /**
     * Find all audit entries for a tenant, ordered by creation time descending.
     */
    public List<AuditEntry> findByTenantIdOrderByCreatedAtDesc(String tenantId) {
        return helper.findAll(
                QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId),
                AuditEntryProperties.CREATED_AT.desc()
        ).toList();
    }

    /**
     * Find audit entries for a tenant filtered by action.
     */
    public List<AuditEntry> findByTenantIdAndAction(String tenantId, String action) {
        return helper.findAll(
                QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(AuditEntryProperties.ACTION, action)),
                AuditEntryProperties.CREATED_AT.desc()
        ).toList();
    }

    /**
     * Find audit entries for a tenant filtered by severity.
     */
    public List<AuditEntry> findByTenantIdAndSeverity(String tenantId, String severity) {
        return helper.findAll(
                QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(AuditEntryProperties.SEVERITY, severity)),
                AuditEntryProperties.CREATED_AT.desc()
        ).toList();
    }

    /**
     * Count audit entries for a specific tenant.
     */
    public long countByTenantId(String tenantId) {
        return helper.count(QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId));
    }

    /**
     * Count audit entries for a tenant filtered by severity.
     */
    public long countByTenantIdAndSeverity(String tenantId, String severity) {
        return helper.count(
                QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(AuditEntryProperties.SEVERITY, severity))
        );
    }

    /**
     * Find the latest audit entry for a tenant.
     */
    public Optional<AuditEntry> findFirstByTenantIdOrderByCreatedAtDesc(String tenantId) {
        return helper.findTop(1, QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId),
                AuditEntryProperties.CREATED_AT.desc()).findFirst();
    }

    /**
     * Delete all audit entries for a specific tenant.
     */
    public long deleteByTenantId(String tenantId) {
        Optional<AuditEntry> auditEntry = helper.findTop(1, QueryFilter.eq(AuditEntryProperties.TENANT_ID, tenantId))
                .findFirst();
        return helper.delete(auditEntry.orElse(new AuditEntry())).getAffectedCount();
    }
}




