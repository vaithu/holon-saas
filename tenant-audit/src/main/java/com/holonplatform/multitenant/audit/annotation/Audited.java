package com.holonplatform.multitenant.audit.annotation;

import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.AuditSeverity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for automatic audit logging via {@code AuditAspect}.
 *
 * <p>When a Spring-managed bean's annotated method completes (normally or with
 * an exception), an {@link com.holonplatform.multitenant.audit.AuditEntry}
 * is persisted automatically. The tenant is resolved from
 * {@link com.holonplatform.multitenant.TenantContext}; the actor from the
 * registered {@link com.holonplatform.multitenant.audit.ActorResolver}.
 *
 * <h2>Examples</h2>
 * <pre>{@code
 * // Simple — no resource, default INFO severity
 * @Audited(action = AuditAction.SETTINGS_CHANGED)
 * public void updateSettings(TenantSettings settings) { ... }
 *
 * // With resource type and SpEL-resolved ID
 * @Audited(action = AuditAction.UPDATE,
 *          resourceType = "User",
 *          resourceIdExpression = "#user.id",
 *          severity = AuditSeverity.WARNING)
 * public void deactivateUser(User user) { ... }
 * }</pre>
 *
 * <h2>Failure logging</h2>
 * <p>If {@link #logOnFailure()} is true (default) and the method throws,
 * an entry is still recorded with {@link AuditSeverity#WARNING} and the
 * exception message appended to the description.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Audited {

    /** The action to log. */
    AuditAction action();

    /**
     * The logical type of the resource being acted on (e.g. {@code "User"},
     * {@code "Invoice"}). Optional — leave blank if not applicable.
     */
    String resourceType() default "";

    /**
     * SpEL expression evaluated against the method parameters to extract the
     * resource ID. Parameter names are accessible as {@code #paramName}.
     * Example: {@code "#order.id"}, {@code "#tenantId"}.
     * Leave blank to skip resource ID extraction.
     */
    String resourceIdExpression() default "";

    /** Severity of the log entry on success. */
    AuditSeverity severity() default AuditSeverity.INFO;

    /**
     * Static description. If blank, defaults to
     * {@link AuditAction#getDescription()}.
     */
    String description() default "";

    /**
     * Whether to log an entry even when the method throws an exception.
     * The logged entry will have {@link AuditSeverity#WARNING} severity
     * and include the exception message. Default: {@code true}.
     */
    boolean logOnFailure() default true;
}

