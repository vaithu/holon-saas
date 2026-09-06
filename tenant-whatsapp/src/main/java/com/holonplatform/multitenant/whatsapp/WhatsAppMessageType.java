package com.holonplatform.multitenant.whatsapp;

/**
 * Supported WhatsApp message content types.
 * <p>
 * Cost note: every type can be sent within an open 24-hour service window at no extra charge.
 * Outside a service window you must use a pre-approved template
 * ({@link WhatsAppConversationType#UTILITY} = free,
 *  {@link WhatsAppConversationType#MARKETING} = paid).
 * </p>
 */
public enum WhatsAppMessageType {

    /** Plain-text body. */
    TEXT,

    /** Approved message template (utility, marketing, authentication). */
    TEMPLATE,

    /** JPEG/PNG image with optional caption. */
    IMAGE,

    /** PDF or other document with optional caption. */
    DOCUMENT,

    /** Audio file (.ogg OPUS preferred). */
    AUDIO,

    /** MP4 video with optional caption. */
    VIDEO,

    /** Animated sticker (.webp). */
    STICKER,

    /** Latitude/longitude location pin. */
    LOCATION,

    /** vCard contact card(s). */
    CONTACTS,

    /** Button list or product list. */
    INTERACTIVE,

    /** Emoji reaction to a specific message. */
    REACTION
}

