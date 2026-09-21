package com.holonplatform.multitenant.demo.ui;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

/**
 * Shell for every signed-in page.
 *
 * <h2>Menu entries reflect permissions</h2>
 * <p>Each navigation entry is added only when the signed-in user actually holds the permission
 * that guards the target view, so a SALESMAN never sees an Accounting link and vice versa.
 *
 * <p>Hiding a link is a usability measure, not a security measure: the views themselves
 * re-check the same permission on entry (see {@link PermissionGuardedView}), so typing the URL
 * directly is refused just the same.
 *
 * <p>The layout carries {@code @PermitAll} in its own right: Vaadin evaluates the access
 * annotations of a route's parent layouts as well as the route itself, and denies the route
 * outright when a layout declares none. Every route using this layout is already restricted to
 * authenticated users, so admitting any of them here changes nothing.
 */
@PermitAll
public class MainLayout extends AppLayout implements BeforeEnterObserver {

    private final boolean tenantBound;

    public MainLayout(TenantPermissionService permissionService, AuthenticationContext authenticationContext) {

        this.tenantBound = TenantContext.get().isPresent();

        String tenantId = TenantContext.get().map(tenant -> tenant.tenantId()).orElse("-");
        String user     = authenticationContext.getPrincipalName().orElse("-");

        SideNav nav = new SideNav();
        // Every entry below asks the permission service, which resolves the caller to a tenant
        // user — impossible without a bound tenant. When the workspace has been suspended the
        // menu is therefore left empty and beforeEnter reroutes away entirely.
        if (tenantBound) {
            nav.addItem(new SideNavItem("Dashboard", DashboardView.class));
            if (permissionService.currentUserHasPermission(DemoRoles.USER_INVITE)) {
                nav.addItem(new SideNavItem("Team", TeamView.class));
            }
            if (permissionService.currentUserHasPermission(DemoRoles.SALES_VIEW)) {
                nav.addItem(new SideNavItem("Sales", SalesView.class));
            }
            if (permissionService.currentUserHasPermission(DemoRoles.ACCOUNTING_VIEW)) {
                nav.addItem(new SideNavItem("Accounting", AccountingView.class));
            }
            if (permissionService.currentUserHasPermission(DemoRoles.SETTINGS_MANAGE)) {
                nav.addItem(new SideNavItem("Settings", SettingsView.class));
            }
            if (permissionService.currentUserHasPermission(DemoRoles.AUDIT_VIEW)) {
                nav.addItem(new SideNavItem("Audit log", AuditView.class));
            }
            if (permissionService.currentUserHasPermission(DemoRoles.PLAN_MANAGE)) {
                nav.addItem(new SideNavItem("Plan", PlanView.class));
            }
            if (permissionService.currentUserHasPermission(DemoRoles.ROLES_MANAGE)) {
                nav.addItem(new SideNavItem("Roles", RolesView.class));
                nav.addItem(new SideNavItem("Internals", InternalsView.class));
            }
        }

        Button logout = new Button("Sign out", event -> authenticationContext.logout());

        HorizontalLayout header = new HorizontalLayout(new DrawerToggle(),
                new H3("Holon SaaS demo"),
                new Span("workspace: " + tenantId),
                new Span(user),
                logout);
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        header.expand(header.getComponentAt(1));

        addToNavbar(header);
        addToDrawer(new VerticalLayout(nav));
    }

    /**
     * Sends a member whose workspace has been suspended to a page that says so.
     *
     * <p>An authenticated session with no tenant bound can only mean the framework's filter
     * declined to bind one, and the only reason it does that for a tenant member is a
     * non-active status.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!tenantBound && !WorkspaceSuspendedView.class.equals(event.getNavigationTarget())) {
            event.rerouteTo(WorkspaceSuspendedView.class);
        }
    }
}
