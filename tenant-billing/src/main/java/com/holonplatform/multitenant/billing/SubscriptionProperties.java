package com.holonplatform.multitenant.billing;

import com.holonplatform.core.Validator;
import com.holonplatform.core.property.StringProperty;
import com.holonplatform.core.property.TemporalProperty;
import com.holonplatform.core.property.PropertySet;

import java.time.Instant;

/**
 * Holon {@link PropertySet} interface for {@link Subscription}.
 *
 * Defines metadata for tenant subscriptions including field names, labels, validation rules,
 * and type information. Used by Holon's UI components (PropertyListing, PropertyForm)
 * for dynamic subscription display and management.
 *
 * <h2>Fields</h2>
 * <ul>
 *   <li>{@link #TENANT_ID} — Tenant this subscription belongs to; also the primary key (required, max 100 chars)</li>
 *   <li>{@link #PLAN} — Current subscription plan (required)</li>
 *   <li>{@link #STATUS} — Subscription lifecycle status (required)</li>
 *   <li>{@link #PERIOD_START} — Start of the current billing period (required)</li>
 *   <li>{@link #PERIOD_END} — End of the current billing period (optional)</li>
 *   <li>{@link #TRIAL_END} — End of the trial period (optional)</li>
 *   <li>{@link #CANCELLED_AT} — When subscription was cancelled (optional)</li>
 *   <li>{@link #EXTERNAL_REF} — External billing system reference (optional, max 255 chars)</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PropertyListing<Subscription> listing = new PropertyListing<>(Subscription.class);
 * listing.setProperties(SubscriptionProperties.class);
 *
 * PropertyForm<Subscription> form = new PropertyForm<>(Subscription.class);
 * form.setPropertySet(SubscriptionProperties.class);
 * form.setReadOnly(true); // Subscriptions are treated as immutable value objects
 * }</pre>
 */
public interface SubscriptionProperties {

    // ── Identifiers ────────────────────────────────────────────────────────────

    /**
     * Tenant identifier this subscription belongs to; also the primary key.
     * Required, never null. Max 100 characters.
     */
    StringProperty TENANT_ID = StringProperty.create("tenantId")
        .withValidator(Validator.notBlank())
        .withValidator(maxLength(100));

    // ── Plan & Status ──────────────────────────────────────────────────────────

    /**
     * Current subscription plan (FREE, STARTER, PROFESSIONAL, ENTERPRISE).
     * Required, determines feature access and pricing.
     */
    StringProperty PLAN = StringProperty.create("plan")
        .withValidator(Validator.notBlank());

    /**
     * Current subscription lifecycle status (TRIALING, ACTIVE, PAST_DUE, CANCELLED, EXPIRED).
     * Required, determines whether tenant can access features.
     */
    StringProperty STATUS = StringProperty.create("status")
        .withValidator(Validator.notBlank());

    // ── Billing Period ─────────────────────────────────────────────────────────

    /**
     * Start of the current billing period (required, never null).
     */
    TemporalProperty<Instant> PERIOD_START = TemporalProperty.create("periodStart", Instant.class);

    /**
     * End of the current billing period (optional, null for open-ended subscriptions).
     */
    TemporalProperty<Instant> PERIOD_END = TemporalProperty.create("periodEnd", Instant.class);

    // ── Trial Period ───────────────────────────────────────────────────────────

    /**
     * End of the trial period (optional, null if not in trial).
     * Used to determine if tenant is in free trial.
     */
    TemporalProperty<Instant> TRIAL_END = TemporalProperty.create("trialEnd", Instant.class);

    // ── Cancellation ───────────────────────────────────────────────────────────

    /**
     * When cancellation was requested (optional, null if not cancelled).
     * Allows tracking when subscription was terminated.
     */
    TemporalProperty<Instant> CANCELLED_AT = TemporalProperty.create("cancelledAt", Instant.class);

    /**
     * External billing system reference (e.g., Stripe subscription ID).
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

