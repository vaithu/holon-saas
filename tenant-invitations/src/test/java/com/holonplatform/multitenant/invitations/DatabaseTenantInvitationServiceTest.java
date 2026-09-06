package com.holonplatform.multitenant.invitations;

import com.holonplatform.multitenant.invitations.exception.InvitationAlreadyAcceptedException;
import com.holonplatform.multitenant.invitations.exception.InvitationException;
import com.holonplatform.multitenant.invitations.exception.InvitationExpiredException;
import com.holonplatform.multitenant.invitations.exception.InvitationNotFoundException;
import com.holonplatform.multitenant.invitations.internal.DatabaseTenantInvitationService;
import com.holonplatform.multitenant.invitations.internal.UserInvitationDatastoreHelper;
import com.holonplatform.multitenant.users.TenantRole;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DatabaseTenantInvitationService}.
 *
 * <p>The {@link UserInvitationDatastoreHelper} dependency is a Mockito mock backed by an
 * in-memory {@link ConcurrentHashMap}, so all queries behave like a real database without
 * requiring one. This preserves the business-behavior test cases that were previously
 * exercised against the in-memory {@code DefaultTenantInvitationService}.
 */
class DatabaseTenantInvitationServiceTest {

    private Map<String, UserInvitation> store;
    private UserInvitationDatastoreHelper datastoreHelper;
    private TenantUserService tenantUserService;
    private ApplicationEventPublisher eventPublisher;
    private InvitationSender invitationSender;
    private DatabaseTenantInvitationService service;

    @BeforeEach
    void setUp() {
        store = new ConcurrentHashMap<>();
        datastoreHelper = mock(UserInvitationDatastoreHelper.class);

        when(datastoreHelper.save(any(UserInvitation.class))).thenAnswer(inv -> {
            UserInvitation invitation = inv.getArgument(0);
            store.put(invitation.token(), invitation);
            return invitation;
        });
        when(datastoreHelper.findByToken(anyString())).thenAnswer(inv ->
                Optional.ofNullable(store.get(inv.getArgument(0, String.class))));
        when(datastoreHelper.findByTenantIdAndStatus(anyString(), anyString())).thenAnswer(inv -> {
            String tenantId = inv.getArgument(0, String.class);
            String status = inv.getArgument(1, String.class);
            List<UserInvitation> result = new ArrayList<>(store.values().stream()
                    .filter(i -> i.tenantId().equals(tenantId) && i.status().name().equals(status))
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                    .toList());
            return result;
        });
        when(datastoreHelper.findByTenantIdAndEmail(anyString(), anyString())).thenAnswer(inv -> {
            String tenantId = inv.getArgument(0, String.class);
            String email = inv.getArgument(1, String.class);
            return store.values().stream()
                    .filter(i -> i.tenantId().equals(tenantId) && i.email().equalsIgnoreCase(email))
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                    .toList();
        });
        when(datastoreHelper.findPendingByTenantIdAndEmail(anyString(), anyString())).thenAnswer(inv -> {
            String tenantId = inv.getArgument(0, String.class);
            String email = inv.getArgument(1, String.class);
            return store.values().stream()
                    .filter(i -> i.tenantId().equals(tenantId) && i.email().equalsIgnoreCase(email)
                            && i.status() == InvitationStatus.PENDING)
                    .findFirst();
        });
        when(datastoreHelper.findAllExpiredPending(any(Instant.class))).thenAnswer(inv -> {
            Instant now = inv.getArgument(0, Instant.class);
            return store.values().stream()
                    .filter(i -> i.status() == InvitationStatus.PENDING && i.expiresAt().isBefore(now))
                    .toList();
        });
        when(datastoreHelper.deleteByTenantId(anyString())).thenAnswer(inv -> {
            String tenantId = inv.getArgument(0, String.class);
            List<String> toRemove = store.values().stream()
                    .filter(i -> i.tenantId().equals(tenantId))
                    .map(UserInvitation::token)
                    .toList();
            toRemove.forEach(store::remove);
            return (long) toRemove.size();
        });

        tenantUserService = mock(TenantUserService.class);
        when(tenantUserService.invite(anyString(), anyString(), anyString(), any(TenantRole.class)))
                .thenAnswer(inv -> TenantUser.builder(inv.getArgument(1), inv.getArgument(0), inv.getArgument(2))
                        .role(inv.getArgument(3)).build());
        when(tenantUserService.activate(anyString(), anyString()))
                .thenAnswer(inv -> TenantUser.builder(inv.getArgument(1), inv.getArgument(0), "user@example.com")
                        .build());

        eventPublisher = mock(ApplicationEventPublisher.class);
        invitationSender = mock(InvitationSender.class);

        service = new DatabaseTenantInvitationService(
                datastoreHelper, tenantUserService, eventPublisher, invitationSender,
                "https://app.example.com/accept-invite");
    }

