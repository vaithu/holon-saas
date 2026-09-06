package com.holonplatform.multitenant.permissions;

import java.util.Optional;

/**
 * Extension of the holon-core {@link com.holonplatform.auth.Permission} that also satisfies
 * a {@link #name()} contract, making it compatible with Java {@code enum} constants.
 *
 * <p>Implement this interface as an enum to define your application's permission set:
 * <pre>{@code
 * public enum CrmPermission implements Permission {
 *     VIEW_CONTACTS, CREATE_CONTACT, DELETE_CONTACT,
 *     VIEW_FINANCIALS, APPROVE_INVOICE,
 *     MANAGE_HR, MANAGE_DELIVERIES;
 * }
 * }</pre>
 *
 * <p>The {@link #getPermission()} method delegates to {@link #name()}, providing
 * compatibility with the holon-core authorization framework ({@code Realm}, {@code Authorizer}).
 */
public interface Permission extends com.holonplatform.auth.Permission {

    /**
     * The unique string identifier of this permission.
     * When using an enum, this is the enum constant name (e.g. {@code "VIEW_FINANCIALS"}).
     */
    String name();

    /**
     * Returns {@link #name()} as the holon-core string permission representation.
     * This bridges the tenant permission system with the holon-core {@code Authorizer}.
     */
    @Override
    default Optional<String> getPermission() {
        return Optional.of(name());
    }
}
