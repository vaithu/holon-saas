package com.holonplatform.multitenant.whatsapp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result returned by
 * {@link TenantWhatsAppService#sendBusinessMessage(String, WhatsAppBusinessMessage)}
 * and all its convenience shortcuts.
 *
 * <p>A single business-document send may produce two WhatsApp messages:</p>
 * <ol>
 *   <li>{@link #notification()} – the text / template message carrying the
 *       document details (always present).</li>
 *   <li>{@link #attachment()} – the PDF document attachment
 *       (present only when {@link WhatsAppBusinessMessage#documentUrl()} was set).</li>
 * </ol>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * WhatsAppBusinessSendResult result = whatsAppService.sendInvoice(
 *         tenantId, "+15551234567",
 *         "INV-2024-0042", new BigDecimal("1250.00"), "USD",
 *         LocalDate.of(2024, 6, 15),
 *         "https://my-app.com/invoices/INV-2024-0042.pdf");
 *
 * WhatsAppMessage notification = result.notification();
 * result.attachment().ifPresent(pdf -> log.info("PDF sent: {}", pdf.messageId()));
 * System.out.println("All messages: " + result.messages());
 * }</pre>
 */
public record WhatsAppBusinessSendResult(
        /** The template / text notification message (always present). */
        WhatsAppMessage notification,
        /**
         * The PDF document attachment message.
         * Present only when {@link WhatsAppBusinessMessage#documentUrl()} was non-null.
         */
        WhatsAppMessage attachmentMessage
) {

    public WhatsAppBusinessSendResult {
        Objects.requireNonNull(notification, "notification");
    }

    /**
     * Returns the PDF document attachment, if one was sent.
     */
    public Optional<WhatsAppMessage> attachment() {
        return Optional.ofNullable(attachmentMessage);
    }

    /**
     * Returns all messages that were sent (notification + optional attachment),
     * in send order.
     */
    public List<WhatsAppMessage> messages() {
        return attachmentMessage != null
                ? List.of(notification, attachmentMessage)
                : List.of(notification);
    }

    /**
     * Returns {@code true} if all messages were sent (or queued) without error.
     */
    public boolean isSuccess() {
        boolean notifOk = notification.status() != WhatsAppDeliveryStatus.FAILED;
        boolean attachOk = attachmentMessage == null
                || attachmentMessage.status() != WhatsAppDeliveryStatus.FAILED;
        return notifOk && attachOk;
    }

    // ── Factory ──────────────────────────────────────────────────────────────

    public static WhatsAppBusinessSendResult of(WhatsAppMessage notification) {
        return new WhatsAppBusinessSendResult(notification, null);
    }

    public static WhatsAppBusinessSendResult of(WhatsAppMessage notification,
                                                 WhatsAppMessage attachment) {
        return new WhatsAppBusinessSendResult(notification, attachment);
    }
}

