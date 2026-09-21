package com.holonplatform.multitenant.demo.ui;

import java.util.Map;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.demo.service.DemoSettings;
import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.settings.TenantSetting;
import com.holonplatform.multitenant.settings.TenantSettingsService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;

import jakarta.annotation.security.PermitAll;

/**
 * F8 — per-tenant settings resolved through global, plan and tenant tiers.
 *
 * <p>The grid deliberately shows every tier rather than only the answer, because the point of
 * the feature is where a value comes from: clearing a workspace override makes the row fall
 * back to the plan default, and clearing that falls back to the value declared in code.
 */
@Route(value = "settings", layout = MainLayout.class)
@PageTitle("Settings")
@SecuredView(id = "settings", displayName = "Settings", category = "Administration", actions = {"READ", "UPDATE"})
@PermitAll
public class SettingsView extends PermissionGuardedView {

    private final TenantSettingsService settingsService;

    private final Grid<TenantSetting> grid = new Grid<>(TenantSetting.class, false);

    public SettingsView(TenantPermissionService permissionService, TenantSettingsService settingsService) {
        super(permissionService, DemoRoles.SETTINGS_MANAGE);
        this.settingsService = settingsService;

        Select<TenantSetting> setting = new Select<>();
        setting.setLabel("Setting");
        setting.setItems(DemoSettings.ALL);
        setting.setItemLabelGenerator(TenantSetting::key);
        setting.setValue(DemoSettings.SALES_CURRENCY);

        TextField value = new TextField("Workspace value");

        Button save = new Button("Save override",
                event -> applyOverride(setting.getValue(), value.getValue()));
        Button clear = new Button("Clear override",
                event -> applyOverride(setting.getValue(), null));

        HorizontalLayout form = new HorizontalLayout(setting, value, save, clear);
        form.setAlignItems(HorizontalLayout.Alignment.BASELINE);

        grid.addColumn(TenantSetting::key).setHeader("Key");
        grid.addColumn(TenantSetting::defaultValue).setHeader("Global default");
        grid.addColumn(this::planDefaultOf).setHeader("Plan default");
        grid.addColumn(this::overrideOf).setHeader("Workspace override");
        grid.addColumn(this::resolvedOf).setHeader("Resolved");
        grid.setAllRowsVisible(true);

        add(new H2("Settings"),
                new Paragraph("A setting resolves as workspace override, then plan default, "
                        + "then the global default declared in code."),
                form, grid);

        refresh();
    }

    private void applyOverride(TenantSetting setting, String value) {
        // A null (or blank) value removes the override rather than storing an empty string —
        // otherwise "clear" would shadow the plan default with emptiness.
        String stored = (value == null || value.isBlank()) ? null : value.trim();
        settingsService.set(tenantId(), setting.key(), stored);

        Notification.show(stored == null
                        ? "Cleared override for " + setting.key()
                        : "Saved " + setting.key() + " = " + stored)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        refresh();
    }

    private void refresh() {
        grid.setItems(DemoSettings.ALL);
    }

    private String planDefaultOf(TenantSetting setting) {
        Map<String, String> planDefaults = settingsService.getPlanDefaults(tenant().plan());
        return planDefaults.getOrDefault(setting.key(), "-");
    }

    private String overrideOf(TenantSetting setting) {
        return settingsService.getAll(tenantId()).getOrDefault(setting.key(), "-");
    }

    private String resolvedOf(TenantSetting setting) {
        return settingsService.getOrDefault(tenantId(), setting, "-");
    }

    private static TenantDetails tenant() {
        return TenantContext.get()
                .orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"));
    }

    private static String tenantId() {
        return tenant().tenantId();
    }
}
