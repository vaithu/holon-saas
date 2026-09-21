package com.holonplatform.multitenant.demo.ui;

import com.holonplatform.multitenant.demo.config.DemoAuthenticator;
import com.holonplatform.multitenant.demo.config.PlatformAdmin;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;

/**
 * Step 2 and step 5 of the demo: signing in to a workspace.
 *
 * <p>The form asks for the workspace as well as the email because an account is identified by
 * the pair — the same person can be a member of several workspaces with the same address.
 */
@Route("login")
@PageTitle("Sign in")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    public LoginView(DemoAuthenticator authenticator) {
        setPadding(true);
        setSpacing(true);
        setMaxWidth("420px");

        TextField     workspace = new TextField("Workspace");
        TextField     email     = new TextField("Email");
        PasswordField password  = new PasswordField("Password");
        workspace.setWidthFull();
        email.setWidthFull();
        password.setWidthFull();
        workspace.setPlaceholder("acme-corp");

        Button signIn = new Button("Sign in", event -> {
            try {
                authenticator.login(workspace.getValue(), email.getValue(), password.getValue());
                // A full page load, not a router navigation: the session was just changed, and
                // reloading lets the tenant filters re-run against the new principal.
                String landing = PlatformAdmin.WORKSPACE.equals(workspace.getValue().trim())
                        ? "/admin/tenants"
                        : "/";
                getUI().ifPresent(ui -> ui.getPage().setLocation(landing));
            } catch (DisabledException e) {
                // Raised when the workspace exists but has been suspended by the platform
                // operator (F14). Spring's DaoAuthenticationProvider performs this check
                // before it verifies the password, so this message appears whether or not
                // the credentials were right.
                Notification.show("This workspace is suspended. Contact the platform operator.")
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            } catch (AuthenticationException e) {
                Notification.show("Invalid workspace, email or password")
                        .addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
            }
        });
        signIn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        add(new H2("Sign in"),
                new Paragraph("Enter the workspace you belong to, then your credentials."),
                workspace, email, password, signIn,
                new Anchor("/signup", "Create a new workspace instead"));
    }
}
