package com.holonplatform.multitenant.quotas.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces a quota check before the annotated method executes.
 *
 * <p>The value must match the {@link com.holonplatform.multitenant.quotas.QuotaKey#key()}
 * of a registered {@link com.holonplatform.multitenant.quotas.QuotaDefinition}.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * @Service
 * public class ContactService {
 *
 *     @EnforceQuota("max_contacts")
 *     public Contact createContact(ContactDto dto) { ... }
 *
 *     @EnforceQuota(value = "max_active_deals", message = "Deal limit reached. Please close some deals first.")
 *     public Deal openDeal(DealDto dto) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnforceQuota {

    /**
     * The quota key string matching a registered
     * {@link com.holonplatform.multitenant.quotas.QuotaDefinition}.
     * E.g. {@code "max_contacts"} or {@code QuotaKeys.MAX_CONTACTS.key()}.
     */
    String value();

    /**
     * Optional message included in the {@link com.holonplatform.multitenant.quotas.exception.QuotaExceededException}.
     */
    String message() default "Quota exceeded for your current plan. Please upgrade to continue.";
}

