package com.holonplatform.multitenant.demo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.holonplatform.multitenant.demo.account.WorkspaceDirectory;
import com.holonplatform.multitenant.demo.account.WorkspaceMembership;
import com.holonplatform.multitenant.demo.config.DemoAuthenticator;
import com.holonplatform.multitenant.demo.config.PlatformAdmin;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
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

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

/**
 * Step 2 and step 5 of the demo: signing in.
 *
 * <h2>Why the form no longer asks for the workspace</h2>
 * <p>An account is still identified by the {@code workspace/email} pair — the same person may
 * be a member of several workspaces with the same address — but making the person type the
 * workspace id put the burden of remembering an internal identifier on them. Instead the view
 * derives the candidate workspaces from the address
 * ({@link WorkspaceDirectory#membershipsOf(String)}) and checks the submitted password against
 * each one:
 * <ul>
 *   <li>no workspace accepts the password — the ordinary "invalid email or password", which
 *       deliberately does not distinguish an unknown address from a wrong password;</li>
 *   <li>exactly one — sign in to it straight away, so the common case is unchanged;</li>
 *   <li>more than one — show the chooser, listing the person's name, role and workspace in
 *       each, which are the three facts that actually tell the workspaces apart.</li>
 * </ul>
 *
 * <p>The candidates are probed with {@link DemoAuthenticator#verify}, which does not touch the
 * session, and only the workspace the user settles on is
 * {@link DemoAuthenticator#establish}ed. That ordering matters twice over: the chooser never
 * lists a workspace the submitted password does not actually open (so it cannot be used to
 * discover where an address holds accounts), and probing cannot leave a half-formed session
 * behind.
 */
@Route("login")
@PageTitle("Sign in")
@AnonymousAllowed
public class LoginView extends VerticalLayout {

    private final DemoAuthenticator  authenticator;
    private final WorkspaceDirectory workspaces;

    public LoginView(DemoAuthenticator authenticator, WorkspaceDirectory workspaces) {
        this.authenticator = authenticator;
        this.workspaces    = workspaces;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("420px");

        TextField     email    = new TextField("Email");
        PasswordField password = new PasswordField("Password");
        email.setWidthFull();
        password.setWidthFull();

        Button signIn = new Button("Sign in", event -> attemptSignIn(email.getValue(), password.getValue()));
        signIn.addClickShortcut(Key.ENTER);

        add(new H2("Sign in"),
                new Paragraph("Enter your credentials - we will find the workspaces you belong to."),
                email, password, signIn,
                new Anchor("/signup", "Create a new workspace instead"));
    }

    private void attemptSignIn(String email, String password) {
        List<Candidate> accepted  = new ArrayList<>();
        boolean         suspended = false;

        for (WorkspaceMembership membership : workspaces.membershipsOf(email)) {
            try {
                accepted.add(new Candidate(membership,
                        authenticator.verify(membership.workspaceId(), email, password)));
            } catch (DisabledException e) {
                // The workspace exists and has been suspended by the platform operator (F14).
                // Spring's DaoAuthenticationProvider performs this check before it verifies the
                // password, so this is raised whether or not the credentials were right — and
                // it is only reported when no other workspace let the person in.
                suspended = true;
            } catch (AuthenticationException e) {
                // Wrong password for this workspace, or the account vanished between the
                // directory lookup and here. Either way it is simply not a candidate.
            }
        }

        if (accepted.isEmpty()) {
            error(suspended
                    ? "This workspace is suspended. Contact the platform operator."
                    : "Invalid email or password");
            return;
        }
        if (accepted.size() == 1) {
            enter(accepted.getFirst());
            return;
        }
        chooseWorkspace(accepted, this::enter);
    }

    /**
     * Asks which of several workspaces to enter.
     *
     * <p>Modal and not dismissable by Esc or an outside click: the password has already been
     * verified at this point, so a stray click would otherwise throw the successful
     * authentication away and force a retype.
     */
    private void chooseWorkspace(List<Candidate> candidates, Consumer<Candidate> onChosen) {
        Grid<Candidate> grid = new Grid<>();
        grid.addColumn(candidate -> candidate.membership().userName()).setHeader("Name");
        grid.addColumn(candidate -> candidate.membership().role()).setHeader("Role");
        grid.addColumn(LoginView::workspaceLabel).setHeader("Workspace");
        grid.setItems(candidates);
        grid.setAllRowsVisible(true);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Choose a workspace");
        dialog.setModal(true);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.add(new Paragraph("You belong to more than one workspace. Pick the one to sign in to."),
                grid);

        Button open = new Button("Open workspace", event -> grid.getSelectedItems().stream().findFirst()
                .ifPresentOrElse(chosen -> {
                    dialog.close();
                    onChosen.accept(chosen);
                }, () -> error("Select a workspace first")));
        Button cancel = new Button("Cancel", event -> dialog.close());
        dialog.getFooter().add(cancel, open);

        // Double-clicking a row is the gesture people try first in a chooser like this.
        grid.addItemDoubleClickListener(event -> {
            dialog.close();
            onChosen.accept(event.getItem());
        });

        dialog.open();
    }

    private void enter(Candidate candidate) {
        authenticator.establish(candidate.authentication());

        // A full page load, not a router navigation: the session was just changed, and
        // reloading lets the tenant filters re-run against the new principal.
        String landing = PlatformAdmin.WORKSPACE.equals(candidate.membership().workspaceId())
                ? "/admin/tenants"
                : "/";
        getUI().ifPresent(ui -> ui.getPage().setLocation(landing));
    }

    private static void error(String message) {
        Notification.show(message).addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    /**
     * The workspace as the chooser names it.
     *
     * <p>The id is appended to the display name because two workspaces may well be called the
     * same thing — the id is the only part guaranteed to be unique, and it is also what the
     * person sees afterwards in the dashboard and in invitation links.
     */
    private static String workspaceLabel(Candidate candidate) {
        WorkspaceMembership membership = candidate.membership();
        return membership.workspaceName().equals(membership.workspaceId())
                ? membership.workspaceId()
                : membership.workspaceName() + " (" + membership.workspaceId() + ")";
    }

    /** A workspace the submitted password opened, paired with the authentication it produced. */
    private record Candidate(WorkspaceMembership membership, Authentication authentication) {
    }
}
