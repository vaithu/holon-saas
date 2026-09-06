package com.holonplatform.multitenant.whatsapp;

/**
 * High-level category of a business document message sent via WhatsApp.
 *
 * <p>Every type maps to an approved <b>UTILITY template</b> (Meta category UTILITY = <em>free</em>)
 * so sending invoices, quotations and payment reminders costs nothing beyond what
 * a normal service-window reply would cost.</p>
 *
 * <p>Default template names used when no override is provided in
 * {@link WhatsAppBusinessMessage} (via the {@code templateName} field):</p>
 * <ul>
 *   <li>{@code invoice_ready}       – invoice notification</li>
 *   <li>{@code quotation_ready}     – quotation / estimate notification</li>
 *   <li>{@code payment_reminder}    – upcoming due-date reminder</li>
 *   <li>{@code payment_overdue}     – overdue-payment alert</li>
 *   <li>(caller-supplied name)      – {@link #CUSTOM}</li>
 * </ul>
 */
public enum WhatsAppBusinessMessageType {

    /**
     * An invoice is ready for payment.
     * Default template: {@code invoice_ready}
     * Default params: {{1}}=invoice#, {{2}}=amount, {{3}}=currency, {{4}}=due-date, {{5}}=download-url.
     */
    INVOICE("invoice_ready"),

    /**
     * A quotation / estimate has been prepared.
     * Default template: {@code quotation_ready}
     * Default params: {{1}}=quote#, {{2}}=amount, {{3}}=currency, {{4}}=valid-until, {{5}}=download-url.
     */
    QUOTATION("quotation_ready"),

    /**
     * A friendly payment reminder, sent before the due date.
     * Default template: {@code payment_reminder}
     * Default params: {{1}}=invoice#, {{2}}=amount, {{3}}=currency, {{4}}=due-date.
     */
    PAYMENT_REMINDER("payment_reminder"),

    /**
     * An overdue-payment alert, sent after the due date has passed.
     * Default template: {@code payment_overdue}
     * Default params: {{1}}=invoice#, {{2}}=amount, {{3}}=currency, {{4}}=due-date.
     */
    PAYMENT_OVERDUE("payment_overdue"),

    /**
     * Fully custom – caller must supply the {@code templateName} in
     * {@link WhatsAppBusinessMessage} and all required template params.
     */
    CUSTOM(null);

    private final String defaultTemplateName;

    WhatsAppBusinessMessageType(String defaultTemplateName) {
        this.defaultTemplateName = defaultTemplateName;
    }

    /**
     * Returns the default Meta template name for this type,
     * or {@code null} if this is {@link #CUSTOM}.
     */
    public String getDefaultTemplateName() {
        return defaultTemplateName;
    }
}