    // ── invite() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("invite creates a PENDING invitation and sends an email")
    void invite_createsPendingInvitation_andSendsEmail() {
        InvitationRequest request = InvitationRequest.builder("acme", "user@example.com")
                .invitedByUserId("admin-1").platformRole(TenantRole.MEMBER).build();

        UserInvitation result = service.invite(request);

        assertThat(result.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(result.tenantId()).isEqualTo("acme");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(service.findByToken(result.token())).contains(result);
        verify(invitationSender, times(1)).send(any(UserInvitation.class), anyString());
    }

    @Test
    @DisplayName("invite works fine when no InvitationSender bean is configured")
    void invite_withoutSender_stillCreatesInvitation() {
        service = new DatabaseTenantInvitationService(
                datastoreHelper, tenantUserService, eventPublisher, null,
                "https://app.example.com/accept-invite");

        UserInvitation result = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());

        assertThat(result.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("invite rejects invalid email addresses")
    void invite_rejectsInvalidEmail() {
        assertThatThrownBy(() -> service.invite(InvitationRequest.builder("acme", "not-an-email").build()))
                .isInstanceOf(InvitationException.class);
    }

    @Test
    @DisplayName("invite throws when a pending invitation already exists for the email")
    void invite_duplicatePending_throws() {
        service.invite(InvitationRequest.builder("acme", "user@example.com").build());

        assertThatThrownBy(() -> service.invite(InvitationRequest.builder("acme", "user@example.com").build()))
                .isInstanceOf(InvitationException.class);
    }

    @Test
    @DisplayName("invite succeeds again after the prior invitation was revoked")
    void invite_afterRevoke_succeeds() {
        UserInvitation first = service.invite(InvitationRequest.builder("acme", "user@example.com").build());
        service.revoke("acme", first.token());

        UserInvitation second = service.invite(InvitationRequest.builder("acme", "user@example.com").build());

        assertThat(second.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("invite succeeds again after the prior invitation was accepted")
    void invite_afterAccept_succeeds() {
        UserInvitation first = service.invite(InvitationRequest.builder("acme", "user@example.com").build());
        service.accept(first.token(), "S3cret!");

        UserInvitation second = service.invite(InvitationRequest.builder("acme", "user@example.com").build());

        assertThat(second.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("invite throws NullPointerException for a null request")
    void invite_null_throwsNpe() {
        assertThatThrownBy(() -> service.invite(null)).isInstanceOf(NullPointerException.class);
    }

    // ── accept() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept creates and activates a TenantUser and publishes InvitationAcceptedEvent")
    void accept_createsAndActivatesUser_andPublishesEvent() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "user@example.com").platformRole(TenantRole.ADMIN).build());

        TenantUser activeUser = service.accept(invitation.token(), "S3cret!");

        assertThat(activeUser).isNotNull();
        assertThat(activeUser.getTenantId()).isEqualTo("acme");
        assertThat(activeUser.getEmail()).isEqualTo("user@example.com");
        assertThat(service.findByToken(invitation.token())).get()
                .extracting(UserInvitation::status).isEqualTo(InvitationStatus.ACCEPTED);

        verify(eventPublisher, times(1)).publishEvent(any(InvitationAcceptedEvent.class));
    }

    @Test
    @DisplayName("accept passes the raw password through the InvitationAcceptedEvent")
    void accept_passesRawPasswordInEvent() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());

        service.accept(invitation.token(), "Sup3rSecret!");

        org.mockito.ArgumentCaptor<InvitationAcceptedEvent> captor =
                org.mockito.ArgumentCaptor.forClass(InvitationAcceptedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getRawPassword()).isEqualTo("Sup3rSecret!");
    }

    @Test
    @DisplayName("accept throws InvitationNotFoundException for an unknown token")
    void accept_unknownToken_throwsNotFound() {
        assertThatThrownBy(() -> service.accept("unknown-token", "pwd"))
                .isInstanceOf(InvitationNotFoundException.class);
    }

    @Test
    @DisplayName("accept throws InvitationExpiredException and marks the invitation EXPIRED")
    void accept_expiredInvitation_throwsExpired_andMarksExpired() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());
        expireNow(invitation);

        assertThatThrownBy(() -> service.accept(invitation.token(), "pwd"))
                .isInstanceOf(InvitationExpiredException.class);

