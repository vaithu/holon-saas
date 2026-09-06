package com.holonplatform.multitenant.whatsapp.webhook;

import com.holonplatform.multitenant.whatsapp.TenantWhatsAppService;
import com.holonplatform.multitenant.whatsapp.config.TenantWhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Handles incoming Meta Platform webhook callbacks for WhatsApp Business.
 *
 * <h3>Endpoint: {@code /webhook/whatsapp}</h3>
 * <ul>
 *   <li>{@code GET}  – Meta verification challenge (one-time setup)</li>
 *   <li>{@code POST} – Real-time message & status update notifications</li>
 * </ul>
 *
 * <h3>Cost benefit of webhook processing</h3>
 * <p>Every inbound user message received here <em>opens a free 24-hour service window</em>
 * via {@link com.holonplatform.multitenant.whatsapp.service.ConversationWindowTracker}.
 * This allows the application to reply without paying for a new conversation – the single
 * most important technique for reducing WhatsApp API costs.</p>
 *
 * <h3>Configuring webhooks in Meta Business Manager</h3>
 * <pre>
 *   Callback URL : https://&lt;your-domain&gt;/webhook/whatsapp
 *   Verify token : ${holon.tenant.whatsapp.webhook.verify-token}
 *   Subscribe to: messages
 * </pre>
 *
 * <p>In multi-tenant SHARED mode, the tenantId is derived from the request or a
 * default configured value.  For DEDICATED mode, tenants may each register their
 * own callback URL with a path variable, e.g. {@code /webhook/whatsapp/{tenantId}}.</p>
 */
@RestController
@RequestMapping("/webhook/whatsapp")
@ConditionalOnProperty(name = "holon.tenant.whatsapp.webhook.enabled", havingValue = "true", matchIfMissing = true)
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final TenantWhatsAppService whatsAppService;
    private final TenantWhatsAppProperties properties;

    public WhatsAppWebhookController(TenantWhatsAppService whatsAppService,
                                      TenantWhatsAppProperties properties) {
        this.whatsAppService = whatsAppService;
        this.properties = properties;
    }

    // ─── GET – Meta verification challenge ───────────────────────────────────

    /**
     * Meta calls this once during webhook setup to verify the endpoint.
     * Responds with the challenge value when the verify-token matches.
     */
    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && matchesVerifyToken(token)) {
            log.info("WhatsApp webhook verified successfully");
            return ResponseEntity.ok(challenge);
        }
        log.warn("WhatsApp webhook verification failed – invalid verify_token");
        return ResponseEntity.status(403).body("Forbidden");
    }

    // ─── POST – incoming events ───────────────────────────────────────────────

    /**
     * Receives inbound messages and delivery status updates from Meta.
     * Must return HTTP 200 immediately; processing is synchronous but fast.
     *
     * <h3>Signature verification</h3>
     * <p>Meta signs every POST with {@code X-Hub-Signature-256: sha256=HMAC-SHA256(appSecret, body)}.
     * When {@code holon.tenant.whatsapp.webhook.app-secret} is configured, the signature is
     * verified using a constant-time comparison before the payload is processed.
     * Requests with missing or invalid signatures are rejected with HTTP 403.</p>
     */
    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody String payload,
                                         @RequestParam(value = "tenantId", required = false)
                                         String tenantIdParam,
                                         @RequestHeader(value = "X-Hub-Signature-256", required = false)
                                         String signatureHeader) {

        // ── Security: verify Meta HMAC-SHA256 signature ───────────────────────
        if (!isValidSignature(payload, signatureHeader)) {
            log.warn("WhatsApp webhook POST rejected — invalid or missing X-Hub-Signature-256");
            return ResponseEntity.status(403).build();
        }

        // Derive tenantId: prefer query param → fall back to shared default
        String tenantId = (tenantIdParam != null && !tenantIdParam.isBlank())
                ? tenantIdParam
                : properties.getDefaultTenantId();

        if (tenantId == null || tenantId.isBlank()) {
            log.warn("WhatsApp webhook received but no tenantId could be resolved; payload ignored");
            return ResponseEntity.ok().build(); // still 200 so Meta doesn't retry
        }

        log.debug("WhatsApp webhook event for tenant '{}': {}…",
                tenantId, payload.length() > 120 ? payload.substring(0, 120) : payload);

        whatsAppService.processWebhookPayload(tenantId, payload);
        return ResponseEntity.ok().build();
    }

    // ─── Tenant-specific endpoint (DEDICATED mode) ───────────────────────────

    @GetMapping("/{tenantId}")
    public ResponseEntity<String> verifyTenant(
            @PathVariable String tenantId,
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {
        return verify(mode, token, challenge);
    }

    @PostMapping("/{tenantId}")
    public ResponseEntity<Void> receiveTenant(@PathVariable String tenantId,
                                               @RequestBody String payload,
                                               @RequestHeader(value = "X-Hub-Signature-256", required = false)
                                               String signatureHeader) {
        return receive(payload, tenantId, signatureHeader);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean matchesVerifyToken(String token) {
        String expected = properties.getWebhook() != null ? properties.getWebhook().getVerifyToken() : null;
        return expected != null && expected.equals(token);
    }

    /**
     * Verifies the {@code X-Hub-Signature-256} header using a constant-time HMAC comparison.
     *
     * <p>If {@code appSecret} is not configured (blank/null), verification is skipped and
     * {@code true} is returned. This allows the webhook to work in development mode without
     * credentials. In production, always configure the app secret.</p>
     *
     * <p>Uses {@link MessageDigest#isEqual} for constant-time comparison to prevent
     * timing-oracle attacks that could allow an attacker to brute-force the secret.</p>
     */
    private boolean isValidSignature(String payload, String signatureHeader) {
        String appSecret = properties.getWebhook() != null ? properties.getWebhook().getAppSecret() : null;

        if (appSecret == null || appSecret.isBlank()) {
            // Dev mode: no secret configured — skip verification (warn once in logs)
            log.debug("WhatsApp webhook app-secret not configured — skipping signature verification (dev mode)");
            return true;
        }

        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("WhatsApp webhook missing X-Hub-Signature-256 header — rejecting request");
            return false;
        }

        try {
            // Compute expected: HMAC-SHA256(appSecret, rawBody)
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            // Convert to lowercase hex string (same format as sha256=...)
            StringBuilder hex = new StringBuilder(expectedBytes.length * 2);
            for (byte b : expectedBytes) {
                hex.append(String.format("%02x", b));
            }
            String expected = "sha256=" + hex;

            // Constant-time comparison — prevents timing-oracle attacks
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.warn("Failed to verify WhatsApp webhook signature: {}", e.getMessage());
            return false;
        }
    }
}




