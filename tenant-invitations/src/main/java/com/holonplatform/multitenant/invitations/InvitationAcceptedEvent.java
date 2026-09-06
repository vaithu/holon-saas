package com.holonplatform.multitenant.invitations;

import com.holonplatform.multitenant.users.TenantUser;
import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published when a user successfully accepts an invitation.
 *
 * <p>Listen to this event to create the Spring Security credentials for the new user.
 * The {@code rawPassword} field contains the plaintext password the user submitted
 * on the accept form — hash it before storing.</p>
 *
 * <pre>{@code
 * @EventListener
 * public void onInvitationAccepted(InvitationAcceptedEvent event) {
 *     TenantUser user = event.getTenantUser();
 *     String encoded = passwordEncoder.encode(event.getRawPassword());
 *     // Store user credentials in your Spring Security UserDetails store
 *     userCredentialsRepository.save(new UserCredentials(user.email(), encoded));
 * }
 * }</pre>
 */
public class InvitationAcceptedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final UserInvitation invitation;
    private final TenantUser     tenantUser;
    private final String         rawPassword;

    public InvitationAcceptedEvent(Object source, UserInvitation invitation,
                                   TenantUser tenantUser, String rawPassword) {
        super(source);
        this.invitation  = invitation;
        this.tenantUser  = tenantUser;
        this.rawPassword = rawPassword;
    }

    /** The accepted invitation record (status = ACCEPTED). */
    public UserInvitation getInvitation() { return invitation; }

    /** The newly created and activated {@link TenantUser}. */
    public TenantUser getTenantUser() { return tenantUser; }

    /**
     * The raw password submitted by the user on the accept form.
     * Hash this before storing — never persist it in plaintext.
     */
    public String getRawPassword() { return rawPassword; }
}

