package com.holonplatform.multitenant.whatsapp.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.whatsapp.TenantWhatsAppService;
import com.holonplatform.multitenant.whatsapp.WhatsAppBusinessMessage;
import com.holonplatform.multitenant.whatsapp.WhatsAppBusinessSendResult;
import com.holonplatform.multitenant.whatsapp.WhatsAppConversation;
import com.holonplatform.multitenant.whatsapp.WhatsAppConversationType;
import com.holonplatform.multitenant.whatsapp.WhatsAppDeliveryStatus;
import com.holonplatform.multitenant.whatsapp.WhatsAppMessage;
import com.holonplatform.multitenant.whatsapp.WhatsAppMessageType;
import com.holonplatform.multitenant.whatsapp.WhatsAppTemplate;
import com.holonplatform.multitenant.whatsapp.WhatsAppUsageStats;
import com.holonplatform.multitenant.whatsapp.client.WhatsAppCloudApiClient;
import com.holonplatform.multitenant.whatsapp.config.TenantWhatsAppProperties;
import com.holonplatform.multitenant.whatsapp.exception.WhatsAppQuotaExceededException;
import com.holonplatform.multitenant.whatsapp.service.ConversationWindowTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Database-backed {@link TenantWhatsAppService} implementation.
 *
 * <p>
 * Replaces the previous in-memory implementation: the message log
 * ({@link WhatsAppMessage}) and the 24-hour conversation window state
 * ({@link WhatsAppConversation}, via {@link ConversationWindowTracker}) are now
 * persisted via Holon Datastore, so neither message history nor active
 * conversation windows are lost across a service restart.
 * </p>
 *
 * <p>Message <strong>templates</strong> ({@link WhatsAppTemplate}) remain a simple
 * in-memory seeded catalogue (or a live fetch from {@link WhatsAppCloudApiClient} when
 * real credentials are configured): they represent static/demo configuration data, not
 * per-tenant durable business state, so no database backing is required for them.</p>
 *
 * <p><strong>Do not annotate with {@code @Service}</strong> — this class is created
 * exclusively by
 * {@link com.holonplatform.multitenant.whatsapp.config.TenantWhatsAppAutoConfiguration}
 * via a {@code @Bean} method, which ensures that {@code ObjectMapper} (from
 * {@code JacksonAutoConfiguration}) is already available when the constructor runs.
 * Annotating with {@code @Service} would cause component-scanning to attempt construction
 * before Jackson auto-configuration has executed, resulting in an
 * {@code UnsatisfiedDependencyException} for {@code ObjectMapper}.</p>
 *
 * <h3>Cost-optimisation pipeline (executed on every outbound message)</h3>
 * <ol>
 *   <li>Check {@link ConversationWindowTracker} – if a free 24-hour window is open,
 *       send the message inside that window (free).</li>
 *   <li>If no window is open, select the cheapest template category:
 *       UTILITY (free) → AUTHENTICATION → MARKETING (most expensive).</li>
 *   <li>Enforce plan-based marketing quota before sending any MARKETING message.</li>
 *   <li>Record the result and update usage counters.</li>
 * </ol>
 *
 * <h3>Marketing quota enforcement — database-backed design note</h3>
 * <p>
 * The previous in-memory implementation reserved a marketing "slot" atomically via
 * {@code ConcurrentHashMap.compute()} before sending, releasing it again on failure. That
 * in-memory counter was <em>not durable</em> (it reset to zero on every restart) and was only
 * ever consistent within a single JVM instance anyway. This implementation instead derives the
 * current month's marketing usage directly from the persisted message log — counting
 * {@link WhatsAppMessage} rows of {@link WhatsAppConversationType#MARKETING} category that did
 * not fail — which is durable and correct across restarts and multiple application instances.
 * The trade-off is that the check-then-insert sequence is not perfectly atomic: under very high
 * concurrency, a small number of requests racing the same check could both pass before either
 * message is persisted, technically allowing the quota to be exceeded by a small margin. This is
 * an accepted, documented behavioural change; introducing a fully atomic cross-instance
 * reservation (e.g. a dedicated counter row updated under a pessimistic database lock) was
 * judged out of scope for this change.
 * </p>
 */
public class DatabaseTenantWhatsAppService implements TenantWhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTenantWhatsAppService.class);

    /** Marketing-conversation limits per plan (MARKETING is the only charged category). */
    private static final Map<TenantPlan, Integer> MARKETING_LIMITS = Map.of(
            TenantPlan.FREE,         50,
            TenantPlan.STARTER,     200,
            TenantPlan.PROFESSIONAL, 2_000,
            TenantPlan.ENTERPRISE,   Integer.MAX_VALUE
    );

    /** tenantId → list of templates (seeded with samples); static/demo data, not durable state. */
    private final ConcurrentHashMap<String, List<WhatsAppTemplate>> templateStore = new ConcurrentHashMap<>();

    private final ConversationWindowTracker windowTracker;
    private final WhatsAppMessageDatastoreHelper messageDatastoreHelper;
    private final TenantWhatsAppProperties properties;
    private final ObjectMapper objectMapper;
    /** Null when running in demo mode (no real credentials). */
    private final WhatsAppCloudApiClient sharedApiClient;

    public DatabaseTenantWhatsAppService(ConversationWindowTracker windowTracker,
                                          WhatsAppMessageDatastoreHelper messageDatastoreHelper,
                                          TenantWhatsAppProperties properties,
                                          ObjectMapper objectMapper) {
        this.windowTracker = Objects.requireNonNull(windowTracker, "windowTracker must not be null");
        this.messageDatastoreHelper = Objects.requireNonNull(messageDatastoreHelper,
                "messageDatastoreHelper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.sharedApiClient = buildSharedClientIfConfigured();
        seedDemoTemplates();
    }

    // ─── Sending ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public WhatsAppMessage sendTextMessage(String tenantId, String to, String text) {
        // Cost optimisation: if a free service window is open → no template needed
        boolean hasFreeWindow = windowTracker.hasOpenFreeWindow(tenantId, to);
        WhatsAppConversationType convType = hasFreeWindow
                ? WhatsAppConversationType.SERVICE
                : WhatsAppConversationType.UTILITY; // fallback – should use utility template in prod

        WhatsAppMessage.Builder builder = WhatsAppMessage.builder()
                .tenantId(tenantId)
                .to(to)
                .type(WhatsAppMessageType.TEXT)
                .conversationType(convType)
                .content(text)
                .freeConversation(convType.isFree());

        return executeAndPersist(tenantId, to, builder, convType, () -> {
            if (sharedApiClient != null && hasFreeWindow) {
                return sharedApiClient.sendText(to, text);
            }
            // In demo / no-window mode: log only (caller should send utility template instead)
            log.info("[DEMO] sendText tenant={} to={} text='{}'", tenantId, to, text);
            return null;
        });
    }

    @Override
    @Transactional
    public WhatsAppMessage sendTemplateMessage(String tenantId, String to,
                                               String templateName, String language,
                                               Map<String, String> params) {
        // Resolve the template category to determine cost
        WhatsAppConversationType category = resolveTemplateCategory(tenantId, templateName);

        // Enforce marketing quota BEFORE sending (see class Javadoc for the DB-backed design note).
        if (category == WhatsAppConversationType.MARKETING) {
            enforceMarketingQuota(tenantId);
        }

        WhatsAppMessage.Builder builder = WhatsAppMessage.builder()
                .tenantId(tenantId)
                .to(to)
                .type(WhatsAppMessageType.TEMPLATE)
                .conversationType(category)
                .templateName(templateName)
                .templateParams(params)
                .content("[Template: " + templateName + "]")
                .freeConversation(category.isFree());

        return executeAndPersist(tenantId, to, builder, category, () -> {
            if (sharedApiClient != null) {
                List<String> paramValues = new ArrayList<>(params.values());
                return sharedApiClient.sendTemplate(to, templateName, language, paramValues);
            }
            log.info("[DEMO] sendTemplate tenant={} to={} template={} params={}", tenantId, to, templateName, params);
            return null;
        });
        // Note: a FAILED marketing send is still persisted with status=FAILED and is therefore
        // naturally excluded from future enforceMarketingQuota() counts – no explicit "release"
        // step is required (unlike the previous in-memory reservation/decrement pattern).
    }

    @Override
    @Transactional
    public WhatsAppMessage sendMediaMessage(String tenantId, String to,
                                            WhatsAppMessageType mediaType,
                                            String mediaUrl, String caption) {
        boolean hasFreeWindow = windowTracker.hasOpenFreeWindow(tenantId, to);
        WhatsAppConversationType convType = hasFreeWindow
                ? WhatsAppConversationType.SERVICE
                : WhatsAppConversationType.UTILITY;

        WhatsAppMessage.Builder builder = WhatsAppMessage.builder()
                .tenantId(tenantId)
                .to(to)
                .type(mediaType)
                .conversationType(convType)
                .content(caption != null ? caption : mediaUrl)
                .freeConversation(convType.isFree());

        return executeAndPersist(tenantId, to, builder, convType, () -> {
            if (sharedApiClient != null && hasFreeWindow) {
                return sharedApiClient.sendMedia(to, mediaType.name().toLowerCase(), mediaUrl, caption);
            }
            log.info("[DEMO] sendMedia tenant={} to={} type={}", tenantId, to, mediaType);
            return null;
        });
    }

    // ─── Business document sending ────────────────────────────────────────────

    @Override
    @Transactional
    public WhatsAppBusinessSendResult sendBusinessMessage(String tenantId,
                                                          WhatsAppBusinessMessage message) {
        Objects.requireNonNull(message, "message");

        String to = message.to();
        boolean hasFreeWindow = windowTracker.hasOpenFreeWindow(tenantId, to);

        WhatsAppMessage notification;

        if (hasFreeWindow) {
            // Free service window open → send a plain-text summary (no template slot consumed)
            String text = buildBusinessTextSummary(message);
            notification = sendTextMessage(tenantId, to, text);
        } else {
            // No open window → send the appropriate UTILITY template (free)
            String templateName = resolveBusinessTemplateName(message);
            Map<String, String> params = buildBusinessTemplateParams(message);
            notification = sendTemplateMessage(tenantId, to, templateName,
                    message.language(), params);
        }

        // Optionally attach the PDF document
        WhatsAppMessage attachmentMsg = null;
        if (message.documentUrl() != null && !message.documentUrl().isBlank()) {
            String caption = buildDocumentCaption(message);
            attachmentMsg = sendMediaMessage(tenantId, to,
                    WhatsAppMessageType.DOCUMENT, message.documentUrl(), caption);
        }

        return WhatsAppBusinessSendResult.of(notification, attachmentMsg);
    }

    // ─── Business message helpers ─────────────────────────────────────────────

    private String resolveBusinessTemplateName(WhatsAppBusinessMessage message) {
        if (message.templateName() != null && !message.templateName().isBlank()) {
            return message.templateName();
        }
        String name = message.messageType().getDefaultTemplateName();
        if (name == null) {
            throw new IllegalArgumentException(
                    "CUSTOM WhatsAppBusinessMessage must supply a templateName()");
        }
        return name;
    }

    private Map<String, String> buildBusinessTemplateParams(WhatsAppBusinessMessage message) {
        Map<String, String> params = new LinkedHashMap<>();
        // Standard positional params used by the seeded UTILITY templates:
        // {{1}} reference number, {{2}} amount, {{3}} currency, {{4}} date, {{5}} URL (optional)
        if (message.referenceNumber() != null)
            params.put("1", message.referenceNumber());
        if (message.amount() != null)
            params.put("2", message.amount().toPlainString());
        if (message.currency() != null)
            params.put("3", message.currency());
        if (message.dueDate() != null)
            params.put("4", message.dueDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        if (message.documentUrl() != null && !message.documentUrl().isBlank())
            params.put("5", message.documentUrl());
        // Merge any extra caller-supplied params (they can override)
        params.putAll(message.extraParams());
        return params;
    }

    private String buildBusinessTextSummary(WhatsAppBusinessMessage message) {
        // Used when a free 24-hour service window is open
        String ref  = Objects.toString(message.referenceNumber(), "");
        String amt  = message.amount() != null
                ? message.amount().toPlainString() + " " + Objects.toString(message.currency(), "")
                : "";
        String date = message.dueDate() != null
                ? message.dueDate().format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
                : "";
        return switch (message.messageType()) {
            case INVOICE ->
                    "📄 Invoice " + ref + " for " + amt + " is ready. Due: " + date
                    + (message.documentUrl() != null ? "\n🔗 " + message.documentUrl() : "");
            case QUOTATION ->
                    "📋 Quotation " + ref + " for " + amt + " is attached. Valid until: " + date
                    + (message.documentUrl() != null ? "\n🔗 " + message.documentUrl() : "");
            case PAYMENT_REMINDER ->
                    "🔔 Reminder: Invoice " + ref + " for " + amt + " is due on " + date + ".";
            case PAYMENT_OVERDUE ->
                    "⚠️ OVERDUE: Invoice " + ref + " for " + amt + " was due on " + date
                    + ". Please settle at your earliest convenience.";
            case CUSTOM ->
                    "📢 " + Objects.toString(message.referenceNumber(), "")
                    + (amt.isBlank() ? "" : " – " + amt);
        };
    }

    private String buildDocumentCaption(WhatsAppBusinessMessage message) {
        return switch (message.messageType()) {
            case INVOICE          -> "Invoice " + Objects.toString(message.referenceNumber(), "");
            case QUOTATION        -> "Quotation " + Objects.toString(message.referenceNumber(), "");
            case PAYMENT_REMINDER -> "Invoice " + Objects.toString(message.referenceNumber(), "") + " (Payment reminder)";
            case PAYMENT_OVERDUE  -> "Invoice " + Objects.toString(message.referenceNumber(), "") + " (Overdue)";
            case CUSTOM           -> Objects.toString(message.referenceNumber(), "Document");
        };
    }

    // ─── Window queries ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public boolean hasOpenServiceWindow(String tenantId, String customerPhone) {
        return windowTracker.hasOpenFreeWindow(tenantId, customerPhone);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WhatsAppConversation> getConversationWindow(String tenantId, String customerPhone) {
        return windowTracker.getWindow(tenantId, customerPhone);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WhatsAppMessage> getMessageStatus(String tenantId, String localId) {
        return messageDatastoreHelper.findByLocalId(localId)
                .filter(m -> tenantId.equals(m.tenantId()));
    }

    // ─── Message history ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<WhatsAppMessage> listMessages(String tenantId, int offset, int limit) {
        // findByTenantId already returns newest-first (createdAt desc)
        List<WhatsAppMessage> all = messageDatastoreHelper.findByTenantId(tenantId);
        int from = Math.min(offset, all.size());
        int to   = Math.min(offset + limit, all.size());
        return all.subList(from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public long countMessages(String tenantId) {
        return messageDatastoreHelper.countByTenantId(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WhatsAppMessage> listMessagesByPhone(String tenantId, String customerPhone) {
        return messageDatastoreHelper.findByTenantIdAndPhone(tenantId, customerPhone);
    }

    // ─── Templates ────────────────────────────────────────────────────────────

    @Override
    public List<WhatsAppTemplate> listTemplates(String tenantId) {
        // If real API client available, fetch live; fallback to seeded demo templates
        if (sharedApiClient != null) {
            List<WhatsAppTemplate> live = sharedApiClient.fetchTemplates();
            if (!live.isEmpty()) return live;
        }
        // Merge tenant-specific + global demo templates
        List<WhatsAppTemplate> result = new ArrayList<>();
        result.addAll(templateStore.getOrDefault(tenantId, List.of()));
        result.addAll(templateStore.getOrDefault("*", List.of()));
        return result;
    }

    // ─── Usage stats ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public WhatsAppUsageStats getUsageStats(String tenantId, YearMonth yearMonth) {
        List<WhatsAppMessage> inPeriod = messagesInMonth(tenantId, yearMonth);

        long freeCount = inPeriod.stream().filter(WhatsAppMessage::freeConversation).count();
        long chargedCount = inPeriod.size() - freeCount;

        Map<WhatsAppConversationType, Long> byType = inPeriod.stream()
                .collect(Collectors.groupingBy(WhatsAppMessage::conversationType, Collectors.counting()));

        // Rough cost estimate: MARKETING ≈ $0.025/conv, AUTHENTICATION ≈ $0.03/msg
        double costEstimate = byType.getOrDefault(WhatsAppConversationType.MARKETING, 0L) * 0.025
                + byType.getOrDefault(WhatsAppConversationType.AUTHENTICATION, 0L) * 0.03;

        int openWindows = windowTracker.openWindowsForTenant(tenantId).size();

        return WhatsAppUsageStats.builder(tenantId, yearMonth)
                .totalMessages(inPeriod.size())
                .freeMessages(freeCount)
                .chargedMessages(chargedCount)
                .openWindows(openWindows)
                .estimatedCostUsd(costEstimate)
                .count(WhatsAppConversationType.SERVICE,
                        byType.getOrDefault(WhatsAppConversationType.SERVICE, 0L))
                .count(WhatsAppConversationType.UTILITY,
                        byType.getOrDefault(WhatsAppConversationType.UTILITY, 0L))
                .count(WhatsAppConversationType.AUTHENTICATION,
                        byType.getOrDefault(WhatsAppConversationType.AUTHENTICATION, 0L))
                .count(WhatsAppConversationType.MARKETING,
                        byType.getOrDefault(WhatsAppConversationType.MARKETING, 0L))
                .build();
    }

    // ─── Webhook processing ───────────────────────────────────────────────────

    @Override
    @Transactional
    public void processWebhookPayload(String tenantId, String payload) {
        try {
            Map<String, Object> root = objectMapper.readValue(payload,
                    new TypeReference<Map<String, Object>>() {});
            List<?> entries = (List<?>) root.get("entry");
            if (entries == null) return;

            for (Object entry : entries) {
                List<?> changes = (List<?>) ((Map<?, ?>) entry).get("changes");
                if (changes == null) continue;
                for (Object change : changes) {
                    processChange(tenantId, (Map<?, ?>) change);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse WhatsApp webhook payload for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void processChange(String tenantId, Map<?, ?> change) {
        Map<?, ?> value = (Map<?, ?>) change.get("value");
        if (value == null) return;

        // Process inbound messages (opens a free service window)
        List<?> messages = (List<?>) value.get("messages");
        if (messages != null) {
            for (Object msg : messages) {
                Map<?, ?> m = (Map<?, ?>) msg;
                String from = (String) m.get("from");
                if (from != null) {
                    windowTracker.registerInboundMessage(tenantId, from);
                    log.debug("Inbound message from {} – service window opened for tenant {}", from, tenantId);
                }
            }
        }

        // Process delivery status updates
        List<?> statuses = (List<?>) value.get("statuses");
        if (statuses != null) {
            for (Object s : statuses) {
                Map<?, ?> statusMap = (Map<?, ?>) s;
                String metaId = (String) statusMap.get("id");
                String statusStr = (String) statusMap.get("status");
                if (metaId != null && statusStr != null) {
                    updateDeliveryStatus(tenantId, metaId, statusStr);
                }
            }
        }
    }

    private void updateDeliveryStatus(String tenantId, String metaMessageId, String statusStr) {
        WhatsAppDeliveryStatus newStatus = switch (statusStr.toLowerCase()) {
            case "sent" -> WhatsAppDeliveryStatus.SENT;
            case "delivered" -> WhatsAppDeliveryStatus.DELIVERED;
            case "read" -> WhatsAppDeliveryStatus.READ;
            case "failed" -> WhatsAppDeliveryStatus.FAILED;
            default -> WhatsAppDeliveryStatus.PENDING;
        };

        Instant now = Instant.now();
        messageDatastoreHelper.findByMessageId(tenantId, metaMessageId).ifPresent(m -> {
            WhatsAppMessage.Builder updated = m.toBuilder().status(newStatus);
            // The original in-memory implementation only updated the status field; since the
            // entity already exposes deliveredAt/readAt columns, populate them here too when the
            // corresponding status is reached (a natural, low-risk improvement).
            if (newStatus == WhatsAppDeliveryStatus.DELIVERED) {
                updated.deliveredAt(now);
            } else if (newStatus == WhatsAppDeliveryStatus.READ) {
                updated.readAt(now);
            }
            messageDatastoreHelper.save(updated.build());
        });
    }

    private WhatsAppMessage executeAndPersist(String tenantId, String to,
                                               WhatsAppMessage.Builder builder,
                                               WhatsAppConversationType convType,
                                               Supplier<String> apiCall) {
        // Attempt the API call
        String metaId = null;
        WhatsAppDeliveryStatus finalStatus;
        String errorCode = null, errorMessage = null;
        try {
            metaId = apiCall.get();
            finalStatus = metaId != null ? WhatsAppDeliveryStatus.SENT : WhatsAppDeliveryStatus.QUEUED;
        } catch (Exception e) {
            log.error("WhatsApp send failed for tenant {}: {}", tenantId, e.getMessage());
            finalStatus = WhatsAppDeliveryStatus.FAILED;
            errorCode = "SEND_ERROR";
            errorMessage = e.getMessage();
        }

        WhatsAppMessage message = builder
                .messageId(metaId)
                .status(finalStatus)
                .sentAt(finalStatus == WhatsAppDeliveryStatus.SENT ? Instant.now() : null)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();

        // Persist – the database is the source of truth (no in-memory message list)
        messageDatastoreHelper.save(message);

        // Update window tracker
        if (finalStatus != WhatsAppDeliveryStatus.FAILED) {
            if (convType == WhatsAppConversationType.SERVICE) {
                windowTracker.recordOutboundMessage(tenantId, to);
            } else {
                windowTracker.registerOutboundTemplate(tenantId, to, convType);
            }
        }

        return message;
    }

    /**
     * Enforces the plan-based monthly marketing quota by counting non-failed MARKETING
     * messages already persisted for the tenant this month. See the class Javadoc for the
     * design trade-off versus the previous in-memory atomic reservation.
     */
    private void enforceMarketingQuota(String tenantId) {
        TenantDetails tenantDetails = TenantContext.get().orElse(null);
        TenantPlan plan = tenantDetails != null ? tenantDetails.plan() : TenantPlan.FREE;

        int limit = MARKETING_LIMITS.getOrDefault(plan, 50);
        if (limit == Integer.MAX_VALUE) return;

        long current = messagesInMonth(tenantId, YearMonth.now()).stream()
                .filter(m -> m.conversationType() == WhatsAppConversationType.MARKETING)
                .filter(m -> m.status() != WhatsAppDeliveryStatus.FAILED)
                .count();

        if (current >= limit) {
            throw new WhatsAppQuotaExceededException(tenantId, limit, (int) current);
        }
    }

    /** Returns all messages persisted for a tenant within the given calendar month (UTC). */
    private List<WhatsAppMessage> messagesInMonth(String tenantId, YearMonth yearMonth) {
        Instant monthStart = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthEnd = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return messageDatastoreHelper.findByTenantIdAndCreatedAtBetween(tenantId, monthStart, monthEnd);
    }

    private WhatsAppConversationType resolveTemplateCategory(String tenantId, String templateName) {
        return listTemplates(tenantId).stream()
                .filter(t -> t.name().equalsIgnoreCase(templateName))
                .map(WhatsAppTemplate::category)
                .findFirst()
                .orElse(WhatsAppConversationType.UTILITY); // default to free if unknown
    }

    private WhatsAppCloudApiClient buildSharedClientIfConfigured() {
        TenantWhatsAppProperties.SharedCredentials creds = properties.getShared();
        if (creds != null
                && creds.getAccessToken() != null && !creds.getAccessToken().isBlank()
                && creds.getPhoneNumberId() != null && !creds.getPhoneNumberId().isBlank()) {
            log.info("WhatsApp Cloud API client configured (shared mode) – phone-number-id={}",
                    creds.getPhoneNumberId());
            return new WhatsAppCloudApiClient(
                    creds.getPhoneNumberId(),
                    creds.getWabaId(),
                    creds.getAccessToken());
        }
        log.info("WhatsApp running in DEMO mode (no real credentials). Messages will be logged only.");
        return null;
    }

    /** Seed a few realistic sample templates for the demo / development environment. */
    private void seedDemoTemplates() {
        List<WhatsAppTemplate> samples = List.of(
                // ── Generic ──────────────────────────────────────────────────────────
                new WhatsAppTemplate("hello_world", "en_US",
                        WhatsAppConversationType.UTILITY, "APPROVED", List.of(), null),

                new WhatsAppTemplate("order_shipped", "en_US",
                        WhatsAppConversationType.UTILITY, "APPROVED",
                        List.of(new WhatsAppTemplate.TemplateComponent("BODY",
                                "Your order {{1}} has shipped and will arrive by {{2}}.", 2)), null),

                new WhatsAppTemplate("account_otp", "en_US",
                        WhatsAppConversationType.AUTHENTICATION, "APPROVED",
                        List.of(new WhatsAppTemplate.TemplateComponent("BODY",
                                "Your verification code is {{1}}. Valid for 5 minutes.", 1)), null),

                new WhatsAppTemplate("monthly_promo", "en_US",
                        WhatsAppConversationType.MARKETING, "APPROVED",
                        List.of(new WhatsAppTemplate.TemplateComponent("BODY",
                                "🎉 Exclusive offer for you: {{1}}. Use code {{2}} to save 20%!", 2)), null),

                // ── Business document templates (UTILITY = FREE) ──────────────────
                new WhatsAppTemplate("invoice_ready", "en_US",
                        WhatsAppConversationType.UTILITY, "APPROVED",
                        List.of(new WhatsAppTemplate.TemplateComponent("BODY",
                                "📄 Invoice *{{1}}* for {{2}} {{3}} is ready.\n"
                                + "Due date: {{4}}.\n"
                                + "Download: {{5}}", 5)), null),

                new WhatsAppTemplate("quotation_ready", "en_US",
                        WhatsAppConversationType.UTILITY, "APPROVED",
                        List.of(new WhatsAppTemplate.TemplateComponent("BODY",
                                "📋 Your quotation *{{1}}* for {{2}} {{3}} is ready.\n"
                                + "Valid until: {{4}}.\n"
                                + "Download: {{5}}", 5)), null),

                new WhatsAppTemplate("payment_reminder", "en_US",
                        WhatsAppConversationType.UTILITY, "APPROVED",
                        List.of(new WhatsAppTemplate.TemplateComponent("BODY",
                                "🔔 Reminder: Invoice *{{1}}* for {{2}} {{3}} is due on {{4}}.\n"
                                + "Please arrange payment to avoid late fees.", 4)), null),

                new WhatsAppTemplate("payment_overdue", "en_US",
                        WhatsAppConversationType.UTILITY, "APPROVED",
                        List.of(new WhatsAppTemplate.TemplateComponent("BODY",
                                "⚠️ OVERDUE: Invoice *{{1}}* for {{2}} {{3}} was due on {{4}}.\n"
                                + "Please contact us immediately to settle this balance.", 4)), null)
        );
        // Register under a wildcard "*" key so all tenants see them in demo mode
        templateStore.put("*", samples);
    }
}
