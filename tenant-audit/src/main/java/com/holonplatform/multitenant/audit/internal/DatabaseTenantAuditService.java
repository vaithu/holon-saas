package com.holonplatform.multitenant.audit.internal;

import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.AuditEntry;
import com.holonplatform.multitenant.audit.AuditSeverity;
import com.holonplatform.multitenant.audit.TenantAuditService;
import com.holonplatform.multitenant.security.aop.HolonPermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Database-backed {@link TenantAuditService} implementation using Holon Datastore.
 *
 * <p>Persists all audit entries to the relational database via Holon's Datastore.
 * Supports tenant isolation, filtering by action/severity, and pagination.
 *
 * <p>This is the sole {@link TenantAuditService} implementation shipped by the framework;
 * a Holon {@code Datastore} bean must be configured (e.g. via {@code holon-datastore-jpa}).
 */
@Service
public class DatabaseTenantAuditService implements TenantAuditService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTenantAuditService.class);

    private final AuditEntryDatastoreHelper datastoreHelper;

    public DatabaseTenantAuditService(AuditEntryDatastoreHelper datastoreHelper) {
        this.datastoreHelper = Objects.requireNonNull(datastoreHelper, "datastoreHelper must not be null");
    }

    // -- Recording ---------------------------------------------------------

    @Override
    @Transactional
    @HolonPermission({"AUDIT_WRITE", "ADMIN"})
    public void log(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");

        // Ensure createdAt is set
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(Instant.now());
        }

        datastoreHelper.save(entry);
        log.debug("Audit [{}] {} by '{}': {}",
                entry.getTenantId(), entry.getAction(), entry.getActor(), entry.getDescription());
    }

    // -- Query ---------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    @HolonPermission({"AUDIT_VIEW", "ADMIN"})
    public List<AuditEntry> findByTenant(String tenantId, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        validatePagination(offset, limit);

        return datastoreHelper.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @HolonPermission({"AUDIT_VIEW", "ADMIN"})
    public List<AuditEntry> findByTenantAndAction(String tenantId, AuditAction action, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        validatePagination(offset, limit);

        String actionStr = action.name();
        return datastoreHelper.findByTenantIdAndAction(tenantId, actionStr).stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @HolonPermission({"AUDIT_VIEW", "ADMIN"})
    public List<AuditEntry> findByTenantAndSeverity(String tenantId, AuditSeverity severity, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        validatePagination(offset, limit);

        String severityStr = severity.name();
        return datastoreHelper.findByTenantIdAndSeverity(tenantId, severityStr).stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @HolonPermission({"AUDIT_VIEW", "ADMIN"})
    public Optional<AuditEntry> findLatest(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return datastoreHelper.findFirstByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    // -- Count ----------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    @HolonPermission({"AUDIT_VIEW", "ADMIN"})
    public long count(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return datastoreHelper.countByTenantId(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    @HolonPermission({"AUDIT_VIEW", "ADMIN"})
    public long countBySeverity(String tenantId, AuditSeverity severity) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        return datastoreHelper.countByTenantIdAndSeverity(tenantId, severity.name());
    }

    // -- Lifecycle ---------------------------------------------------------------

    @Override
    @Transactional
    @HolonPermission("ADMIN")
    public void deleteAll(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        long count = datastoreHelper.countByTenantId(tenantId);
        datastoreHelper.deleteByTenantId(tenantId);
        log.info("Deleted {} audit entries for tenant '{}'", count, tenantId);
    }

    // -- Helpers ---------------------------------------------------------------

    private void validatePagination(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
    }
}


