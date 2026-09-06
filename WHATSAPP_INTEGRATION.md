# Adopting the WhatsApp Integration in a New App

This document is a step-by-step guide for integrating the `holon-vaadin-flow-tenant-whatsapp`
module into a **new** Spring Boot + Vaadin multi-tenant application.

---

## 1. Add the dependency

### Option A — You already use `holon-starter-vaadin-flow-saas`

The starter lists `tenant-whatsapp` as **optional**. Just declare it explicitly:

```xml
<!-- pom.xml -->
<dependencies>

    <!-- Pulls in all other SaaS modules -->
    <dependency>
        <groupId>com.holon-platform.saas</groupId>
        <artifactId>holon-starter-vaadin-flow-saas</artifactId>
    </dependency>

    <!-- ✅ Add this — WhatsApp integration -->
    <dependency>
        <groupId>com.holon-platform.saas</groupId>
        <artifactId>holon-vaadin-flow-tenant-whatsapp</artifactId>
    </dependency>

</dependencies>
```

**Version** is managed by the parent BOM (`holon-saas-root`) — no `<version>` tag needed.

---

### Option B — Standalone (no holon-starter)

```xml
<dependencies>

    <dependency>
        <groupId>com.holon-platform.saas</groupId>
        <artifactId>holon-vaadin-flow-tenant-whatsapp</artifactId>
        <version>1.0.0</version>   <!-- pin version explicitly -->
    </dependency>

    <!-- Required transitive deps if not using the starter -->
    <dependency>
        <groupId>com.holon-platform.saas</groupId>
        <artifactId>holon-vaadin-flow-tenant-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

</dependencies>
```

---

## 2. Configure `application.properties`

### Minimal — Demo / development mode (no real WhatsApp account)

```properties
# WhatsApp integration is ON by default; no credentials → DEMO mode
# Messages are logged only; no real API calls are made.
holon.tenant.whatsapp.default-tenant-id=my-app
holon.tenant.whatsapp.webhook.verify-token=change-me
```

The app starts, the Vaadin UI at `/whatsapp` is fully functional, and
all sends are logged (no Meta Cloud API calls, no charge).

---

### Production — Connected to Meta WhatsApp Cloud API

```properties
# ── Meta credentials ────────────────────────────────────────────────────────
holon.tenant.whatsapp.shared.access-token=EAAG...         # System User token
holon.tenant.whatsapp.shared.phone-number-id=12345678901  # Phone Number ID
holon.tenant.whatsapp.shared.waba-id=98765432109          # WABA ID

# ── Webhook ──────────────────────────────────────────────────────────────────
# Must match "Verify Token" you enter in the Meta App Dashboard → Webhooks
holon.tenant.whatsapp.webhook.verify-token=my-secret-token

# ── Mode ─────────────────────────────────────────────────────────────────────
# SHARED  = one WABA phone, all tenants receive/send on the same number
# DEDICATED = each tenant has its own WABA (implement TenantWhatsAppCredentialsProvider)
holon.tenant.whatsapp.mode=SHARED

# ── Fallback tenant (SHARED mode only) ───────────────────────────────────────
# Which tenantId to assign inbound webhook events to when not specified in the URL
holon.tenant.whatsapp.default-tenant-id=my-tenant-id

# ── Plan-based marketing quotas (optional overrides) ─────────────────────────
holon.tenant.whatsapp.plan-marketing-limits.FREE=50
holon.tenant.whatsapp.plan-marketing-limits.STARTER=200
holon.tenant.whatsapp.plan-marketing-limits.PROFESSIONAL=2000
holon.tenant.whatsapp.plan-marketing-limits.ENTERPRISE=-1
```

---

## 3. What you get for free (zero additional code)

As soon as the dependency is on the classpath, Spring Boot's auto-configuration
(`TenantWhatsAppAutoConfiguration`) registers the following beans automatically:

| Bean | Description |
|---|---|
| `ConversationWindowTracker` | In-memory tracker of 24-hour free service windows — the core cost-saving component |
| `TenantWhatsAppService` (default impl) | In-memory service; sends via Meta Cloud API when credentials are set |
| `WhatsAppWebhookController` | `GET /webhook/whatsapp` (Meta verification) + `POST /webhook/whatsapp` (events) |

**Vaadin UI is also registered automatically:**

| Route | Description |
|---|---|
| `/whatsapp` | Cost dashboard — KPI cards, free/paid ratio, quick-send |
| `/whatsapp/conversations` | Active service windows + lazy message history |
| `/whatsapp/templates` | All WABA templates with cost colour-coding |
| `/whatsapp/settings` | Credentials status + cost guide |

All views are accessible immediately at `http://localhost:8080/whatsapp`.

---

## 4. Add WhatsApp to your Vaadin navigation menu

In your main `AppLayout`, add a `SideNavItem` pointing to the dashboard:

```java
import com.holonplatform.vaadin.flow.tenant.whatsapp.ui.views.WhatsAppDashboardView;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.icon.VaadinIcon;

// Inside your layout constructor:
SideNavItem whatsappItem = new SideNavItem(
    "WhatsApp",
    WhatsAppDashboardView.class,
    VaadinIcon.MOBILE.create()
);
nav.addItem(whatsappItem);
```

Or point to the entire WhatsApp shell layout (if you want the WhatsApp sub-navigation):

```java
// Just link to the management section from a button or router link
Anchor link = new Anchor("/whatsapp", "WhatsApp Management →");
```

---

## 5. Send a WhatsApp message from your own code

Inject `TenantWhatsAppService` anywhere in your Spring beans or Vaadin views:

