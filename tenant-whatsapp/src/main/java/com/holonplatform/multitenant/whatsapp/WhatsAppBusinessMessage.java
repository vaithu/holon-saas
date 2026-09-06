package com.holonplatform.multitenant.whatsapp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Descriptor for a high-level business document message (invoice, quotation, payment reminder)
 * to be sent via WhatsApp.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Send an invoice notification (free UTILITY template)
 * WhatsAppBusinessMessage msg = WhatsAppBusinessMessage.invoice()
 *         .to("+15551234567")
 *         .referenceNumber("INV-2024-0042")
 *         .amount(new BigDecimal("1250.00"))
 *         .currency("USD")
 *         .dueDate(LocalDate.of(2024, 6, 15))
 *         .documentUrl("https://my-app.com/invoices/INV-2024-0042.pdf")
 *         .build();
 *
 * whatsAppService.sendBusinessMessage(tenantId, msg);
 *
 * // Send a quotation
 * WhatsAppBusinessMessage quote = WhatsAppBusinessMessage.quotation()
 *         .to("+15557654321")
 *         .referenceNumber("QUOTE-0011")
 *         .amount(new BigDecimal("4800.00"))
 *         .currency("EUR")
 *         .dueDate(LocalDate.now().plusDays(30))   // valid-until date
 *         .build();
 *
 * // Send a payment reminder
 * WhatsAppBusinessMessage reminder = WhatsAppBusinessMessage.paymentReminder()
 *         .to("+15551234567")
 *         .referenceNumber("INV-2024-0042")
 *         .amount(new BigDecimal("1250.00"))
 *         .currency("USD")
 *         .dueDate(LocalDate.of(2024, 6, 15))
 *         .build();
 * }</pre>
 *
 * <p>All built-in types route through a <strong>UTILITY template (free)</strong>.
 * If an open 24-hour service window exists, the service may send a plain-text
 * summary instead, saving the template slot as well.</p>
 */
public record WhatsAppBusinessMessage(
        /** Type of business document. */
        WhatsAppBusinessMessageType messageType,
        /** Recipient phone in E.164 format (e.g. +15551234567). */
        String to,
        /** Document / reference number (invoice#, quote#, order#…). */
        String referenceNumber,
        /** Monetary amount to display. */
        BigDecimal amount,
        /** ISO-4217 currency code (USD, EUR, GBP…). */
        String currency,
        /**
         * Contextual date: due date for invoices and reminders,
         * expiry / valid-until for quotations.
         */
        LocalDate dueDate,
        /**
         * Public HTTPS URL to a PDF (or other document).
         * When set, the service sends the PDF as a WhatsApp DOCUMENT attachment
         * in addition to the template notification.
         */
        String documentUrl,
        /**
         * Override the default Meta template name.
         * If {@code null}, the service uses {@link WhatsAppBusinessMessageType#getDefaultTemplateName()}.
         */
        String templateName,
        /**
         * BCP-47 language code for the template (default: {@code en_US}).
         */
        String language,
        /**
         * Extra template parameters appended after the standard ones.
         * Keys are positional (e.g. "6", "7") or semantic labels used by custom templates.
         */
        Map<String, String> extraParams
) {

    // Compact constructor – defensive copy + guard
    public WhatsAppBusinessMessage {
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(to, "to");
        extraParams = extraParams != null ? Map.copyOf(extraParams) : Map.of();
        if (language == null || language.isBlank()) language = "en_US";
    }

    // ─── Convenience factory methods ─────────────────────────────────────────

    /** Starts building an {@link WhatsAppBusinessMessageType#INVOICE} message. */
    public static Builder invoice()         { return new Builder(WhatsAppBusinessMessageType.INVOICE); }

    /** Starts building a {@link WhatsAppBusinessMessageType#QUOTATION} message. */
    public static Builder quotation()       { return new Builder(WhatsAppBusinessMessageType.QUOTATION); }

    /** Starts building a {@link WhatsAppBusinessMessageType#PAYMENT_REMINDER} message. */
    public static Builder paymentReminder() { return new Builder(WhatsAppBusinessMessageType.PAYMENT_REMINDER); }

    /** Starts building a {@link WhatsAppBusinessMessageType#PAYMENT_OVERDUE} message. */
    public static Builder paymentOverdue()  { return new Builder(WhatsAppBusinessMessageType.PAYMENT_OVERDUE); }

    /** Starts building a {@link WhatsAppBusinessMessageType#CUSTOM} message. */
    public static Builder custom(String templateName) {
        return new Builder(WhatsAppBusinessMessageType.CUSTOM).templateName(templateName);
    }

    // ─── Builder ─────────────────────────────────────────────────────────────

    public static final class Builder {
        private final WhatsAppBusinessMessageType messageType;
        private String to;
        private String referenceNumber;
        private BigDecimal amount;
        private String currency;
        private LocalDate dueDate;
        private String documentUrl;
        private String templateName;
        private String language = "en_US";
        private final Map<String, String> extraParams = new LinkedHashMap<>();

        private Builder(WhatsAppBusinessMessageType type) {
            this.messageType = type;
        }

        /** Recipient phone number in E.164 format. */
        public Builder to(String v)               { this.to = v; return this; }

        /** Document reference number (invoice#, quote#, order#). */
        public Builder referenceNumber(String v)  { this.referenceNumber = v; return this; }

        /** Monetary amount. */
        public Builder amount(BigDecimal v)        { this.amount = v; return this; }

        /** Convenience – amount as a plain double. */
        public Builder amount(double v)            { return amount(BigDecimal.valueOf(v)); }

        /** ISO-4217 currency code (USD, EUR, GBP…). */
        public Builder currency(String v)          { this.currency = v; return this; }

        /**
         * Due date for invoices/reminders; valid-until for quotations.
         */
        public Builder dueDate(LocalDate v)        { this.dueDate = v; return this; }

        /** Public HTTPS URL to a PDF / document to attach. */
        public Builder documentUrl(String v)       { this.documentUrl = v; return this; }

        /** Override the default template name. */
        public Builder templateName(String v)      { this.templateName = v; return this; }

        /** BCP-47 language code (default: en_US). */
        public Builder language(String v)          { this.language = v; return this; }

        /** Add an extra template parameter. */
        public Builder param(String key, String value) {
            this.extraParams.put(key, value); return this;
        }

        public WhatsAppBusinessMessage build() {
            return new WhatsAppBusinessMessage(
                    messageType, to, referenceNumber, amount, currency,
                    dueDate, documentUrl, templateName, language, extraParams);
        }
    }
}

