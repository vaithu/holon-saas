# Holon SaaS Framework

**Version:** 1.0.0  
**Java:** 25  
**Spring Boot:** 4.1.0  

The **Holon SaaS Framework** is a **UI-agnostic** multi-tenant backend platform for building
production-ready SaaS applications with Spring Boot. Every module exposes only services,
repositories and Spring beans — no module depends on any UI framework. Build a Vaadin, React,
REST, or mobile front end on top of it.

---

## Modules

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| `tenant-core` | `holon-multi-tenant-core` | Core tenant model: `TenantDetails`, `TenantContext`, `TenantPlan`, `TenantFeature`, lifecycle events |
| `tenant-data` | `holon-multi-tenant-data` | Multi-tenant datasource routing, per-tenant Flyway migrations, schema isolation |
| `tenant-security` | `holon-multi-tenant-security` | Spring Security integration: `TenantAwareUserDetails`, `TenantIsolationFilter`, tenant-aware `AuthContext` wiring |
| `tenant-onboarding` | `holon-multi-tenant-onboarding` | Tenant provisioning pipeline with hooks and event publishing |
| `tenant-audit` | `holon-multi-tenant-audit` | `@AuditAction` AOP support, in-memory and pluggable audit storage |
| `tenant-settings` | `holon-multi-tenant-settings` | Per-tenant key/value settings with typed access |
| `tenant-limits` | `holon-multi-tenant-limits` | `@RateLimit` / `@TenantLimit` AOP enforcement |
| `tenant-users` | `holon-multi-tenant-users` | Tenant user management: invite, activate, suspend, roles |
| `tenant-billing` | `holon-multi-tenant-billing` | Subscription and invoice management |
| `tenant-admin` | `holon-multi-tenant-admin` | Super-admin backend services for tenant management (UI-agnostic) |
| `tenant-permissions` | `holon-multi-tenant-permissions` | Fine-grained role-based permission management |
| `tenant-invitations` | `holon-multi-tenant-invitations` | Tenant invitation workflows |
| `tenant-quotas` | `holon-multi-tenant-quotas` | Per-tenant quota enforcement |
| `tenant-whatsapp` | `holon-multi-tenant-whatsapp` | WhatsApp Business API integration |
| `starter-saas` | `holon-starter-multi-tenancy` | Spring Boot auto-configuration starter for all modules |
| `bom` | `holon-multi-tenancy-bom` | Bill of Materials — import to manage all module versions |

---

## Prerequisites

| Requirement | Version | Why |
|---|---|---|
| JDK | 25 | `maven.compiler.release` for every module |
| Maven | 3.9+ | Build tool used by this reactor |
| A relational database | H2 (local dev) / PostgreSQL (production) | `tenant-data`'s schema routing only ships tested dialects for these two — see Step 2 |
| Spring Boot | 4.1.0 | Managed transitively via the BOM |

---

## Getting Started — Step by Step

This walks a **new Spring Boot project** from zero to a working multi-tenant sign-up flow.
For the *full* feature-by-feature tutorial (plans, billing, audit, permissions, WhatsApp, a
super-admin console, building a UI layer, ...) continue on to
[`docs/multi-tenant-crm-guide.md`](docs/multi-tenant-crm-guide.md) once this gets you
running. Before writing your first entity, also skim
[`docs/new-saas-app-checklist.md`](docs/new-saas-app-checklist.md) — it documents the sharp
edges below in more depth (entity mapping rules, transaction-boundary rules, migration
naming) so you don't have to rediscover them yourself.

### Step 1 — Add the dependency

One dependency pulls in the entire backend stack (core, data, security, users, billing,
audit, settings, admin services, signup). The framework is UI-agnostic — no manual wiring is
needed regardless of which UI you build on top (Vaadin, React, REST clients, ...).

```xml
<!-- pom.xml of your new project -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.holon-platform.multi-tenancy</groupId>
            <artifactId>holon-multi-tenancy-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- All-in-one starter: security, data, audit, billing, users, onboarding, signup, admin -->
    <dependency>
        <groupId>com.holon-platform.multi-tenancy</groupId>
        <artifactId>holon-starter-multi-tenancy</artifactId>
    </dependency>

    <!-- Your own DB driver, e.g. PostgreSQL for production -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Add the usual Spring Boot starters your app needs -->
    <!-- spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, ... -->
</dependencies>
```

