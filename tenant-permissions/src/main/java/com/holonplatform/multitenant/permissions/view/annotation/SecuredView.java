package com.holonplatform.multitenant.permissions.view.annotation;

import java.lang.annotation.*;

/**
 * Annotation to declare fine-grained view metadata and security attributes on a view class (e.g. Vaadin Flow view).
 *
 * <p>Views annotated with {@code @SecuredView} are automatically discovered and registered
 * into the {@link com.holonplatform.multitenant.permissions.view.ViewRegistry}.</p>
 *
 * <pre>{@code
 * @Route("customers")
 * @SecuredView(
 *     id = "customer-details",
 *     displayName = "Customer Details",
 *     category = "CRM",
 *     actions = {"READ", "UPDATE", "EXPORT"}
 * )
 * public class CustomerDetailsView extends VerticalLayout {
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SecuredView {

    /**
     * Unique identifier of the view (e.g. {@code "customer-details"}).
     */
    String id();

    /**
     * Human-readable label for tenant administrators (e.g. {@code "Customer Details"}).
     * If empty, defaults to {@link #id()}.
     */
    String displayName() default "";

    /**
     * Functional category or module name (e.g. {@code "CRM"}, {@code "Billing"}, {@code "Settings"}).
     */
    String category() default "General";

    /**
     * Optional brief description of what this view does.
     */
    String description() default "";

    /**
     * Set of actions supported on this view. Defaults to {@code {"READ"}}.
     */
    String[] actions() default {"READ"};
}
