package com.holonplatform.multitenant.demo.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.annotation.Audited;
import com.holonplatform.multitenant.invitations.InvitationRequest;
import com.holonplatform.multitenant.invitations.TenantInvitationService;
import com.holonplatform.multitenant.invitations.UserInvitation;
import com.holonplatform.multitenant.quotas.QuotaKeys;
import com.holonplatform.multitenant.quotas.annotation.EnforceQuota;
import com.holonplatform.multitenant.users.TenantRole;

/**
 * Wraps invitation creation so it is subject to the workspace's user quota (F9) and recorded in
 * the audit log (F11).
 *
 * <h2>Why the demo wraps the framework service instead of calling it directly</h2>
 * <p>{@code TenantInvitationService} is framework code and knows nothing about which quota an
 * application wants to charge an invitation against — a workspace might count seats, or count
 * only certain roles, or not cap membership at all. Quota enforcement therefore belongs to the
 * application, and {@code @EnforceQuota} is designed to be applied to the application's own
 * method. The framework aspect runs the check before this method body executes, so a workspace
 * that is already at its limit never creates the invitation at all.
 *
 * <p>The quota is charged against <em>current members</em>, not pending invitations: the
 * built-in usage provider counts rows in {@code tenant_users}. A workspace can therefore have
 * more invitations outstanding than seats, and the seat is taken at acceptance.
 */
@Service
public class TeamService {

    private final TenantInvitationService invitationService;

    public TeamService(TenantInvitationService invitationService) {
        this.invitationService = Objects.requireNonNull(invitationService,
                "invitationService must not be null");
    }

    /**
     * Invites someone to the workspace with the given job role.
     *
     * @throws com.holonplatform.multitenant.quotas.exception.QuotaExceededException
     *         if the workspace already holds as many members as its plan allows
     */
    @EnforceQuota(value = "max_users",
            message = "This workspace has as many members as its plan allows. "
                    + "Upgrade the plan to invite more people.")
    @Audited(action = AuditAction.USER_INVITED,
            resourceType = "Invitation",
            resourceIdExpression = "#email",
            description = "User invited to the workspace")
    public UserInvitation invite(String tenantId, String email, String jobRole) {
        return invitationService.invite(InvitationRequest.builder(tenantId, email)
                .platformRole(TenantRole.MEMBER)
                .jobRole(jobRole)
                .build());
    }

    /**
     * The quota key charged by {@link #invite}, exposed so the Team view can display current
     * usage using the same key the aspect enforces.
     */
    public static String quotaKey() {
        return QuotaKeys.MAX_USERS.key();
    }
}
