package com.holonplatform.multitenant.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity representing a single entry in the tenant audit log.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #tenantId} — the tenant this event belongs to</li>
 *   <li>{@link #action} — what happened</li>
 *   <li>{@link #actor} — who triggered the event (user id, email, or {@code "system"})</li>
 *   <li>{@link #resourceType} — the type of entity affected (e.g. {@code "Tenant"}, {@code "User"}); may be null</li>
 *   <li>{@link #resourceId} — the ID of the affected entity; may be null</li>
 *   <li>{@link #description} — human-readable summary; defaults to {@link AuditAction#getDescription()}</li>
 *   <li>{@link #ipAddress} — originating IP; may be null (e.g. for background jobs)</li>
 *   <li>{@link #severity} — {@link AuditSeverity} classification</li>
 *   <li>{@link #createdAt} — UTC timestamp; always set by the service implementation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AuditEntry entry = AuditEntry.builder("acme", AuditAction.PLAN_CHANGED)
 *     .actor("admin@acme.com")
 *     .resourceType("Tenant")
 *     .resourceId("acme")
 *     .description("Plan upgraded from FREE to PROFESSIONAL")
 *     .severity(AuditSeverity.WARNING)
 *     .build();
 * }</pre>
 */
@Entity(name = "auditentry")
@Table(name = "audit_entries", indexes = {
    @Index(name = "idx_tenant_created", columnList = "tenant_id, created_at"),
    @Index(name = "idx_tenant_action", columnList = "tenant_id, action")
})
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(nullable = false, length = 255)
    private String actor;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 255)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditSeverity severity;

    @Column(nullable = false)
    private Instant createdAt;

    // ── Constructors ───────────────────────────────────────────────────────────

    public AuditEntry() {
        // JPA default constructor
    }

    public AuditEntry(String tenantId, AuditAction action, String actor, String resourceType,
                      String resourceId, String description, String ipAddress,
                      AuditSeverity severity, Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.actor = actor != null ? actor : "system";
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.description = description != null ? description : action.getDescription();
        this.ipAddress = ipAddress;
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    // ── Getters ────────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public AuditSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(AuditSeverity severity) {
        this.severity = severity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    // ── Record-style accessors ───────────────────────────────────────────────
    // Convenience aliases for the getters above, for callers that prefer the
    // concise record-style accessor naming convention.

    public String tenantId() {
        return getTenantId();
    }

    public AuditAction action() {
        return getAction();
    }

    public String actor() {
        return getActor();
    }

    public String description() {
        return getDescription();
    }


    // ── Builder ────────────────────────────────────────────────────────────────

    public static Builder builder(String tenantId, AuditAction action) {
        return new Builder(tenantId, action);
    }

    public static final class Builder {

        private final String      tenantId;
        private final AuditAction action;
        private String        actor;
        private String        resourceType;
        private String        resourceId;
        private String        description;
        private String        ipAddress;
        private AuditSeverity severity = AuditSeverity.INFO;

        private Builder(String tenantId, AuditAction action) {
            this.tenantId = tenantId;
            this.action   = action;
        }

        public Builder actor(String actor)             { this.actor        = actor;        return this; }
        public Builder resourceType(String type)       { this.resourceType = type;         return this; }
        public Builder resourceId(String id)           { this.resourceId   = id;           return this; }
        public Builder description(String description) { this.description  = description;  return this; }
        public Builder ipAddress(String ip)            { this.ipAddress    = ip;           return this; }
        public Builder severity(AuditSeverity severity){ this.severity     = severity;     return this; }

        public AuditEntry build() {
            return new AuditEntry(tenantId, action, actor, resourceType,
                    resourceId, description, ipAddress, severity, Instant.now());
        }
    }
}

