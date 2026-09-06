package com.holonplatform.multitenant.billing;

/**
 * Status of a tenant subscription.
 */
public enum SubscriptionStatus {

    /** Trial period is active. */
    TRIALING,

    /** Active, paid subscription. */
    ACTIVE,

    /** Payment is past due; grace period may apply. */
    PAST_DUE,

    /**
     * Subscription has been cancelled by the tenant owner.
     * Access continues until the period end date.
     */
    CANCELLED,

    /**
     * Subscription has expired (trial expired or cancellation period ended).
     * Tenant may still log in but features are restricted to the FREE plan defaults.
     */
    EXPIRED;

    /** Returns {@code true} if the subscription grants full feature access. */
    public boolean isAccessible() {
        return this == TRIALING || this == ACTIVE || this == PAST_DUE || this == CANCELLED;
    }
}

