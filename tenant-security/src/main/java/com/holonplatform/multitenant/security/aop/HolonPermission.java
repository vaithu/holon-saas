package com.holonplatform.multitenant.security.aop;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for fine-grained permission-based method access control.
 *
 * <p>Use this annotation to declare that a method requires one or more permissions
 * to execute. The HolonPermissionAspect intercepts calls to annotated methods and
 * validates permissions before allowing execution.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @HolonPermission("AUDIT_VIEW")
 * public List<AuditEntry> getAuditLog(String tenantId) { ... }
 *
 * @HolonPermission({"AUDIT_VIEW", "AUDIT_EXPORT"})
 * public File exportAuditLog(String tenantId) { ... }
 * }</pre>
 *
 * <h2>Permission Checking</h2>
 * <p>The aspect checks if the current authenticated user (from Holon AuthContext
 * or Spring Security) has ANY of the specified permissions. If none match,
 * a {@link com.holonplatform.multitenant.security.TenantSecurityException} is thrown.
 *
 * <p>Permissions are matched case-insensitively and support both:
 * <ul>
 *   <li>Holon Permission objects (from AuthContext)</li>
 *   <li>Spring Security Granted Authorities (with ROLE_ or PERMISSION_ prefix)</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HolonPermission {

    /**
     * Required permission(s). If multiple values are specified, the user must
     * have at least one of them (OR logic).
     *
     * @return array of permission strings
     */
    String[] value() default {};

    /**
     * Optional description for logging and documentation purposes.
     *
     * @return description
     */
    String description() default "";
}

