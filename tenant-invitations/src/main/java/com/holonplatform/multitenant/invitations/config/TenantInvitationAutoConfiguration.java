package com.holonplatform.multitenant.invitations.config;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.invitations.InvitationSender;
import com.holonplatform.multitenant.invitations.TenantInvitationService;
import com.holonplatform.multitenant.invitations.internal.DatabaseTenantInvitationService;
import com.holonplatform.multitenant.invitations.internal.UserInvitationDatastoreHelper;
import com.holonplatform.multitenant.users.TenantUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for the tenant invitation subsystem.
 *
 * <h2>What is registered</h2>
 * <ul>
 *   <li>A {@link DatabaseTenantInvitationService} — Holon Datastore-backed implementation,
 *       requiring a {@code Datastore} bean (e.g. via {@code holon-datastore-jpa}) to be
 *       configured for this module to start.</li>
 * </ul>
 *
 * <h2>Configuration properties</h2>
 * <pre>
 * # Base URL prepended to the invitation token to form the accept link
 * holon.saas.invitations.accept-base-url=https://yourapp.com/accept-invite
 * </pre>
 *
 * <h2>Providing an email sender</h2>
 * <p>Declare an {@link InvitationSender} bean to send invitation emails:
 * <pre>{@code
 * @Bean
 * public InvitationSender invitationSender(JavaMailSender mail) {
 *     return (invitation, link) -> {
 *         SimpleMailMessage msg = new SimpleMailMessage();
 *         msg.setTo(invitation.email());
 *         msg.setSubject("You've been invited!");
 *         msg.setText("Accept here: " + link);
 *         mail.send(msg);
 *     };
 * }
 * }</pre>
 */
@AutoConfiguration
@Import(TenantInvitationEntityScanRegistrar.class)
public class TenantInvitationAutoConfiguration {

    /**
     * Registers the {@link UserInvitationDatastoreHelper} explicitly — it is not
     * {@code @Component}-scanned (it lives in an internal package outside any consuming
     * application's own base package), so this module must construct it itself.
     */
    @Bean
    @ConditionalOnMissingBean(UserInvitationDatastoreHelper.class)
    public UserInvitationDatastoreHelper userInvitationDatastoreHelper(Datastore datastore) {
        return new UserInvitationDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(TenantInvitationService.class)
    public DatabaseTenantInvitationService tenantInvitationService(
            UserInvitationDatastoreHelper datastoreHelper,
            TenantUserService tenantUserService,
            ApplicationEventPublisher eventPublisher,
            // Optional — no bean = no email sent
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            InvitationSender invitationSender,
            @Value("${holon.saas.invitations.accept-base-url:}") String acceptBaseUrl) {
        return new DatabaseTenantInvitationService(
                datastoreHelper, tenantUserService, eventPublisher, invitationSender, acceptBaseUrl);
    }
}

