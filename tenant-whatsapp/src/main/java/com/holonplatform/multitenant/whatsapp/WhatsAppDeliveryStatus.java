package com.holonplatform.multitenant.whatsapp;

/** Lifecycle status of a single WhatsApp message. */
public enum WhatsAppDeliveryStatus {

    /** Message is queued locally, not yet submitted to the Meta Cloud API. */
    QUEUED,

    /** Submitted to Meta; awaiting delivery to the device. */
    PENDING,

    /** Accepted and dispatched by the WhatsApp server. */
    SENT,

    /** Successfully delivered to the recipient's device. */
    DELIVERED,

    /** The recipient has opened / read the message. */
    READ,

    /** Delivery permanently failed (invalid number, blocked, etc.). */
    FAILED,

    /** Message was deleted by the sender before being read. */
    DELETED
}

