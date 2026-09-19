package com.holonplatform.multitenant.permissions.internal;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity representing a custom role defined by a tenant.
 */
@Entity(name = "tenantcustomroleentity")
@Table(name = "tenant_custom_roles", indexes = {
    @Index(name = "idx_custom_role_tenant", columnList = "tenant_id"),
    @Index(name = "idx_custom_role_lookup", columnList = "tenant_id, role_name", unique = true)
})
public class TenantCustomRoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(name = "role_name", nullable = false, length = 100)
    private String roleName;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "base_role", length = 100)
    private String baseRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public TenantCustomRoleEntity() {
    }

    public TenantCustomRoleEntity(String tenantId, String roleName, String displayName, String description, String baseRole) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.roleName = Objects.requireNonNull(roleName, "roleName must not be null");
        this.displayName = displayName != null ? displayName : roleName;
        this.description = description;
        this.baseRole = baseRole;
        this.createdAt = Instant.now();
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseRole() {
        return baseRole;
    }

    public void setBaseRole(String baseRole) {
        this.baseRole = baseRole;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
