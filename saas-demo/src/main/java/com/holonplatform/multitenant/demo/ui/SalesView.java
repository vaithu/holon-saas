package com.holonplatform.multitenant.demo.ui;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.demo.service.DemoLimits;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.demo.service.SalesService;
import com.holonplatform.multitenant.limits.RateLimitExceededException;
import com.holonplatform.multitenant.limits.TenantRateLimiter;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.permissions.exception.PermissionDeniedException;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;

import jakarta.annotation.security.PermitAll;

/**
 * View-level authorization, half one: reachable only with {@link DemoRoles#SALES_VIEW}.
 *
 * <p>The button additionally exercises method-level authorization by calling
 * {@code SalesService#recordSale}, which is guarded by {@code @RequiresPermission}. The two
 * checks are independent: entering the view proves the permission was granted for navigation,
 * while the service call is re-checked by the framework's aspect on the server.
 */
@Route(value = "sales", layout = MainLayout.class)
@PageTitle("Sales")
@SecuredView(id = "sales", displayName = "Sales", category = "Sales", actions = {"READ", "CREATE", "EXPORT"})
@PermitAll
public class SalesView extends PermissionGuardedView {

    private final TenantRateLimiter rateLimiter;
    private final Paragraph         budget = new Paragraph();

    public SalesView(TenantPermissionService permissionService,
                     SalesService salesService,
                     TenantRateLimiter rateLimiter) {
        super(permissionService, DemoRoles.SALES_VIEW);
        this.rateLimiter = rateLimiter;

        TextField customer = new TextField("Customer");
        TextField amount   = new TextField("Amount");
        amount.setValue("100");

        Button record = new Button("Record sale", event -> {
            try {
                String entry = salesService.recordSale(customer.getValue(), parse(amount.getValue()));
                Notification.show("Recorded: " + entry).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (RateLimitExceededException e) {
                Notification.show("Slow down: " + e.getMessage())
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } catch (PermissionDeniedException e) {
                Notification.show("Denied: " + e.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
            refreshBudget();
        });

        Button export = new Button("Export sales", event -> exportSales(permissionService));

        add(new H2("Sales"),
                new Paragraph("Workspace: " + TenantContext.get().map(t -> t.tenantId()).orElse("-")
                        + ". Recording a sale requires the SALES_RECORD permission."),
                budget,
                customer, amount, record,
                new Paragraph("Exporting is governed by the per-tenant role matrix, not by a flat "
                        + "permission — the owner decides on the Roles page whether SALESMAN may export."),
                export);

        refreshBudget();
    }

    /**
     * The F16 payoff: a check whose answer is owned by the tenant, not by the product.
     *
     * <p>{@link DemoRoles#SALES_EXPORT_ACTION} is a {@code viewId:ACTION} key, so the permission
     * service routes it to the role matrix rather than the flat store. Nothing about Alice
     * changes when this starts succeeding — only what her workspace's owner has granted the
     * SALESMAN role on the {@code sales} view.
     */
    private void exportSales(TenantPermissionService permissionService) {
        if (!permissionService.currentUserHasPermission(DemoRoles.SALES_EXPORT_ACTION)) {
            Notification.show("Denied: your role has no EXPORT permission on the sales view")
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        Notification.show("Sales exported")
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    /**
     * Shows how many sales the workspace may still record this minute. The count is per
     * workspace, not per user, so a second signed-in colleague draws from the same bucket.
     */
    private void refreshBudget() {
        TenantContext.get().ifPresent(tenant -> budget.setText(
                "Remaining this minute: "
                        + rateLimiter.remaining(tenant.tenantId(), DemoLimits.SALES_RECORD_LIMIT_KEY)
                        + " of " + DemoLimits.SALES_RECORDS_PER_MINUTE));
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
