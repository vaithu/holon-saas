package com.holonplatform.multitenant.whatsapp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Primary API for multi-tenant WhatsApp Business messaging.
 *
 * <h3>Cost-optimisation contract</h3>
 * Implementations MUST implement the following strategy to minimise Meta billing:
 * <ol>
 *   <li><b>Window-first routing</b> – before sending any message, check
 *       {@link #hasOpenServiceWindow(String, String)}.  If a free 24-hour window
 *       is open, plain TEXT is sent (cheapest path).</li>
 *   <li><b>Utility templates before marketing</b> – when no window is open, prefer
 *       {@link WhatsAppConversationType#UTILITY} templates (free) over
 *       {@link WhatsAppConversationType#MARKETING} (charged).</li>
 *   <li><b>Plan-based quota enforcement</b> – marketing sends are blocked once the
 *       tenant's monthly marketing quota (determined by {@link com.holonplatform.multitenant.TenantPlan})
 *       is reached.  Use {@link #getUsageStats(String, YearMonth)} to monitor usage.</li>
 *   <li><b>Rate limiting</b> – the default implementation integrates with
 *       {@code TenantRateLimiter} to prevent burst sends.</li>
 * </ol>
 */
public interface TenantWhatsAppService {

    // ─── Sending ─────────────────────────────────────────────────────────────

    /**
     * Sends a plain-text message to a customer phone number.
     * <p>
     * The method automatically picks the cheapest path:
     * if a service window is open the message is sent as a free reply;
     * otherwise it queues a UTILITY template as a fallback.
     * </p>
     *
     * @param tenantId tenant identifier
     * @param to       recipient phone in E.164 format
     * @param text     message text (max 4 096 characters)
     * @return the persisted {@link WhatsAppMessage}
     */
    WhatsAppMessage sendTextMessage(String tenantId, String to, String text);

    /**
     * Sends a pre-approved message template.
     *
     * @param tenantId     tenant identifier
     * @param to           recipient phone in E.164 format
     * @param templateName template name as registered in Meta Business Manager
     * @param language     BCP-47 language code (e.g. {@code "en_US"})
     * @param params       ordered substitution variables ({{1}}, {{2}}, …)
     * @return the persisted {@link WhatsAppMessage}
     */
    WhatsAppMessage sendTemplateMessage(String tenantId, String to,
                                        String templateName, String language,
                                        Map<String, String> params);

    /**
     * Sends a media message (image, document, audio, video).
     *
     * @param tenantId  tenant identifier
     * @param to        recipient phone in E.164 format
     * @param mediaType must be one of IMAGE, DOCUMENT, AUDIO, VIDEO
     * @param mediaUrl  public HTTPS URL or Meta media-id
     * @param caption   optional caption (ignored for AUDIO)
     * @return the persisted {@link WhatsAppMessage}
     */
    WhatsAppMessage sendMediaMessage(String tenantId, String to,
                                     WhatsAppMessageType mediaType,
                                     String mediaUrl, String caption);

    // ─── Business document sending ────────────────────────────────────────────

    /**
     * Sends a high-level business document message (invoice, quotation, payment reminder…).
     *
     * <p>The implementation automatically:</p>
     * <ol>
     *   <li>Selects the appropriate <strong>UTILITY template (free)</strong> for the
     *       {@link WhatsAppBusinessMessageType}.</li>
     *   <li>If a 24-hour service window is open for the recipient, sends a plain-text
     *       summary instead (also free and even cheaper).</li>
     *   <li>If {@link WhatsAppBusinessMessage#documentUrl()} is set, sends the PDF
     *       as a WhatsApp DOCUMENT attachment in addition to the text/template.</li>
     * </ol>
     *
     * @param tenantId tenant identifier
     * @param message  business message descriptor built via {@link WhatsAppBusinessMessage#invoice()}, etc.
     * @return the persisted {@link WhatsAppMessage} for the text/template notification
     *         (the document attachment, if sent, is returned by the list in
     *         {@link WhatsAppBusinessSendResult#messages()})
     */
    WhatsAppBusinessSendResult sendBusinessMessage(String tenantId, WhatsAppBusinessMessage message);

    /**
     * Shortcut — sends an invoice notification via WhatsApp (UTILITY template = <em>free</em>).
     *
     * @param tenantId      tenant identifier
     * @param to            recipient phone in E.164 format
     * @param invoiceNumber invoice reference number
     * @param amount        invoice amount
     * @param currency      ISO-4217 currency code (e.g. "USD")
     * @param dueDate       payment due date
     * @param documentUrl   optional public HTTPS URL to the invoice PDF (may be {@code null})
     * @return send result wrapping the notification message and optional PDF attachment
     */
    default WhatsAppBusinessSendResult sendInvoice(
            String tenantId, String to,
            String invoiceNumber, BigDecimal amount, String currency,
            LocalDate dueDate, String documentUrl) {
        return sendBusinessMessage(tenantId, WhatsAppBusinessMessage.invoice()
                .to(to)
                .referenceNumber(invoiceNumber)
                .amount(amount)
                .currency(currency)
                .dueDate(dueDate)
                .documentUrl(documentUrl)
                .build());
    }

    /**
     * Shortcut — sends a quotation / estimate via WhatsApp (UTILITY template = <em>free</em>).
     *
     * @param tenantId    tenant identifier
     * @param to          recipient phone in E.164 format
     * @param quoteNumber quotation reference number
     * @param amount      quoted amount
     * @param currency    ISO-4217 currency code
     * @param validUntil  date until which the quotation is valid
     * @param documentUrl optional public HTTPS URL to the quotation PDF
     * @return send result
     */
    default WhatsAppBusinessSendResult sendQuotation(
            String tenantId, String to,
            String quoteNumber, BigDecimal amount, String currency,
            LocalDate validUntil, String documentUrl) {
        return sendBusinessMessage(tenantId, WhatsAppBusinessMessage.quotation()
                .to(to)
                .referenceNumber(quoteNumber)
                .amount(amount)
                .currency(currency)
                .dueDate(validUntil)
                .documentUrl(documentUrl)
                .build());
    }

    /**
     * Shortcut — sends a payment reminder (UTILITY template = <em>free</em>).
     * Should be sent a few days before the due date.
     *
     * @param tenantId      tenant identifier
     * @param to            recipient phone in E.164 format
     * @param invoiceNumber invoice reference number
     * @param amount        outstanding amount
     * @param currency      ISO-4217 currency code
     * @param dueDate       payment due date
     * @return send result
     */
    default WhatsAppBusinessSendResult sendPaymentReminder(
            String tenantId, String to,
            String invoiceNumber, BigDecimal amount, String currency,
            LocalDate dueDate) {
        return sendBusinessMessage(tenantId, WhatsAppBusinessMessage.paymentReminder()
                .to(to)
                .referenceNumber(invoiceNumber)
                .amount(amount)
                .currency(currency)
                .dueDate(dueDate)
                .build());
    }

    /**
     * Shortcut — sends an overdue-payment alert (UTILITY template = <em>free</em>).
     * Should be sent when a payment has not been received after the due date.
     *
     * @param tenantId      tenant identifier
     * @param to            recipient phone in E.164 format
     * @param invoiceNumber overdue invoice reference number
     * @param amount        overdue amount
     * @param currency      ISO-4217 currency code
     * @param dueDate       original due date
     * @return send result
     */
    default WhatsAppBusinessSendResult sendPaymentOverdue(
            String tenantId, String to,
            String invoiceNumber, BigDecimal amount, String currency,
            LocalDate dueDate) {
        return sendBusinessMessage(tenantId, WhatsAppBusinessMessage.paymentOverdue()
                .to(to)
                .referenceNumber(invoiceNumber)
                .amount(amount)
                .currency(currency)
                .dueDate(dueDate)
                .build());
    }

    // ─── Window / status queries ──────────────────────────────────────────────

    /**
     * Returns {@code true} if a 24-hour service window is currently open for the
     * given customer phone.  Use this to decide whether a free text message can
     * be sent without needing a template.
     */
    boolean hasOpenServiceWindow(String tenantId, String customerPhone);

    /**
     * Returns the active conversation window for a customer, if any.
     */
    Optional<WhatsAppConversation> getConversationWindow(String tenantId, String customerPhone);

    /**
     * Returns the delivery status of a previously sent message by its local ID.
     */
    Optional<WhatsAppMessage> getMessageStatus(String tenantId, String localId);

    // ─── Message history ─────────────────────────────────────────────────────

    /**
     * Returns a page of messages for a tenant, ordered <em>newest-first</em>.
     * Used by Vaadin's lazy {@code grid.setItems(query -&gt; ...)} callback.
     *
     * @param tenantId tenant identifier
     * @param offset   zero-based start index
     * @param limit    maximum number of items to return
     */
    List<WhatsAppMessage> listMessages(String tenantId, int offset, int limit);

    /**
     * Returns the total number of messages stored for a tenant.
     * Used by the grid's count callback when exact row count is needed.
     */
    long countMessages(String tenantId);

    /**
     * Returns the most recent {@code limit} outbound messages for a tenant
     * (convenience method; delegates to {@link #listMessages(String, int, int)}).
     */
    default List<WhatsAppMessage> listRecentMessages(String tenantId, int limit) {
        return listMessages(tenantId, 0, limit);
    }

    /**
     * Returns all messages exchanged with a specific customer phone.
     */
    List<WhatsAppMessage> listMessagesByPhone(String tenantId, String customerPhone);

    // ─── Templates ───────────────────────────────────────────────────────────

    /** Returns the available templates for a tenant (cached from Meta API or in-memory). */
    List<WhatsAppTemplate> listTemplates(String tenantId);

    // ─── Usage & cost reporting ───────────────────────────────────────────────

    /**
     * Returns messaging usage and cost statistics for the given billing month.
     * Displayed in the Vaadin cost-optimisation dashboard.
     */
    WhatsAppUsageStats getUsageStats(String tenantId, YearMonth yearMonth);

    // ─── Webhook processing ───────────────────────────────────────────────────

    /**
     * Processes a raw webhook payload received from Meta.
     * Implementations update message delivery statuses and open/refresh
     * service windows for inbound messages.
     *
     * @param tenantId identifier of the tenant that owns the WABA
     * @param payload  raw JSON string from Meta
     */
    void processWebhookPayload(String tenantId, String payload);
}




