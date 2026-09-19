package com.holonplatform.multitenant.permissions.view;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Helper bean for Vaadin Flow views and components to conditionally configure UI elements
 * (buttons, menus, forms, tabs) based on fine-grained view action permissions.
 *
 * <pre>{@code
 * // In a Vaadin View:
 * uiPermissionHelper.ifAllowed("customer-details", ViewAction.CREATE, () -> {
 *     createButton.setVisible(true);
 * });
 *
 * // Or declarative binding:
 * uiPermissionHelper.bindVisibility("customer-details", ViewAction.DELETE, deleteButton, button -> button.setVisible(false));
 * }</pre>
 */
public class UIPermissionHelper {

    private final TenantRoleManagementService roleManagementService;

    public UIPermissionHelper(TenantRoleManagementService roleManagementService) {
        this.roleManagementService = Objects.requireNonNull(roleManagementService, "roleManagementService must not be null");
    }

    /**
     * Returns true if the current user is permitted to perform the specified action on the view.
     */
    public boolean isAllowed(String viewId, String action) {
        return roleManagementService.currentUserCanPerformAction(viewId, action);
    }

    /**
     * Executes the given action if the current user is permitted to perform the view action.
     */
    public void ifAllowed(String viewId, String action, Runnable runnable) {
        if (isAllowed(viewId, action)) {
            runnable.run();
        }
    }

    /**
     * Executes the given callback when allowed, or fallback if denied.
     */
    public void ifAllowedOrElse(String viewId, String action, Runnable onAllowed, Runnable onDenied) {
        if (isAllowed(viewId, action)) {
            onAllowed.run();
        } else if (onDenied != null) {
            onDenied.run();
        }
    }

    /**
     * Returns all actions permitted for the current user on the given view.
     */
    public Set<String> getPermittedActions(String viewId) {
        return roleManagementService.getCurrentUserPermittedActions(viewId);
    }

    /**
     * Conditionally applies a consumer to a UI component based on whether an action is allowed.
     */
    public <T> void configureComponent(String viewId, String action, T component, Consumer<T> ifAllowed, Consumer<T> ifDenied) {
        if (isAllowed(viewId, action)) {
            if (ifAllowed != null) {
                ifAllowed.accept(component);
            }
        } else {
            if (ifDenied != null) {
                ifDenied.accept(component);
            }
        }
    }
}
