package com.holonplatform.multitenant.demo.ui.admin;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.billing.Invoice;
import com.holonplatform.multitenant.billing.Subscription;
import com.holonplatform.multitenant.demo.config.PlatformAdmin;
import com.holonplatform.multitenant.demo.service.PlatformConsoleService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * F15 — the subscription and invoice history of a chosen workspace.
 *
 * <h2>Why this page has to pick a workspace first</h2>
 * <p>Unlike the tenant registry, the billing tables are created <em>inside each tenant's
 * schema</em> by the onboarding migration. There is no single table holding every tenant's
 * invoices, so there is no such thing as a cross-tenant invoice list: the operator selects a
 * workspace, and {@code PlatformConsoleService} binds that tenant for the duration of each
 * call. Selecting is not a filter, it is what makes the query addressable.
 *
 * <h2>A plan change writes to both planes</h2>
 * <p>Changing the plan here updates the subscription and raises an invoice in the tenant's
 * schema, and updates the tenant registry in the control plane. Only the latter is what
 * {@code @RequiresPlan} consults, so skipping it would leave a workspace paying for a plan it
 * is still refused the features of.
 */
@Route(value = "admin/billing", layout = AdminLayout.class)
@PageTitle("Billing")
@RolesAllowed(PlatformAdmin.ROLE)
public class AdminBillingView extends VerticalLayout {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final PlatformConsoleService console;

    private final Select<String>  workspace    = new Select<>();
    private final Select<TenantPlan> plan      = new Select<>();
    private final Paragraph       subscription = new Paragraph();
    private final Grid<Invoice>   invoices     = new Grid<>(Invoice.class, false);

    public AdminBillingView(PlatformConsoleService console) {
        this.console = console;
        setPadding(true);
        setSpacing(true);

        List<String> workspaceIds = console.list(0, 100, "").stream()
                .map(TenantDetails::tenantId)
                .toList();

        workspace.setLabel("Workspace");
        workspace.setItems(workspaceIds);
        workspace.addValueChangeListener(event -> reload());

        plan.setLabel("Move to plan");
        plan.setItems(TenantPlan.values());

        Button change = new Button("Change plan and invoice", event -> changePlan());
        Button cancel = new Button("Cancel subscription", event -> cancelSubscription());

        invoices.addColumn(invoice -> TIMESTAMP.format(invoice.issuedAt()))
                .setHeader("Issued").setAutoWidth(true);
        invoices.addColumn(invoice -> invoice.currency() + " " + invoice.amountDue())
                .setHeader("Amount").setAutoWidth(true);
        invoices.addColumn(invoice -> invoice.status().name()).setHeader("Status").setAutoWidth(true);
        invoices.addColumn(Invoice::externalRef).setHeader("Reason").setAutoWidth(true);
        invoices.setAllRowsVisible(true);

        add(new H2("Billing"),
                new Paragraph("Subscriptions and invoices live in the selected workspace's own schema, "
                        + "so each call below binds that tenant before querying."),
                workspace,
                subscription,
                new HorizontalLayout(plan, change, cancel),
                invoices);

        if (!workspaceIds.isEmpty()) {
            workspace.setValue(workspaceIds.getFirst());
        }
    }

    private void changePlan() {
        if (!selected() || plan.getValue() == null) {
            Notification.show("Pick a workspace and a plan first");
            return;
        }
        console.changePlan(workspace.getValue(), plan.getValue());
        Notification.show("Plan changed and invoice raised")
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        reload();
    }

    private void cancelSubscription() {
        if (!selected()) {
            Notification.show("Pick a workspace first");
            return;
        }
        console.cancelSubscription(workspace.getValue());
        Notification.show("Subscription cancelled")
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
        reload();
    }

    private void reload() {
        if (!selected()) {
            return;
        }
        String tenantId = workspace.getValue();
        Subscription current = console.subscriptionOf(tenantId);
        subscription.setText("Subscription: " + current.plan().name()
                + " — " + current.status().name()
                + " — since " + TIMESTAMP.format(current.periodStart())
                + " — " + console.countInvoices(tenantId) + " invoice(s)");
        plan.setValue(current.plan());
        invoices.setItems(console.invoicesOf(tenantId, 0, 50));
    }

    private boolean selected() {
        return workspace.getValue() != null && !workspace.getValue().isBlank();
    }
}
