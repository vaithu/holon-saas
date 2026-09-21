package com.holonplatform.multitenant.demo.ui;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;
import com.vaadin.flow.spring.security.AuthenticationContext;

import jakarta.annotation.security.PermitAll;

/**
 * Landing page for a signed-in user, showing which workspace the request resolved to and
 * exactly which of the demo's permissions the current role grants.
 *
 * <p>The workspace shown here comes from the {@code TenantContext}, which was bound from the
 * authenticated principal — it is the same value every tenant-aware query will use, so it is a
 * direct read-out of the isolation the framework is applying to this request.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Dashboard")
@SecuredView(id = "dashboard", displayName = "Dashboard", category = "General", actions = {"READ"})
@PermitAll
public class DashboardView extends VerticalLayout {

    public DashboardView(TenantPermissionService permissionService, AuthenticationContext authenticationContext) {
        setPadding(true);
        setSpacing(true);

        String tenantId = TenantContext.get().map(tenant -> tenant.tenantId()).orElse("(none)");
        String user     = authenticationContext.getPrincipalName().orElse("(anonymous)");

        add(new H2("Signed in as " + user),
                new Paragraph("Active workspace: " + tenantId
                        + " - every tenant-aware query on this request reads schema tenant_" + tenantId + "."),
                new H4("Your permissions in this workspace"));

        for (String permission : new String[] {
                DemoRoles.USER_INVITE,
                DemoRoles.SALES_VIEW, DemoRoles.SALES_RECORD,
                DemoRoles.ACCOUNTING_VIEW, DemoRoles.ACCOUNTING_POST }) {

            boolean granted = permissionService.currentUserHasPermission(permission);
            add(new Span((granted ? "GRANTED  " : "DENIED   ") + permission));
        }
    }
}
