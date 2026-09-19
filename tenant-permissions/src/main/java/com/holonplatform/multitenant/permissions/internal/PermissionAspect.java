package com.holonplatform.multitenant.permissions.internal;

import com.holonplatform.multitenant.permissions.TenantPermissionService;
import com.holonplatform.multitenant.permissions.annotation.RequiresPermission;
import com.holonplatform.multitenant.permissions.exception.PermissionDeniedException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;

/**
 * AOP aspect that enforces {@link RequiresPermission} on annotated methods and classes.
 *
 * <p>Registered automatically when AspectJ is on the classpath.
 * Override by declaring a custom {@code PermissionAspect} bean.</p>
 */
@Aspect
@Order(15)
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    private final TenantPermissionService permissionService;

    public PermissionAspect(TenantPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** Enforces {@link RequiresPermission} on individual methods. */
    @Before("execution(* *(..)) && @annotation(requiresPermission)")
    public void enforceOnMethod(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        enforce(requiresPermission);
    }

    /** Enforces {@link RequiresPermission} on all methods of an annotated class. */
    @Before("execution(* *(..)) && @within(requiresPermission)")
    public void enforceOnClass(JoinPoint joinPoint, RequiresPermission requiresPermission) {
        enforce(requiresPermission);
    }

    private void enforce(RequiresPermission annotation) {
        String[] permissions = annotation.value();
        log.trace("@RequiresPermission check for {}", java.util.Arrays.toString(permissions));
        for (String permission : permissions) {
            if (permissionService.currentUserHasPermission(permission)) {
                return;
            }
        }
        String joined = String.join(" OR ", permissions);
        log.debug("@RequiresPermission({}) denied for current user", joined);
        throw new PermissionDeniedException(joined, annotation.message());
    }
}

