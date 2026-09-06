package com.holonplatform.multitenant.whatsapp.exception;

/**
 * Thrown when a tenant has exhausted its configured WhatsApp message quota for the current month.
 * The quota is enforced per {@link com.holonplatform.multitenant.TenantPlan}:
 * FREE → 50 marketing messages/month, STARTER → 200, PROFESSIONAL → 2 000, ENTERPRISE → unlimited.
 */
public class WhatsAppQuotaExceededException extends WhatsAppException {

    private static final long serialVersionUID = 1L;

    private final String tenantId;
    private final int limit;
    private final int current;

    public WhatsAppQuotaExceededException(String tenantId, int limit, int current) {
        super("WhatsApp marketing quota exceeded for tenant '%s': limit=%d, current=%d"
                .formatted(tenantId, limit, current));
        this.tenantId = tenantId;
        this.limit = limit;
        this.current = current;
    }

    public String getTenantId() { return tenantId; }
    public int getLimit()       { return limit; }
    public int getCurrent()     { return current; }
}

