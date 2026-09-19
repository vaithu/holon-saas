package com.holonplatform.multitenant.permissions.view.internal;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantUserRoleResolver;
import com.holonplatform.multitenant.permissions.internal.TenantCustomRoleEntity;
import com.holonplatform.multitenant.permissions.internal.TenantPermissionOverrideEntity;
import com.holonplatform.multitenant.permissions.internal.TenantRoleManagementDatastoreHelper;
import com.holonplatform.multitenant.permissions.view.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Default implementation of {@link TenantRoleManagementService} backed by Holon Datastore
 * with Virtual Thread-safe Caffeine caching.
 */
public class DefaultTenantRoleManagementService implements TenantRoleManagementService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantRoleManagementService.class);

    private final TenantRoleManagementDatastoreHelper datastoreHelper;
    private final ViewRegistry viewRegistry;
    private final SystemDefaultPermissionRegistry defaultPermissionRegistry;
    private final TenantUserRoleResolver tenantUserRoleResolver;

    /** Name of {@link #permissionEvaluationCache}, for logging/metrics correlation. */
    public static final String CACHE_NAME = "tenantPermissionEvaluation";

    /**
     * Cache for resolved permissions to optimize Virtual Thread execution, exposed through
     * Spring's {@link Cache} abstraction instead of a raw Caffeine-typed field.
     * Key: "{tenantId}:{roleName}:{viewId}:{action}" -> Boolean
     */
    private final Cache permissionEvaluationCache;

    public DefaultTenantRoleManagementService(
            TenantRoleManagementDatastoreHelper datastoreHelper,
            ViewRegistry viewRegistry,
            SystemDefaultPermissionRegistry defaultPermissionRegistry,
            TenantUserRoleResolver tenantUserRoleResolver) {
        this(datastoreHelper, viewRegistry, defaultPermissionRegistry, tenantUserRoleResolver,
                defaultPermissionEvaluationCache());
    }

    /**
     * Creates a new service backed by the given {@link Cache} instead of the default per-JVM
     * Caffeine cache — e.g. to share permission evaluations across instances, or to tune
     * capacity/TTL differently from the 5-minute/50,000-entry default.
     */
    public DefaultTenantRoleManagementService(
            TenantRoleManagementDatastoreHelper datastoreHelper,
            ViewRegistry viewRegistry,
            SystemDefaultPermissionRegistry defaultPermissionRegistry,
            TenantUserRoleResolver tenantUserRoleResolver,
            Cache permissionEvaluationCache) {
        this.datastoreHelper = Objects.requireNonNull(datastoreHelper, "datastoreHelper must not be null");
        this.viewRegistry = Objects.requireNonNull(viewRegistry, "viewRegistry must not be null");
        this.defaultPermissionRegistry = Objects.requireNonNull(defaultPermissionRegistry, "defaultPermissionRegistry must not be null");
        this.tenantUserRoleResolver = Objects.requireNonNull(tenantUserRoleResolver, "tenantUserRoleResolver must not be null");
        this.permissionEvaluationCache = Objects.requireNonNull(permissionEvaluationCache, "permissionEvaluationCache must not be null");
    }

    private static Cache defaultPermissionEvaluationCache() {
        return new CaffeineCache(CACHE_NAME, Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(50_000)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDefinition> getRoles(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        List<RoleDefinition> result = new ArrayList<>();

        // 1. Add known system roles
        for (String sysRole : defaultPermissionRegistry.getKnownSystemRoles()) {
            String displayName = formatRoleDisplayName(sysRole);
            result.add(new RoleDefinition(sysRole, displayName, "Platform baseline role", true, null));
        }

        // 2. Add tenant custom roles
        List<TenantCustomRoleEntity> customRoles = datastoreHelper.findCustomRolesByTenant(tenantId);
        for (TenantCustomRoleEntity custom : customRoles) {
            result.add(new RoleDefinition(
                    custom.getRoleName(),
                    custom.getDisplayName(),
                    custom.getDescription(),
                    false,
                    custom.getBaseRole()
            ));
        }

        return result;
    }

    @Override
    @Transactional
    public RoleDefinition createCustomRole(String tenantId, String roleName, String displayName, String description, String baseRole) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        if (defaultPermissionRegistry.isSystemRole(normalizedRole)) {
            throw new IllegalArgumentException("Cannot create custom role with reserved system role name: " + roleName);
        }

        TenantCustomRoleEntity entity = new TenantCustomRoleEntity(
                tenantId, normalizedRole, displayName, description, baseRole != null ? baseRole.toUpperCase(Locale.ROOT).trim() : null);
        datastoreHelper.saveCustomRole(entity);

        // If base role is specified, copy any active overrides or inherit from base
        invalidateCache(tenantId, normalizedRole);

        return new RoleDefinition(entity.getRoleName(), entity.getDisplayName(), entity.getDescription(), false, entity.getBaseRole());
    }

    @Override
    @Transactional
    public void deleteCustomRole(String tenantId, String roleName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        datastoreHelper.deleteCustomRole(tenantId, normalizedRole);
        invalidateCache(tenantId, normalizedRole);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleMatrixItem> getRoleMatrix(String tenantId, String roleName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();

        // Load all active overrides for this tenant and role
        List<TenantPermissionOverrideEntity> overrides = datastoreHelper.findOverridesByTenantAndRole(tenantId, normalizedRole);
        Map<String, Boolean> overrideMap = new HashMap<>();
        for (TenantPermissionOverrideEntity override : overrides) {
            overrideMap.put(override.getViewId() + ":" + override.getAction().toUpperCase(Locale.ROOT), override.isAllowed());
        }

        // Check if custom role has a base role
        Optional<TenantCustomRoleEntity> customRoleOpt = datastoreHelper.findCustomRole(tenantId, normalizedRole);
        String baseRole = customRoleOpt.map(TenantCustomRoleEntity::getBaseRole).orElse(null);

        List<ViewResource> allViews = viewRegistry.getAllViews();
        List<RoleMatrixItem> matrix = new ArrayList<>();

        for (ViewResource view : allViews) {
            for (String action : view.supportedActions()) {
                String normalizedAction = action.toUpperCase(Locale.ROOT);
                String key = view.viewId() + ":" + normalizedAction;

                boolean inheritedDefault;
                if (baseRole != null) {
                    inheritedDefault = defaultPermissionRegistry.isDefaultAllowed(baseRole, view.viewId(), normalizedAction);
                } else {
                    inheritedDefault = defaultPermissionRegistry.isDefaultAllowed(normalizedRole, view.viewId(), normalizedAction);
                }

                boolean allowed;
                boolean customized = false;

                if (overrideMap.containsKey(key)) {
                    allowed = overrideMap.get(key);
                    customized = true;
                } else {
                    allowed = inheritedDefault;
                }

                matrix.add(new RoleMatrixItem(
                        view.viewId(),
                        view.displayName(),
                        view.category(),
                        normalizedAction,
                        allowed,
                        customized,
                        inheritedDefault
                ));
            }
        }

        return matrix;
    }

    @Override
    @Transactional
    public void setActionOverride(String tenantId, String roleName, String viewId, String action, boolean allowed) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");
        Objects.requireNonNull(viewId, "viewId must not be null");
        Objects.requireNonNull(action, "action must not be null");

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        String normalizedAction = action.toUpperCase(Locale.ROOT).trim();

        TenantPermissionOverrideEntity entity = new TenantPermissionOverrideEntity(
                tenantId, normalizedRole, viewId, normalizedAction, allowed);
        datastoreHelper.saveOverride(entity);

        invalidateCache(tenantId, normalizedRole);
        log.info("Set permission override for tenant='{}', role='{}', view='{}', action='{}' to {}",
                tenantId, normalizedRole, viewId, normalizedAction, allowed);
    }

    @Override
    @Transactional
    public void resetViewToDefault(String tenantId, String roleName, String viewId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");
        Objects.requireNonNull(viewId, "viewId must not be null");

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        datastoreHelper.deleteOverridesByView(tenantId, normalizedRole, viewId);
        invalidateCache(tenantId, normalizedRole);
        log.info("Reset view '{}' permissions to default for tenant='{}', role='{}'", viewId, tenantId, normalizedRole);
    }

    @Override
    @Transactional
    public void resetRoleToDefault(String tenantId, String roleName) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(roleName, "roleName must not be null");

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        datastoreHelper.deleteOverridesByRole(tenantId, normalizedRole);
        invalidateCache(tenantId, normalizedRole);
        log.info("Reset entire role '{}' to system default for tenant='{}'", normalizedRole, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActionPermitted(String tenantId, String roleName, String viewId, String action) {
        if (tenantId == null || roleName == null || viewId == null || action == null) {
            return false;
        }

        String normalizedRole = roleName.toUpperCase(Locale.ROOT).trim();
        if (SystemRole.ADMIN.equals(normalizedRole)) {
            return true;
        }

        String normalizedAction = action.toUpperCase(Locale.ROOT).trim();
        String cacheKey = tenantId + ":" + normalizedRole + ":" + viewId + ":" + normalizedAction;

        Boolean allowed = permissionEvaluationCache.get(cacheKey,
                () -> evaluatePermission(tenantId, normalizedRole, viewId, normalizedAction));
        return Boolean.TRUE.equals(allowed);
    }

    private boolean evaluatePermission(String tenantId, String roleName, String viewId, String action) {
        // 1. Check explicit override for this specific tenant, role, view, and action
        Optional<TenantPermissionOverrideEntity> override = datastoreHelper.findOverride(tenantId, roleName, viewId, action);
        if (override.isPresent()) {
            return override.get().isAllowed();
        }

        // 2. Check if custom role has a base role
        Optional<TenantCustomRoleEntity> customRole = datastoreHelper.findCustomRole(tenantId, roleName);
        if (customRole.isPresent() && customRole.get().getBaseRole() != null) {
            String baseRole = customRole.get().getBaseRole().toUpperCase(Locale.ROOT);
            Optional<TenantPermissionOverrideEntity> baseOverride = datastoreHelper.findOverride(tenantId, baseRole, viewId, action);
            if (baseOverride.isPresent()) {
                return baseOverride.get().isAllowed();
            }
            return defaultPermissionRegistry.isDefaultAllowed(baseRole, viewId, action);
        }

        // 3. Fallback to platform system baseline default
        return defaultPermissionRegistry.isDefaultAllowed(roleName, viewId, action);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isViewPermitted(String tenantId, String roleName, String viewId) {
        return isActionPermitted(tenantId, roleName, viewId, ViewAction.READ);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean currentUserCanAccessView(String viewId) {
        return currentUserCanPerformAction(viewId, ViewAction.READ);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean currentUserCanPerformAction(String viewId, String action) {
        return TenantContext.get()
                .flatMap(tenant -> resolveCurrentUserRole(tenant.tenantId())
                        .map(role -> isActionPermitted(tenant.tenantId(), role, viewId, action)))
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getCurrentUserPermittedActions(String viewId) {
        Optional<String> tenantIdOpt = TenantContext.get().map(t -> t.tenantId());
        if (tenantIdOpt.isEmpty()) {
            return Set.of();
        }

        String tenantId = tenantIdOpt.get();
        Optional<String> roleOpt = resolveCurrentUserRole(tenantId);
        if (roleOpt.isEmpty()) {
            return Set.of();
        }

        String role = roleOpt.get();
        Optional<ViewResource> viewResource = viewRegistry.getView(viewId);
        if (viewResource.isEmpty()) {
            return Set.of();
        }

        return viewResource.get().supportedActions().stream()
                .filter(action -> isActionPermitted(tenantId, role, viewId, action))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Optional<String> resolveCurrentUserRole(String tenantId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return Optional.empty();
        }

        return tenantUserRoleResolver.resolve(tenantId, auth.getName())
                .map(TenantUserRoleResolver.ResolvedTenantUserRole::effectiveRole);
    }

    private void invalidateCache(String tenantId, String roleName) {
        // Spring's Cache abstraction exposes no key-enumeration API, so this only precisely
        // evicts matching entries when the cache is Caffeine-backed (the default); for any
        // other Cache implementation, fall back to a full clear — a safe superset (a spurious
        // re-evaluation for another tenant/role, never a stale permission decision).
        String prefix = tenantId + ":" + roleName + ":";
        if (permissionEvaluationCache instanceof CaffeineCache caffeineCache) {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            nativeCache.asMap().keySet().removeIf(key -> key instanceof String s && s.startsWith(prefix));
        } else {
            permissionEvaluationCache.clear();
        }
    }

    private String formatRoleDisplayName(String roleName) {
        String lower = roleName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