```java
@Service
public class OrderService {

    private final TenantWhatsAppService whatsApp;

    public OrderService(TenantWhatsAppService whatsApp) {
        this.whatsApp = whatsApp;
    }

    public void notifyShipped(String tenantId, String customerPhone, String orderNumber) {
        // Cost-optimal path:
        // 1. If a free service window is open → sends as a free plain-text reply
        // 2. If no window → uses the 'order_shipped' UTILITY template (also free)
        if (whatsApp.hasOpenServiceWindow(tenantId, customerPhone)) {
            whatsApp.sendTextMessage(tenantId, customerPhone,
                "Your order " + orderNumber + " has shipped! 🚀");
        } else {
            whatsApp.sendTemplateMessage(tenantId, customerPhone,
                "order_shipped", "en_US",
                Map.of("1", orderNumber, "2", "tomorrow"));
        }
    }
}
```

---

## 6. Replace the in-memory implementation with a persistent one

The default `InMemoryTenantWhatsAppService` stores messages in `ConcurrentHashMap`.
For production, implement `TenantWhatsAppService` and expose it as a `@Service` bean —
`@ConditionalOnMissingBean` ensures yours replaces the default automatically:

```java
@Service
public class JpaTenantWhatsAppService implements TenantWhatsAppService {

    private final WhatsAppMessageRepository repo;           // your JPA repo
    private final ConversationWindowTracker windowTracker;  // inject — still managed by auto-config
    private final TenantWhatsAppProperties properties;      // inject — auto-configured

    @Override
    public WhatsAppMessage sendTextMessage(String tenantId, String to, String text) {
        boolean freeWindow = windowTracker.hasOpenFreeWindow(tenantId, to);
        // … build message, call Meta API, persist to DB …
    }

    @Override
    public List<WhatsAppMessage> listMessages(String tenantId, int offset, int limit) {
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId,
            PageRequest.of(offset / limit, limit)).getContent();
    }

    // … implement remaining interface methods …
}
```

---

## 7. Handle per-tenant WABA credentials (DEDICATED mode)

When each tenant has their own Meta account:

```properties
holon.tenant.whatsapp.mode=DEDICATED
```

Then in your `TenantWhatsAppService` implementation, use `TenantContext` to look up
the tenant's credentials at send time:

```java
private WhatsAppCloudApiClient clientFor(String tenantId) {
    // Load credentials from DB/secrets vault for this tenant
    TenantWabaCredentials creds = credentialsRepo.findByTenantId(tenantId)
        .orElseThrow(() -> new WhatsAppNotConfiguredException(tenantId));
    return new WhatsAppCloudApiClient(
        creds.phoneNumberId(),
        creds.wabaId(),
        creds.accessToken()
    );
}
```

For per-tenant webhook routing, Meta calls `POST /webhook/whatsapp/{tenantId}` —
the controller already handles this route variant out of the box.

---

## 8. Register the webhook in Meta Business Manager

1. Go to **Meta for Developers** → your App → **WhatsApp → Configuration**
2. Under **Webhooks**, click **Edit**
3. Set:
   - **Callback URL**: `https://<your-public-domain>/webhook/whatsapp`
   - **Verify Token**: must match `holon.tenant.whatsapp.webhook.verify-token`
4. Subscribe to **messages** field
5. Click **Verify and Save**

> **Local development**: Use [ngrok](https://ngrok.com/) or [localtunnel](https://localtunnel.me/)
> to expose your local port 8080: `ngrok http 8080`
> Then set the ngrok URL as the Callback URL.

---

## 9. Obtain Meta credentials (one-time setup)

| Step | Where |
|---|---|
| Create a Meta App | [developers.facebook.com](https://developers.facebook.com) → **My Apps → Create App** → type **Business** |
| Add WhatsApp product | App Dashboard → **Add Product → WhatsApp** |
| Get Phone Number ID | WhatsApp → **API Setup** → copy **Phone number ID** |
| Get WABA ID | WhatsApp → **API Setup** → copy **WhatsApp Business Account ID** |
| Create System User token | **Business Settings → System Users → Add → Assign to WABA (MANAGE permission) → Generate Token** |

The System User token **never expires** (set to "Standard" token type) and is the recommended
approach for production. Never use a personal user access token.

---

## 10. Cost optimisation — what happens automatically

You don't need to do anything special to benefit from cost savings.
The framework's `ConversationWindowTracker` runs the following logic on every send:

```
Customer sends you a WhatsApp message
         ↓
Webhook fires → registerInboundMessage(tenantId, phone)
         ↓
24-hour free service window opens in memory
         ↓
Your app calls service.sendTextMessage(tenantId, phone, "Hello!")
         ↓
hasOpenFreeWindow() == true → plain TEXT reply → ✅ FREE
         ↓ (if window expired / first contact)
Service picks UTILITY template (also FREE since 2024)
Only MARKETING templates are charged; plan quota enforced
```

**Free categories** (no Meta charge):
- `SERVICE` — reply within open 24-h customer window  
- `UTILITY` — order confirmations, shipping, reminders, alerts

**Charged categories**:
- `MARKETING` — promotions, re-engagement (gated by plan quota)
- `AUTHENTICATION` — OTP / verification codes

---

## Quick-start checklist

```
☐  1. Add holon-vaadin-flow-tenant-whatsapp to pom.xml
☐  2. Set holon.tenant.whatsapp.webhook.verify-token in application.properties
☐  3. (Optional) Set shared.access-token / phone-number-id / waba-id for live API
☐  4. (Optional) Add WhatsApp nav item in your AppLayout
☐  5. (Optional) Implement TenantWhatsAppService to persist messages in DB
☐  6. Register webhook URL in Meta Business Manager
☐  7. Start app → navigate to /whatsapp to see the dashboard
```

