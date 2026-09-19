package com.holonplatform.multitenant.permissions.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity representing a tenant's explicit permission override on a specific view action for a role.
 *
 * <p>When present, this override takes precedence over system defaults.</p>
 */
@Entity(name = "tenantpermissionoverrideentity")
@Table(name = "tenant_permission_overrides", indexes = {
    @Index(name = "idx_perm_override_tenant", columnList = "tenant_id"),
    @Index(name = "idx_perm_override_lookup", columnList = "tenant_id, role_name, view_id, action", unique = true)
})
public class TenantPermissionOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "view_id", nullable = false, length = 100)
    private String viewId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "allowed", nullable = false)
    private boolean allowed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public TenantPermissionOverrideEntity() {
    }

    public TenantPermissionOverrideEntity(String tenantId, String roleName, String viewId, String action, boolean allowed) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.roleName = Objects.requireNonNull(roleName, "roleName must not be null");
        this.viewId = Objects.requireNonNull(viewId, "viewId must not be null");
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.allowed = allowed;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

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

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getViewId() {
        return viewId;
    }

    public void setViewId(String viewId) {
        this.viewId = viewId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
