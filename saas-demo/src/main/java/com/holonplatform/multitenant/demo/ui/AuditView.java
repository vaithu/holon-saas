package com.holonplatform.multitenant.demo.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.AuditEntry;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.demo.service.ReportService;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.security.TenantFeatureNotEnabledException;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;

import jakarta.annotation.security.PermitAll;

/**
 * F11 and F12 — the workspace's audit trail, behind a feature gate.
 *
 * <p>Nothing on this page writes audit entries: every row was produced by the {@code @Audited}
 * annotation on a service method elsewhere in the demo, including the failures.
 *
 * <p>Reaching the page needs the {@code AUDIT_VIEW} permission, but <em>reading</em> also needs
 * the workspace to hold the {@code AUDIT_LOG} feature — which a FREE workspace does not. That
 * refusal is shown on the page rather than as an error, because it is a sales message rather
 * than a fault.
 */
@Route(value = "audit", layout = MainLayout.class)
@PageTitle("Audit log")
@SecuredView(id = "audit", displayName = "Audit log", category = "Administration", actions = {"READ"})
@PermitAll
public class AuditView extends PermissionGuardedView {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** A null selection means "every action". */
    private static final AuditAction ALL_ACTIONS = null;

    private final ReportService reportService;

    private final Grid<AuditEntry> grid    = new Grid<>(AuditEntry.class, false);
    private final VerticalLayout   message = new VerticalLayout();

    public AuditView(TenantPermissionService permissionService, ReportService reportService) {
        super(permissionService, DemoRoles.AUDIT_VIEW);
        this.reportService = reportService;

        Select<AuditAction> action = new Select<>();
        action.setLabel("Action");
        action.setItems(AuditAction.CREATE, AuditAction.USER_INVITED, AuditAction.PLAN_CHANGED);
        action.setEmptySelectionAllowed(true);
        action.setEmptySelectionCaption("All actions");
        action.setValue(ALL_ACTIONS);
        action.addValueChangeListener(event -> refresh(event.getValue()));

        grid.addColumn(entry -> TIMESTAMP.format(entry.getCreatedAt())).setHeader("When");
        grid.addColumn(entry -> entry.getAction().name()).setHeader("Action");
        grid.addColumn(AuditEntry::getActor).setHeader("Actor");
        grid.addColumn(AuditEntry::getResourceType).setHeader("Resource");
        grid.addColumn(AuditEntry::getResourceId).setHeader("Id");
        grid.addColumn(entry -> entry.getSeverity().name()).setHeader("Severity");
        grid.addColumn(AuditEntry::getDescription).setHeader("Description");
        grid.setAllRowsVisible(true);

        message.setPadding(false);

        add(new H2("Audit log"),
                new Paragraph("Every entry below was written by the framework's @Audited aspect, "
                        + "not by this page."),
                action, message, grid);

        refresh(ALL_ACTIONS);
    }

    private void refresh(AuditAction action) {
        message.removeAll();
        try {
            List<AuditEntry> entries = (action == ALL_ACTIONS)
                    ? reportService.recentAuditEntries(tenantId(), 100)
                    : reportService.auditEntriesByAction(tenantId(), action, 100);

            grid.setItems(entries);
            grid.setVisible(true);

        } catch (TenantFeatureNotEnabledException e) {
            grid.setVisible(false);
            message.add(new Paragraph("Audit log unavailable: " + e.getMessage()));
        }
    }

    private static String tenantId() {
        return TenantContext.get()
                .map(tenant -> tenant.tenantId())
                .orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"));
    }
}
