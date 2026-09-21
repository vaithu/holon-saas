package com.holonplatform.multitenant.demo.ui;

import java.util.List;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.permissions.view.RoleDefinition;
import com.holonplatform.multitenant.permissions.view.RoleMatrixItem;
import com.holonplatform.multitenant.permissions.view.TenantRoleManagementService;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * F16 — the per-tenant role and permission matrix.
 *
 * <h2>What makes this different from the flat permissions</h2>
 * <p>The rest of the demo guards things with flat keys like {@code SALES_RECORD}, seeded once
 * per workspace at sign-up. Those are fixed: every workspace's SALESMAN means the same thing.
 * The matrix shown here is the opposite — the baseline is only a default, and a workspace owner
 * can grant or revoke any action on any registered view for any role, for their workspace
 * alone. Two tenants can run the same product with genuinely different notions of what a
 * SALESMAN may do.
 *
 * <p>The rows come from the {@code ViewRegistry} (F17): each registered view contributes one
 * row per action it declares. A view that never registers itself cannot be granted here at all.
 *
 * <h2>The demonstration to watch</h2>
 * <p>The baseline gives SALESMAN {@code READ} and {@code CREATE} on the sales view, but not
 * {@code EXPORT} — so Alice's Export button is refused. Ticking {@code sales / EXPORT} for
 * SALESMAN here writes a tenant override, and the very same button then works, with no change
 * to Alice's account, role or plan. "Reset to defaults" takes it away again.
 */
@Route(value = "roles", layout = MainLayout.class)
@PageTitle("Roles")
@SecuredView(id = "roles", displayName = "Roles", category = "Administration",
        actions = {"READ", "UPDATE"})
@PermitAll
public class RolesView extends PermissionGuardedView {

    private final TenantRoleManagementService roleService;

    private final Select<String>        role   = new Select<>();
    private final Grid<RoleMatrixItem>  matrix = new Grid<>(RoleMatrixItem.class, false);

    public RolesView(TenantPermissionService permissionService,
                     TenantRoleManagementService roleService) {
        super(permissionService, DemoRoles.ROLES_MANAGE);
        this.roleService = roleService;

        // Labelled distinctly rather than just "Role": the matrix checkboxes carry accessible
        // names like "roles READ", which an accessible-name lookup for "Role" also matches.
        role.setLabel("Role to edit");
        role.addValueChangeListener(event -> refreshMatrix());

        Button reset = new Button("Reset role to defaults", event -> resetRole());

        TextField newRole = new TextField("New custom role");
        newRole.setPlaceholder("JUNIOR_SALESMAN");
        Select<String> baseRole = new Select<>();
        baseRole.setLabel("Cloned from");
        baseRole.setItems(DemoRoles.SALESMAN, DemoRoles.ACCOUNTANT);
        baseRole.setValue(DemoRoles.SALESMAN);
        Button create = new Button("Create role",
                event -> createRole(newRole.getValue(), baseRole.getValue()));

        matrix.addColumn(RoleMatrixItem::viewDisplayName).setHeader("View").setAutoWidth(true);
        matrix.addColumn(RoleMatrixItem::category).setHeader("Category").setAutoWidth(true);
        matrix.addColumn(RoleMatrixItem::action).setHeader("Action").setAutoWidth(true);
        matrix.addComponentColumn(this::allowedCheckbox).setHeader("Allowed").setAutoWidth(true);
        matrix.addColumn(item -> item.customized() ? "overridden" : "inherited")
                .setHeader("Source").setAutoWidth(true);
        matrix.setAllRowsVisible(true);

        add(new H2("Roles and permissions"),
                new Paragraph("Rows come from the view registry — one per action each registered view "
                        + "declares. Ticking a box writes an override that applies to this workspace only."),
                new HorizontalLayout(role, reset),
                matrix,
                new H4("Custom roles"),
                new Paragraph("A custom role starts from a system role's baseline and can then diverge "
                        + "from it, without the system role itself changing."),
                new HorizontalLayout(newRole, baseRole, create));

        refreshRoles();
    }

    /**
     * The grant/revoke control for one matrix cell.
     *
     * <p>Every change is written as an explicit override — including one that happens to agree
     * with the baseline, which is why the Source column can read "overridden" for a value that
     * looks unchanged. "Reset to defaults" is what removes overrides and restores inheritance.
     */
    private Checkbox allowedCheckbox(RoleMatrixItem item) {
        Checkbox checkbox = new Checkbox(item.allowed());
        checkbox.getElement().setAttribute("aria-label", item.viewId() + " " + item.action());
        checkbox.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                roleService.setActionOverride(tenantId(), role.getValue(),
                        item.viewId(), item.action(), event.getValue());
                Notification.show((event.getValue() ? "Granted " : "Revoked ")
                                + item.viewId() + ":" + item.action() + " for " + role.getValue())
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                refreshMatrix();
            }
        });
        return checkbox;
    }

    private void createRole(String roleName, String base) {
        if (roleName == null || roleName.isBlank()) {
            Notification.show("Enter a role name");
            return;
        }
        try {
            roleService.createCustomRole(tenantId(), roleName.trim(),
                    roleName.trim(), "Created from the demo Roles view", base);
            Notification.show("Custom role " + roleName.trim().toUpperCase() + " created")
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshRoles();
        } catch (IllegalArgumentException e) {
            // Raised when the name collides with a reserved system role.
            Notification.show(e.getMessage()).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void resetRole() {
        if (role.getValue() == null) {
            return;
        }
        roleService.resetRoleToDefault(tenantId(), role.getValue());
        Notification.show(role.getValue() + " reset to baseline defaults")
                .addThemeVariants(NotificationVariant.LUMO_WARNING);
        refreshMatrix();
    }

    private void refreshRoles() {
        List<String> names = roleService.getRoles(tenantId()).stream()
                .map(RoleDefinition::roleName)
                .sorted()
                .toList();
        String previous = role.getValue();
        role.setItems(names);
        if (previous != null && names.contains(previous)) {
            role.setValue(previous);
        } else if (names.contains(DemoRoles.SALESMAN)) {
            role.setValue(DemoRoles.SALESMAN);
        } else if (!names.isEmpty()) {
            role.setValue(names.getFirst());
        }
        refreshMatrix();
    }

    private void refreshMatrix() {
        if (role.getValue() == null) {
            matrix.setItems(List.of());
            return;
        }
        matrix.setItems(roleService.getRoleMatrix(tenantId(), role.getValue()));
    }

    private static String tenantId() {
        return TenantContext.get()
                .orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"))
                .tenantId();
    }
}
