package com.holonplatform.multitenant.permissions.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces a permission check on a Spring-managed method or class.
 *
 * <p>When applied to a method, the current user must have <strong>at least one</strong> of
 * the specified permissions (OR logic, resolved via
 * {@link com.holonplatform.multitenant.permissions.TenantPermissionService#currentUserHasPermission})
 * or a {@link com.holonplatform.multitenant.permissions.exception.PermissionDeniedException} is thrown.</p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@literal @}Service
 * public class FinancialService {
 *
 *     {@literal @}RequiresPermission("VIEW_FINANCIALS")
 *     public List{@literal <}Invoice{@literal >} getInvoices() { ... }
 *
 *     {@literal @}RequiresPermission({"APPROVE_INVOICE", "ADMIN"})
 *     public void approveInvoice(Long id) { ... }
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    /**
     * The permission string(s) that the current user must have. If more than one value is
     * given, the user needs only one of them (OR logic) — matching the pattern commonly used
     * to grant either a specific permission or a blanket role such as {@code "ADMIN"}.
     * Should match a key registered in {@link com.holonplatform.multitenant.permissions.TenantPermissionService}.
     */
    String[] value();

    /**
     * Optional human-readable message included in the exception when access is denied.
     */
    String message() default "You do not have permission to perform this action.";
}

