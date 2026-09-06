# Building a Multi-Tenant CRM with Holon SaaS Framework

This framework was built specifically for this use case. Every concern a multi-tenant CRM needs — isolation, onboarding, billing, roles, and audit — is already a first-class module.

> **Before you start:** read [new-saas-app-checklist.md](./new-saas-app-checklist.md) first. It's a short pre-flight checklist covering the framework's sharpest edges (entity mapping rules, `TenantContext` binding, transaction boundaries, migration naming) — each one discovered the hard way while building this exact guide's reference app. This tutorial shows you *how* to wire up each feature; the checklist tells you the pitfalls to avoid while doing it.

## Table of Contents

- [Step 1 — Add the Dependency](#step-1--add-the-dependency)
- [Step 2 — Choose a Tenant Resolution Strategy](#step-2--choose-a-tenant-resolution-strategy)
- [Step 3 — Tell the Framework How to Load Tenant Details](#step-3--tell-the-framework-how-to-load-tenant-details)
- [Step 4 — Configure Data Isolation](#step-4--configure-data-isolation)
- [Step 5 — Define Your CRM Plans & Feature Flags](#step-5--define-your-crm-plans--feature-flags)
- [Step 6 — Onboard New CRM Customers](#step-6--onboard-new-crm-customers)
- [Step 7 — User Management Within Each Tenant](#step-7--user-management-within-each-tenant)
- [Step 8 — Audit Trail for Compliance](#step-8--audit-trail-for-compliance)
- [Step 9 — Per-Tenant Settings (Branding, Limits, Email)](#step-9--per-tenant-settings-branding-limits-email)
- [Step 10 — Rate Limiting & Resource Quotas](#step-10--rate-limiting--resource-quotas)
- [Step 11 — Billing & Subscriptions](#step-11--billing--subscriptions)
- [Step 12 — Security Isolation (Cross-Tenant Protection)](#step-12--security-isolation-cross-tenant-protection)
- [Step 13 — Building Your UI Layer](#step-13--building-your-ui-layer)
- [Step 14 — Super-Admin Console (Your Platform Dashboard)](#step-14--super-admin-console-your-platform-dashboard)
- [Step 15 — Fine-Grained Permissions Beyond Roles](#step-15--fine-grained-permissions-beyond-roles)
- [Step 16 — Adding a WhatsApp Messaging Channel (Optional)](#step-16--adding-a-whatsapp-messaging-channel-optional)
- [Complete Project Structure for Your CRM](#complete-project-structure-for-your-crm)
- [Why This Architecture Works for a CRM](#why-this-architecture-works-for-a-crm)

## Step 1 — Add the Dependency

Why: One dependency pulls in the entire backend stack (core, data, security, users, billing, audit, settings, admin services). The framework is UI-agnostic, so no manual wiring is needed regardless of which UI you build on top (Vaadin, React, REST clients, ...).

```xml
<!-- pom.xml of your CRM project -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.holon-platform.saas</groupId>
            <artifactId>holon-saas-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- All-in-one SaaS starter: includes security, data, audit, billing, users, etc. -->
    <dependency>
        <groupId>com.holon-platform.multi-tenancy</groupId>
        <artifactId>holon-starter-multi-tenancy</artifactId>
    </dependency>

    <!-- Add your DB driver, e.g. PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

## Step 2 — Choose a Tenant Resolution Strategy

Why: The framework must know which company is making each HTTP request. Three strategies are built in; pick the one that fits your CRM URL design.

| Strategy | Class | URL example | Best for CRM |
|---|---|---|---|
| Subdomain | `SubdomainTenantResolver` | `acme.yourcrm.com` | ✅ Best UX |
| Header | `HeaderTenantResolver` | `X-Tenant-ID: acme` | API-first or mobile |
| Path prefix | `PathPrefixTenantResolver` | `/t/acme/contacts` | Single-domain setups |

```java
// CrmConfig.java
@Configuration
public class CrmConfig {

    // Option A: Subdomain — acme.yourcrm.com → "acme"
    @Bean
    public TenantResolver tenantResolver() {
        return new SubdomainTenantResolver(); // reads first subdomain label
    }

    // Option B: Composite — try subdomain first, fall back to header
    // @Bean
    // public TenantResolver tenantResolver() {
    //     return new CompositeTenantResolver(
    //         new SubdomainTenantResolver(),
    //         new HeaderTenantResolver("X-Tenant-ID")
    //     );
    // }
}
```

The `TenantContextFilter` (auto-registered at order `-50`) calls your resolver on every HTTP request and stores the result in `TenantContext` (a `ThreadLocal`). All downstream code — your JPA repositories, services, UI views — reads from there without you passing tenant IDs around manually.

> **Vaadin Flow apps: watch out for the "path prefix" strategy.** A Vaadin UI only sends a
> request whose `getRequestURI()` matches the visible browser route on the very first, full
> page load. Every later interaction in the same session (button clicks, `UI.navigate(...)`,
> `RouterLink` clicks) goes through Vaadin's internal UIDL/heartbeat channel instead, whose
> request path is always the servlet root — it does **not** carry `/t/acme/...` again. A
> plain `PathPrefixTenantResolver` would therefore resolve the tenant correctly only on that
> first request and silently lose it (or, with `holon.tenant.required=true`, reject the
> request) on everything after it. Subdomain- and header-based strategies aren't affected,
> since the browser resends the same host/header on every request regardless of route.
>
> Because of this, `holon.tenant.strategy=PATH` is **automatically wrapped** with
> `SessionCachingTenantResolver`, which caches the resolved tenant ID in the `HttpSession`
> and falls back to it whenever the underlying resolver can't extract one from the current
> request. This happens with zero extra configuration. Override it with
> `holon.tenant.session-cache=false` (to disable) or `=true` (to force it on for another
> strategy) if you need different behavior.

## Step 3 — Tell the Framework How to Load Tenant Details

Why: After resolving the tenant ID (`"acme"`), the framework needs the full tenant record — plan, status, features, locale. You provide the data source for this.

```java
// CrmConfig.java (continued)

@Autowired
private CrmTenantRepository crmTenantRepository; // your JPA repo for the platform schema

@Bean
public TenantDetailsLoader tenantDetailsLoader() {
    return tenantId -> {
        // Load from your "tenants" table in the platform/shared schema
        CrmTenant t = crmTenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        return TenantDetails.builder(t.getId())
                .name(t.getCompanyName())
                .plan(t.getPlan())                  // FREE / STARTER / PROFESSIONAL / ENTERPRISE
                .status(t.getStatus())              // ACTIVE / SUSPENDED / etc.
                .feature(TenantFeature.AUDIT_LOG)   // if this tenant has it enabled
                .locale(t.getLocale())
                .timezone(t.getTimezone())
                .theme(t.getTheme())               // for white-labelling
                .attribute("industry", t.getIndustry())
                .build();
    };
}
```

`TenantDetails` is an immutable record. Once loaded per request, any code can call:

```java
TenantDetails tenant = TenantContext.require(); // throws if not set
String tenantId = tenant.tenantId();            // "acme"
boolean canExport = tenant.hasFeature(TenantFeature.ADVANCED_REPORTS);
```

## Step 4 — Configure Data Isolation

Why: This is the most critical multi-tenancy concern. You cannot let Acme see Globex's contacts. The `tenant-data` module handles this automatically.

Pick a schema strategy:

```yaml
# application.yml
holon:
  tenant:
    data:
      strategy: SCHEMA_PER_TENANT   # recommended for CRM
      # or: SHARED (cheaper, add tenant_id columns)
      # or: DATABASE_PER_TENANT (maximum isolation for enterprise)
  datasource:
    url: jdbc:postgresql://localhost:5432/crm_platform
    username: crm_app
    password: secret
```

What happens at runtime (`SCHEMA_PER_TENANT`):

1. When a request for tenant `acme` arrives, `TenantAwareDataSource` asks `TenantContext.peekTenantId()` → `"acme"`.
2. It selects (or lazily creates) a dedicated HikariCP pool pointing to schema `acme`.
3. Every JDBC call your code makes — JPA, jOOQ, JDBC template — automatically goes to schema `acme`.
4. No code change in your repositories is needed.

Per-tenant Flyway migrations:

```text
src/main/resources/db/migration/tenant/
V1__create_contacts.sql
V2__create_deals.sql
V3__add_pipeline_stage.sql
```

## Step 5 — Define Your CRM Plans & Feature Flags

Why: A CRM has tiers — some customers only get contact management (`FREE`), others get pipeline + reports (`PROFESSIONAL`), enterprise customers get SSO + API (`ENTERPRISE`). The framework's `TenantPlan` and `TenantFeature` model this exactly.

You can extend `TenantFeature`'s string-based system with your own CRM feature keys:

```java
// CrmFeatures.java — your own CRM-specific feature constants
public final class CrmFeatures {
    public static final String PIPELINE = "crm.pipeline";
    public static final String EMAIL_SEQUENCES = "crm.email_sequences";
    public static final String BULK_IMPORT = "crm.bulk_import";
    public static final String CUSTOM_FIELDS = "crm.custom_fields";
    public static final String ZAPIER = "crm.zapier_integration";
}
```

Then gate your service methods:

```java
// PipelineService.java
@Service
public class PipelineService {

    // AOP enforcement — throws TenantPlanRequiredException if below STARTER
    @RequiresPlan(minimum = TenantPlan.STARTER)
    public List<Deal> getDeals() { ... }

    // @CheckTenantFeature only accepts the built-in TenantFeature enum
    @CheckTenantFeature(TenantFeature.CUSTOM_INTEGRATIONS)
    public void syncZapier() { ... }

    // Your own CRM-specific string flags (like CrmFeatures.EMAIL_SEQUENCES) aren't
    // annotation targets — check them programmatically via TenantDetails.hasFeature(String)
    public void sendSequence(String contactId) {
        if (!TenantContext.require().hasFeature(CrmFeatures.EMAIL_SEQUENCES)) {
            throw new UpgradeRequiredException(CrmFeatures.EMAIL_SEQUENCES);
        }
        ...
    }
}
```

Gate your own UI layer's routes/views using the same checks (Vaadin example — implement a small navigation guard that calls the service):

```java
// ReportsView.java
@Route("reports")
public class ReportsView extends Div {
    // In a BeforeEnterListener (or equivalent guard in your chosen UI framework):
    //   if (!tenant.hasPlanAtLeast(TenantPlan.PROFESSIONAL)) event.rerouteTo("/upgrade");
}

// CustomFieldsView.java
@Route("custom-fields")
public class CustomFieldsView extends Div {
    //   if (!tenant.hasFeature(CrmFeatures.CUSTOM_FIELDS)) event.rerouteTo("/upgrade");
}
```

## Step 6 — Onboard New CRM Customers

Why: When a company signs up for your CRM, you need to create the tenant record, provision their database schema, run migrations, create the first owner user, and store their credentials — a self-service sign-up form needs all four steps in one call. The `tenant-signup` module's `SignupService` orchestrates exactly this on top of `TenantOnboardingService`.

```java
// SignupController.java or SignupView.java
@Autowired
private SignupService signupService;

public void signUp(SignupForm form) {
    String slug = SignupService.slugify(form.getCompanyName()); // "Acme Corp!" → "acme-corp"

    SignupRequest request = SignupRequest.builder(slug, form.getEmail(), form.getRawPassword())
            .companyName(form.getCompanyName())
            .plan(TenantPlan.STARTER)          // default plan on signup
            .attribute("industry", form.getIndustry())
            .build();

    TenantDetails tenant = signupService.signup(request);
    // Tenant schema created, Flyway ran, owner TenantUser created (role OWNER),
    // password hashed and persisted via your AccountCredentialsStore bean
    redirectToLogin(tenant.tenantId());
}
```

`SignupService` needs one bean from your application — where to store the hashed login credentials:

```java
// CrmAccountCredentialsStore.java
@Component
public class CrmAccountCredentialsStore implements AccountCredentialsStore {

    @Autowired private CrmAccountRepository accountRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public boolean existsByEmail(String tenantId, String email) {
        return accountRepo.existsByTenantIdAndEmailIgnoreCase(tenantId, email);
    }

    @Override
    public void save(String tenantId, String email, String encodedPassword) {
        // encodedPassword is already BCrypt-hashed by SignupService — never the raw password
        accountRepo.save(new CrmAccount(tenantId, email, encodedPassword));
    }
}
```

Listen to onboarding events fired under the hood (`SignupService` delegates provisioning to `TenantOnboardingService`, which publishes these):

```java
@Component
public class CrmOnboardingListener {

    @EventListener
    public void onOnboarded(TenantOnboardingCompletedEvent event) {
        // ⚠️ TenantContext is NOT bound here — Spring's ApplicationEventPublisher is
        // synchronous by default, so this listener runs DURING onboard(...), before any
        // tenant has been resolved for the current thread. Anything below that writes to
        // the NEW tenant's own schema (via Holon's Datastore, TenantAwareDataSource-routed)
        // must bind TenantContext itself, or it silently falls back to the platform
        // connection and fails with "Table '...' not found" the moment that table only
        // exists in the tenant's own schema. See new-saas-app-checklist.md Section 3.
        // TenantContextTemplate.run(...) handles the set()/finally-clear() boilerplate
        // (and correctly restores rather than blindly clears, if this ever runs nested
        // inside an already-bound context).
        TenantContextTemplate.run(event.getTenant(), () -> {
            // Provision default pipeline stages, send a "workspace is ready" notification, etc.
            pipelineService.createDefaultStages(event.tenantId());
        });
    }

    @EventListener
    public void onFailed(TenantOnboardingFailedEvent event) {
        log.error("Onboarding failed for {}: {}", event.tenantId(), event.reason());
        // Alert ops team
    }
}
```

Prefer calling `TenantOnboardingService.onboard(...)` directly (instead of `SignupService`) only when *you* — the platform operator — are provisioning a tenant without an accompanying self-service owner account, e.g. from the [Super-Admin Console](#step-14--super-admin-console-your-platform-dashboard). In that case `TenantOnboardingRequest.adminEmail()` is stored only as metadata; it does **not** create a login — seed the owner user yourself via `TenantUserService.invite(...)` or `TenantInvitationService.invite(...)` (see [Step 7](#step-7--user-management-within-each-tenant)).

## Step 7 — User Management Within Each Tenant

Why: In a CRM, the Acme account admin needs to add/remove salespeople, assign roles, and invite new team members by email. `TenantUserService` (`tenant-users`) manages the user lifecycle; `TenantInvitationService` (`tenant-invitations`) adds the email/token layer on top so invitees click a link and set their own password instead of being created directly.

```java
// CrmUserService.java (wraps TenantInvitationService + TenantUserService with CRM-specific logic)
@Service
public class CrmUserService {

    @Autowired private TenantInvitationService invitationService;
    @Autowired private TenantUserService userService;

    public void inviteSalesperson(String tenantId, String invitedByUserId, String email) {
        // Creates a PENDING invitation and emails it (if an InvitationSender bean is registered)
        invitationService.invite(InvitationRequest.builder(tenantId, email)
                .invitedByUserId(invitedByUserId)
                .platformRole(TenantRole.MEMBER)
                .validity(Duration.ofHours(72))
                .build());
    }

    public void promoteToAdmin(String tenantId, String userId) {
        userService.changeRole(tenantId, userId, TenantRole.ADMIN);
    }

    public List<TenantUser> listActiveUsers(String tenantId) {
        return userService.findByStatus(tenantId, TenantUserStatus.ACTIVE, 0, 50);
    }
}
```

When the invitee clicks the link and submits their chosen password, call `accept(...)` — this creates the `TenantUser` (in `ACTIVE` state) and fires `InvitationAcceptedEvent` so your app can store the login credentials:

```java
// InvitationAcceptController.java or AcceptInvitationView.java
public void onAcceptSubmit(String token, String rawPassword) {
    TenantUser newUser = invitationService.accept(token, rawPassword);
    redirectToLogin(newUser.getTenantId());
}

@Component
public class CrmInvitationListener {
    @EventListener
    public void onInvitationAccepted(InvitationAcceptedEvent event) {
        String hashed = passwordEncoder.encode(event.getRawPassword());
        credentialsRepository.save(event.getTenantUser().email(), hashed);
    }
}
```

Calling `TenantUserService.invite(tenantId, userId, email, role)` directly is still supported — `TenantInvitationService.accept(...)` uses it internally — but it puts the user straight into `INVITED` state with no email/token, so reserve it for cases where *you* control activation (e.g. bulk-importing a team from a CSV, or the [Super-Admin Console](#step-14--super-admin-console-your-platform-dashboard)).

Role hierarchy in your CRM:

| TenantRole | CRM meaning |
|---|---|
| `VIEWER` | Read-only — sees contacts/deals but cannot edit |
| `MEMBER` | Salesperson — full CRM access within their plan |
| `ADMIN` | Team lead — manages users, settings, billing |
| `OWNER` | Account owner — can delete the account, transfer ownership |

## Step 8 — Audit Trail for Compliance

Why: Enterprise CRM customers (healthcare, finance, legal) require a full audit log of who did what and when. The `@AuditAction` AOP annotation captures this with zero boilerplate.

```java
// ContactService.java
@Service
public class ContactService {

    @AuditAction(action = "CONTACT_DELETED", description = "Contact permanently deleted")
    public void deleteContact(String contactId) {
        // The aspect automatically records: actor, tenantId, timestamp, IP
        contactRepository.deleteById(contactId);
    }

    @AuditAction(action = "DEAL_CLOSED_WON", description = "Deal marked as closed-won")
    public Deal closeWon(String dealId, BigDecimal amount) {
        ...
    }
}
```

Query audit history in the UI:

```java
@Autowired TenantAuditService auditService;

List<AuditEntry> recentActivity = auditService.findByTenant(tenantId, 0, 20);
```

## Step 9 — Per-Tenant Settings (Branding, Limits, Email)

Why: Each CRM customer needs their own logo, brand colour, SMTP server, upload limits, etc. `TenantSettingsService` provides a three-tier inheritance chain: tenant-specific → plan defaults → system defaults.

```java
@Autowired TenantSettingsService settingsService;

// Tenant admin saves their branding
settingsService.set(tenantId, TenantSettingKeys.LOGO_URL, "https://acme.com/logo.png");
settingsService.set(tenantId, TenantSettingKeys.PRIMARY_COLOR, "#E53E3E");
settingsService.set(tenantId, TenantSettingKeys.EMAIL_SENDER_NAME, "Acme CRM");

// Reading from any UI layer (falls back to plan default, then system default)
String logoUrl = settingsService.get(tenantId, TenantSettingKeys.LOGO_URL)
        .orElse("/default-logo.png");
```

Add your own CRM-specific settings:

```java
// CrmSettingKeys.java
public final class CrmSettingKeys {
    public static final TenantSetting MAX_CONTACTS =
            TenantSetting.of("crm.max_contacts", "1000", "Max contacts allowed");
    public static final TenantSetting DEFAULT_PIPELINE =
            TenantSetting.of("crm.default_pipeline", "sales", "Default pipeline name");
}
```

## Step 10 — Rate Limiting & Resource Quotas

Why: A CRM needs two different kinds of guardrails: **rate limits** throttle how *fast* a tenant can call something (e.g. 60 API requests/minute, regardless of plan), while **quotas** cap how *much* of a resource a tenant may hold at once, scaled by plan (e.g. 500 contacts on `FREE`, unlimited on `ENTERPRISE`). `tenant-limits` handles the former, `tenant-quotas` the latter.

Rate limiting with `@RateLimit` (`tenant-limits`) — register a `TenantRateLimiter.LimitDefinition` per key, then guard methods by key:

```java
// CrmConfig.java
@Autowired
private TokenBucketRateLimiter rateLimiter; // the default TenantRateLimiter implementation

@PostConstruct
void configureRateLimits() {
    // Built-in keys ship with sensible defaults (see TenantLimitKeys)...
    rateLimiter.registerDefinition(TenantLimitKeys.API_REQUESTS, TenantLimitKeys.API_REQUESTS_DEFAULT);
    // ...or register your own CRM-specific key
    rateLimiter.registerDefinition("crm.bulk_import", TenantRateLimiter.LimitDefinition.perHour(10));
}

// CrmApiController.java
@RestController
@RequestMapping("/api/v1")
public class CrmApiController {

    @GetMapping("/contacts")
    @RateLimit(key = TenantLimitKeys.API_REQUESTS)   // resolved against the registered LimitDefinition
    public List<Contact> listContacts() {
        ...
    }

    @PostMapping("/contacts/bulk-import")
    @RequiresPlan(minimum = TenantPlan.PROFESSIONAL)
    @RateLimit(key = "crm.bulk_import")
    public ImportResult bulkImport(@RequestBody List<ContactDto> contacts) {
        ...
    }
}
```

Plan-based resource quotas with `@EnforceQuota` (`tenant-quotas`) — register limits per `TenantPlan`, then guard the creation method:

```java
@Autowired
private TenantQuotaService quotaService;

@PostConstruct
void configureQuotas() {
    quotaService.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
            .limit(TenantPlan.FREE,          500L)
            .limit(TenantPlan.STARTER,      5_000L)
            .limit(TenantPlan.PROFESSIONAL, 50_000L)
            .limit(TenantPlan.ENTERPRISE,      -1L)   // -1 = unlimited
            .build());
}

// ContactService.java
@Service
public class ContactService {

    @EnforceQuota("max_contacts")   // throws QuotaExceededException once the plan limit is reached
    public Contact createContact(ContactDto dto) {
        ...
    }
}
```

Check quota usage programmatically for a "you're using 80% of your plan" banner:

```java
QuotaCheckResult result = quotaService.checkQuota(tenantId, QuotaKeys.MAX_CONTACTS);
if (result.percentUsed() >= 80 && !result.unlimited()) {
    showUpgradeBanner(result.remaining());
}
```

| | Rate limits (`tenant-limits`, `@RateLimit`) | Quotas (`tenant-quotas`, `@EnforceQuota`) |
|---|---|---|
| Question answered | "Can this request happen *right now*?" | "Does this tenant still have capacity?" |
| Time basis | Sliding token-bucket window (per second/minute/hour) | Cumulative, scoped to the tenant's current plan |
| Example | 60 API requests/minute | 500 contacts max on the `FREE` plan |
| Failure | `RateLimitExceededException` | `QuotaExceededException` |

## Step 11 — Billing & Subscriptions

Why: Your CRM needs to track what plan each customer is on, display invoice history, and handle plan upgrades/downgrades.

```java
// BillingController.java
@Autowired TenantBillingService billingService;

// Called from your Stripe webhook handler
public void handleStripePayment(String tenantId, BigDecimal amount, String invoiceId) {
    billingService.saveInvoice(
            Invoice.builder(invoiceId, tenantId)
                    .amountDue(amount).currency("USD")
                    .status(InvoiceStatus.PAID)
                    .issuedAt(Instant.now())
                    .paidAt(Instant.now())
                    .build());
}

// Upgrade a tenant from STARTER → PROFESSIONAL
public void upgradePlan(String tenantId) {
    billingService.updateSubscriptionPlan(tenantId, TenantPlan.PROFESSIONAL);
    // This also updates TenantDetails so @RequiresPlan gates immediately respect the new plan
}
```

## Step 12 — Security Isolation (Cross-Tenant Protection)

Why: This is automatic, but important to understand. The `TenantIsolationFilter` (order `-50`) runs before every request and ensures a logged-in user from tenant `acme` cannot access tenant `globex`'s data even if they manipulate URLs or headers.

```java
// Spring Security integration — your UserDetailsService
@Service
public class CrmUserDetailsService implements UserDetailsService {

    @Override
    public UserDetails loadUserByUsername(String email) {
        String tenantId = TenantContext.require().tenantId();  // already set by TenantContextFilter

        // The framework's TenantAwareUserDetails carries the tenantId alongside Spring roles
        CrmUser user = userRepository.findByTenantAndEmail(tenantId, email)
                .orElseThrow(() -> new UsernameNotFoundException(email));

        return TenantAwareUserDetails.builder(user.getEmail(), user.getPasswordHash(), tenantId)
                .role("ROLE_" + user.getRole().name())
                .build();
    }
}
```

If a user authenticated to `acme` hits a URL with a different tenant ID in it, `TenantIsolationFilter` returns HTTP 403 automatically — no extra code needed.

## Step 13 — Building Your UI Layer

Why: The framework is UI-agnostic — it exposes only backend services (`TenantContext`, `TenantPermissionService`, plan/feature checks, ...). Your own UI layer calls these to gate navigation and render tenant-aware content. The example below shows the pattern with Vaadin, but the same calls work identically from a React app's backing REST controller or any other UI.

```java
// CrmMainLayout.java — the app shell (Vaadin example)
@Layout("/")
public class CrmMainLayout extends AppLayout {

    @Override
    protected void onAttach(AttachEvent event) {
        TenantDetails tenant = TenantContext.require();

        // Build navigation dynamically based on plan/feature checks exposed by the framework
        SideNav nav = new SideNavBuilder()
                .addItem("Contacts", ContactsView.class, VaadinIcon.USERS)
                .addItem("Deals", DealsView.class, VaadinIcon.MONEY)
                .addItemIf(tenant.hasFeature(CrmFeatures.PIPELINE),
                        "Pipeline", PipelineView.class, VaadinIcon.CHART_LINE)
                .addItemIf(tenant.hasFeature(TenantFeature.ADVANCED_REPORTS),
                        "Reports", ReportsView.class, VaadinIcon.BAR_CHART)
                .build();

        addToDrawer(nav);
    }
}
```

## Step 14 — Super-Admin Console (Your Platform Dashboard)

Why: You (the SaaS operator) need to see all tenants, suspend problematic ones, change their plans, and view metrics. The `tenant-admin` module provides the UI-agnostic backend service (`TenantAdminService`) for this — tenant listing, lookups, plan changes, suspend/activate — so you only need to build the presentation layer.

```java
@Autowired TenantAdminService tenantAdminService;

// List all tenants for a dashboard/grid
List<TenantDetails> tenants = tenantAdminService.findAll();

// Act on one
TenantDetails tenant = tenantAdminService.findById(tenantId).orElseThrow();
tenantAdminService.save(tenant.toBuilder().plan(TenantPlan.ENTERPRISE).build());
tenantAdminService.suspend(tenantId);
```

Build the console itself with whichever UI you chose in Step 13 (a Vaadin `Grid` + detail panel, a React admin page calling a REST controller that wraps `TenantAdminService`, ...). Protect the route(s) in your `SecurityFilterChain`:

```java
http.authorizeHttpRequests(auth -> auth
        .requestMatchers("/admin/**").hasRole("SUPER_ADMIN")
        .requestMatchers("/api/**").authenticated()
        .anyRequest().authenticated()
);
```

## Step 15 — Fine-Grained Permissions Beyond Roles

Why: `TenantRole` (Step 7) gives you a coarse four-level hierarchy (`VIEWER`/`MEMBER`/`ADMIN`/`OWNER`), but real CRM teams need job-specific access too — an `ACCOUNTANT` should see invoices, a `DRIVER` should only see their own delivery tasks. `TenantPermissionService` (`tenant-permissions`) layers a per-tenant, per-role permission set on top, enforced with `@RequiresPermission`.

```java
// CrmPermissionSetup.java — typically run once per tenant, e.g. from a TenantOnboardingCompletedEvent listener
@Autowired
private TenantPermissionService permissionService;

public void setupDefaultRoles(String tenantId) {
    permissionService.setPermissions(tenantId, "ACCOUNTANT",
            Set.of("VIEW_FINANCIALS", "APPROVE_INVOICE", "VIEW_CONTACTS"));
    permissionService.setPermissions(tenantId, "DRIVER",
            Set.of("VIEW_OWN_TASKS", "MANAGE_DELIVERIES"));
    permissionService.setPermissions(tenantId, "ADMIN",
            Set.of("*"));   // wildcard = all permissions
}
```

Assign the job role to a user via the standard attribute key when inviting them, then gate service methods with `@RequiresPermission`:

```java
// Assigning a job role at invite time (Step 7)
invitationService.invite(InvitationRequest.builder(tenantId, email)
        .platformRole(TenantRole.MEMBER)
        .jobRole("ACCOUNTANT")   // stored under TenantPermissionService.JOB_ROLE_ATTRIBUTE
        .build());

// FinancialService.java
@Service
public class FinancialService {

    @RequiresPermission("APPROVE_INVOICE")  // throws PermissionDeniedException if missing
    public void approveInvoice(Long invoiceId) { ... }
}
```

Or check programmatically for UI-level gating (e.g. hiding a button):

```java
if (permissionService.currentUserHasPermission("VIEW_FINANCIALS")) {
    showFinancialsTab();
}
```

## Step 16 — Adding a WhatsApp Messaging Channel (Optional)

Why: Many CRM customers run their sales/support conversations over WhatsApp. `tenant-whatsapp` wraps the Meta WhatsApp Business Cloud API with per-tenant conversation-window tracking (to avoid paying for messages sent inside the free 24-hour service window) and plan-based marketing-message quotas. It's shipped as an **optional** dependency of the starter — declare it explicitly to enable it:

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.holon-platform.multi-tenancy</groupId>
    <artifactId>holon-multi-tenant-whatsapp</artifactId>
</dependency>
```

```java
// CrmWhatsAppService.java
@Autowired
private TenantWhatsAppService whatsAppService;

public void notifyDealWon(String tenantId, String customerPhone, Deal deal) {
    if (whatsAppService.hasOpenServiceWindow(tenantId, customerPhone)) {
        // Free-form text — no charge, we're inside the 24h customer-initiated window
        whatsAppService.sendTextMessage(tenantId, customerPhone,
                "Thanks for closing the deal, " + deal.getContactName() + "!");
    } else {
        // Outside the window — send an invoice via a pre-approved template instead
        whatsAppService.sendInvoice(tenantId, customerPhone,
                deal.getInvoiceNumber(), deal.getAmount(), "USD",
                deal.getDueDate(), deal.getInvoiceUrl());
    }
}

// Monitor spend for a billing/usage dashboard
WhatsAppUsageStats stats = whatsAppService.getUsageStats(tenantId, YearMonth.now());
```

See [WHATSAPP_INTEGRATION.md](../WHATSAPP_INTEGRATION.md) for full setup: Meta credentials, webhook configuration, SHARED vs DEDICATED (per-tenant WABA) mode, and the demo mode that logs messages instead of calling the real API when no credentials are configured.

## Complete Project Structure for Your CRM

```text
crm-saas/
├── pom.xml                          ← imports holon-saas-bom + starter (+ optional tenant-whatsapp)
└── src/main/java/com/yourco/crm/
    ├── CrmApplication.java          ← @SpringBootApplication
    ├── config/
    │   ├── CrmConfig.java           ← Step 2+3+10: TenantResolver + TenantDetailsLoader + rate-limit/quota registration
    │   └── CrmSecurityConfig.java   ← Step 12: SecurityFilterChain
    ├── domain/
    │   ├── Contact.java             ← JPA entity (no tenant_id column needed with SCHEMA_PER_TENANT)
    │   ├── Deal.java
    │   └── Pipeline.java
    ├── service/
    │   ├── ContactService.java      ← Step 8+10: @AuditAction, @EnforceQuota
    │   ├── PipelineService.java     ← Step 5: @RequiresPlan, @CheckTenantFeature
    │   ├── FinancialService.java    ← Step 15: @RequiresPermission
    │   ├── CrmUserService.java      ← Step 7: wraps TenantInvitationService + TenantUserService
    │   ├── CrmAccountCredentialsStore.java ← Step 6: AccountCredentialsStore for SignupService
    │   └── CrmWhatsAppService.java  ← Step 16 (optional): wraps TenantWhatsAppService
    ├── api/
    │   └── CrmApiController.java    ← Step 10: @RateLimit + @RequiresPlan
    ├── listener/
    │   ├── CrmOnboardingListener.java  ← Step 6: TenantOnboardingCompletedEvent/Failed
    │   └── CrmInvitationListener.java  ← Step 7: InvitationAcceptedEvent
    └── ui/
        ├── CrmMainLayout.java       ← Step 13: AppBar + SideNav + plan-aware nav
        └── views/
            ├── SignupView.java      ← Step 6: uses SignupService
            ├── ContactsView.java    ← always visible
            ├── DealsView.java       ← always visible
            ├── PipelineView.java    ← @TenantFeatureRequired(CrmFeatures.PIPELINE)
            ├── ReportsView.java     ← @TenantPlanRequired(PROFESSIONAL)
            ├── SettingsView.java    ← uses TenantSettingsService
            ├── UsersView.java       ← uses TenantUserService + TenantInvitationService
            ├── BillingView.java     ← uses TenantBillingService
            └── UpgradeView.java     ← redirect target for plan-gated views
```

## Why This Architecture Works for a CRM

| Multi-tenancy concern | How Holon SaaS handles it |
|---|---|
| Who is making this request? | `TenantResolver` + `TenantContextFilter` — resolved automatically per request |
| Data never leaks between tenants | `TenantAwareDataSource` routes all JDBC to tenant schema; `TenantIsolationFilter` blocks HTTP cross-tenant access |
| New customer signs up | `SignupService` (`tenant-signup`) validates, provisions the tenant via `TenantOnboardingService`, creates the owner user, and stores credentials — one call |
| Different customers get different features | `TenantPlan` enum + `TenantFeature` flags + `@RequiresPlan` / `@TenantFeatureRequired` AOP |
| Audit log for compliance | `@AuditAction` aspect — annotate a method, get a full audit trail |
| Per-tenant branding | `TenantSettingsService` with three-tier inheritance |
| User invites & roles | `TenantInvitationService` (email/token lifecycle: `PENDING` → `ACCEPTED`/`REVOKED`/`EXPIRED`) on top of `TenantUserService` (`INVITED` → `ACTIVE` → `SUSPENDED` → `REMOVED`) |
| Job-specific permissions | `TenantPermissionService` + `@RequiresPermission` — per-tenant, per-role permission sets beyond `TenantRole` |
| Request throttling per tenant | `TenantRateLimiter` + `@RateLimit` AOP — token-bucket sliding window |
| Resource caps per plan | `TenantQuotaService` + `@EnforceQuota` AOP — e.g. max contacts/users/storage per `TenantPlan` |
| Billing & invoices | `TenantBillingService` + Stripe webhook integration point |
| Admin sees all customers | `TenantAdminService` (from `tenant-admin`) exposes tenant listing/plan-change/suspend backend calls for your own admin UI |
| WhatsApp messaging (optional) | `TenantWhatsAppService` (`tenant-whatsapp`) — conversation-window cost optimisation + plan-based marketing quotas |

The key insight is that your CRM code only writes business logic (contacts, deals, pipeline stages). Every multi-tenancy concern — context propagation, data isolation, access control, onboarding, permissions, rate limits, quotas, billing, audit — is handled by the framework modules automatically through Spring Boot auto-configuration and AOP.


