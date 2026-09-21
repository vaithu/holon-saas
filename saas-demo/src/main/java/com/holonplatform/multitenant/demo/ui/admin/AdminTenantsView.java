package com.holonplatform.multitenant.demo.ui.admin;

import java.util.List;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.demo.config.PlatformAdmin;
import com.holonplatform.multitenant.demo.service.PlatformConsoleService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.RolesAllowed;

/**
 * F13 and F14 — every workspace on the platform, and the power to take one out of service.
 *
 * <h2>The grid is lazily loaded on purpose</h2>
 * <p>The rows come from {@code TenantAdminService.findSlice(offset, limit, search)} through a
 * Vaadin callback data provider, so only the visible window is fetched and the count query is
 * issued separately — and only because the header tiles ask for it. A platform console is the
 * one screen in a SaaS product guaranteed to grow without bound, so {@code findAll} would be
 * exactly the wrong choice here.
 *
 * <h2>What suspension actually does</h2>
 * <p>Suspending sets the tenant's status in the control-plane registry. Nothing is deleted and
 * the tenant's schema is untouched; what changes is that
 * {@code AuthenticatedPrincipalTenantContextFilter} refuses to bind a non-active tenant, and
 * the demo's {@code DemoUserDetailsService} marks its accounts disabled. The effect is that
 * members can neither sign in nor continue a session — while every other workspace carries on
 * unaffected, which is the point worth watching in the walkthrough.
 */
@Route(value = "admin/tenants", layout = AdminLayout.class)
@PageTitle("Workspaces")
@RolesAllowed(PlatformAdmin.ROLE)
public class AdminTenantsView extends VerticalLayout {

    private final PlatformConsoleService console;

    private final Grid<TenantDetails> grid   = new Grid<>(TenantDetails.class, false);
    private final TextField           search = new TextField("Search workspaces");
    private final HorizontalLayout    tiles  = new HorizontalLayout();

    private String searchTerm = "";

    public AdminTenantsView(PlatformConsoleService console) {
        this.console = console;
        setPadding(true);
        setSpacing(true);

        search.setPlaceholder("name or id");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(event -> {
            searchTerm = event.getValue() == null ? "" : event.getValue().trim();
            refresh();
        });

        grid.addColumn(TenantDetails::tenantId).setHeader("Workspace").setAutoWidth(true);
        grid.addColumn(TenantDetails::name).setHeader("Name").setAutoWidth(true);
        grid.addColumn(tenant -> tenant.plan().name()).setHeader("Plan").setAutoWidth(true);
        grid.addColumn(tenant -> tenant.status().name()).setHeader("Status").setAutoWidth(true);
        grid.addComponentColumn(this::statusButton).setHeader("Action").setAutoWidth(true);
        grid.setAllRowsVisible(true);

        grid.setItems(DataProvider.fromCallbacks(
                query -> console.list(query.getOffset(), query.getLimit(), searchTerm).stream(),
                query -> (int) console.count(searchTerm)));

        add(new H2("Workspaces"),
                new Paragraph("Read straight from the control-plane tenant registry. The operator has no "
                        + "tenant bound, which is what makes a cross-tenant query possible at all."),
                tiles, search, grid);

        refreshTiles();
    }

    /** Suspend or Activate, whichever the row's current status calls for. */
    private Button statusButton(TenantDetails tenant) {
        boolean active = tenant.status() == TenantStatus.ACTIVE;
        Button button = new Button(active ? "Suspend" : "Activate", event -> {
            if (active) {
                console.suspend(tenant.tenantId());
                Notification.show("Workspace " + tenant.tenantId() + " suspended")
                        .addThemeVariants(NotificationVariant.LUMO_WARNING);
            } else {
                console.activate(tenant.tenantId());
                Notification.show("Workspace " + tenant.tenantId() + " reactivated")
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }
            refresh();
        });
        // A stable accessible name per row, so the walkthrough can target the button for a
        // specific workspace rather than "the first Suspend button on the page".
        button.getElement().setAttribute("aria-label",
                (active ? "Suspend " : "Activate ") + tenant.tenantId());
        return button;
    }

    private void refresh() {
        grid.getDataProvider().refreshAll();
        refreshTiles();
    }

    /**
     * The count tiles. These are the only calls that ask for a total — the grid itself never
     * does, which is the distinction the lazy API is there to make.
     */
    private void refreshTiles() {
        tiles.removeAll();
        tiles.setSpacing(true);
        tiles.add(tile("Total", console.count("")));
        for (TenantStatus status : List.of(TenantStatus.ACTIVE, TenantStatus.SUSPENDED)) {
            tiles.add(tile(status.name(), console.countByStatus(status)));
        }
        for (TenantPlan plan : TenantPlan.values()) {
            long count = console.countByPlan(plan);
            if (count > 0) {
                tiles.add(tile(plan.name(), count));
            }
        }
    }

    private static Span tile(String label, long value) {
        Span span = new Span(label + ": " + value);
        span.getElement().getThemeList().add("badge");
        return span;
    }
}
