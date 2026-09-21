package com.holonplatform.multitenant.demo.ui;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.demo.service.DemoTenantProvisioner;
import com.holonplatform.multitenant.demo.config.PlatformAdmin;
import com.holonplatform.multitenant.signup.SignupException;
import com.holonplatform.multitenant.signup.SignupService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Step 1 of the demo: self-service tenant sign-up.
 *
 * <p>Submitting this form provisions a complete tenant — a dedicated {@code tenant_<id>} schema
 * with the framework's per-tenant tables migrated into it, an owner membership, and the owner's
 * credentials — after which the workspace can immediately be logged into.
 */
@Route("signup")
@PageTitle("Create a workspace")
@AnonymousAllowed
public class SignupView extends VerticalLayout {

    public SignupView(DemoTenantProvisioner provisioner) {
        setPadding(true);
        setSpacing(true);
        setMaxWidth("520px");

        TextField     company   = new TextField("Company name");
        TextField     workspace = new TextField("Workspace id");
        TextField     email     = new TextField("Your email");
        PasswordField password  = new PasswordField("Choose a password");
        company.setWidthFull();
        workspace.setWidthFull();
        email.setWidthFull();
        password.setWidthFull();
        workspace.setHelperText("Lowercase letters, digits and hyphens; 3-64 characters.");

        // Offer a valid workspace id derived from the company name, using the framework's
        // own slug helper so the suggestion always satisfies its validation rules.
        company.addValueChangeListener(event -> {
            if (workspace.isEmpty() && event.getValue() != null && !event.getValue().isBlank()) {
                workspace.setValue(SignupService.slugify(event.getValue()));
            }
        });

        Button create = new Button("Create workspace", event -> {
            try {
                String workspaceId = workspace.getValue().trim();
                // "platform" addresses the operator console in the login form, so letting a
                // tenant claim it would make the operator unreachable.
                if (PlatformAdmin.WORKSPACE.equalsIgnoreCase(workspaceId)) {
                    Notification.show("'" + PlatformAdmin.WORKSPACE + "' is a reserved workspace id")
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                TenantDetails tenant = provisioner.signUpWorkspace(
                        workspaceId,
                        company.getValue().trim(),
                        email.getValue().trim(),
                        password.getValue());

                Notification.show("Workspace '" + tenant.tenantId() + "' is ready - please sign in")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.getPage().setLocation("/login"));

            } catch (SignupException e) {
                Notification.show(e.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        add(new H2("Create your workspace"),
                new Paragraph("Each workspace gets its own database schema, provisioned and migrated on sign-up."),
                company, workspace, email, password, create,
                new Anchor("/login", "I already have an account"));
    }
}
