package com.holonplatform.multitenant.whatsapp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.holonplatform.multitenant.whatsapp.WhatsAppConversationType;
import com.holonplatform.multitenant.whatsapp.WhatsAppTemplate;
import com.holonplatform.multitenant.whatsapp.exception.WhatsAppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Thin HTTP wrapper around the <b>Meta WhatsApp Business Cloud API v21.0</b>.
 *
 * <p>Instances are created per-tenant (or shared in SHARED mode) by
 * {@link com.holonplatform.multitenant.whatsapp.config.TenantWhatsAppAutoConfiguration}.</p>
 *
 * <h3>API endpoints used</h3>
 * <ul>
 *   <li>{@code POST /v21.0/{phone-number-id}/messages} – send any message type</li>
 *   <li>{@code GET  /v21.0/{waba-id}/message_templates} – list approved templates</li>
 * </ul>
 *
 * <h3>Auth</h3>
 * Uses a <em>System User Access Token</em> passed as {@code Authorization: Bearer <token>}.
 * Tokens never expire if granted to a System User with "standard" token type.
 */
public class WhatsAppCloudApiClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiClient.class);
    private static final String BASE_URL = "https://graph.facebook.com/v21.0";

    private final String phoneNumberId;
    private final String wabaId;
    private final RestClient restClient;

    public WhatsAppCloudApiClient(String phoneNumberId, String wabaId, String accessToken) {
        this.phoneNumberId = phoneNumberId;
        this.wabaId = wabaId;
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ─── Send text ────────────────────────────────────────────────────────────

    /**
     * Sends a plain-text message.  Only valid when a 24-hour service window is open.
     *
     * @return Meta-assigned message ID (wamid.…)
     */
    public String sendText(String to, String text) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", to,
                "type", "text",
                "text", Map.of("preview_url", false, "body", text)
        );
        return callMessages(body);
    }

    // ─── Send template ────────────────────────────────────────────────────────

    /**
     * Sends an approved message template with ordered parameter substitutions.
     *
     * @param to           recipient E.164 phone
     * @param templateName template name in Business Manager
     * @param language     BCP-47 code, e.g. {@code "en_US"}
     * @param bodyParams   ordered body component parameters ({{1}}, {{2}}, …)
     * @return Meta-assigned message ID
     */
    public String sendTemplate(String to, String templateName, String language,
                               List<String> bodyParams) {
        List<Map<String, Object>> paramObjects = bodyParams.stream()
                .map(v -> Map.<String, Object>of("type", "text", "text", v))
                .toList();

        Map<String, Object> templateObj = Map.of(
                "name", templateName,
                "language", Map.of("code", language),
                "components", Collections.singletonList(Map.of(
                        "type", "body",
                        "parameters", paramObjects
                ))
        );

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "template",
                "template", templateObj
        );
        return callMessages(body);
    }

    // ─── Send media ───────────────────────────────────────────────────────────

    /**
     * Sends an image, document, audio or video message.
     *
     * @param to        recipient E.164 phone
     * @param mediaType WhatsApp media type string ("image", "document", "audio", "video")
     * @param mediaUrl  public HTTPS URL or a Meta media-id
     * @param caption   optional caption for image/document/video
     * @return Meta-assigned message ID
     */
    public String sendMedia(String to, String mediaType, String mediaUrl, String caption) {
        Map<String, Object> mediaObj = caption != null && !caption.isBlank()
                ? Map.of("link", mediaUrl, "caption", caption)
                : Map.of("link", mediaUrl);

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", mediaType,
                mediaType, mediaObj
        );
        return callMessages(body);
    }

    // ─── List templates ───────────────────────────────────────────────────────

    /** Fetches approved templates from the Meta Business account. */
    public List<WhatsAppTemplate> fetchTemplates() {
        try {
            TemplateListResponse response = restClient.get()
                    .uri("/{wabaId}/message_templates?fields=name,language,category,status,components",
                            wabaId)
                    .retrieve()
                    .body(TemplateListResponse.class);

            if (response == null || response.data() == null) return List.of();

            return response.data().stream().map(t -> {
                WhatsAppConversationType category = switch (t.category() == null ? "" : t.category().toUpperCase()) {
                    case "UTILITY" -> WhatsAppConversationType.UTILITY;
                    case "AUTHENTICATION" -> WhatsAppConversationType.AUTHENTICATION;
                    case "MARKETING" -> WhatsAppConversationType.MARKETING;
                    default -> WhatsAppConversationType.UTILITY;
                };
                return new WhatsAppTemplate(t.name(), t.language(), category, t.status(),
                        List.of(), null);
            }).toList();

        } catch (RestClientException e) {
            log.warn("Failed to fetch WhatsApp templates for WABA {}: {}", wabaId, e.getMessage());
            return List.of();
        }
    }

    // ─── Internal ───────────────────────────���─────────────────────────────────

    private String callMessages(Map<String, Object> body) {
        try {
            SendMessageResponse resp = restClient.post()
                    .uri("/{phoneNumberId}/messages", phoneNumberId)
                    .body(body)
                    .retrieve()
                    .body(SendMessageResponse.class);

            if (resp == null || resp.messages() == null || resp.messages().isEmpty()) {
                throw new WhatsAppException("Empty response from Meta Cloud API");
            }
            String messageId = resp.messages().getFirst().id();
            log.debug("Message sent via Meta API: {}", messageId);
            return messageId;

        } catch (RestClientException e) {
            throw new WhatsAppException("Meta Cloud API call failed: " + e.getMessage(), e);
        }
    }

    // ─── Response DTOs ────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SendMessageResponse(
            @JsonProperty("messages") List<MessageRef> messages) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageRef(@JsonProperty("id") String id) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TemplateListResponse(
            @JsonProperty("data") List<TemplateDto> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TemplateDto(
            @JsonProperty("name") String name,
            @JsonProperty("language") String language,
            @JsonProperty("category") String category,
            @JsonProperty("status") String status) {}
}

