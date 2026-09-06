package com.holonplatform.multitenant.invitations;

import com.holonplatform.multitenant.users.TenantRole;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Input object for {@link TenantInvitationService#invite(InvitationRequest)}.
 *
 * @param tenantId        tenant to invite the user to
 * @param email           invitee's email address
 * @param invitedByUserId userId of the admin sending the invite
 * @param platformRole    TenantRole the user will receive (default: MEMBER)
 * @param jobRole         CRM job role (e.g. "ACCOUNTANT", "DRIVER") — stored in user attributes (nullable)
 * @param validity        how long the invitation link remains valid (default: 48 hours)
 * @param attributes      extra metadata passed through to the invitation record
 */
public record InvitationRequest(
        String tenantId,
        String email,
        String invitedByUserId,
        TenantRole platformRole,
        String jobRole,
        Duration validity,
        Map<String, String> attributes
) {
    public InvitationRequest {
        Objects.requireNonNull(tenantId,     "tenantId must not be null");
        Objects.requireNonNull(email,        "email must not be null");
        Objects.requireNonNull(platformRole, "platformRole must not be null");
        if (validity == null) validity = Duration.ofHours(48);
        attributes = attributes != null ? Map.copyOf(attributes) : Map.of();
    }

    public static Builder builder(String tenantId, String email) {
        return new Builder(tenantId, email);
    }

    public static final class Builder {
        private final String tenantId;
        private final String email;
        private String     invitedByUserId;
        private TenantRole platformRole = TenantRole.MEMBER;
        private String     jobRole;
        private Duration   validity = Duration.ofHours(48);
        private final Map<String, String> attributes = new HashMap<>();

        private Builder(String tenantId, String email) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
            this.email    = Objects.requireNonNull(email,    "email must not be null");
        }

        public Builder invitedByUserId(String id)    { this.invitedByUserId = id; return this; }
        public Builder platformRole(TenantRole r)    { this.platformRole = Objects.requireNonNull(r); return this; }
        public Builder jobRole(String role)          { this.jobRole = role;       return this; }
        public Builder validity(Duration d)          { this.validity = Objects.requireNonNull(d);     return this; }
        public Builder attribute(String k, String v) { this.attributes.put(k, v); return this; }

        public InvitationRequest build() {
            return new InvitationRequest(tenantId, email, invitedByUserId, platformRole,
                    jobRole, validity, Map.copyOf(attributes));
        }
    }
}

