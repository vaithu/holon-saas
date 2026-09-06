package com.holonplatform.multitenant.whatsapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration properties for the WhatsApp integration module.
 *
 * <h3>Minimal production setup (SHARED mode)</h3>
 * <pre>
 * holon.tenant.whatsapp.shared.access-token=EAAG...
 * holon.tenant.whatsapp.shared.phone-number-id=123456789012345
 * holon.tenant.whatsapp.shared.waba-id=987654321098765
 * holon.tenant.whatsapp.webhook.verify-token=my-secret-verify-token
 * </pre>
 *
 * <h3>DEDICATED mode (per-tenant credentials)</h3>
 * <p>Implement {@code TenantWhatsAppCredentialsProvider} and expose it as a Spring bean.
 * The auto-configuration will prefer it over the shared credentials.</p>
 *
 * <h3>Plan-based marketing quotas</h3>
 * Override the default limits per plan:
 * <pre>
 * holon.tenant.whatsapp.plan-marketing-limits.FREE=50
 * holon.tenant.whatsapp.plan-marketing-limits.STARTER=200
 * holon.tenant.whatsapp.plan-marketing-limits.PROFESSIONAL=2000
 * holon.tenant.whatsapp.plan-marketing-limits.ENTERPRISE=-1
 * </pre>
 */
@ConfigurationProperties(prefix = "holon.tenant.whatsapp")
public class TenantWhatsAppProperties {

    /** Whether the WhatsApp integration is enabled. Default: {@code true}. */
    private boolean enabled = true;

    /** API Graph version to use. Default: {@code v21.0}. */
    private String apiVersion = "v21.0";

    /**
     * Deployment mode: SHARED (one WABA for all tenants) or DEDICATED
     * (each tenant has its own WABA configured via a credentials provider).
     */
    private Mode mode = Mode.SHARED;

    /** Shared WABA credentials (used in SHARED mode). */
    private SharedCredentials shared = new SharedCredentials();

    /** Webhook configuration. */
    private WebhookConfig webhook = new WebhookConfig();

    /**
     * Fallback tenantId to associate inbound webhooks with when no tenantId
     * can be derived from the request (typically in SHARED mode with a single tenant).
     */
    private String defaultTenantId;

    /**
     * Per-plan monthly marketing conversation limits.
     * Marketing is the only charged conversation type (UTILITY is free).
     * Use {@code -1} for unlimited.
     */
    private Map<String, Integer> planMarketingLimits = new LinkedHashMap<>(Map.of(
            "FREE", 50,
            "STARTER", 200,
            "PROFESSIONAL", 2000,
            "ENTERPRISE", -1
    ));

    // ── Getters / setters ─────────────────────────────────────────────────────

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public SharedCredentials getShared() { return shared; }
    public void setShared(SharedCredentials shared) { this.shared = shared; }

    public WebhookConfig getWebhook() { return webhook; }
    public void setWebhook(WebhookConfig webhook) { this.webhook = webhook; }

    public String getDefaultTenantId() { return defaultTenantId; }
    public void setDefaultTenantId(String defaultTenantId) { this.defaultTenantId = defaultTenantId; }

    public Map<String, Integer> getPlanMarketingLimits() { return planMarketingLimits; }
    public void setPlanMarketingLimits(Map<String, Integer> v) { this.planMarketingLimits = v; }

    // ── Nested types ──────────────────────────────────────────────────────────

    public enum Mode { SHARED, DEDICATED }

    public static class SharedCredentials {
        /** Meta System User permanent access token. */
        private String accessToken;
        /** Phone Number ID from the Meta App Dashboard. */
        private String phoneNumberId;
        /** WhatsApp Business Account (WABA) ID. */
        private String wabaId;

        public String getAccessToken() { return accessToken; }
        public void setAccessToken(String v) { this.accessToken = v; }
        public String getPhoneNumberId() { return phoneNumberId; }
        public void setPhoneNumberId(String v) { this.phoneNumberId = v; }
        public String getWabaId() { return wabaId; }
        public void setWabaId(String v) { this.wabaId = v; }
    }

    public static class WebhookConfig {
        /**
         * Token you set in the Meta App Dashboard → Webhooks.
         * Must match the {@code hub.verify_token} parameter Meta sends during setup.
         */
        private String verifyToken = "change-me-verify-token";
        /**
         * Meta App Secret used to verify the {@code X-Hub-Signature-256} header on
         * every incoming webhook POST request. Found in:
         * Meta for Developers → App → Settings → Basic → App Secret.
         *
         * <p>When set, every POST that does not carry a valid HMAC-SHA256 signature
         * is rejected with HTTP 403. When left blank, signature verification is
         * skipped (development mode only).</p>
         *
         * <p><strong>Never commit the real app secret to source control.</strong>
         * Inject it via an environment variable:
         * {@code holon.tenant.whatsapp.webhook.app-secret=${WHATSAPP_APP_SECRET}}</p>
         */
        private String appSecret;
        /** Whether to enable the webhook controller. Default: {@code true}. */
        private boolean enabled = true;

        public String getVerifyToken() { return verifyToken; }
        public void setVerifyToken(String v) { this.verifyToken = v; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String v) { this.appSecret = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
    }
}



