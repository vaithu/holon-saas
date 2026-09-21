package com.holonplatform.multitenant.demo.ui;

import java.util.List;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.demo.service.TeamService;
import com.holonplatform.multitenant.invitations.TenantInvitationService;
import com.holonplatform.multitenant.invitations.UserInvitation;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.quotas.QuotaCheckResult;
import com.holonplatform.multitenant.quotas.TenantQuotaService;
import com.holonplatform.multitenant.quotas.exception.QuotaExceededException;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;

import jakarta.annotation.security.PermitAll;

/**
 * Step 3 of the demo: the workspace owner invites colleagues and assigns each a job role.
 *
 * <p>The job role chosen here is what ultimately decides which views and service methods the
 * invitee will be able to reach: it is carried on the invitation, copied onto the new
 * {@code TenantUser} on acceptance, and then resolved against the workspace's permission table
 * on every authorization check.
 *
 * <p>Because the demo has no mail server, the invitation link is rendered on screen instead of
 * being emailed. The link embeds the workspace id as well as the token — see
 * {@link AcceptInvitationView} for why that is necessary.
 */
@Route(value = "team", layout = MainLayout.class)
@PageTitle("Team")
@SecuredView(id = "team", displayName = "Team", category = "Administration", actions = {"READ", "CREATE"})
@PermitAll
public class TeamView extends PermissionGuardedView {

    private final TeamService             teamService;
    private final TenantInvitationService invitationService;
    private final TenantUserService       userService;
    private final TenantQuotaService      quotaService;

    private final VerticalLayout members = new VerticalLayout();
    private final VerticalLayout pending = new VerticalLayout();
    private final Paragraph      quota   = new Paragraph();

    public TeamView(TenantPermissionService permissionService,
                    TeamService teamService,
                    TenantInvitationService invitationService,
                    TenantUserService userService,
                    TenantQuotaService quotaService) {
        super(permissionService, DemoRoles.USER_INVITE);
        this.teamService       = teamService;
        this.invitationService = invitationService;
        this.userService       = userService;
        this.quotaService      = quotaService;

        TextField email = new TextField("Email to invite");
        email.setPlaceholder("alice@example.com");

        Select<String> jobRole = new Select<>();
        jobRole.setLabel("Job role");
        jobRole.setItems(DemoRoles.SALESMAN, DemoRoles.ACCOUNTANT);
        jobRole.setValue(DemoRoles.SALESMAN);

        Button invite = new Button("Send invitation", event -> invite(email.getValue(), jobRole.getValue()));

        HorizontalLayout form = new HorizontalLayout(email, jobRole, invite);
        form.setAlignItems(HorizontalLayout.Alignment.BASELINE);

        members.setPadding(false);
        pending.setPadding(false);

        add(new H2("Team"),
                new Paragraph("Invite a SALESMAN and an ACCOUNTANT to see the two roles diverge."),
                quota,
                form,
                new H4("Members"), members,
                new H4("Pending invitations"), pending);

        refresh();
    }

    private void invite(String email, String jobRole) {
        if (email == null || email.isBlank()) {
            Notification.show("Enter an email address");
            return;
        }
        try {
            teamService.invite(currentTenantId(), email.trim(), jobRole);

            Notification.show("Invitation created for " + email)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refresh();

        } catch (QuotaExceededException e) {
            Notification.show("Quota reached: " + e.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (RuntimeException e) {
            Notification.show("Could not invite: " + e.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void refresh() {
        String tenantId = currentTenantId();

        QuotaCheckResult seats = quotaService.checkQuota(tenantId, TeamService.quotaKey());
        quota.setText("Members: %d of %s allowed on the %s plan"
                .formatted(seats.currentUsage(),
                        seats.unlimited() ? "unlimited" : String.valueOf(seats.limit()),
                        currentPlan()));

        members.removeAll();
        Grid<TenantUser> grid = new Grid<>(TenantUser.class, false);
        grid.addColumn(TenantUser::getEmail).setHeader("Email");
        grid.addColumn(user -> user.getRole().name()).setHeader("Platform role");
        grid.addColumn(user -> user.getAttributes().getOrDefault("job_role", "-")).setHeader("Job role");
        grid.addColumn(user -> user.getStatus().name()).setHeader("Status");
        grid.setItems(userService.findByTenant(tenantId, 0, 100));
        grid.setAllRowsVisible(true);
        members.add(grid);

        pending.removeAll();
        List<UserInvitation> invitations = invitationService.findPendingByTenant(tenantId);
        if (invitations.isEmpty()) {
            pending.add(new Paragraph("No pending invitations."));
        } else {
            for (UserInvitation invitation : invitations) {
                String link = "/accept-invite/" + invitation.tenantId() + "/" + invitation.token();
                pending.add(new HorizontalLayout(
                        new Paragraph(invitation.email() + " (" + invitation.jobRole() + "):"),
                        new Anchor(link, link)));
            }
        }
    }

    private static String currentTenantId() {
        return TenantContext.get()
                .map(tenant -> tenant.tenantId())
                .orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"));
    }

    private static String currentPlan() {
        return TenantContext.get()
                .map(tenant -> tenant.plan().name())
                .orElse("unknown");
    }
}
