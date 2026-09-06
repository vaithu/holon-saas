package com.holonplatform.multitenant.whatsapp;

import java.util.List;

/**
 * Approved WhatsApp message template registered in the Meta Business Manager.
 *
 * <p>Templates are created on the Meta side and referenced by name + language when sending.
 * The {@link WhatsAppConversationType} of the template determines billing:
 * UTILITY = free, MARKETING = charged per conversation.</p>
 */
public record WhatsAppTemplate(
        String name,
        String language,
        WhatsAppConversationType category,
        /** Meta approval status: APPROVED, PENDING, REJECTED. */
        String status,
        List<TemplateComponent> components,
        String tenantId
) {

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(status);
    }

    public boolean isFree() {
        return category != null && category.isFree();
    }

    /**
     * A single component in the template body (HEADER, BODY, FOOTER, BUTTONS).
     *
     * @param type       component type (HEADER, BODY, FOOTER, BUTTONS)
     * @param text       raw template text with {{1}} placeholders
     * @param paramCount number of variable substitutions required
     */
    public record TemplateComponent(String type, String text, int paramCount) {}
}

