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
| `tenant-security` | `holon-multi-tenant-security` | Spring Security integration: `TenantAwareUserDetails`, `TenantIsolationFilter`, AOP guards |
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

## Quick Start

### BOM Import (recommended)
Add the BOM to your project's `<dependencyManagement>`:
```xml
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
```

### All-in-one Starter
```xml
<dependency>
    <groupId>com.holon-platform.multi-tenancy</groupId>
    <artifactId>holon-starter-multi-tenancy</artifactId>
    <version>1.0.0</version>
</dependency>
```
Then add the Spring Boot starters your application needs (e.g., `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`).

### Cherry-pick individual modules
```xml
<!-- Core (always required) -->
<dependency>
    <groupId>com.holon-platform.multi-tenancy</groupId>
    <artifactId>holon-multi-tenant-core</artifactId>
</dependency>

<!-- Add as needed -->
<dependency>
    <groupId>com.holon-platform.multi-tenancy</groupId>
    <artifactId>holon-multi-tenant-security</artifactId>
</dependency>
<dependency>
    <groupId>com.holon-platform.multi-tenancy</groupId>
    <artifactId>holon-multi-tenant-data</artifactId>
</dependency>
```

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

### Tenant Isolation
Every tenant gets its own database schema (via `tenant-data`) and Spring Security context isolation (via `tenant-security`). The `TenantIsolationFilter` (order -50) blocks cross-tenant access with HTTP 403.

### Plan-Based Access Control
```java
// Protect a service method
@RequiresPlan(TenantPlan.PROFESSIONAL)
public void exportReports() { ... }

// Protect any UI layer's own routing/navigation the same way, by calling
// TenantPermissionService / the plan-check APIs from your UI framework's guard hook.
```

### Feature Flags
```java
@CheckTenantFeature(TenantFeature.ADVANCED_REPORTS)
public Report generateAdvancedReport() { ... }
```

### Tenant Onboarding
```java
@Autowired TenantOnboardingService onboarding;

onboarding.onboard(TenantOnboardingRequest.builder()
    .tenantId("acme")
    .plan(TenantPlan.PROFESSIONAL)
    .ownerEmail("owner@acme.com")
    .build());
```

### Audit Trail
```java
@AuditAction(action = "USER_LOGIN", description = "User logged in")
public void login(String userId) { ... }
```

### Rate Limiting
```java
@RateLimit(limit = 100, window = 60, unit = TimeUnit.SECONDS)
public void apiEndpoint() { ... }
```

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

## CRM walkthrough moved

The long CRM walkthrough has been moved to a dedicated guide:

- [`docs/multi-tenant-crm-guide.md`](docs/multi-tenant-crm-guide.md)

Keep `README.md` for the project overview, module list, quick start, and migration notes.
