package com.holonplatform.multitenant.invitations.internal;

import com.holonplatform.multitenant.invitations.InvitationAcceptedEvent;
import com.holonplatform.multitenant.invitations.InvitationRequest;
import com.holonplatform.multitenant.invitations.InvitationSender;
import com.holonplatform.multitenant.invitations.InvitationStatus;
import com.holonplatform.multitenant.invitations.TenantInvitationService;
import com.holonplatform.multitenant.invitations.UserInvitation;
import com.holonplatform.multitenant.invitations.exception.InvitationAlreadyAcceptedException;
import com.holonplatform.multitenant.invitations.exception.InvitationException;
import com.holonplatform.multitenant.invitations.exception.InvitationExpiredException;
import com.holonplatform.multitenant.invitations.exception.InvitationNotFoundException;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Database-backed {@link TenantInvitationService} implementation using Holon Datastore.
 *
 * <p>Persists all invitation data to the relational database via
 * {@link UserInvitationDatastoreHelper}, replacing the previous in-memory
 * {@code DefaultTenantInvitationService}'s {@code ConcurrentHashMap} indexes while
 * preserving all of its business rules: email format validation, duplicate-pending-
 * invitation-per-email rejection on {@link #invite(InvitationRequest)}, expiry and
 * already-accepted checks on {@link #accept(String, String)}, silent no-op
 * {@link #revoke(String, String)} for non-pending/foreign-tenant invitations, and
 * {@link #cleanupExpired()} marking PENDING+expired invitations as EXPIRED.
 */
@Service
public class DatabaseTenantInvitationService implements TenantInvitationService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTenantInvitationService.class);

    /** Attribute key used to store the CRM job role on the {@code TenantUser} created upon acceptance. */
    static final String JOB_ROLE_ATTRIBUTE = "job_role";

    /**
     * Minimal RFC 5322 email validator.  Rejects clearly invalid addresses without
     * allowing header-injection characters ({@code \r}, {@code \n}).
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s\r\n]+@[^@\\s\r\n]+\\.[^@\\s\r\n]+$");

    private final UserInvitationDatastoreHelper datastoreHelper;
    private final TenantUserService             tenantUserService;
    private final ApplicationEventPublisher     eventPublisher;
    private final InvitationSender              invitationSender;
    private final String                        acceptBaseUrl;

    public DatabaseTenantInvitationService(UserInvitationDatastoreHelper datastoreHelper,
                                           TenantUserService tenantUserService,
                                           ApplicationEventPublisher eventPublisher,
                                           InvitationSender invitationSender,
                                           String acceptBaseUrl) {
        this.datastoreHelper  = Objects.requireNonNull(datastoreHelper, "datastoreHelper must not be null");
        this.tenantUserService = Objects.requireNonNull(tenantUserService, "tenantUserService must not be null");
        this.eventPublisher    = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.invitationSender  = invitationSender;
        this.acceptBaseUrl     = acceptBaseUrl != null ? acceptBaseUrl : "";
    }

    // ── Invite ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public UserInvitation invite(InvitationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        // ── Security: validate email format ──────────────────────────────────
        if (!EMAIL_PATTERN.matcher(request.email()).matches()) {
            throw new InvitationException(
                    "Invalid email address: '" + request.email() + "'");
        }

        // ── Reject if a PENDING invitation already exists for this email+tenant ──
        if (datastoreHelper.findPendingByTenantIdAndEmail(request.tenantId(), request.email()).isPresent()) {
            throw new InvitationException("A pending invitation already exists for '"
                    + request.email() + "' in tenant '" + request.tenantId() + "'");
        }

        String token = UUID.randomUUID().toString();
        UserInvitation invitation = UserInvitation.builder(token, request.tenantId(), request.email())
                .invitedByUserId(request.invitedByUserId())
                .platformRole(request.platformRole())
                .jobRole(request.jobRole())
                .validity(request.validity())
                .attributes(request.attributes())
                .build();

        datastoreHelper.save(invitation);

        log.info("Invitation created for '{}' in tenant '{}' (token: {}, expires: {})",
                request.email(), request.tenantId(), token, invitation.expiresAt());

        if (invitationSender != null) {
            String link = acceptBaseUrl + "/" + token;
            try {
                invitationSender.send(invitation, link);
                log.debug("Invitation email sent to '{}' (link: {})", request.email(), link);
            } catch (Exception ex) {
                log.error("Failed to send invitation email to '{}': {}", request.email(), ex.getMessage(), ex);
            }
        } else {
            log.warn("No InvitationSender bean found — invitation created but no email was sent. Token: {}", token);
        }

        return invitation;
    }

    // ── Accept ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TenantUser accept(String token, String rawPassword) {
        Objects.requireNonNull(token, "token must not be null");

        // ── Step 1: read the current invitation (fail-fast if unknown) ────────
        UserInvitation invitation = datastoreHelper.findByToken(token)
                .orElseThrow(() -> new InvitationNotFoundException(token));
        if (invitation.status() != InvitationStatus.PENDING) {
            throw new InvitationAlreadyAcceptedException(token, invitation.status().name());
        }
        if (invitation.isExpired()) {
            datastoreHelper.save(invitation.withStatus(InvitationStatus.EXPIRED));
            throw new InvitationExpiredException(token);
        }

        // ── Step 2: PENDING → ACCEPTED ─────────────────────────────────────────
        UserInvitation acceptedInv = invitation.accepted();
        datastoreHelper.save(acceptedInv);

        // ── Step 3: Create and activate the user ─────────────────────────────
        // Deliberately the single acceptInvitation(...) call, NOT invite() + activate():
        // the invitee is unauthenticated (they hold only the token validated in Step 1), and
        // both of those methods are @RequiresPermission-gated, so composing them here made
        // every acceptance fail with PermissionDeniedException.
        String userId = UUID.randomUUID().toString();
        Map<String, String> userAttributes = new HashMap<>();
        if (invitation.jobRole() != null && !invitation.jobRole().isBlank()) {
            userAttributes.put(JOB_ROLE_ATTRIBUTE, invitation.jobRole());
        }
        TenantUser active = tenantUserService.acceptInvitation(
                invitation.tenantId(), userId, invitation.email(), invitation.platformRole(), userAttributes);

        log.info("Invitation accepted: user '{}' joined tenant '{}'", invitation.email(), invitation.tenantId());
        eventPublisher.publishEvent(new InvitationAcceptedEvent(this, acceptedInv, active, rawPassword));
        return active;
    }

    // ── Revoke / Query ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void revoke(String tenantId, String token) {
        Optional<UserInvitation> found = datastoreHelper.findByToken(token);
        if (found.isPresent()) {
            UserInvitation inv = found.get();
            if (inv.tenantId().equals(tenantId) && inv.status() == InvitationStatus.PENDING) {
                datastoreHelper.save(inv.withStatus(InvitationStatus.REVOKED));
                log.info("Invitation revoked: token '{}' in tenant '{}'", token, tenantId);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserInvitation> findByToken(String token) {
        return datastoreHelper.findByToken(token);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserInvitation> findPendingByTenant(String tenantId) {
        return datastoreHelper.findByTenantIdAndStatus(tenantId, InvitationStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserInvitation> findByEmail(String tenantId, String email) {
        return datastoreHelper.findByTenantIdAndEmail(tenantId, email);
    }

    @Override
    @Transactional
    public int cleanupExpired() {
        List<UserInvitation> expired = datastoreHelper.findAllExpiredPending(java.time.Instant.now());
        for (UserInvitation inv : expired) {
            datastoreHelper.save(inv.withStatus(InvitationStatus.EXPIRED));
        }
        int count = expired.size();
        if (count > 0) {
            log.info("Expired {} pending invitation(s)", count);
        }
        return count;
    }

    @Override
    @Transactional
    public void deleteAll(String tenantId) {
        long count = datastoreHelper.deleteByTenantId(tenantId);
        log.debug("Deleted {} invitation(s) for tenant '{}'", count, tenantId);
    }
}
