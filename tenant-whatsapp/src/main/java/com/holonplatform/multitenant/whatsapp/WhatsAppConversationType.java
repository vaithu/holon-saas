package com.holonplatform.multitenant.whatsapp;

/**
 * WhatsApp Business conversation categories that determine billing.
 *
 * <h3>Current pricing model (Meta, effective 2024-11-01)</h3>
 * <ul>
 *   <li><b>SERVICE</b>  – user-initiated reply within 24 h window → <em>FREE (unlimited)</em></li>
 *   <li><b>UTILITY</b>  – business-initiated transactional template  → <em>FREE (unlimited)</em></li>
 *   <li><b>AUTHENTICATION</b> – OTP/verification template → <em>charged per message</em></li>
 *   <li><b>MARKETING</b>     – promotional/re-engagement template → <em>charged per conversation</em></li>
 *   <li><b>REFERRAL_CONVERSION</b> – first message from click-to-WhatsApp ad → <em>FREE first conv.</em></li>
 * </ul>
 *
 * <h3>Cost optimisation strategy</h3>
 * Always try to keep conversations in SERVICE or UTILITY category.
 * Use MARKETING only when explicitly required by the tenant's business rules and the tenant plan allows it.
 */
public enum WhatsAppConversationType {

    /**
     * User-initiated conversation (service window).
     * Any number of messages sent within the 24-hour window are <strong>free</strong>.
     * The {@link com.holonplatform.multitenant.whatsapp.service.ConversationWindowTracker}
     * tracks open windows so the service can exploit them before falling back to templates.
     */
    SERVICE,

    /**
     * Business-initiated utility template (order confirmations, delivery updates, account alerts).
     * <strong>Free</strong> since the Meta 2024 pricing simplification.
     * Prefer this over MARKETING for all transactional notifications.
     */
    UTILITY,

    /**
     * One-time password / verification code templates.
     * Charged per message; use only when OTP is genuinely required.
     */
    AUTHENTICATION,

    /**
     * Promotional, re-engagement or upsell templates.
     * Most expensive category – restricted to PROFESSIONAL and ENTERPRISE plans by default.
     */
    MARKETING,

    /**
     * Conversation started via a click-to-WhatsApp advertisement.
     * The first conversation is <strong>free</strong> regardless of template category.
     */
    REFERRAL_CONVERSION;

    /** Returns {@code true} if this conversation type results in zero additional API cost. */
    public boolean isFree() {
        return this == SERVICE || this == UTILITY || this == REFERRAL_CONVERSION;
    }

    /** Returns {@code true} if Meta charges for messages sent in this conversation. */
    public boolean isCharged() {
        return !isFree();
    }
}

