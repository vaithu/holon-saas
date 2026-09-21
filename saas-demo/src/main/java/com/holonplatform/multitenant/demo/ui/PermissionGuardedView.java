package com.holonplatform.multitenant.demo.ui;

import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;

/**
 * Base class for a view that only a permission holder may open.
 *
 * <h2>Why the check is repeated here</h2>
 * <p>{@link MainLayout} already hides the navigation entry for a view the user cannot open, but
 * a hidden link is not access control — the URL can still be typed, bookmarked or shared. This
 * guard re-evaluates the same permission when the router actually tries to enter the view and
 * rejects the navigation, so the two layers together give "invisible <em>and</em> unreachable".
 *
 * <p>Navigation is rerouted to {@link AccessDeniedView} rather than silently redirected, so the
 * demo visibly shows that the framework refused the request.
 */
public abstract class PermissionGuardedView extends VerticalLayout implements BeforeEnterObserver {

    private final TenantPermissionService permissionService;
    private final String                  requiredPermission;

    protected PermissionGuardedView(TenantPermissionService permissionService, String requiredPermission) {
        this.permissionService  = permissionService;
        this.requiredPermission = requiredPermission;
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!permissionService.currentUserHasPermission(requiredPermission)) {
            event.rerouteTo(AccessDeniedView.class);
        }
    }
}
