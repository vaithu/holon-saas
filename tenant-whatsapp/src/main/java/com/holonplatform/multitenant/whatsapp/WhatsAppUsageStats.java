package com.holonplatform.multitenant.whatsapp;

import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/**
 * Monthly usage statistics for a tenant's WhatsApp channel.
 *
 * <p>The Vaadin dashboard uses this record to render a live cost breakdown,
 * helping operators understand how much of their budget is consumed by
 * {@link WhatsAppConversationType#MARKETING} (charged) vs free categories.</p>
 *
 * @param tenantId           tenant identifier
 * @param period             billing month
 * @param totalMessages      all outbound messages (queued + sent + delivered)
 * @param freeMessages       messages sent in free conversation windows
 * @param chargedMessages    messages that triggered a billing entry
 * @param byConversationType message counts grouped by conversation type
 * @param openWindows        number of currently active 24-hour service windows
 * @param estimatedCostUsd   rough cost estimate (USD) based on Meta's published rates
 */
public record WhatsAppUsageStats(
        String tenantId,
        YearMonth period,
        long totalMessages,
        long freeMessages,
        long chargedMessages,
        Map<WhatsAppConversationType, Long> byConversationType,
        int openWindows,
        double estimatedCostUsd
) {

    /** Returns the percentage of messages that were free (0-100). */
    public double freePercentage() {
        if (totalMessages == 0) return 100.0;
        return (double) freeMessages / totalMessages * 100.0;
    }

    public static Builder builder(String tenantId, YearMonth period) {
        return new Builder(tenantId, period);
    }

    public static final class Builder {
        private final String tenantId;
        private final YearMonth period;
        private long totalMessages;
        private long freeMessages;
        private long chargedMessages;
        private final Map<WhatsAppConversationType, Long> byType = new EnumMap<>(WhatsAppConversationType.class);
        private int openWindows;
        private double estimatedCostUsd;

        private Builder(String tenantId, YearMonth period) {
            this.tenantId = tenantId;
            this.period = period;
        }

        public Builder totalMessages(long v) { this.totalMessages = v; return this; }
        public Builder freeMessages(long v) { this.freeMessages = v; return this; }
        public Builder chargedMessages(long v) { this.chargedMessages = v; return this; }
        public Builder count(WhatsAppConversationType type, long count) { this.byType.put(type, count); return this; }
        public Builder openWindows(int v) { this.openWindows = v; return this; }
        public Builder estimatedCostUsd(double v) { this.estimatedCostUsd = v; return this; }

        public WhatsAppUsageStats build() {
            return new WhatsAppUsageStats(tenantId, period, totalMessages, freeMessages,
                    chargedMessages, Map.copyOf(byType), openWindows, estimatedCostUsd);
        }
    }
}

