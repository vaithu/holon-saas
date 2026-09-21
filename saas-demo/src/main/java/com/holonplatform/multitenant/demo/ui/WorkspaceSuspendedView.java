package com.holonplatform.multitenant.demo.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

/**
 * Where a member lands when their workspace has been suspended underneath them (F14).
 *
 * <h2>Why a page rather than an error</h2>
 * <p>Suspension is enforced at the point the tenant would be bound: the framework's
 * {@code AuthenticatedPrincipalTenantContextFilter} declines to bind a non-active tenant and
 * lets the request continue unbound. That is the right behaviour for the framework — it has no
 * idea what the application wants to do about it — but it means the session survives while
 * every tenant-scoped query underneath it has lost its schema.
 *
 * <p>Left alone, the user would meet a stack trace or a mysteriously empty screen. {@code
 * MainLayout} therefore detects the unbound state and reroutes here, which turns an internal
 * failure mode into a plain statement of what happened.
 *
 * <p>Deliberately carries no layout: the shell it would otherwise sit in is the very thing that
 * cannot render without a tenant.
 */
@Route("workspace-suspended")
@PageTitle("Workspace suspended")
@PermitAll
public class WorkspaceSuspendedView extends VerticalLayout {

    public WorkspaceSuspendedView(AuthenticationContext authenticationContext) {
        setPadding(true);
        setSpacing(true);

        add(new H2("This workspace is suspended"),
                new Paragraph("The platform operator has taken this workspace out of service. "
                        + "Your account and your data are intact — access resumes as soon as the "
                        + "workspace is reactivated."),
                new Button("Sign out", event -> authenticationContext.logout()));
    }
}
