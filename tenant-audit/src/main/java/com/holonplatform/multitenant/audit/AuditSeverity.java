package com.holonplatform.multitenant.audit;

/**
 * Severity classification for audit log entries.
 *
 * <p>Used to categorise entries for alerting and retention policies:
 * <ul>
 *   <li>{@link #INFO} — routine operations, no attention required</li>
 *   <li>{@link #WARNING} — unusual but non-critical actions (e.g. failed login)</li>
 *   <li>{@link #CRITICAL} — security-sensitive or destructive operations
 *       (e.g. impersonation, data deletion, plan downgrade)</li>
 * </ul>
 */
public enum AuditSeverity {
    INFO,
    WARNING,
    CRITICAL;
}

