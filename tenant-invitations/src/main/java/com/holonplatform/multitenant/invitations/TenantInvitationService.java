package com.holonplatform.multitenant.invitations;

import com.holonplatform.multitenant.invitations.exception.InvitationAlreadyAcceptedException;
import com.holonplatform.multitenant.invitations.exception.InvitationExpiredException;
import com.holonplatform.multitenant.invitations.exception.InvitationNotFoundException;
import com.holonplatform.multitenant.users.TenantUser;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing email-based user invitations within a tenant.
 *
 * <h2>Invitation lifecycle</h2>
 * <pre>
 *   invite()  → PENDING  (email sent via InvitationSender)
 *   accept()  → ACCEPTED (TenantUser created + InvitationAcceptedEvent fired)
 *   revoke()  → REVOKED  (admin action before acceptance)
 *   cleanup() → EXPIRED  (batch job marks expired PENDING invitations)
 * </pre>
 *
 * <h2>Handling acceptance (set user credentials)</h2>
 * <p>Listen to {@link InvitationAcceptedEvent} to create the Spring Security
 * user entry with the user's chosen password:
 * <pre>{@code
 * @EventListener
 * public void onInvitationAccepted(InvitationAcceptedEvent event) {
 *     TenantUser user = event.getTenantUser();
 *     String rawPassword = event.getRawPassword(); // from the accept form
 *     securityUserRepo.createUser(user.email(), passwordEncoder.encode(rawPassword));
 * }
 * }</pre>
 */
public interface TenantInvitationService {

    /**
     * Creates a new invitation and (if an {@link InvitationSender} bean is present)
     * sends the invitation email.
     *
     * @param request invitation parameters — must not be null
     * @return the created {@link UserInvitation} in {@link InvitationStatus#PENDING} state
     * @throws com.holonplatform.multitenant.invitations.exception.InvitationException
     *         if the email already has a pending invitation for this tenant
     */
    UserInvitation invite(InvitationRequest request);

    /**
     * Accepts an invitation: creates the {@link TenantUser}, fires
     * {@link InvitationAcceptedEvent}, and marks the invitation as ACCEPTED.
     *
     * <p>The {@code rawPassword} is passed through the event so the application
     * can hash and store it in its own user-credentials store.
     *
     * @param token       the invitation token from the email link
     * @param rawPassword the password chosen by the user on the accept form
     * @return the newly created {@link TenantUser}
     * @throws InvitationNotFoundException      if no invitation exists for this token
     * @throws InvitationExpiredException       if the invitation has expired
     * @throws InvitationAlreadyAcceptedException if already accepted or revoked
     */
    TenantUser accept(String token, String rawPassword);

    /**
     * Revokes a pending invitation. Has no effect if already accepted or expired.
     *
     * @param tenantId tenant context — must not be null
     * @param token    invitation token — must not be null
     */
    void revoke(String tenantId, String token);

    /**
     * Finds an invitation by its token.
     *
     * @param token the invitation token
     * @return the invitation, or empty if not found
     */
    Optional<UserInvitation> findByToken(String token);

    /**
     * Returns all PENDING invitations for a tenant.
     *
     * @param tenantId tenant — must not be null
     * @return list of pending invitations, newest first
     */
    List<UserInvitation> findPendingByTenant(String tenantId);

    /**
     * Returns all invitations (in any state) for a given email within a tenant.
     *
     * @param tenantId tenant — must not be null
     * @param email    email address — must not be null
     * @return list of matching invitations
     */
    List<UserInvitation> findByEmail(String tenantId, String email);

    /**
     * Marks all PENDING invitations whose expiry has passed as EXPIRED.
     * Intended to be called from a scheduled job.
     *
     * @return number of invitations expired
     */
    int cleanupExpired();

    /**
     * Deletes all invitation records for a tenant (use during tenant offboarding).
     *
     * @param tenantId tenant — must not be null
     */
    void deleteAll(String tenantId);
}

