package com.holonplatform.multitenant.invitations;

/**
 * SPI for sending invitation emails.
 *
 * <p>Declare a Spring bean implementing this interface to connect the invitation
 * system to your email provider. If no bean is present, invitations are still
 * created in the store but no email is sent (useful for testing).</p>
 *
 * <p>Example with Spring Mail:</p>
 * <pre>{@code
 * @Component
 * public class SmtpInvitationSender implements InvitationSender {
 *
 *     @Autowired JavaMailSender mailSender;
 *
 *     @Override
 *     public void send(UserInvitation invitation, String invitationLink) {
 *         SimpleMailMessage msg = new SimpleMailMessage();
 *         msg.setTo(invitation.email());
 *         msg.setSubject("You have been invited to " + invitation.tenantId());
 *         msg.setText("Click here to join: " + invitationLink);
 *         mailSender.send(msg);
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface InvitationSender {

    /**
     * Sends the invitation email.
     *
     * @param invitation     the invitation record (never null)
     * @param invitationLink the full accept URL with token, e.g.
     *                       {@code https://app.example.com/accept-invite/UUID-TOKEN}
     */
    void send(UserInvitation invitation, String invitationLink);
}

