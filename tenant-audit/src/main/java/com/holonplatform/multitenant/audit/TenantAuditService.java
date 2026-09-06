package com.holonplatform.multitenant.audit;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for recording and querying the tenant audit log.
 *
 * <h2>Recording</h2>
 * <p>Use the fluent {@link AuditEntry#builder(String, AuditAction)} API to construct
 * entries and pass them to {@link #log(AuditEntry)}, or use the shorthand
 * {@link #log(String, AuditAction, String, String)} for simple cases.
 *
 * <h2>Querying</h2>
 * <p>All query methods accept {@code offset} / {@code limit} so callers can page
 * through large logs without loading the entire history. Count is always queried
 * separately via {@link #count(String)} — never issued as a side-effect of a fetch.
 *
 * <h2>Implementations</h2>
 * <p>The default implementation, {@code DatabaseTenantAuditService}, persists entries via
 * Holon Datastore and requires a configured {@code Datastore} bean (e.g. via
 * {@code holon-datastore-jpa}).
 */
public interface TenantAuditService {

    // ── Recording ─────────────────────────────────────────────────────────────

    /**
     * Records an audit entry.
     *
     * @param entry the entry to record; must not be null
     */
    void log(AuditEntry entry);

    /**
     * Convenience method — records a minimal audit entry with the current timestamp.
     *
     * @param tenantId    target tenant; must not be null
     * @param action      the action that occurred; must not be null
     * @param actor       who performed the action (user ID, email, or {@code "system"})
     * @param description human-readable summary
     */
    default void log(String tenantId, AuditAction action, String actor, String description) {
        log(AuditEntry.builder(tenantId, action)
                .actor(actor)
                .description(description)
                .build());
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns a page of audit entries for the given tenant, newest first.
     *
     * @param tenantId target tenant; must not be null
     * @param offset   zero-based start index
     * @param limit    maximum entries to return
     * @return ordered slice, never null
     */
    List<AuditEntry> findByTenant(String tenantId, int offset, int limit);

    /**
     * Returns entries for the given tenant filtered by action, newest first.
     *
     * @param tenantId target tenant; must not be null
     * @param action   action filter; must not be null
     * @param offset   zero-based start index
     * @param limit    maximum entries to return
     * @return ordered slice, never null
     */
    List<AuditEntry> findByTenantAndAction(String tenantId, AuditAction action, int offset, int limit);

    /**
     * Returns entries for the given tenant filtered by severity, newest first.
     *
     * @param tenantId target tenant; must not be null
     * @param severity severity filter; must not be null
     * @param offset   zero-based start index
     * @param limit    maximum entries to return
     * @return ordered slice, never null
     */
    List<AuditEntry> findByTenantAndSeverity(String tenantId, AuditSeverity severity, int offset, int limit);

    /**
     * Returns the most recent audit entry for the given tenant, if any.
     *
     * @param tenantId target tenant; must not be null
     * @return the most recent entry, or empty
     */
    Optional<AuditEntry> findLatest(String tenantId);

    // ── Count ───────────────────────────────────────────────────────────────���─

    /**
     * Returns the total number of audit entries for the given tenant.
     * Call explicitly only when the UI needs a count — never as a guard for fetch.
     *
     * @param tenantId target tenant; must not be null
     * @return total entry count
     */
    long count(String tenantId);

    /**
     * Returns the count of entries for the given tenant with a specific severity.
     *
     * @param tenantId target tenant; must not be null
     * @param severity severity filter; must not be null
     * @return matching entry count
     */
    long countBySeverity(String tenantId, AuditSeverity severity);

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Removes all audit entries for the given tenant.
     * Intended for GDPR erasure workflows — use with care.
     *
     * @param tenantId target tenant; must not be null
     */
    void deleteAll(String tenantId);
}

