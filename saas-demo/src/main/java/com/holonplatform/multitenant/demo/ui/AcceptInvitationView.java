package com.holonplatform.multitenant.demo.ui;

import com.holonplatform.multitenant.TenantContextTemplate;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.invitations.TenantInvitationService;
import com.holonplatform.multitenant.quotas.TenantQuotaService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.WildcardParameter;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Step 4 of the demo: an invited user accepts their invitation and chooses a password.
 *
 * <h2>Why the link carries the workspace as well as the token</h2>
 * <p>Invitations are stored <em>inside each tenant's own schema</em>, so a token cannot be
 * looked up until the right tenant is bound to the {@code TenantContext}. The invitee is not
 * logged in and sends no tenant signal of their own, so the workspace travels in the URL and is
 * bound explicitly here via {@link TenantContextTemplate}.
 *
 * <p>Accepting is not permission-gated: authority comes from possession of a valid, unexpired,
 * single-use token, which the framework validates before creating the membership.
 */
@Route("accept-invite")
@PageTitle("Accept invitation")
@AnonymousAllowed
public class AcceptInvitationView extends VerticalLayout implements HasUrlParameter<String> {

    private final TenantInvitationService invitationService;
    private final TenantDetailsLoader     tenantDetailsLoader;
    private final TenantQuotaService      quotaService;

    private String tenantId;
    private String token;

    public AcceptInvitationView(TenantInvitationService invitationService,
                                TenantDetailsLoader tenantDetailsLoader,
                                TenantQuotaService quotaService) {
        this.invitationService   = invitationService;
        this.tenantDetailsLoader = tenantDetailsLoader;
        this.quotaService        = quotaService;
        setPadding(true);
        setSpacing(true);
        setMaxWidth("520px");
    }

    @Override
    public void setParameter(BeforeEvent event, @WildcardParameter String parameter) {
        removeAll();

        // The invitation link has the shape /accept-invite/{workspace}/{token}
        String[] parts = parameter == null ? new String[0] : parameter.split("/");
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            add(new H2("Invalid invitation link"),
                    new Paragraph("Expected a link of the form /accept-invite/{workspace}/{token}."));
            return;
        }
        this.tenantId = parts[0];
        this.token    = parts[1];

        PasswordField password = new PasswordField("Choose a password");
        password.setWidthFull();

        Button accept = new Button("Join workspace", click -> acceptInvitation(password.getValue()));

        add(new H2("Join " + tenantId),
                new Paragraph("You have been invited to the '" + tenantId + "' workspace."),
                password, accept);
    }

    private void acceptInvitation(String password) {
        try {
            TenantDetails tenant = tenantDetailsLoader.load(tenantId);

            // Bind the tenant so the invitation (and the new membership) resolve to the right
            // schema, then let the framework validate and consume the token.
            TenantContextTemplate.run(tenant, () -> invitationService.accept(token, password));

            // Accepting takes a seat without going through @EnforceQuota, so the cached member
            // count would otherwise stay stale until it expired — and the Team view would
            // report fewer members than it lists.
            quotaService.invalidateUsageCacheForTenant(tenantId);

            Notification.show("Welcome aboard - please sign in")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            getUI().ifPresent(ui -> ui.getPage().setLocation("/login"));

        } catch (RuntimeException e) {
            Notification.show("Could not accept this invitation: " + e.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
