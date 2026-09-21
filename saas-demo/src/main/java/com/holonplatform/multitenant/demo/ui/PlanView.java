package com.holonplatform.multitenant.demo.ui;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantFeature;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.demo.service.DemoPlanService;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.demo.service.ReportService;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.security.TenantPlanRequiredException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;

import jakarta.annotation.security.PermitAll;

/**
 * F12 — the workspace's plan, and what changing it unlocks.
 *
 * <p>Exporting a report is guarded by {@code @RequiresPlan(PROFESSIONAL)}. On a FREE workspace
 * the button reports a refusal; after switching the plan here the same button succeeds, without
 * anything about the user or their role having changed. That is the distinction this page is
 * for: role decides who, plan decides what the workspace has bought.
 *
 * <p>The plan change takes effect on the next request, because the tenant is re-read and bound
 * per request — so the page reloads itself rather than trying to patch what is already on
 * screen.
 */
@Route(value = "plan", layout = MainLayout.class)
@PageTitle("Plan")
@SecuredView(id = "plan", displayName = "Plan", category = "Administration", actions = {"READ", "UPDATE"})
@PermitAll
public class PlanView extends PermissionGuardedView {

    private final DemoPlanService planService;
    private final ReportService   reportService;

    public PlanView(TenantPermissionService permissionService,
                    DemoPlanService planService,
                    ReportService reportService) {
        super(permissionService, DemoRoles.PLAN_MANAGE);
        this.planService   = planService;
        this.reportService = reportService;

        TenantDetails tenant = tenant();

        Select<TenantPlan> plan = new Select<>();
        plan.setLabel("Plan");
        plan.setItems(TenantPlan.values());
        plan.setValue(tenant.plan());

        Button change = new Button("Change plan", event -> changePlan(plan.getValue()));
        Button export = new Button("Export sales report", event -> exportReport());

        add(new H2("Plan"),
                new Paragraph("Current plan: " + tenant.plan().name()),
                new Paragraph("Features: " + describeFeatures(tenant)),
                new HorizontalLayout(plan, change),
                new Paragraph("Exporting a report requires the PROFESSIONAL plan or better."),
                export);
    }

    private void changePlan(TenantPlan newPlan) {
        planService.changePlan(tenantId(), newPlan);
        Notification.show("Workspace moved to " + newPlan.name())
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        // The bound tenant belongs to the request that is already in flight; a fresh navigation
        // picks up the new plan.
        getUI().ifPresent(ui -> ui.getPage().reload());
    }

    private void exportReport() {
        try {
            Notification.show(reportService.exportSalesReport(tenantId()))
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (TenantPlanRequiredException e) {
            Notification.show("Denied: " + e.getMessage())
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    /** Lists the features the workspace holds, whether from its plan or granted individually. */
    private static String describeFeatures(TenantDetails tenant) {
        String held = Arrays.stream(TenantFeature.values())
                .filter(feature -> tenant.hasFeature(feature.key()))
                .map(TenantFeature::key)
                .collect(Collectors.joining(", "));
        return held.isEmpty() ? "none" : held;
    }

    private static TenantDetails tenant() {
        return TenantContext.get()
                .orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"));
    }

    private static String tenantId() {
        return tenant().tenantId();
    }
}
