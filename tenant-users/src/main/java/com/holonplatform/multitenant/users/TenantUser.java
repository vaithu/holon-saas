package com.holonplatform.multitenant.users;

import jakarta.persistence.*;
import com.holonplatform.multitenant.convert.StringMapJsonConverter;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JPA entity representing a user's membership in a specific tenant.
 */
@Entity(name = "tenantuser")
@Table(name = "tenant_users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tenant_id", "email"}, name = "uq_tenant_email")
}, indexes = {
    @Index(name = "idx_tenant_user", columnList = "tenant_id, user_id"),
    @Index(name = "idx_tenant_email", columnList = "tenant_id, email"),
    @Index(name = "idx_user_status", columnList = "user_id, status")
})
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantRole role;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TenantUserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "attributes", columnDefinition = "TEXT")
    private Map<String, String> attributes = new HashMap<>();

    // ── Constructors ──────────────────────────────────────��────────────────────

    public TenantUser() {
        // JPA default constructor
    }

    public TenantUser(String userId, String tenantId, String email, String displayName,
                      TenantRole role, TenantUserStatus status, Instant createdAt,
                      Instant lastLoginAt, Map<String, String> attributes) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.displayName = displayName;
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.lastLoginAt = lastLoginAt;
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    }

    // ── Getters/Setters ────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public TenantRole getRole() {
        return role;
    }

    public void setRole(TenantRole role) {
        this.role = role;
    }

    public TenantUserStatus getStatus() {
        return status;
    }

    public void setStatus(TenantUserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    }

    // ── Convenience ────────────────────────────────────────────────────────────

    /** Returns the display name, falling back to email if null. */
    public String effectiveName() {
        return displayName != null ? displayName : email;
    }

    /** Returns {@code true} if the user can currently access the application. */
    public boolean isActive() {
        return status.canAccess();
    }

    /** Returns {@code true} if the user's role is at least {@code required}. */
    public boolean hasAtLeastRole(TenantRole required) {
        return role.isAtLeast(required);
    }

    // ── Factory ────────────────────────────────────────────────────────────────

    /**
     * Starts a fluent {@link Builder} for a new {@code TenantUser}.
     */
    public static Builder builder(String userId, String tenantId, String email) {
        return new Builder(userId, tenantId, email);
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static final class Builder {
        private final TenantUser user;

        private Builder(String userId, String tenantId, String email) {
            this.user = new TenantUser();
            this.user.userId = Objects.requireNonNull(userId, "userId must not be null");
            this.user.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
            this.user.email = Objects.requireNonNull(email, "email must not be null");
            this.user.role = TenantRole.MEMBER;
            this.user.status = TenantUserStatus.INVITED;
            this.user.createdAt = Instant.now();
        }

        public Builder displayName(String name) {
            this.user.displayName = name;
            return this;
        }

        public Builder role(TenantRole r) {
            this.user.role = Objects.requireNonNull(r);
            return this;
        }

        public Builder status(TenantUserStatus s) {
            this.user.status = Objects.requireNonNull(s);
            return this;
        }

        public Builder createdAt(Instant t) {
            this.user.createdAt = Objects.requireNonNull(t);
            return this;
        }

        public Builder lastLoginAt(Instant t) {
            this.user.lastLoginAt = t;
            return this;
        }

        public Builder attribute(String k, String v) {
            this.user.attributes.put(k, v);
            return this;
        }

        public TenantUser build() {
            return user;
        }
    }
}

