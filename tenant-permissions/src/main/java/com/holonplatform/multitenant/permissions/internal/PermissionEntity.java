package com.holonplatform.multitenant.permissions.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity representing a permission granted to a role within a tenant.
 *
 * <p>Permissions are organized as (tenant → role → set of permission names).
 * This entity stores individual permission grant records.</p>
 */
@Entity(name = "permissionentity")
@Table(name = "tenant_permissions", indexes = {
    @Index(name = "idx_permission_tenant", columnList = "tenant_id"),
    @Index(name = "idx_permission_tenant_role", columnList = "tenant_id, role_name"),
    @Index(name = "idx_permission_uq", columnList = "tenant_id, role_name, permission", unique = true)
})
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String tenantId;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(nullable = false, length = 255)
    private String permission;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ── Constructors ────────────────────────────────────────────────────────

    public PermissionEntity() {
    }

    public PermissionEntity(String tenantId, String roleName, String permission) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.roleName = Objects.requireNonNull(roleName, "roleName must not be null");
        this.permission = Objects.requireNonNull(permission, "permission must not be null");
        this.createdAt = Instant.now();
    }

    // ── Getters/Setters ────────────────────────────────────────────────────

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

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "PermissionEntity{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", roleName='" + roleName + '\'' +
                ", permission='" + permission + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}

