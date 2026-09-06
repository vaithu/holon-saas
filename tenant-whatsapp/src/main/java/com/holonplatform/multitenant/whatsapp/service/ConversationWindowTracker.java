package com.holonplatform.multitenant.whatsapp.service;

import com.holonplatform.multitenant.whatsapp.WhatsAppConversation;
import com.holonplatform.multitenant.whatsapp.WhatsAppConversationType;
import com.holonplatform.multitenant.whatsapp.internal.WhatsAppConversationDatastoreHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * Tracks active 24-hour WhatsApp conversation windows per {@code (tenantId, customerPhone)}.
 *
 * <h3>Why this component is the most important cost-saving mechanism</h3>
 * <p>
 * WhatsApp charges for business-initiated conversations only when the business
 * has to <em>start</em> a new conversation (typically by sending a template).
 * {@link WhatsAppConversationType#MARKETING} templates cost the most.
 * However, when a <em>customer sends a message first</em>, a free 24-hour
 * <em>service window</em> opens.  Inside that window the business can reply
 * with any content (text, images, documents) at <strong>zero extra cost</strong>.
 * </p>
 *
 * <h3>Usage in the sending pipeline</h3>
 * <pre>{@code
 * if (tracker.hasOpenFreeWindow(tenantId, to)) {
 *     // send a plain TEXT message – completely free
 * } else {
 *     // no open window → must start a new conversation with a template
 *     // prefer UTILITY template (free) over MARKETING (charged)
 * }
 * }</pre>
 *
 * <p>Windows are persisted via {@link WhatsAppConversationDatastoreHelper} (backed by Holon
 * Datastore) so that a service restart does not lose active 24-hour windows. A window is
 * considered "open" purely based on {@link WhatsAppConversation#isOpen()} (i.e. whether
 * {@code expiresAt} is in the future); expired windows are simply excluded from queries, so
 * unlike the previous in-memory implementation no scheduled in-JVM eviction is required for
 * correctness.</p>
 */
public class ConversationWindowTracker {

    private static final Logger log = LoggerFactory.getLogger(ConversationWindowTracker.class);

    private final WhatsAppConversationDatastoreHelper datastoreHelper;

    public ConversationWindowTracker(WhatsAppConversationDatastoreHelper datastoreHelper) {
        this.datastoreHelper = Objects.requireNonNull(datastoreHelper, "datastoreHelper must not be null");
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a free 24-hour window is open for the customer.
     * The service layer calls this before every outbound message to decide whether
     * a free plain-text reply is possible.
     */
    public boolean hasOpenFreeWindow(String tenantId, String customerPhone) {
        return getWindow(tenantId, customerPhone)
                .map(WhatsAppConversation::isFreeWindow)
                .orElse(false);
    }

    /**
     * Returns the current conversation window, regardless of whether it is free.
     */
    public Optional<WhatsAppConversation> getWindow(String tenantId, String customerPhone) {
        return datastoreHelper.findByTenantIdAndCustomerPhone(tenantId, normalise(customerPhone))
                .filter(WhatsAppConversation::isOpen);
    }

    /**
     * Called when an <em>inbound</em> message arrives from a customer.
     * Opens (or refreshes) a 24-hour SERVICE window – the cheapest possible window.
     */
    public WhatsAppConversation registerInboundMessage(String tenantId, String customerPhone) {
        String phone = normalise(customerPhone);
        Optional<WhatsAppConversation> existing = datastoreHelper.findByTenantIdAndCustomerPhone(tenantId, phone);

        Instant now = Instant.now();
        WhatsAppConversation updated;

        if (existing.isPresent() && existing.get().isOpen()) {
            // refresh window + increment message count
            updated = existing.get().toBuilder()
                    .expiresAt(now.plusSeconds(86_400))
                    .incrementMessages()
                    .build();
        } else {
            // open a brand-new free service window
            updated = WhatsAppConversation.builder(tenantId, phone)
                    .type(WhatsAppConversationType.SERVICE)
                    .openedAt(now)
                    .expiresAt(now.plusSeconds(86_400))
                    .messageCount(1)
                    .charged(false)
                    .build();
        }

        datastoreHelper.save(updated);
        log.debug("Service window opened/refreshed for {}/{} – expires {}", tenantId, phone, updated.expiresAt());
        return updated;
    }

    /**
     * Called when the business sends a template message.
     * Records whether the conversation started by this template is charged.
     *
     * @param type  category of the template being sent
     */
    public WhatsAppConversation registerOutboundTemplate(String tenantId, String customerPhone,
                                                          WhatsAppConversationType type) {
        String phone = normalise(customerPhone);
        Instant now = Instant.now();

        WhatsAppConversation conv = WhatsAppConversation.builder(tenantId, phone)
                .type(type)
                .openedAt(now)
                .expiresAt(now.plusSeconds(86_400))
                .messageCount(1)
                .charged(type.isCharged())
                .build();

        datastoreHelper.save(conv);
        log.debug("Template window ({}) opened for {}/{}", type, tenantId, phone);
        return conv;
    }

    /**
     * Records an outbound message inside an already-open window.
     */
    public void recordOutboundMessage(String tenantId, String customerPhone) {
        String phone = normalise(customerPhone);
        datastoreHelper.findByTenantIdAndCustomerPhone(tenantId, phone)
                .filter(WhatsAppConversation::isOpen)
                .ifPresent(w -> datastoreHelper.save(w.toBuilder().incrementMessages().build()));
    }

    /**
     * Returns all currently open windows for a tenant (used by the Vaadin dashboard).
     */
    public Collection<WhatsAppConversation> openWindowsForTenant(String tenantId) {
        return datastoreHelper.findOpenWindowsForTenant(tenantId, Instant.now());
    }

    /** Returns the number of currently open free windows across all tenants (observability). */
    public long totalOpenWindows() {
        return datastoreHelper.countOpenWindows(Instant.now());
    }

    // ─── Background cleanup ───────────────────────────────────────────────────

    /** Purges expired windows every 10 minutes to prevent unbounded table growth. */
    @Scheduled(fixedDelay = 600_000)
    public void evictExpiredWindows() {
        long removed = datastoreHelper.deleteExpiredWindows(Instant.now());
        if (removed > 0) {
            log.debug("Evicted {} expired WhatsApp conversation windows", removed);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Normalises phone to E.164 without spaces or dashes (best-effort). */
    private static String normalise(String phone) {
        if (phone == null) return "";
        String n = phone.replaceAll("[\\s\\-()]", "");
        return n.startsWith("+") ? n : "+" + n;
    }
}



