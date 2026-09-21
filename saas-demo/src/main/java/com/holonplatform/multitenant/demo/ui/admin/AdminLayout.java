package com.holonplatform.multitenant.demo.ui.admin;

import com.holonplatform.multitenant.demo.config.PlatformAdmin;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.RolesAllowed;

/**
 * Shell for the platform operator console.
 *
 * <p>A separate layout from {@code MainLayout}, not a section within it, because the two are
 * mutually exclusive by construction: {@code MainLayout} reads the bound tenant and gates its
 * menu on tenant permissions, and the operator has neither a tenant nor a tenant user record.
 *
 * <p>The guard is {@link RolesAllowed} rather than the demo's tenant permission service for the
 * same reason — {@code TenantPermissionService} resolves the caller to a tenant user, which for
 * an unbound principal finds nothing and would deny everything. Vaadin evaluates the access
 * annotations of a route's parent layouts as well as the route itself, so this annotation alone
 * already bars every admin route to anyone but the operator; each view repeats it so that the
 * guard survives the layout being changed.
 */
@RolesAllowed(PlatformAdmin.ROLE)
public class AdminLayout extends AppLayout {

    public AdminLayout(AuthenticationContext authenticationContext) {

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Workspaces", AdminTenantsView.class));
        nav.addItem(new SideNavItem("Billing", AdminBillingView.class));

        Button logout = new Button("Sign out", event -> authenticationContext.logout());

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(),
                new H3("Holon SaaS platform console"),
                new Span(authenticationContext.getPrincipalName().orElse("-")),
                logout);
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.expand(header.getComponentAt(1));

        addToNavbar(header);
        addToDrawer(new VerticalLayout(nav));
    }
}
