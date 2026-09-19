package com.holonplatform.multitenant.permissions.view.internal;

import com.holonplatform.multitenant.permissions.view.SystemDefaultPermissionRegistry;
import com.holonplatform.multitenant.permissions.view.SystemRole;
import com.holonplatform.multitenant.permissions.view.ViewAction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory thread-safe implementation of {@link SystemDefaultPermissionRegistry}.
 *
 * <p>Ships with pre-configured sensible baseline security defaults for standard roles
 * (e.g. {@code ADMIN} has full access {@code *:*}, {@code VIEWER} has read-only access).</p>
 */
public class DefaultSystemDefaultPermissionRegistry implements SystemDefaultPermissionRegistry {

    // roleName -> (viewId -> Set<action>)
    private final Map<String, Map<String, Set<String>>> roleDefaults = new ConcurrentHashMap<>();

    public DefaultSystemDefaultPermissionRegistry() {
        initStandardDefaults();
    }

    private void initStandardDefaults() {
        // ADMIN has wildcard access across all views and actions
        registerDefault(SystemRole.ADMIN, "*", "*");

        // VIEWER has read-only access across basic dashboard and reporting views
        registerDefault(SystemRole.VIEWER, "dashboard", ViewAction.READ);
        registerDefault(SystemRole.VIEWER, "reports", ViewAction.READ);
        registerDefault(SystemRole.VIEWER, "customer-details", ViewAction.READ);

        // MEMBER standard baseline defaults
        registerDefault(SystemRole.MEMBER, "dashboard", ViewAction.READ);
        registerDefault(SystemRole.MEMBER, "profile", ViewAction.READ, ViewAction.UPDATE);

        // SALESMAN standard baseline defaults
        registerDefault(SystemRole.SALESMAN, "dashboard", ViewAction.READ);
        registerDefault(SystemRole.SALESMAN, "customer-details", ViewAction.READ, ViewAction.UPDATE, ViewAction.EXPORT);
        registerDefault(SystemRole.SALESMAN, "leads", ViewAction.READ, ViewAction.CREATE, ViewAction.UPDATE, ViewAction.EXPORT);
        registerDefault(SystemRole.SALESMAN, "opportunities", ViewAction.READ, ViewAction.CREATE, ViewAction.UPDATE);
        // Note: customer-creation is intentionally not in default SALESMAN baseline!

        // ACCOUNTANT standard baseline defaults
        registerDefault(SystemRole.ACCOUNTANT, "dashboard", ViewAction.READ);
        registerDefault(SystemRole.ACCOUNTANT, "invoices", ViewAction.READ, ViewAction.CREATE, ViewAction.UPDATE, ViewAction.EXPORT, ViewAction.EXECUTE);
        registerDefault(SystemRole.ACCOUNTANT, "billing", ViewAction.READ, ViewAction.UPDATE, ViewAction.EXPORT);
        registerDefault(SystemRole.ACCOUNTANT, "reports", ViewAction.READ, ViewAction.EXPORT, ViewAction.EXECUTE);

        // OPERATOR standard baseline defaults
        registerDefault(SystemRole.OPERATOR, "dashboard", ViewAction.READ);
        registerDefault(SystemRole.OPERATOR, "tasks", ViewAction.READ, ViewAction.CREATE, ViewAction.UPDATE, ViewAction.EXECUTE);
    }

    @Override
    public void registerDefault(String roleName, String viewId, String... actions) {
        Objects.requireNonNull(roleName, "roleName must not be null");
        Objects.requireNonNull(viewId, "viewId must not be null");

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        Map<String, Set<String>> viewMap = roleDefaults.computeIfAbsent(normalizedRole, k -> new ConcurrentHashMap<>());
        Set<String> actionSet = viewMap.computeIfAbsent(viewId, k -> ConcurrentHashMap.newKeySet());

        if (actions != null) {
            for (String action : actions) {
                if (action != null && !action.isBlank()) {
                    actionSet.add(action.toUpperCase(Locale.ROOT).trim());
                }
            }
        }
    }

    @Override
    public boolean isDefaultAllowed(String roleName, String viewId, String action) {
        if (roleName == null || viewId == null || action == null) {
            return false;
        }

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        Map<String, Set<String>> viewMap = roleDefaults.get(normalizedRole);
        if (viewMap == null) {
            return false;
        }

        String normalizedAction = action.toUpperCase(Locale.ROOT).trim();

        // 1. Check full role wildcard (*:*)
        Set<String> allViewsActions = viewMap.get("*");
        if (allViewsActions != null && (allViewsActions.contains("*") || allViewsActions.contains(normalizedAction))) {
            return true;
        }

        // 2. Check view-specific actions
        Set<String> specificViewActions = viewMap.get(viewId);
        if (specificViewActions != null) {
            return specificViewActions.contains("*") || specificViewActions.contains(normalizedAction);
        }

        return false;
    }

    @Override
    public Map<String, Set<String>> getDefaultRolePermissions(String roleName) {
        if (roleName == null) {
            return Map.of();
        }
        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        Map<String, Set<String>> viewMap = roleDefaults.get(normalizedRole);
        if (viewMap == null) {
            return Map.of();
        }

        Map<String, Set<String>> result = new HashMap<>();
        viewMap.forEach((view, actions) -> result.put(view, Collections.unmodifiableSet(new HashSet<>(actions))));
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Set<String> getKnownSystemRoles() {
        return Collections.unmodifiableSet(roleDefaults.keySet());
    }

    @Override
    public boolean isSystemRole(String roleName) {
        if (roleName == null) {
            return false;
        }
        return roleDefaults.containsKey(roleName.toUpperCase(Locale.ROOT).trim());
    }
}
