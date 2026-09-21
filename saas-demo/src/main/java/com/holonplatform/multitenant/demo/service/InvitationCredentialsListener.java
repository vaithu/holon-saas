package com.holonplatform.multitenant.demo.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.holonplatform.multitenant.demo.account.PlatformAccountStore;
import com.holonplatform.multitenant.invitations.InvitationAcceptedEvent;

/**
 * Gives an invited user a password.
 *
 * <p>The framework's invitation flow creates the tenant <em>membership</em> but deliberately
 * stores no credentials — it has no opinion about how an application authenticates. It instead
 * publishes an {@link InvitationAcceptedEvent} carrying the password the invitee just chose,
 * which is the intended hook for an application to persist its own credentials.
 *
 * <p>Sign-up does not need an equivalent listener because {@code SignupService} writes the
 * owner's credentials through the {@code AccountCredentialsStore} SPI itself.
 */
@Component
public class InvitationCredentialsListener {

    private static final Logger log = LoggerFactory.getLogger(InvitationCredentialsListener.class);

    private final PlatformAccountStore accountStore;
    private final PasswordEncoder      passwordEncoder;

    public InvitationCredentialsListener(PlatformAccountStore accountStore, PasswordEncoder passwordEncoder) {
        this.accountStore    = Objects.requireNonNull(accountStore, "accountStore must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    }

    @EventListener
    public void onInvitationAccepted(InvitationAcceptedEvent event) {
        String tenantId = event.getInvitation().tenantId();
        String email    = event.getInvitation().email();

        accountStore.save(tenantId, email, passwordEncoder.encode(event.getRawPassword()));
        log.info("Stored credentials for invited user '{}' in workspace '{}'", email, tenantId);
    }
}