Prefer cherry-picking modules instead of the all-in-one starter? Depend on `tenant-core`
(always required) plus only the modules you need, e.g. `tenant-data`, `tenant-security`.

### Step 2 — Configure `application.properties`

Copy this skeleton and adjust the datasource/dialect for your environment. Every property
here exists because a missing one produces a working-looking app that fails the moment a
real user signs up — see
[the checklist's annotated version](docs/new-saas-app-checklist.md#1-dependencies--properties-template)
for the full reasoning behind each line.

```properties
# ─── JPA — schema is owned by Flyway, never let Hibernate auto-generate DDL ──
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false

# ─── Flyway ───────────────────────────────────────────────────────────────
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# ─── Tenant resolution (see Step 3) ───────────────────────────────────────
holon.tenant.strategy=PATH
holon.tenant.path-prefix=/t/
holon.tenant.required=true
# MUST include every public/unauthenticated route — signup and login run with
# NO tenant resolved yet. This is the #1 source of "Table ... not found" errors.
holon.tenant.ignored-paths=/,/login,/signup,/actuator/**,/public/**,/error

# ─── Tenant data isolation (see Step 4) ───────────────────────────────────
holon.tenant.data.schema-strategy=SCHEMA_PER_TENANT
# Match your REAL database: H2 for local dev, POSTGRESQL for production.
# MYSQL is not a supported value for the framework's own tenant-schema migrations.
holon.tenant.data.dialect=H2
```

### Step 3 — Choose a tenant resolution strategy

The framework must know which tenant is making each HTTP request. Three strategies ship
built in:

| Strategy | Property value | Class | URL example | Best for |
|---|---|---|---|---|
| Subdomain | `SUBDOMAIN` | `SubdomainTenantResolver` | `acme.yourapp.com` | Best UX, no path clutter |
| Header | `HEADER` | `HeaderTenantResolver` | `X-Tenant-ID: acme` | API-first / mobile clients |
| Path prefix | `PATH` | `PathPrefixTenantResolver` | `/t/acme/...` | Single-domain setups |

`TenantContextFilter` (auto-registered at order `-50`) calls the configured resolver on every
request and stores the result in `TenantContext` (a `ThreadLocal`). All downstream code —
repositories, services, UI views — reads from there without you passing tenant IDs around
manually.

> **Building a Vaadin UI on `PATH` strategy?** Only the first full page load carries
> `/t/acme/...` in the request URI — every later Vaadin UIDL/heartbeat request hits the
> servlet root instead. `holon.tenant.strategy=PATH` is therefore automatically wrapped with
> `SessionCachingTenantResolver`, caching the resolved tenant in the `HttpSession`. Override
> with `holon.tenant.session-cache=false`/`true` if you need different behavior.

Need to combine strategies (e.g. subdomain first, header fallback for API calls)? Define your
own `TenantResolver` bean using `CompositeTenantResolver` — see
[`multi-tenant-crm-guide.md` Step 2](docs/multi-tenant-crm-guide.md#step-2--choose-a-tenant-resolution-strategy).

### Step 4 — Provide `TenantDetailsLoader` and your platform-schema `tenant` table

After resolving a tenant ID, the framework needs the full tenant record (plan, status,
features, locale). `tenant-onboarding` auto-configures a default loader backed by its own
repository — you only need to write your own if you want a different tenant store (e.g.
cache-backed).

The platform-schema `tenant` table itself is **not** bundled by the framework — create it
yourself in your own platform-schema Flyway migration (`src/main/resources/db/migration/`),
with columns matching `TenantDetails`' `@Column` mappings (`tenant_id`, `name`, `plan`,
`status`, `locale`, `timezone`, `theme`).

### Step 5 — Data isolation: how it works, and your tenant-schema migrations

With `SCHEMA_PER_TENANT` (recommended default):

1. A request for tenant `acme` arrives; `TenantAwareDataSource` reads
   `TenantContext.peekTenantId()` → `"acme"`.
2. It selects (or lazily creates) a dedicated connection pool pointed at schema `acme`.
3. Every JDBC call your code makes — JPA, JdbcTemplate, whatever — goes to schema `acme`
   automatically. No repository code changes needed.

Put your own tenant-schema migrations under `src/main/resources/db/tenant-migration/`:

```text
src/main/resources/db/tenant-migration/
V2__create_your_first_entity.sql
V3__add_a_column.sql
```

**Number your own scripts `V2`/`V002` or higher — never `V1`/`V1.x`.** The framework reserves
that range for its own bundled tenant-schema tables (`tenant_users`, `audit_entries`,
`tenant_billing_invoice`, `user_invitation`, `tenant_permissions`, and more — the full,
compile-time-checkable list is `FrameworkReservedTableNames.ALL` in `tenant-core`). Never
name your own table any of those names. Full details, including the `@ElementCollection`
pitfall and the `HolonEntityMappingValidator` startup check, are in
[the checklist, Sections 2 and 4](docs/new-saas-app-checklist.md#2-entity-mapping-rules-read-this-before-writing-any-entity).

### Step 6 — Wire up self-service sign-up

`tenant-signup`'s `SignupService` orchestrates the whole flow — create the tenant record,
provision the schema, run migrations, create the owner `TenantUser`, and store hashed
credentials — behind one call. You provide exactly one bean:

```java
@Component
public class AppAccountCredentialsStore implements AccountCredentialsStore {

    @Autowired private AccountRepository accountRepo; // your own platform-schema JPA repo
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public boolean existsByEmail(String tenantId, String email) {
        return accountRepo.existsByTenantIdAndEmailIgnoreCase(tenantId, email);
    }

    @Override
    public void save(String tenantId, String email, String encodedPassword) {
        // encodedPassword is already BCrypt-hashed by SignupService — never the raw password
        accountRepo.save(new Account(tenantId, email, encodedPassword));
    }
}
```

```java
@Autowired private SignupService signupService;

public TenantDetails signUp(String companyName, String email, String rawPassword) {
    String slug = SignupService.slugify(companyName); // "Acme Corp!" → "acme-corp"

    SignupRequest request = SignupRequest.builder(slug, email, rawPassword)
            .companyName(companyName)
            .plan(TenantPlan.STARTER) // default plan on signup
            .build();

    return signupService.signup(request); // schema created, migrations run, owner user created
}
```

> **Transaction boundary rule:** never wrap a method spanning both the platform schema and a
> tenant schema in `@Transactional` — a single JDBC connection can't switch schemas
> mid-transaction. `SignupService` already follows this; if you write your own orchestration
> logic, see [the checklist, Section 3](docs/new-saas-app-checklist.md#3-tenantcontext--transaction-boundary-rules)
> before you do.

Reacting to onboarding completion (e.g. seeding default data for the new tenant)?

```java
@EventListener
public void onOnboarded(TenantOnboardingCompletedEvent event) {
    // TenantContext is NOT bound here automatically — bind it yourself before writing
    // to the new tenant's own schema (see checklist Section 3, Rule A).
    TenantContextTemplate.run(event.getTenant(), () ->
        myTenantScopedService.seedDefaults(event.tenantId()));
}
```

### Step 7 — Run it locally

For local dev, use a **file-based** H2 database (not `jdbc:h2:mem:...`) so you can inspect it
with an external tool while the app runs:

```properties
spring.datasource.url=jdbc:h2:file:/absolute/path/to/yourdb;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.username=sa
spring.datasource.password=
```

```bash
mvn spring-boot:run
```

Then exercise the sign-up flow (adjust the path to match your own controller/view):

```bash
curl -X POST http://localhost:8080/signup \
  -H "Content-Type: application/json" \
  -d '{"companyName":"Acme Corp","email":"owner@acme.com","password":"S3cret!23"}'

# Then access tenant-scoped routes under the resolved tenant, e.g.:
curl http://localhost:8080/t/acme-corp/dashboard
```

### Step 8 — Add plans, feature flags, and gate access

```java
@Service
public class ReportingService {

    @RequiresPlan(minimum = TenantPlan.STARTER)          // AOP-enforced
    public List<Report> getReports() { ... }

    @CheckTenantFeature(TenantFeature.ADVANCED_REPORTS)  // AOP-enforced
    public Report generateAdvancedReport() { ... }

    public void customCheck() {
        if (!TenantContext.require().hasFeature(TenantFeature.AUDIT_LOG)) {
            throw new UpgradeRequiredException(TenantFeature.AUDIT_LOG);
        }
    }
}
```

Gate your own UI routes the same way, by calling these same service/`TenantDetails` checks
from your UI framework's navigation guard (`BeforeEnterListener` for Vaadin, a route guard
for React, etc.).

### Step 9 — Add audit, settings, rate limiting, and more as needed

Each concern is a self-contained module you add only when you need it:

```java
@AuditAction(action = "USER_LOGIN", description = "User logged in")
public void login(String userId) { ... }

@RateLimit(limit = 100, window = 60, unit = TimeUnit.SECONDS)
public void apiEndpoint() { ... }
```

See the [module table](#modules) below for what each one covers, and
[`multi-tenant-crm-guide.md`](docs/multi-tenant-crm-guide.md) Steps 7–16 for full walkthroughs
of user management, invitations, billing, cross-tenant security isolation, a super-admin
console, fine-grained permissions, and building your UI layer on top.

### Where to go next

| Doc | Use it for |
|---|---|
| [`docs/multi-tenant-crm-guide.md`](docs/multi-tenant-crm-guide.md) | The complete 16-step feature tutorial (billing, permissions, admin console, UI layer, WhatsApp, ...) |
| [`docs/new-saas-app-checklist.md`](docs/new-saas-app-checklist.md) | Pre-flight checklist of real pitfalls (entity mapping, transaction boundaries, migration naming, go-live checklist) |
| [`docs/vaadin-flow-integration-guide.md`](docs/vaadin-flow-integration-guide.md) | Wiring this framework into a Vaadin Flow front end specifically |

---

## Architecture

```
┌─────────────────────────────────────────┐
│            Your SaaS Application         │
│         (any UI: Vaadin / React /        │
│          REST clients / mobile)          │
├─────────────────────────────────────────┤
│  holon-starter-multi-tenancy             │
│  ┌──────────┐  ┌─────────┐  ┌─────────┐│
│  │  admin   │  │permission│ │ billing ││
│  └──────────┘  └──────────┘  └─────────┘│
│  ┌──────────┐  ┌─────────┐  ┌─────────┐│
│  │ security │  │  audit  │  │ limits  ││
│  └──────────┘  └──────────┘  └─────────┘│
│  ┌──────────┐  ┌─────────┐  ┌─────────┐│
│  │  users   │  │settings │  │ quotas  ││
│  └──────────┘  └─────────┘  └─────────┘│
│  ┌──────────┐  ┌───────────────────────┐│
│  │   data   │  │      onboarding       ││
│  └──────────┘  └───────────────────────┘│
│  ┌──────────────────────────────────────┤
│  │             tenant-core              │
│  └──────────────────────────────────────┘
└─────────────────────────────────────────┘
```

---

## Key Concepts

- **Tenant isolation** — every tenant gets its own database schema (`tenant-data`) plus
  Spring Security context isolation (`tenant-security`); `TenantIsolationFilter` (order -50)
  blocks cross-tenant access with HTTP 403.
- **Plan-based access control & feature flags** — `@RequiresPlan`/`@CheckTenantFeature`,
  covered in [Step 8](#step-8--add-plans-feature-flags-and-gate-access) above.
- **Onboarding** — `TenantOnboardingService.onboard(...)`, used directly by platform
  operators (e.g. a super-admin console) rather than self-service sign-up; see
  [`multi-tenant-crm-guide.md` Step 6](docs/multi-tenant-crm-guide.md#step-6--onboard-new-crm-customers).
- **Audit & rate limiting** — `@AuditAction`/`@RateLimit`, covered in
  [Step 9](#step-9--add-audit-settings-rate-limiting-and-more-as-needed) above.

---

## Building

```bash
# Build and install
mvn clean install

# Run tests only
mvn test

# Skip tests
mvn install -DskipTests
```

---

## Migration from holon-vaadin-flow

If you were previously depending on tenant modules from `holon-vaadin-flow`, update your dependency coordinates:

| Old (holon-vaadin-flow) | New (holon-saas) |
|-------------------------|------------------|
| `com.holon-platform.vaadin:holon-vaadin-flow-tenant-core:12.0.0` | `com.holon-platform.multi-tenancy:holon-multi-tenant-core:1.0.0` |
| `com.holon-platform.vaadin:holon-vaadin-flow-tenant-security:12.0.0` | `com.holon-platform.multi-tenancy:holon-multi-tenant-security:1.0.0` |
| … | … |
| `com.holon-platform.vaadin:holon-vaadin-flow-bom:12.0.0` | `com.holon-platform.multi-tenancy:holon-multi-tenancy-bom:1.0.0` |

Artifact IDs are **unchanged** — only the `groupId` and `version` change.
No Java source code changes are required.
