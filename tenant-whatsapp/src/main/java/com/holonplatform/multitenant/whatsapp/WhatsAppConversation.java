package com.holonplatform.multitenant.whatsapp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Mutable, JPA-mapped representation of an active WhatsApp conversation window between a
 * tenant's business phone and a customer.
 *
 * <h3>How windows work</h3>
 * <ul>
 *   <li>A <em>service window</em> opens when the customer sends any message to the business.
 *       Within 24 hours, the business may reply with any content type <strong>free of charge</strong>.</li>
 *   <li>A <em>utility-template window</em> opens when the business sends an approved Utility template.
 *       The 24-hour clock starts from that first template message.</li>
 *   <li>If no window is open, sending a text message is not allowed (Meta API rejects it).
 *       The business must start a new conversation with a pre-approved template.</li>
 * </ul>
 *
 * <p>This is the durable window-state entity: it is the source of truth for open conversation
 * windows, persisted via Holon's {@code BeanDatastoreHelper} (see
 * {@code internal.WhatsAppConversationDatastoreHelper}) so a service restart does not lose
 * active 24-hour windows. One row exists per {@code (tenantId, customerPhone)} pair (identified
 * by the natural key {@link #windowKey()}) and is considered "open" while
 * {@link Instant#now()} is before {@link #expiresAt()} — no separate eviction is required for
 * correctness, since expired windows are simply excluded from "open windows" queries.</p>
 *
 * <p><strong>Mutability note:</strong> unlike its previous record-based incarnation, this
 * class is mutable to satisfy JPA/Datastore requirements (no-arg constructor, settable
 * fields). Prefer {@link #toBuilder()} to derive modified copies rather than calling setters
 * directly, except where required by persistence infrastructure.</p>
 */
@Entity(name = "whatsappconversation")
@Table(name = "whatsapp_conversation", indexes = {
        @Index(name = "idx_whatsapp_conversation_tenant", columnList = "tenant_id"),
        @Index(name = "idx_whatsapp_conversation_tenant_expires", columnList = "tenant_id, expires_at")
})
public class WhatsAppConversation {

    /** Composite natural key: {@code tenantId::customerPhone}. */
    @Id
    @Column(name = "window_key", nullable = false, length = 128)
    private String windowKey;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** Customer phone in E.164 format. */
    @Column(name = "customer_phone", nullable = false, length = 32)
    private String customerPhone;

    @Column(name = "type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private WhatsAppConversationType type = WhatsAppConversationType.SERVICE;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    /** When the 24-hour free-reply window closes. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Total messages exchanged in this window. */
    @Column(name = "message_count", nullable = false)
    private int messageCount;

    /**
     * {@code true} when at least one message in this window was in a charged conversation
     * (i.e. a MARKETING or AUTHENTICATION template was used to open the conversation).
     */
    @Column(name = "charged", nullable = false)
    private boolean charged;

    /**
     * JPA / Datastore default constructor.
     *
     * <p>Must be {@code public} — Holon's JPA Datastore ({@code JpaSave}) instantiates
     * entities reflectively via {@code Class.newInstance()}, which performs a normal Java
     * access check; a {@code protected}/{@code private} constructor fails at runtime with
     * {@code IllegalAccessException}.
     */
    public WhatsAppConversation() {
    }

    private WhatsAppConversation(String windowKey, String tenantId, String customerPhone,
            WhatsAppConversationType type, Instant openedAt, Instant expiresAt,
            int messageCount, boolean charged) {
        this.windowKey = Objects.requireNonNull(windowKey, "windowKey must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.customerPhone = Objects.requireNonNull(customerPhone, "customerPhone must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.messageCount = messageCount;
        this.charged = charged;
    }

    // -------------------------------------------------------------------------
    // Record-style accessors (kept for source compatibility with existing callers)
    // -------------------------------------------------------------------------

    public String windowKey() {
        return windowKey;
    }

    public String tenantId() {
        return tenantId;
    }

    public String customerPhone() {
        return customerPhone;
    }

    public WhatsAppConversationType type() {
        return type;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public int messageCount() {
        return messageCount;
    }

    public boolean charged() {
        return charged;
    }

    // -------------------------------------------------------------------------
    // JavaBean getters/setters (required for JPA and Holon BeanDatastoreHelper)
    // -------------------------------------------------------------------------

    public String getWindowKey() {
        return windowKey;
    }

    public void setWindowKey(String windowKey) {
        this.windowKey = windowKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public WhatsAppConversationType getType() {
        return type;
    }

    public void setType(WhatsAppConversationType type) {
        this.type = type;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(int messageCount) {
        this.messageCount = messageCount;
    }

    public boolean isCharged() {
        return charged;
    }

    public void setCharged(boolean charged) {
        this.charged = charged;
    }

    // -------------------------------------------------------------------------
    // Convenience query methods
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the 24-hour service window is still open. */
    public boolean isOpen() {
        return Instant.now().isBefore(expiresAt);
    }

    /** Returns {@code true} if messages sent now in this window are free. */
    public boolean isFreeWindow() {
        return isOpen() && type.isFree();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder(String tenantId, String customerPhone) {
        return new Builder(tenantId, customerPhone);
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static final class Builder {
        private final String windowKey;
        private final String tenantId;
        private final String customerPhone;
        private WhatsAppConversationType type = WhatsAppConversationType.SERVICE;
        private Instant openedAt = Instant.now();
        private Instant expiresAt = openedAt.plusSeconds(86_400);
        private int messageCount = 0;
        private boolean charged = false;

        private Builder(String tenantId, String customerPhone) {
            this.tenantId = Objects.requireNonNull(tenantId);
            this.customerPhone = Objects.requireNonNull(customerPhone);
            this.windowKey = tenantId + "::" + customerPhone;
        }

        private Builder(WhatsAppConversation c) {
            this.windowKey = c.windowKey();
            this.tenantId = c.tenantId();
            this.customerPhone = c.customerPhone();
            this.type = c.type();
            this.openedAt = c.openedAt();
            this.expiresAt = c.expiresAt();
            this.messageCount = c.messageCount();
            this.charged = c.charged();
        }

        public Builder type(WhatsAppConversationType v) { this.type = v; return this; }
        public Builder openedAt(Instant v) { this.openedAt = v; this.expiresAt = v.plusSeconds(86_400); return this; }
        public Builder expiresAt(Instant v) { this.expiresAt = v; return this; }
        public Builder messageCount(int v) { this.messageCount = v; return this; }
        public Builder charged(boolean v) { this.charged = v; return this; }
        public Builder incrementMessages() { this.messageCount++; return this; }

        public WhatsAppConversation build() {
            return new WhatsAppConversation(windowKey, tenantId, customerPhone,
                    type, openedAt, expiresAt, messageCount, charged);
        }
    }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WhatsAppConversation that)) {
            return false;
        }
        return messageCount == that.messageCount
                && charged == that.charged
                && Objects.equals(windowKey, that.windowKey)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(customerPhone, that.customerPhone)
                && type == that.type
                && Objects.equals(openedAt, that.openedAt)
                && Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowKey, tenantId, customerPhone, type, openedAt, expiresAt, messageCount, charged);
    }

    @Override
    public String toString() {
        return "WhatsAppConversation[windowKey=" + windowKey + ", tenantId=" + tenantId
                + ", customerPhone=" + customerPhone + ", type=" + type + ", openedAt=" + openedAt
                + ", expiresAt=" + expiresAt + ", messageCount=" + messageCount + ", charged=" + charged + "]";
    }
}

