package com.holonplatform.multitenant.demo.ui;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.demo.service.AccountingService;
import com.holonplatform.multitenant.demo.service.DemoRoles;
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
 * View-level authorization, half two: reachable only with {@link DemoRoles#ACCOUNTING_VIEW}.
 *
 * <p>Together with {@link SalesView} this gives the demo its symmetry — the SALESMAN can open
 * Sales but not Accounting, the ACCOUNTANT the reverse, and each can invoke only their own
 * service method.
 */
@Route(value = "accounting", layout = MainLayout.class)
@PageTitle("Accounting")
@SecuredView(id = "accounting", displayName = "Accounting", category = "Finance", actions = {"READ", "CREATE"})
@PermitAll
public class AccountingView extends PermissionGuardedView {

    public AccountingView(TenantPermissionService permissionService, AccountingService accountingService) {
        super(permissionService, DemoRoles.ACCOUNTING_VIEW);

        TextField account = new TextField("Account");
        TextField amount  = new TextField("Amount");
        amount.setValue("250");

        Button post = new Button("Post ledger entry", event -> {
            try {
                String entry = accountingService.postLedgerEntry(account.getValue(), parse(amount.getValue()));
                Notification.show("Posted: " + entry).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (PermissionDeniedException e) {
                Notification.show("Denied: " + e.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        add(new H2("Accounting"),
                new Paragraph("Workspace: " + TenantContext.get().map(t -> t.tenantId()).orElse("-")
                        + ". Posting an entry requires the ACCOUNTING_POST permission."),
                account, amount, post);
    }

    private static long parse(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
