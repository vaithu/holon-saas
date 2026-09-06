package com.holonplatform.multitenant.security.aop;

import com.holonplatform.auth.AuthContext;
import com.holonplatform.auth.Authentication;
import com.holonplatform.auth.Permission;
import com.holonplatform.multitenant.security.TenantSecurityException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Optional;


/**
 * AOP aspect for {@link HolonPermission} annotation-based permission checking.
 *
 * <p>Intercepts method calls marked with @HolonPermission and validates that
 * the current authenticated user has the required permission.
 *
 * <p>Works in conjunction with Holon's AuthContext for role-based access control.
 */
@Aspect
@Component
public class HolonPermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(HolonPermissionAspect.class);

    @Pointcut("@annotation(com.holonplatform.multitenant.security.aop.HolonPermission)")
    public void holonPermissionMethods() {}

    @Around("holonPermissionMethods()")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // Get the method that was called
            Method method = getMethod(joinPoint);
            HolonPermission permission = method.getAnnotation(HolonPermission.class);

            if (permission == null) {
                return joinPoint.proceed();
            }

            String[] requiredPermissions = permission.value();

            // Check if current user has any of the required permissions
            boolean authorized = false;

            try {
                // Try to get Holon authentication
                Optional<Authentication> auth = AuthContext.getCurrent().flatMap(AuthContext::getAuthentication);
                if (auth.isPresent()) {
                    for (String requiredPerm : requiredPermissions) {
                        if (hasPermission(auth.get(), requiredPerm)) {
                            authorized = true;
                            break;
                        }
                    }
                } else {
                    authorized = checkSpringSecurityPermission(requiredPermissions);
                }
            } catch (Exception e) {
                log.debug("Could not retrieve Holon authentication context", e);
                // Fall back to Spring Security if Holon context not available
                authorized = checkSpringSecurityPermission(requiredPermissions);
            }

            if (!authorized) {
                String missing = String.join(", ", requiredPermissions);
                log.warn("Permission denied for method {}: required permissions [{}]",
                        method.getName(), missing);
                throw new TenantSecurityException("Access denied: required permission(s) [" + missing + "]");
            }

            log.debug("Permission granted for method {}: {}", method.getName(),
                    String.join(", ", requiredPermissions));

            return joinPoint.proceed();

        } catch (TenantSecurityException e) {
            throw e;
        } catch (Throwable e) {
            log.error("Permission check error", e);
            throw e;
        }
    }

    /**
     * Check if the Holon Authentication has the required permission.
     */
    private boolean hasPermission(Authentication auth, String permission) {
        return auth.getPermissions()
                .stream()
                .map(Permission::getPermission)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .anyMatch(p -> p.equalsIgnoreCase(permission));
    }

    /**
     * Fallback to Spring Security permission check.
     */
    private boolean checkSpringSecurityPermission(String[] requiredPermissions) {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }

        for (String permission : requiredPermissions) {
            if (auth.getAuthorities()
                    .stream()
                    .anyMatch(ga -> ga.getAuthority().equals("ROLE_" + permission) ||
                                   ga.getAuthority().equals("PERMISSION_" + permission) ||
                                   ga.getAuthority().equalsIgnoreCase(permission))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Extract the method from the ProceedingJoinPoint.
     */
    private Method getMethod(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        String methodName = joinPoint.getSignature().getName();
        Class<?>[] parameterTypes = new Class[joinPoint.getArgs().length];
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < args.length; i++) {
            parameterTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
        }

        return joinPoint.getTarget().getClass().getMethod(methodName, parameterTypes);
    }
}

