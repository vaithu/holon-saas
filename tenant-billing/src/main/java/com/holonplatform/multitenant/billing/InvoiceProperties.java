package com.holonplatform.multitenant.billing;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.NumericProperty;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;
import com.holonplatform.core.property.PropertySet;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Holon {@link PropertySet} interface for {@link Invoice}.
 *
 * Defines metadata for tenant invoices including field names, labels, validation rules,
 * and type information. Used by Holon's UI components (PropertyListing, PropertyForm)
 * for dynamic field generation and validation.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #INVOICE_ID} — Unique identifier for the invoice (required, max 100 chars)</li>
 *   <li>{@link #TENANT_ID} — Tenant this invoice belongs to (required, max 100 chars)</li>
 *   <li>{@link #AMOUNT_DUE} — Total amount due (required)</li>
 *   <li>{@link #CURRENCY} — ISO 4217 currency code, e.g. "USD" (required, max 3 chars)</li>
 *   <li>{@link #STATUS} — Current payment status (required)</li>
 *   <li>{@link #ISSUED_AT} — When the invoice was created (required)</li>
 *   <li>{@link #PAID_AT} — When the invoice was paid (optional)</li>
 *   <li>{@link #EXTERNAL_REF} — External billing system reference (optional, max 255 chars)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PropertyListing<Invoice> listing = new PropertyListing<>(Invoice.class);
 * listing.setProperties(InvoiceProperties.class);
 *
 * PropertyForm<Invoice> form = new PropertyForm<>(Invoice.class);
 * form.setPropertySet(InvoiceProperties.class);
 * form.setReadOnly(true); // Invoices are treated as immutable value objects
 * }</pre>
 */
public interface InvoiceProperties {

    // ── Identifiers ────────────────────────────────────────────────────────────

    /**
     * Unique identifier for this invoice (required, never null).
     * Max 100 characters.
     */
    StringProperty INVOICE_ID = StringProperty.create("invoiceId")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(100));

    /**
     * Tenant identifier this invoice belongs to.
     * Required, indexed for efficient queries. Max 100 characters.
     */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(100));

    // ── Amount & Currency ──────────────────────────────────────────────────────

    /**
     * Total amount due for this invoice (required, never null).
     */
    NumericProperty<BigDecimal> AMOUNT_DUE = NumericProperty.create("amountDue", BigDecimal.class);

    /**
     * ISO 4217 currency code, e.g. "USD" (required, never null).
     * Max 3 characters.
     */
    StringProperty CURRENCY = StringProperty.create("currency")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(3));

    // ── Status ─────────────────────────────────────────────────────────────────

    /**
     * Current payment status of the invoice (OPEN, PAID, PAST_DUE, VOID).
     * Required, determines whether the invoice is settled.
     */
    StringProperty STATUS = StringProperty.create("status")
        .withValidator(Validator.notBlank());

    // ── Timestamps ─────────────────────────────────────────────────────────────

    /**
     * UTC timestamp when the invoice was created (required, never null).
     */
    TemporalProperty<Instant> ISSUED_AT = TemporalProperty.create("issuedAt", Instant.class);

    /**
     * UTC timestamp when the invoice was paid (optional, null if not yet paid).
     */
    TemporalProperty<Instant> PAID_AT = TemporalProperty.create("paidAt", Instant.class);

    /**
     * External billing system reference (e.g. Stripe invoice ID).
     * Optional, max 255 characters.
     */
    StringProperty EXTERNAL_REF = StringProperty.create("externalRef")
        .withValidator(maxLength(255));

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

