package com.holonplatform.multitenant.users;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;
import com.holonplatform.core.property.PropertySet;

import java.time.Instant;

/**
 * Holon {@link PropertySet} interface for {@link TenantUser}.
 *
 * Defines metadata for tenant users including field names, labels, validation rules,
 * and type information. Used by Holon's UI components (PropertyListing, PropertyForm)
 * for dynamic field generation, validation, and data binding.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #ID} — Unique identifier (server-generated)</li>
 *   <li>{@link #USER_ID} — Global user identifier (required, max 255 chars)</li>
 *   <li>{@link #TENANT_ID} — Tenant identifier (required, max 255 chars)</li>
 *   <li>{@link #EMAIL} — User email address (required, max 255 chars)</li>
 *   <li>{@link #DISPLAY_NAME} — User display name (optional, max 255 chars)</li>
 *   <li>{@link #ROLE} — User role within this tenant (required)</li>
 *   <li>{@link #STATUS} — User status/membership state (required)</li>
 *   <li>{@link #CREATED_AT} — When user was added to tenant (server-generated)</li>
 *   <li>{@link #LAST_LOGIN_AT} — Last successful login timestamp (optional)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PropertyListing<TenantUser> listing = new PropertyListing<>(TenantUser.class);
 * listing.setProperties(TenantUserProperties.class);
 *
 * PropertyForm<TenantUser> form = new PropertyForm<>(TenantUser.class);
 * form.setPropertySet(TenantUserProperties.class);
 * form.bind(tenantUser); // Two-way binding
 * }</pre>
 */
public interface TenantUserProperties {

    // ── Identifiers ────────────────────────────────────────────────────────────

    /**
     * Unique identifier for this tenant-user mapping (server-generated, never user-editable).
     */
    NumericProperty<Long> ID = NumericProperty.longType("id")
        .messageCode("user.id");

    /**
     * Global user identifier across all tenants.
     * Required, max 255 characters.
     */
    StringProperty USER_ID = StringProperty.create("userId")
        .messageCode("user.user_id")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    /**
     * Tenant identifier this user belongs to.
     * Required, max 255 characters.
     */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .messageCode("user.tenant_id")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    // ── User Information ───────────────────────────────────────────────────────

    /**
     * User email address (must be unique per tenant).
     * Required, max 255 characters, validated format.
     */
    StringProperty EMAIL = StringProperty.create("email")
        .messageCode("user.email")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    /**
     * User display name (optional, used in UI instead of email when available).
     * Optional, max 255 characters.
     */
    StringProperty DISPLAY_NAME = StringProperty.create("displayName")
        .messageCode("user.display_name")
        .withValidator(maxLength(255));

    // ── Tenant Membership ──────────────────────────────────────────────────────

    /**
     * User role within this tenant (VIEWER, MEMBER, ADMIN, OWNER).
     * Required, determines permissions and feature access.
     */
    StringProperty ROLE = StringProperty.create("role")
        .messageCode("user.role")
        .withValidator(Validator.notBlank());

    /**
     * Current status of user membership (INVITED, ACTIVE, SUSPENDED, REMOVED).
     * Required, controls whether user can access the tenant.
     */
    StringProperty STATUS = StringProperty.create("status")
        .messageCode("user.status")
        .withValidator(Validator.notBlank());

    // ── Timestamps ─────────────────────────────────────────────────────────────

    /**
     * UTC timestamp when user was added to this tenant.
     * Server-generated, always set when the record is created.
     */
    TemporalProperty<Instant> CREATED_AT = TemporalProperty.instant("createdAt")
        .messageCode("user.created_at");

    /**
     * UTC timestamp of user's last successful login.
     * Optional, null if user has never logged in.
     */
    TemporalProperty<Instant> LAST_LOGIN_AT = TemporalProperty.instant("lastLoginAt")
        .messageCode("user.last_login_at");

    // ── Validators ─────────────────────────────────────────────────────────────

    /**
     * Builds a validator rejecting {@link String} values longer than {@code max} characters.
     * Null values are considered valid (combine with {@link Validator#notBlank()} to also require a value).
     */
    private static Validator<String> maxLength(int max) {
        return Validator.create(value -> value == null || value.length() <= max,
            "must not exceed " + max + " characters");
    }
}


