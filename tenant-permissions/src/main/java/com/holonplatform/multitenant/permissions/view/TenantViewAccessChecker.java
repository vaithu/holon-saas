package com.holonplatform.multitenant.permissions.view;

import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Access checker for validating navigation to views annotated with {@link SecuredView}.
 *
 * <p>Can be called directly from Vaadin {@code BeforeEnterListener} or navigation guards:</p>
 * <pre>{@code
 * @Component
 * public class AppNavigationGuard implements BeforeEnterListener {
 *     private final TenantViewAccessChecker accessChecker;
 *
 *     @Override
 *     public void beforeEnter(BeforeEnterEvent event) {
 *         if (!accessChecker.checkAccess(event.getNavigationTarget())) {
 *             event.rerouteTo(AccessDeniedView.class);
 *         }
 *     }
 * }
 * }</pre>
 */
public class TenantViewAccessChecker {

    private static final Logger log = LoggerFactory.getLogger(TenantViewAccessChecker.class);

    private final TenantRoleManagementService roleManagementService;

    public TenantViewAccessChecker(TenantRoleManagementService roleManagementService) {
        this.roleManagementService = Objects.requireNonNull(roleManagementService, "roleManagementService must not be null");
    }

    /**
     * Checks if the currently authenticated user is permitted to navigate to the target view class.
     *
     * @param targetViewClass the class of the view being navigated to
     * @return true if navigation is allowed, or if the view is not annotated with {@link SecuredView}
     */
    public boolean checkAccess(Class<?> targetViewClass) {
        if (targetViewClass == null) {
            return true;
        }

        SecuredView securedView = targetViewClass.getAnnotation(SecuredView.class);
        if (securedView == null) {
            // View has no @SecuredView annotation; permit navigation
            return true;
        }

        String viewId = securedView.id();
        boolean permitted = roleManagementService.currentUserCanAccessView(viewId);
        if (!permitted) {
            log.warn("Access denied for current user to view '{}' ({})", viewId, targetViewClass.getName());
        }
        return permitted;
    }

    /**
     * Checks if the currently authenticated user can access the view with the given viewId.
     *
     * @param viewId the view identifier
     * @return true if access is permitted
     */
    public boolean checkAccess(String viewId) {
        if (viewId == null || viewId.isBlank()) {
            return true;
        }
        return roleManagementService.currentUserCanAccessView(viewId);
    }
}
