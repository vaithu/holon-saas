package com.holonplatform.multitenant.billing;

/**
 * Payment status of a tenant invoice.
 */
public enum InvoiceStatus {

    /** Invoice has been issued and awaits payment. */
    OPEN,

    /** Invoice has been paid in full. */
    PAID,

    /** Invoice payment is past due. */
    PAST_DUE,

    /** Invoice has been voided. */
    VOID
}

