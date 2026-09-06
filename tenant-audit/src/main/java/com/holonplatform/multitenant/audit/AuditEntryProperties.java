package com.holonplatform.multitenant.audit;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;
import com.holonplatform.core.property.PropertySet;

import java.time.Instant;

/**
 * Holon {@link PropertySet} interface for {@link AuditEntry}.
 *
 * Defines metadata for audit entries including field names, labels, validation rules,
 * and type information. Used by Holon's UI components (PropertyListing, PropertyForm)
 * for dynamic field generation and validation.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #ID} — Unique identifier (server-generated)</li>
 *   <li>{@link #TENANT_ID} — Tenant identifier (required, max 255 chars)</li>
 *   <li>{@link #ACTION} — Audit action (required)</li>
 *   <li>{@link #ACTOR} — User or system that triggered the event (required, max 255 chars)</li>
 *   <li>{@link #RESOURCE_TYPE} — Type of resource affected (optional, max 100 chars)</li>
 *   <li>{@link #RESOURCE_ID} — ID of resource affected (optional, max 255 chars)</li>
 *   <li>{@link #DESCRIPTION} — Human-readable description (optional, text)</li>
 *   <li>{@link #IP_ADDRESS} — Originating IP address (optional, max 100 chars)</li>
 *   <li>{@link #SEVERITY} — Audit severity level (required)</li>
 *   <li>{@link #CREATED_AT} — Timestamp when event occurred (server-generated)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PropertyListing<AuditEntry> listing = new PropertyListing<>(AuditEntry.class);
 * listing.setProperties(AuditEntry.class);
 *
 * PropertyForm<AuditEntry> form = new PropertyForm<>(AuditEntry.class);
 * form.setPropertySet(AuditEntry.class);
 * form.setReadOnly(true); // Audit entries are immutable
 * }</pre>
 */
public interface AuditEntryProperties {

    // ── Identifiers ────────────────────────────────────────────────────────────

    /**
     * Unique identifier for the audit entry (server-generated, never user-editable).
     */
    NumericProperty<Long> ID = NumericProperty.longType("id");

    /**
     * Tenant identifier this audit entry belongs to.
     * Required, indexed for efficient queries.
     */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    // ── Event Details ────────────────────────────────────────────────────────

    /**
     * The action that was audited (e.g., USER_CREATED, PLAN_CHANGED).
     * Required, enum-based.
     */
    StringProperty ACTION = StringProperty.create("action")
        .withValidator(Validator.notBlank());

    /**
     * The actor who triggered the event (email, user ID, or "system").
     * Required, max 255 characters.
     */
    StringProperty ACTOR = StringProperty.create("actor")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(255));

    // ── Resource Details ──────────────────────────────────────────────────────

    /**
     * Type of resource affected by this audit event (e.g., "Tenant", "User", "Billing").
     * Optional, max 100 characters.
     */
    StringProperty RESOURCE_TYPE = StringProperty.create("resourceType")
        .withValidator(maxLength(100));

    /**
     * ID of the resource affected by this audit event.
     * Optional, max 255 characters.
     */
    StringProperty RESOURCE_ID = StringProperty.create("resourceId")
        .withValidator(maxLength(255));

    /**
     * Human-readable description of the audit event.
     * Optional, stored as TEXT in database.
     */
    StringProperty DESCRIPTION = StringProperty.create("description");

    // ── Technical Details ─────────────────────────────────────────────────────

    /**
     * IP address of the request that triggered this audit event.
     * Optional, useful for security analysis. Max 100 characters.
     */
    StringProperty IP_ADDRESS = StringProperty.create("ipAddress")
        .withValidator(maxLength(100));

    /**
     * Severity level of this audit event (INFO, WARNING, CRITICAL).
     * Required, used for filtering and alerting.
     */
    StringProperty SEVERITY = StringProperty.create("severity")
        .withValidator(Validator.notBlank());

    // ── Timestamps ─────────────────────────────────────────────────────────────

    /**
     * UTC timestamp when this audit event occurred.
     * Server-generated, always set by the service.
     */
    TemporalProperty<Instant> CREATED_AT = TemporalProperty.create("createdAt", Instant.class);

    // ── Validators ─────────────────────────────────────────────────────────────

    /**
     * Builds a validator rejecting {@link String} values longer than {@code max} characters.
     * Null values are considered valid (combine with {@link Validator#notBlank()} to also require a value).
     */
    static Validator<String> maxLength(int max) {
        return Validator.create(value -> value == null || value.length() <= max,
            "must not exceed " + max + " characters");
    }
}
