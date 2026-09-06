package com.holonplatform.multitenant.whatsapp;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.holonplatform.multitenant.convert.StringMapJsonConverter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Mutable, JPA-mapped representation of a WhatsApp message, including its delivery status
 * and whether it falls into a <em>free</em> or <em>charged</em> conversation.
 *
 * <p>This class is the durable message-log entity: it is the source of truth for message
 * history/audit and for computing {@link WhatsAppUsageStats}, persisted via Holon's
 * {@code BeanDatastoreHelper} (see {@code internal.WhatsAppMessageDatastoreHelper}), which is
 * why it exposes standard JavaBean getters/setters in addition to its original record-style
 * accessors ({@link #localId()}, {@link #tenantId()}, ...).
 *
 * <p><strong>Mutability note:</strong> unlike its previous record-based incarnation, this
 * class is mutable to satisfy JPA/Datastore requirements (no-arg constructor, settable
 * fields). Application code should still treat instances as effectively immutable and prefer
 * {@link #toBuilder()} to derive modified copies rather than calling setters directly, except
 * where required by persistence infrastructure.</p>
 *
 * <p>Use {@link #builder()} to construct instances.</p>
 */
@Entity(name = "whatsappmessage")
@Table(name = "whatsapp_message", indexes = {
        @Index(name = "idx_whatsapp_message_tenant", columnList = "tenant_id"),
        @Index(name = "idx_whatsapp_message_tenant_created", columnList = "tenant_id, created_at"),
        @Index(name = "idx_whatsapp_message_message_id", columnList = "message_id"),
        @Index(name = "idx_whatsapp_message_tenant_to", columnList = "tenant_id, to_phone"),
        @Index(name = "idx_whatsapp_message_tenant_from", columnList = "tenant_id, from_phone")
})
public class WhatsAppMessage {

    /** Local UUID assigned before the message is submitted to Meta; natural primary key. */
    @Id
    @Column(name = "local_id", nullable = false, length = 64)
    private String localId;

    /** Meta-assigned message ID (wamid.…); populated after a successful API call. */
    @Column(name = "message_id", length = 128)
    private String messageId;

    /** Tenant that owns this message. */
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    /** Recipient phone number in E.164 format (e.g. +15551234567). */
    @Column(name = "to_phone", nullable = false, length = 32)
    private String to;

    /** Sender phone-number-id from the Meta Business account. */
    @Column(name = "from_phone", length = 32)
    private String from;

    /** Content type. */
    @Column(name = "type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private WhatsAppMessageType type = WhatsAppMessageType.TEXT;

    /**
     * Conversation billing category.
     * Used by the cost optimiser to pick the cheapest sending path.
     */
    @Column(name = "conversation_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private WhatsAppConversationType conversationType = WhatsAppConversationType.SERVICE;

    /** Plain-text content (for TEXT type) or human-readable description. */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** Template name (for TEMPLATE type). */
    @Column(name = "template_name", length = 255)
    private String templateName;

    /** Template variable substitutions (for TEMPLATE type). */
    @Convert(converter = StringMapJsonConverter.class)
    @Column(name = "template_params", columnDefinition = "TEXT")
    private Map<String, String> templateParams = new LinkedHashMap<>();

    /** Current delivery status. */
    @Column(name = "status", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private WhatsAppDeliveryStatus status = WhatsAppDeliveryStatus.QUEUED;

    /** When the message was created locally. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** When the message was submitted to Meta. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** When Meta confirmed delivery to the device. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** When the recipient marked it as read. */
    @Column(name = "read_at")
    private Instant readAt;

    /**
     * {@code true} when this message was sent within a free conversation window
     * (SERVICE or UTILITY category, or REFERRAL_CONVERSION first conversation).
     */
    @Column(name = "free_conversation", nullable = false)
    private boolean freeConversation = true;

    /** Meta error code when status is {@link WhatsAppDeliveryStatus#FAILED}. */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** Human-readable error description. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * JPA / Datastore default constructor.
     *
     * <p>Must be {@code public} — Holon's JPA Datastore ({@code JpaSave}) instantiates
     * entities reflectively via {@code Class.newInstance()}, which performs a normal Java
     * access check; a {@code protected}/{@code private} constructor fails at runtime with
     * {@code IllegalAccessException}.
     */
    public WhatsAppMessage() {
    }

    private WhatsAppMessage(String localId, String messageId, String tenantId, String to, String from,
            WhatsAppMessageType type, WhatsAppConversationType conversationType, String content,
            String templateName, Map<String, String> templateParams, WhatsAppDeliveryStatus status,
            Instant createdAt, Instant sentAt, Instant deliveredAt, Instant readAt,
            boolean freeConversation, String errorCode, String errorMessage) {
        this.localId = Objects.requireNonNull(localId, "localId must not be null");
        this.messageId = messageId;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.to = Objects.requireNonNull(to, "to must not be null");
        this.from = from;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.conversationType = conversationType;
        this.content = content;
        this.templateName = templateName;
        this.templateParams = templateParams != null ? new LinkedHashMap<>(templateParams) : new LinkedHashMap<>();
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = createdAt;
        this.sentAt = sentAt;
        this.deliveredAt = deliveredAt;
        this.readAt = readAt;
        this.freeConversation = freeConversation;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    // -------------------------------------------------------------------------
    // Record-style accessors (kept for source compatibility with existing callers)
    // -------------------------------------------------------------------------

    public String localId() {
        return localId;
    }

    public String messageId() {
        return messageId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String to() {
        return to;
    }

    public String from() {
        return from;
    }

    public WhatsAppMessageType type() {
        return type;
    }

    public WhatsAppConversationType conversationType() {
        return conversationType;
    }

    public String content() {
        return content;
    }

    public String templateName() {
        return templateName;
    }

    public Map<String, String> templateParams() {
        return Map.copyOf(templateParams);
    }

    public WhatsAppDeliveryStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant sentAt() {
        return sentAt;
    }

    public Instant deliveredAt() {
        return deliveredAt;
    }

    public Instant readAt() {
        return readAt;
    }

    public boolean freeConversation() {
        return freeConversation;
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }

    // -------------------------------------------------------------------------
    // JavaBean getters/setters (required for JPA and Holon BeanDatastoreHelper)
    // -------------------------------------------------------------------------

    public String getLocalId() {
        return localId;
    }

    public void setLocalId(String localId) {
        this.localId = localId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public WhatsAppMessageType getType() {
        return type;
    }

    public void setType(WhatsAppMessageType type) {
        this.type = type;
    }

    public WhatsAppConversationType getConversationType() {
        return conversationType;
    }

    public void setConversationType(WhatsAppConversationType conversationType) {
        this.conversationType = conversationType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public Map<String, String> getTemplateParams() {
        return templateParams;
    }

    public void setTemplateParams(Map<String, String> templateParams) {
        this.templateParams = templateParams != null ? new LinkedHashMap<>(templateParams) : new LinkedHashMap<>();
    }

    public WhatsAppDeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(WhatsAppDeliveryStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public boolean isFreeConversation() {
        return freeConversation;
    }

    public void setFreeConversation(boolean freeConversation) {
        this.freeConversation = freeConversation;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // -------------------------------------------------------------------------
    // Convenience query methods
    // -------------------------------------------------------------------------

    /** Convenience – returns {@code true} for DELIVERED or READ. */
    public boolean isDelivered() {
        return status == WhatsAppDeliveryStatus.DELIVERED || status == WhatsAppDeliveryStatus.READ;
    }

    public boolean hasFailed() {
        return status == WhatsAppDeliveryStatus.FAILED;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Builder
    // ─────────────────────────────────────────────────────────────────────────

    public static final class Builder {
        private String localId = UUID.randomUUID().toString();
        private String messageId;
        private String tenantId;
        private String to;
        private String from;
        private WhatsAppMessageType type = WhatsAppMessageType.TEXT;
        private WhatsAppConversationType conversationType = WhatsAppConversationType.SERVICE;
        private String content;
        private String templateName;
        private final Map<String, String> templateParams = new LinkedHashMap<>();
        private WhatsAppDeliveryStatus status = WhatsAppDeliveryStatus.QUEUED;
        private Instant createdAt = Instant.now();
        private Instant sentAt;
        private Instant deliveredAt;
        private Instant readAt;
        private boolean freeConversation = true;
        private String errorCode;
        private String errorMessage;

        private Builder() {}

        private Builder(WhatsAppMessage m) {
            this.localId = m.localId();
            this.messageId = m.messageId();
            this.tenantId = m.tenantId();
            this.to = m.to();
            this.from = m.from();
            this.type = m.type();
            this.conversationType = m.conversationType();
            this.content = m.content();
            this.templateName = m.templateName();
            this.templateParams.putAll(m.templateParams());
            this.status = m.status();
            this.createdAt = m.createdAt();
            this.sentAt = m.sentAt();
            this.deliveredAt = m.deliveredAt();
            this.readAt = m.readAt();
            this.freeConversation = m.freeConversation();
            this.errorCode = m.errorCode();
            this.errorMessage = m.errorMessage();
        }

        public Builder localId(String v) { this.localId = v; return this; }
        public Builder messageId(String v) { this.messageId = v; return this; }
        public Builder tenantId(String v) { this.tenantId = v; return this; }
        public Builder to(String v) { this.to = v; return this; }
        public Builder from(String v) { this.from = v; return this; }
        public Builder type(WhatsAppMessageType v) { this.type = v; return this; }
        public Builder conversationType(WhatsAppConversationType v) { this.conversationType = v; return this; }
        public Builder content(String v) { this.content = v; return this; }
        public Builder templateName(String v) { this.templateName = v; return this; }
        public Builder templateParam(String key, String value) { this.templateParams.put(key, value); return this; }
        public Builder templateParams(Map<String, String> v) { this.templateParams.putAll(v); return this; }
        public Builder status(WhatsAppDeliveryStatus v) { this.status = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder sentAt(Instant v) { this.sentAt = v; return this; }
        public Builder deliveredAt(Instant v) { this.deliveredAt = v; return this; }
        public Builder readAt(Instant v) { this.readAt = v; return this; }
        public Builder freeConversation(boolean v) { this.freeConversation = v; return this; }
        public Builder errorCode(String v) { this.errorCode = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }

        public WhatsAppMessage build() {
            return new WhatsAppMessage(localId, messageId, tenantId, to, from, type,
                    conversationType, content, templateName, templateParams,
                    status, createdAt, sentAt, deliveredAt, readAt,
                    freeConversation, errorCode, errorMessage);
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
        if (!(o instanceof WhatsAppMessage that)) {
            return false;
        }
        return freeConversation == that.freeConversation
                && Objects.equals(localId, that.localId)
                && Objects.equals(messageId, that.messageId)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(to, that.to)
                && Objects.equals(from, that.from)
                && type == that.type
                && conversationType == that.conversationType
                && Objects.equals(content, that.content)
                && Objects.equals(templateName, that.templateName)
                && Objects.equals(templateParams, that.templateParams)
                && status == that.status
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(sentAt, that.sentAt)
                && Objects.equals(deliveredAt, that.deliveredAt)
                && Objects.equals(readAt, that.readAt)
                && Objects.equals(errorCode, that.errorCode)
                && Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localId, messageId, tenantId, to, from, type, conversationType, content,
                templateName, templateParams, status, createdAt, sentAt, deliveredAt, readAt,
                freeConversation, errorCode, errorMessage);
    }

    @Override
    public String toString() {
        return "WhatsAppMessage[localId=" + localId + ", messageId=" + messageId + ", tenantId=" + tenantId
                + ", to=" + to + ", from=" + from + ", type=" + type + ", conversationType=" + conversationType
                + ", status=" + status + ", createdAt=" + createdAt + ", freeConversation=" + freeConversation
                + "]";
    }
}

