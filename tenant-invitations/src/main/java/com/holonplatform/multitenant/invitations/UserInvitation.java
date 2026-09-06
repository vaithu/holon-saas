package com.holonplatform.multitenant.invitations;

import com.holonplatform.multitenant.users.TenantRole;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.holonplatform.multitenant.convert.StringMapJsonConverter;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable, JPA-mapped value object representing a pending or completed user invitation.
 *
 * <p>Instances are created via the static {@link #builder(String, String, String)} factory
 * and are directly persistable via Holon's {@code BeanDatastoreHelper} (see
 * {@code UserInvitationDatastoreHelper}), which is why this class exposes standard
 * JavaBean getters/setters in addition to its original record-style accessors
 * ({@link #token()}, {@link #tenantId()}, {@link #status()}, ...).
 *
 * <p><strong>Mutability note:</strong> unlike its previous record-based incarnation,
 * this class is mutable to satisfy JPA/Datastore requirements (no-arg constructor,
 * settable fields). Application code should still treat instances as effectively
 * immutable and prefer {@link #withStatus(InvitationStatus)} / {@link #accepted()}
 * (which both return new copies) rather than mutating a shared instance in place.
 */
@Entity(name = "userinvitation")
@Table(name = "user_invitation", indexes = {
        @Index(name = "idx_invitation_tenant", columnList = "tenant_id"),
        @Index(name = "idx_invitation_email", columnList = "email")
})
public class UserInvitation {

    /** UUID token embedded in the invitation link (primary key). */
    @Id
    @Column(name = "token", nullable = false, length = 64)
    private String token;

    /** tenant the user is being invited to */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** invitee's email address */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /** userId of the admin who sent the invitation (nullable) */
    @Column(name = "invited_by_user_id", length = 255)
    private String invitedByUserId;

    /** the {@link TenantRole} the user will receive on acceptance */
    @Column(name = "platform_role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private TenantRole platformRole = TenantRole.MEMBER;

    /** CRM job role stored in {@code TenantUser.attributes(job_role)} on acceptance (nullable) */
    @Column(name = "job_role", length = 100)
    private String jobRole;

    /** current lifecycle state */
    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private InvitationStatus status = InvitationStatus.PENDING;

    /** when the invitation was sent */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** when the invitation token becomes invalid */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** when the invitee accepted (null until accepted) */
    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** arbitrary metadata for application-specific use */
    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "attributes", columnDefinition = "TEXT")
    private Map<String, String> attributes = new HashMap<>();

    /**
     * JPA / Datastore default constructor.
     *
     * <p>Must be {@code public} — Holon's JPA Datastore ({@code JpaSave}) instantiates
     * entities reflectively via {@code Class.newInstance()}, which performs a normal Java
     * access check; a {@code protected}/{@code private} constructor fails at runtime with
     * {@code IllegalAccessException}.
     */
    public UserInvitation() {
    }

    private UserInvitation(String token, String tenantId, String email, String invitedByUserId,
            TenantRole platformRole, String jobRole, InvitationStatus status, Instant createdAt,
            Instant expiresAt, Instant acceptedAt, Map<String, String> attributes) {
        this.token = Objects.requireNonNull(token, "token must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.email = Objects.requireNonNull(email, "email must not be null");
        this.invitedByUserId = invitedByUserId;
        this.platformRole = Objects.requireNonNull(platformRole, "platformRole must not be null");
        this.jobRole = jobRole;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.acceptedAt = acceptedAt;
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    }

    // -------------------------------------------------------------------------
    // Record-style accessors (kept for source compatibility with existing callers)
    // -------------------------------------------------------------------------

    public String token() {
        return token;
    }

    public String tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public String invitedByUserId() {
        return invitedByUserId;
    }

    public TenantRole platformRole() {
        return platformRole;
    }

    public String jobRole() {
        return jobRole;
    }

    public InvitationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant acceptedAt() {
        return acceptedAt;
    }

    public Map<String, String> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    // -------------------------------------------------------------------------
    // JavaBean getters/setters (required for JPA and Holon BeanDatastoreHelper)
    // -------------------------------------------------------------------------

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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

    public String getInvitedByUserId() {
        return invitedByUserId;
    }

    public void setInvitedByUserId(String invitedByUserId) {
        this.invitedByUserId = invitedByUserId;
    }

    public TenantRole getPlatformRole() {
        return platformRole;
    }

    public void setPlatformRole(TenantRole platformRole) {
        this.platformRole = platformRole;
    }

    public String getJobRole() {
        return jobRole;
    }

    public void setJobRole(String jobRole) {
        this.jobRole = jobRole;
    }

    public InvitationStatus getStatus() {
        return status;
    }

    public void setStatus(InvitationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
    }

    // -------------------------------------------------------------------------
    // Convenience query methods
    // -------------------------------------------------------------------------

    /** Returns {@code true} if this invitation is past its expiry date. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** Returns {@code true} if this invitation is awaiting acceptance. */
    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    /**
     * Returns a copy of this invitation with the given status. Kept as a
     * "returns a new copy" operation (rather than in-place mutation) for source
     * compatibility with existing callers such as {@code invitation = invitation.withStatus(...)}.
     */
    public UserInvitation withStatus(InvitationStatus newStatus) {
        return new UserInvitation(token, tenantId, email, invitedByUserId, platformRole,
                jobRole, newStatus, createdAt, expiresAt, acceptedAt, attributes);
    }

    /** Returns a copy of this invitation marked as accepted now. */
    public UserInvitation accepted() {
        return new UserInvitation(token, tenantId, email, invitedByUserId, platformRole,
                jobRole, InvitationStatus.ACCEPTED, createdAt, expiresAt, Instant.now(), attributes);
    }

    /**
     * Returns a new {@link Builder} pre-populated with all fields of this instance,
     * allowing selective mutation through the fluent API.
     */
    public Builder toBuilder() {
        return new Builder(token, tenantId, email)
                .invitedByUserId(invitedByUserId)
                .platformRole(platformRole)
                .jobRole(jobRole)
                .status(status)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .acceptedAt(acceptedAt)
                .attributes(attributes);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static Builder builder(String token, String tenantId, String email) {
        return new Builder(token, tenantId, email);
    }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserInvitation that)) {
            return false;
        }
        return Objects.equals(token, that.token)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(email, that.email)
                && Objects.equals(invitedByUserId, that.invitedByUserId)
                && platformRole == that.platformRole
                && Objects.equals(jobRole, that.jobRole)
                && status == that.status
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(expiresAt, that.expiresAt)
                && Objects.equals(acceptedAt, that.acceptedAt)
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, tenantId, email, invitedByUserId, platformRole, jobRole,
                status, createdAt, expiresAt, acceptedAt, attributes);
    }

    @Override
    public String toString() {
        return "UserInvitation[token=" + token + ", tenantId=" + tenantId + ", email=" + email
                + ", invitedByUserId=" + invitedByUserId + ", platformRole=" + platformRole
                + ", jobRole=" + jobRole + ", status=" + status + ", createdAt=" + createdAt
                + ", expiresAt=" + expiresAt + ", acceptedAt=" + acceptedAt
                + ", attributes=" + attributes + "]";
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {
        private final String token;
        private final String tenantId;
        private final String email;
        private String          invitedByUserId;
        private TenantRole      platformRole = TenantRole.MEMBER;
        private String          jobRole;
        private InvitationStatus status = InvitationStatus.PENDING;
        private Instant         createdAt  = Instant.now();
        private Instant         expiresAt  = Instant.now().plus(Duration.ofHours(48));
        private Instant         acceptedAt;
        private final Map<String, String> attributes = new HashMap<>();

        private Builder(String token, String tenantId, String email) {
            this.token    = Objects.requireNonNull(token,    "token must not be null");
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
            this.email    = Objects.requireNonNull(email,    "email must not be null");
        }

        public Builder invitedByUserId(String id)      { this.invitedByUserId = id;   return this; }
        public Builder platformRole(TenantRole r)      { this.platformRole = Objects.requireNonNull(r); return this; }
        public Builder jobRole(String role)            { this.jobRole = role;          return this; }
        public Builder status(InvitationStatus s)      { this.status = Objects.requireNonNull(s);      return this; }
        public Builder createdAt(Instant t)            { this.createdAt = Objects.requireNonNull(t);   return this; }
        public Builder expiresAt(Instant t)            { this.expiresAt = Objects.requireNonNull(t);   return this; }
        public Builder validity(Duration d)            { this.expiresAt = createdAt.plus(d);           return this; }
        public Builder acceptedAt(Instant t)           { this.acceptedAt = t;          return this; }
        public Builder attribute(String k, String v)   { this.attributes.put(k, v);    return this; }

        /** Copies all entries from {@code attrs} into the attributes map (null-safe). */
        public Builder attributes(Map<String, String> attrs) {
            if (attrs != null) this.attributes.putAll(attrs);
            return this;
        }

        public UserInvitation build() {
            return new UserInvitation(token, tenantId, email, invitedByUserId, platformRole,
                    jobRole, status, createdAt, expiresAt, acceptedAt, Map.copyOf(attributes));
        }
    }
}


