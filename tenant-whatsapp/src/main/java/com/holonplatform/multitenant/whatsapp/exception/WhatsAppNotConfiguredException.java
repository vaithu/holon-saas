package com.holonplatform.multitenant.whatsapp.exception;

/**
 * Thrown when the WhatsApp integration has not been configured for a tenant
 * (missing phone-number-id, access token, or WABA credentials).
 */
public class WhatsAppNotConfiguredException extends WhatsAppException {

    private static final long serialVersionUID = 1L;

    public WhatsAppNotConfiguredException(String tenantId) {
        super("WhatsApp is not configured for tenant '%s'. "
                + "Set holon.tenant.whatsapp.shared.* or provide a TenantWhatsAppCredentialsProvider bean."
                        .formatted(tenantId));
    }
}