        assertThat(service.findByToken(invitation.token())).get()
                .extracting(UserInvitation::status).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    @DisplayName("accept throws InvitationAlreadyAcceptedException when already accepted")
    void accept_alreadyAccepted_throws() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());
        service.accept(invitation.token(), "pwd1");

        assertThatThrownBy(() -> service.accept(invitation.token(), "pwd2"))
                .isInstanceOf(InvitationAlreadyAcceptedException.class);
    }

    @Test
    @DisplayName("accept throws InvitationAlreadyAcceptedException when revoked")
    void accept_revoked_throwsAlreadyAccepted() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());
        service.revoke("acme", invitation.token());

        assertThatThrownBy(() -> service.accept(invitation.token(), "pwd"))
                .isInstanceOf(InvitationAlreadyAcceptedException.class);
    }

    @Test
    @DisplayName("accept throws NullPointerException for a null token")
    void accept_nullToken_throwsNpe() {
        assertThatThrownBy(() -> service.accept(null, "pwd")).isInstanceOf(NullPointerException.class);
    }

    // ── revoke() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("revoke changes a pending invitation's status to REVOKED")
    void revoke_pending_changesStatus() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());

        service.revoke("acme", invitation.token());

        assertThat(service.findByToken(invitation.token())).get()
                .extracting(UserInvitation::status).isEqualTo(InvitationStatus.REVOKED);
    }

    @Test
    @DisplayName("revoke is a silent no-op for the wrong tenant")
    void revoke_wrongTenant_noOp() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());

        service.revoke("other-tenant", invitation.token());

        assertThat(service.findByToken(invitation.token())).get()
                .extracting(UserInvitation::status).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    @DisplayName("revoke is a silent no-op when already accepted")
    void revoke_alreadyAccepted_noOp() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());
        service.accept(invitation.token(), "pwd");

        service.revoke("acme", invitation.token());

        assertThat(service.findByToken(invitation.token())).get()
                .extracting(UserInvitation::status).isEqualTo(InvitationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("revoke is a silent no-op for an unknown token")
    void revoke_unknownToken_noOp() {
        service.revoke("acme", "unknown-token");
        // no exception thrown
    }

    // ── findPendingByTenant() / findByEmail() ────────────────────────────────

    @Test
    @DisplayName("findPendingByTenant returns only PENDING invitations, newest first")
    void findPendingByTenant_returnsOnlyPending_newestFirst() throws InterruptedException {
        UserInvitation first = service.invite(
                InvitationRequest.builder("acme", "user1@example.com").build());
        Thread.sleep(5);
        UserInvitation second = service.invite(
                InvitationRequest.builder("acme", "user2@example.com").build());
        service.accept(first.token(), "pwd");

        List<UserInvitation> pending = service.findPendingByTenant("acme");

        assertThat(pending).extracting(UserInvitation::token).containsExactly(second.token());
    }

    @Test
    @DisplayName("findByEmail returns invitations for the email regardless of status, case-insensitively")
    void findByEmail_returnsAllStatuses_caseInsensitive() {
        UserInvitation invitation = service.invite(
                InvitationRequest.builder("acme", "User@Example.com").build());
        service.revoke("acme", invitation.token());

        List<UserInvitation> found = service.findByEmail("acme", "user@example.com");

        assertThat(found).extracting(UserInvitation::token).containsExactly(invitation.token());
    }

    @Test
    @DisplayName("findPendingByTenant and findByEmail respect tenant isolation")
    void queries_respectTenantIsolation() {
        service.invite(InvitationRequest.builder("acme", "user@example.com").build());
        service.invite(InvitationRequest.builder("globex", "user@example.com").build());

        assertThat(service.findPendingByTenant("acme")).hasSize(1);
        assertThat(service.findPendingByTenant("globex")).hasSize(1);
    }

    // ── cleanupExpired() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("cleanupExpired marks expired PENDING invitations as EXPIRED and returns the count")
    void cleanupExpired_marksExpiredPending_returnsCount() {
        UserInvitation expired = service.invite(
                InvitationRequest.builder("acme", "expired@example.com").build());
        expireNow(expired);
        UserInvitation stillValid = service.invite(
                InvitationRequest.builder("acme", "valid@example.com").build());
        UserInvitation acceptedOne = service.invite(
                InvitationRequest.builder("acme", "accepted@example.com").build());
        service.accept(acceptedOne.token(), "pwd");
        // Even though its validity window has technically passed, an ACCEPTED
        // invitation must never be reported/mutated by cleanupExpired.
        expireNow(service.findByToken(acceptedOne.token()).orElseThrow());

        int count = service.cleanupExpired();

        assertThat(count).isEqualTo(1);
        assertThat(service.findByToken(expired.token())).get()
                .extracting(UserInvitation::status).isEqualTo(InvitationStatus.EXPIRED);
        assertThat(service.findByToken(stillValid.token())).get()
                .extracting(UserInvitation::status).isEqualTo(InvitationStatus.PENDING);
        assertThat(service.findByToken(acceptedOne.token())).get()
                .extracting(UserInvitation::status).isEqualTo(InvitationStatus.ACCEPTED);
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Directly rewrites the stored invitation's expiry to a moment in the past, deterministically. */
    private void expireNow(UserInvitation invitation) {
        UserInvitation stored = store.get(invitation.token());
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        store.put(stored.token(), stored);
    }

    // ── deleteAll() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAll removes only the target tenant's invitations")
    void deleteAll_removesOnlyTargetTenant() {
        UserInvitation acmeInv = service.invite(
                InvitationRequest.builder("acme", "user@example.com").build());
        UserInvitation globexInv = service.invite(
                InvitationRequest.builder("globex", "user@example.com").build());

        service.deleteAll("acme");

        assertThat(service.findByToken(acmeInv.token())).isEmpty();
        assertThat(service.findByToken(globexInv.token())).isPresent();
        assertThat(service.findByToken(globexInv.token()).get().token()).isEqualTo(globexInv.token());
    }
}
