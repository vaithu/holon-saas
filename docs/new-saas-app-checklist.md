# New SaaS App Starter Checklist

This is a **pre-flight checklist**, not a tutorial — for the step-by-step "how do I wire up
feature X" guide, see [multi-tenant-crm-guide.md](./multi-tenant-crm-guide.md). Read (or at
least skim) this document *before* writing your first entity or signup flow. Every single
item below maps to a real bug that cost real debugging time building `crm-minimal`, the
framework's own reference app — this checklist exists so your app doesn't have to rediscover
each one from scratch.

## Table of Contents

- [1. Dependencies & properties template](#1-dependencies--properties-template)
- [2. Entity mapping rules (read this before writing any entity)](#2-entity-mapping-rules-read-this-before-writing-any-entity)
- [3. TenantContext & transaction boundary rules](#3-tenantcontext--transaction-boundary-rules)
- [4. Migration & table-naming conventions](#4-migration--table-naming-conventions)
- [5. Signup flow checklist](#5-signup-flow-checklist)
- [6. Choosing Holon Datastore vs. plain JPA for a new entity](#6-choosing-holon-datastore-vs-plain-jpa-for-a-new-entity)
- [7. Local dev environment (H2)](#7-local-dev-environment-h2)
- [8. Go-live checklist](#8-go-live-checklist)

---

## 1. Dependencies & properties template

Start from [multi-tenant-crm-guide.md Step 1](./multi-tenant-crm-guide.md#step-1--add-the-dependency)
for the Maven coordinates. Here is a complete, copy-paste `application.properties` skeleton
with every property this checklist assumes, annotated with *why* each one matters (all of
these were discovered the hard way — a missing one produces a working-looking app that fails
the moment a real user signs up):

```properties
# ─── DataSource ─────────────────────────────────────────────────────────────
# Your real DB driver/URL goes here (Postgres/etc. in production — see the
# holon.tenant.data.dialect note below for which dialects are actually supported).
# For local H2 dev, see Section 7 — do NOT use jdbc:h2:mem:... if you ever need
# to inspect the database with an external tool (DBeaver, etc.) while the app runs.

# ─── JPA ─────────────────────────────────────────────────────────────────
# Schema is owned by Flyway (framework migrations + your own) — Hibernate must
# never auto-generate DDL, or it will silently fight Flyway over table ownership.
spring.jpa.hibernate.ddl-auto=none
spring.jpa.open-in-view=false

# ─── Flyway ──────────────────────────────────────────────────────────────
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

# ─── Holon Tenant Resolution ─────────────────────────────────────────────
# Pick ONE strategy (see multi-tenant-crm-guide.md Step 2). PATH shown here.
holon.tenant.strategy=PATH
holon.tenant.path-prefix=/t/
holon.tenant.required=true
# MUST include your public/unauthenticated routes — signup and login run
# with NO tenant resolved yet (see Section 3, this is the #1 source of
# "Table ... not found" errors). Also include your framework's static-asset
# and health-check paths.
holon.tenant.ignored-paths=/,/login,/signup,/actuator/**,/public/**,/error,/VAADIN/**,/frontend/**,/vaadinServlet/**,/sw.js,/favicon.ico,/manifest.webmanifest,/offline.html,/robots.txt

# ─── Holon Tenant Data (schema-per-tenant isolation) ─────────────────────
holon.tenant.data.schema-strategy=SCHEMA_PER_TENANT
# Match your REAL database here — H2 for local dev (see Section 7), POSTGRESQL for
# production. MYSQL is NOT a supported value — the framework's bundled tenant-schema
# migration scripts use syntax MySQL doesn't support (confirmed via a real
# Testcontainers-backed test); see DatabaseDialect's Javadoc if you need MySQL support
# badly enough to fix that yourself. Getting the dialect wrong produces dialect-specific
# SQL syntax errors (e.g. Postgres' "SET search_path" sent to an H2 database) that only
# surface once schema switching actually runs.
holon.tenant.data.dialect=H2

# ─── Virtual Threads (required for the concurrency this platform is designed for) ─────
# This starter's high-concurrency design (Virtual Thread-optimized tenant permission/user
# resolution caches, TenantAwareTaskDecorator, etc.) assumes requests are dispatched on
# Virtual Threads, not the servlet container's default bounded platform-thread pool
# (Tomcat's default is ~200 threads — nowhere near enough for thousands of concurrent
# users). Requires Java 21+. If this is missing or false, HolonSaasAutoConfiguration logs
# a WARN at startup to catch it before it becomes a silent production capacity ceiling.
spring.threads.virtual.enabled=true
```

### Required beans

| Bean | Interface | Notes |
|---|---|---|
| `AccountCredentialsStore` | `com.holonplatform.multitenant.signup.AccountCredentialsStore` | Your own platform-schema login-credentials table. See [Section 5](#5-signup-flow-checklist). The only piece of application-specific code this checklist can't eliminate. |
| `PasswordEncoder` | `org.springframework.security.crypto.password.PasswordEncoder` | Usually already present for Spring Security. |

**You do NOT need to write these yourself** — they're now auto-configured with sensible defaults:

| Bean | Auto-configured by | What you'd only override for |
|---|---|---|
| `TenantDetailsLoader` | `tenant-onboarding` (delegates to its own `TenantRepository`) | A completely different tenant store, or one backed by a cache. |
| `com.holonplatform.jdbc.TenantDataSourceProvider` | `tenant-data` (built from your existing `spring.datasource.*` properties) | Per-tenant credentials from a secrets manager, or any connection detail `spring.datasource.*` can't express. |
| `ActorResolver` (audit) / `AuditorAware<String>` (JPA `@CreatedBy`/`@LastModifiedBy`) | `tenant-audit`'s `SecurityContextActorResolver` (one bean satisfies both, named `securityContextActorResolver`) | A non-Spring-Security actor source. To enable JPA entity auditing, you still need one line of your own: `@EnableJpaAuditing(auditorAwareRef = "securityContextActorResolver")`. |

If you copied hand-written versions of any of these into an app built against an older
`holon-saas` version (a common pattern before these defaults existed), delete them — the
defaults back off automatically (`@ConditionalOnMissingBean`) if you ever do need to keep a
custom one.

---

## 2. Entity mapping rules (read this before writing any entity)

Holon's Datastore introspects your entity's bean shape (getters/setters/fields) at
**runtime**, not compile time. Every rule below produces code that compiles fine and looks
completely normal, then fails (or silently loses data) the first time a real user triggers
the affected path.

**Do this for every `@Entity` class that is (or might be) saved via Holon's Datastore**
(directly, or through a `BeanDatastoreHelper`/`*DatastoreHelper` wrapper):

- [ ] **Every persisted property has both a public getter AND a public setter** (or is a
  public field). A getter-only property **that has a backing field** (e.g.
  `private Instant createdAt = Instant.now();` with no `setCreatedAt(...)`) fails at
  save-time with:
  ```
  PropertyReadException: No write method and no accessible field available to write property
  ```
  A purely computed getter with **no backing field at all** (e.g. `isActive()` deriving
  from other fields) is safe automatically — `TenantCoreBeanPropertyFix` (`tenant-core`)
  registers a JVM-wide rule excluding exactly that shape from Holon's Datastore write path,
  for every class, unconditionally. `HolonEntityMappingValidator` only flags the
  field-backed case as an error; the no-field case is reported at `Severity.INFO` (logged at
  `DEBUG`, not `WARNING`/`ERROR`) purely for transparency, since nothing needs fixing.
- [ ] **The no-arg constructor is `public`** (not `protected`/package-private). Holon's
  `JpaSave` instantiates entities via `Class.newInstance()`, a plain reflective call that
  enforces normal Java access control — a non-public constructor fails at runtime with
  `IllegalAccessException`.
- [ ] **No `@ElementCollection` fields** (`Map`/`Set`/`List` mapped to a separate join table).
  Holon's `BeanDatastoreHelper` always projects the whole bean in one JPQL query, referencing
  every property (including collection-valued ones) directly in the `SELECT` list — Hibernate
  always turns that into an **inner join**, which **silently drops the entire parent row**
  whenever the collection happens to be empty (the common case for a freshly-created entity).
  Not an exception — just missing data, indistinguishable from "the row doesn't exist".
  **Fix:** store the collection as a single JSON-serialized text column via a JPA
  `AttributeConverter` instead (see `StringMapJsonConverter` in `tenant-core` for a
  ready-to-reuse example) — or use a plain `JpaRepository`/`EntityManager` for this entity
  instead of Holon's Datastore (see [Section 6](#6-choosing-holon-datastore-vs-plain-jpa-for-a-new-entity)).
  This is a genuine `holon-core` bug, not something specific to this framework — see
  [upstream-holon-core-elementcollection-bug-report.md](./upstream-holon-core-elementcollection-bug-report.md)
  for the ready-to-file writeup.
- [ ] **Don't rely on `@Transient` to exclude a computed property.** It does not do what you
  expect: `holon-jpa-bean-processors` maps `@Transient` to `IgnoreMode.IGNORE_INCLUDE_NESTED`
  (meant to suppress a *nested bean's own sub-properties*) — a no-op for an ordinary scalar
  getter. A property you *intend* to exclude (e.g. a computed `isActive()` with no backing
  field) needs either a real setter, or an explicit `BeanPropertyPostProcessor` applying
  `IgnoreMode.IGNORE_PROPERTY` (see `TenantCoreBeanPropertyFix` in `tenant-core` for the
  pattern this framework uses for its own such properties).

**Automate this instead of manually re-checking it every time:** add one test using
[`HolonEntityMappingValidator`](../tenant-core/src/main/java/com/holonplatform/multitenant/testing/HolonEntityMappingValidator.java)
(ships in `tenant-core`), listing every entity your app persists via Holon's Datastore:

```java
@Test
void allEntitiesAreHolonDatastoreSafe() {
    HolonEntityMappingValidator.assertValid(CrmAccount.class, /* ...all your other entities */);
}
```

This turns every rule above into a **build-time failure with an actionable message**,
instead of a runtime crash the first time a real user hits the code path.

**Even better — this now also runs automatically at startup, for free.** `tenant-core`
registers `EntityMappingValidationRunner`, which discovers every entity via your
`EntityManagerFactory`'s own metamodel and runs `HolonEntityMappingValidator` against all of
them at `ApplicationReadyEvent` — no dedicated test required, though writing one is still
recommended (it fails your build, not just your running app). It automatically acknowledges
`holon-saas`'s own framework-shipped entities that are known-safe despite matching one of the
check patterns (e.g. `TenantDetails#features`/`#attributes`, safe because that entity is
persisted via a plain `JpaRepository`, never Holon's Datastore) — you should never see an
error against an entity you didn't write yourself. If you ever do, that's a bug in this
validator, not in your code. Controlled by `holon.tenant.entity-validation.*` properties:

```properties
# Default true — set false to disable the automatic startup check entirely.
holon.tenant.entity-validation.enabled=true
# Default false (log-only). Set true in dev/CI profiles to fail startup immediately
# instead of only logging an ERROR — catches the bug at boot, not downstream at save-time.
holon.tenant.entity-validation.fail-fast=true
# Intentionally-excluded computed properties, same format as HolonEntityMappingValidator's
# acknowledgedProperties parameter (fully.qualified.ClassName#propertyName).
holon.tenant.entity-validation.acknowledged-properties=com.example.MyEntity#computedThing
```

---

## 3. TenantContext & transaction boundary rules

This is the single biggest source of "works in dev, breaks in front of a user" bugs in this
framework. Two hard rules:

### Rule A — `TenantAwareDataSource` routes by `ThreadLocal`, never by method arguments

Every tenant-scoped write goes through `TenantAwareDataSource`, which resolves the target
schema purely from `TenantContext.peekTenantId()` — a thread-local read, completely
independent of any `tenantId` parameter your own method signature might have. If no tenant is
bound, it silently falls back to the platform/bootstrap connection — **no error is thrown**,
just wrong-schema routing that surfaces later as `Table "..." not found` for a tenant-only
table.

`TenantContext` is bound automatically by `TenantContextFilter` for ordinary authenticated
requests — but **not** for:
- Your signup/login routes (deliberately on `holon.tenant.ignored-paths` — no tenant exists
  yet for the filter to resolve against).
- Any `@EventListener` reacting to `TenantOnboardingCompletedEvent` — Spring's
  `ApplicationEventPublisher` is **synchronous by default**, so your listener runs *during*
  `TenantOnboardingService.onboard(...)`, before any signup-flow code has bound
  `TenantContext` for the new tenant.
- Any background job, scheduled task, or async executor.

**Rule of thumb:** any code that writes to a tenant's schema, running outside a normal
authenticated HTTP request, must bind it explicitly. Use `TenantContextTemplate`
(`tenant-core`, `com.holonplatform.multitenant`) instead of hand-writing the
`set()`/`try`/`finally`/`clear()` boilerplate — it also correctly *restores* whatever tenant
was previously bound (rather than blindly clearing), which matters if this ever runs nested
inside an already-tenant-scoped call:

```java
TenantContextTemplate.run(tenant, () -> {
    tenantScopedService.doSomething(tenantId);
});
```

This applies to **your own onboarding-event listeners**, not just framework code — e.g.:

```java
@EventListener
public void onOnboarded(TenantOnboardingCompletedEvent event) {
    TenantDetails tenant = event.getTenant();

    // Platform-schema writes (e.g. via a plain JpaRepository) do NOT need TenantContext.
    tenantAdminService.save(tenant.toBuilder()./* ... */.build());

    // Anything writing to the NEW TENANT'S OWN schema does need it:
    TenantContextTemplate.run(tenant, () -> pipelineService.createDefaultStages(event.getTenantId()));
}
```

**Extra safety net (not a substitute for the above):** `TenantOnboardingCompletedEvent`
implements `com.holonplatform.multitenant.event.TenantScopedEvent`. If your app has AspectJ on
the classpath (e.g. you already added `spring-boot-starter-aop`/`aspectjweaver` for
`tenant-audit`/`tenant-quotas`/`tenant-limits`/`tenant-permissions`), `tenant-core`
auto-registers `TenantScopedEventListenerAspect`, which binds `TenantContext` to
`event.getTenant()` automatically around **any** `@EventListener` method receiving a
`TenantScopedEvent` — including your own custom events, if you implement the same interface.
This only helps when AOP auto-proxying is actually active and your listener's first parameter
is the event itself — keep binding explicitly with `TenantContextTemplate` regardless, since it
always works with zero extra dependencies.

### Rule B — never `@Transactional` a method that spans both the platform schema and a tenant schema

A single JDBC connection/transaction cannot switch schemas mid-transaction. If your signup
(or similar) method starts with a platform-schema check (e.g.
`credentialsStore.existsByEmail(...)`) and later writes to the newly-created tenant's schema,
wrapping the whole method in `@Transactional` will pin the connection to whichever schema
resolved *first* — silently ignoring any later `TenantContext.set(...)` call. The symptom is
identical to Rule A's violation (`Table "..." not found`), but the root cause and fix are
different: **remove the outer `@Transactional`**, and let each step (platform check →
onboarding → tenant-user creation → credential storage) commit independently. This does
sacrifice cross-schema atomicity — a failure partway through can leave a provisioned tenant
with no owner user — but that trade-off is architecturally unavoidable without distributed/XA
transactions.

**Verify your own signup/onboarding flow with a real, non-mocked test** — a test that
constructs your service via plain `new(...)` will never catch this (no Spring AOP proxy = no
`@Transactional` behavior applied at all, masking the bug). Wire it as a genuine Spring bean,
with a real JPA-backed `AccountCredentialsStore`, against a real database, asserting the
tenant-scoped data actually landed in the tenant's own schema.

---

## 4. Migration & table-naming conventions

`holon-saas` ships its own Flyway migrations on the shared classpath location
`db/tenant-migration/` (auto-discovered by Flyway across every JAR on the classpath — yours
and the framework's, together):

| Framework script | Tenant-schema tables it creates |
|---|---|
| `V1__tenant_baseline.sql` | (legacy placeholder tables — not entity-backed) |
| `V1.1__tenant_framework_module_tables.sql` | `tenant_users`, `audit_entries`, `tenant_billing_invoice`, `tenant_billing_subscription`, `user_invitation`, `whatsapp_conversation`, `whatsapp_message`, `tenant_permissions`, `tenant_permission_overrides`, `tenant_custom_roles`, `tenant_setting_override`, `tenant_plan_setting_default` |

The full list above is also available as a compile-time-checkable Java constant —
`FrameworkReservedTableNames.ALL` (`tenant-core`) — kept mechanically in sync with the actual
migration scripts by a dedicated test in `tenant-onboarding`. Use it directly in your own test
suite instead of copy-pasting the table names:

```java
@Test
void myAppTablesDoNotCollideWithFrameworkTables() {
    Set<String> myTables = Set.of("crm_account", "crm_contact", "crm_opportunity");
    assertThat(myTables).doesNotContainAnyElementsOf(FrameworkReservedTableNames.ALL);
}
```

- [ ] **Number your own tenant-schema scripts `V2` / `V002` or higher** — never `V1`/`V1.x`.
  Flyway normalizes `V2` and `V002` to the *same* version identifier, so the framework
  reserves the whole `V1`/`V1.x` range specifically so it can never collide with any app's own
  numbering, no matter which of the two common conventions (`V2` vs `V002`) you use.
- [ ] **Never name your own tenant-schema table any of the names in the table above** —
  they already exist in every tenant schema. Generic domain terms are the highest-risk
  collision (this framework itself once shipped bare `invoice`/`subscription` table names
  before renaming them to `tenant_billing_invoice`/`tenant_billing_subscription` specifically
  to avoid this).
- [ ] **The platform-schema `tenant` table (backing `TenantDetails`) is *not* bundled by the
  framework at all** — your app must create it yourself, in your own platform-schema
  migration, with columns matching `TenantDetails`' exact `@Column` mappings (`tenant_id`,
  `name`, `plan`, `status`, `locale`, `timezone`, `theme`, plus `features`/`attributes` if
  you're on a `holon-saas` version where `TenantDetails` maps those as
  `@ElementCollection` join tables — check your version's actual entity source, since this
  mapping has changed at least once).
- [ ] **Quote schema/table identifiers consistently across your own DDL and Flyway's own
  handling** if you ever write raw schema-creation SQL yourself — H2 (and Oracle) uppercase
  *unquoted* identifiers, while Flyway's own internal handling preserves whatever exact case
  string you pass it, which can otherwise create two different phantom schema objects with
  the same apparent name.

---

## 5. Signup flow checklist

- [ ] `AccountCredentialsStore` implementation stores credentials in the **platform** schema
  (a shared, cross-tenant table) — never the tenant's own schema (there's no tenant to scope
  it to yet at signup time, and no other tenant should ever collide on email uniqueness with
  it).
- [ ] Your `SignupService`/`DefaultSignupService`-equivalent binds `TenantContext` around the
  post-onboarding tenant-user creation steps (see [Section 3, Rule A](#3-tenantcontext--transaction-boundary-rules)).
- [ ] The signup method itself is **not** `@Transactional` (see [Section 3, Rule B](#3-tenantcontext--transaction-boundary-rules)).
- [ ] Any `@EventListener` on `TenantOnboardingCompletedEvent` that writes tenant-scoped data
  also binds `TenantContext` itself — it does **not** inherit any binding from the code that
  triggered onboarding.
- [ ] Write one real, end-to-end signup test (real database, real Spring-managed beans, real
  `@Transactional` semantics) rather than only a unit test with hand-constructed objects —
  see [Section 3, Rule B](#3-tenantcontext--transaction-boundary-rules) for why this
  distinction matters.
- [ ] If you write your own `SignupService`-equivalent, make it **safe to call again with the
  identical request** after a partial failure (tenant provisioned, but owner-user creation or
  credential storage didn't complete) — `DefaultSignupService` does this by catching the
  onboarding service's "tenant already exists" error, checking the existing tenant is
  `ACTIVE`, and skipping owner-user creation if a user with that email already exists (so
  retrying never creates a duplicate owner). Without this, a transient failure permanently
  strands the tenant with no way to log in and no clean way to retry.

---

## 6. Choosing Holon Datastore vs. plain JPA for a new entity

Most entities are fine through Holon's Datastore (`BeanDatastoreHelper`) as long as they pass
[`HolonEntityMappingValidator`](#2-entity-mapping-rules-read-this-before-writing-any-entity).
Consider a plain `JpaRepository`/`EntityManager` instead when:

- The entity is read on **every single request** (e.g. the platform's own tenant-lookup
  entity) — Holon's Datastore query-building overhead is unnecessary for a simple
  primary-key lookup, and a plain repository sidesteps the entire `@ElementCollection`
  inner-join risk class regardless of the entity's own field shapes.
- You need to construct the repository **imperatively inside an `@AutoConfiguration`** class
  (bypassing `@EnableJpaRepositories` component scanning, e.g. because the entity lives
  outside any consuming application's own base package). If you do this, use
  **`HolonSaasRepositoryFactory.create(YourRepository.class, entityManagerFactory,
  beanFactory)`** (`tenant-core`) rather than reinventing it — it wraps
  `JpaRepositoryFactoryBean` correctly (never a bare `JpaRepositoryFactory`, which skips
  Spring Data's transactional-proxy wiring entirely and makes every write fail at runtime
  with):
  ```
  InvalidDataAccessApiUsageException / TransactionRequiredException:
  No EntityManager with actual transaction available for current thread - cannot reliably process 'merge' call
  ```
  (`TenantDetailsRepositoryFactory` in `tenant-core` is a one-line wrapper around this same
  factory — a working reference for the pattern).

---

## 7. Local dev environment (H2)

- [ ] **Use a file-based H2 database with `AUTO_SERVER=TRUE`**, not `jdbc:h2:mem:...`, if you
  ever want to inspect the live database with an external tool (DBeaver, etc.) while the app
  is running:
  ```properties
  spring.datasource.url=jdbc:h2:file:/absolute/path/to/yourdb;AUTO_SERVER=TRUE;DB_CLOSE_ON_EXIT=FALSE
  ```
  In-memory H2 databases are isolated *per JVM process* — a separate tool connecting to
  `jdbc:h2:mem:...` gets its own brand-new empty database, not your running app's data. Use
  an **absolute** path for both your app's `application.properties` and any external tool's
  connection string, so both resolve to the identical file regardless of each process's
  working directory.
- [ ] **Match your external tool's H2 driver jar version to your app's own H2 version**
  (check with `mvn dependency:tree | grep h2database`). A mismatched driver (e.g. an older
  version bundled with DBeaver) fails to open a newer database file with:
  ```
  The write format 3 is larger than the supported format 2
  ```
  Point the tool at the exact jar in your local Maven repository
  (`~/.m2/repository/com/h2database/h2/<version>/h2-<version>.jar`) instead of relying on its
  bundled default.
- [ ] **Don't bother with Spring Boot's embedded H2 console if you're also using Vaadin +
  Spring Security** — the interaction between Vaadin's security configurer, CSRF, and frame
  options is fiddly to get right, and a file-based DB + external tool (above) is simpler and
  more reliable anyway.

---

## 8. Go-live checklist

Quick summary — before shipping, confirm:

- [ ] `HolonEntityMappingValidator.assertValid(...)` passes for every entity you persist via
  Holon's Datastore. Consider setting `holon.tenant.entity-validation.fail-fast=true` in your
  CI/staging profile so the automatic startup check fails the deploy instead of only logging.
- [ ] Your own tenant-schema migrations start at `V2`/`V002` or higher, and don't collide with
  any framework-reserved table name (Section 4) — check mechanically against
  `FrameworkReservedTableNames.ALL` rather than eyeballing the table above.
- [ ] `holon.tenant.data.dialect` matches your **production** database, not just your local
  dev H2 setup.
- [ ] Every `@EventListener` reacting to a tenant-onboarding event has been reviewed for
  correct `TenantContext` binding (Section 3) — don't rely solely on
  `TenantScopedEventListenerAspect` being active.
- [ ] Your signup/onboarding orchestration method is not wrapped in an outer `@Transactional`
  spanning both platform and tenant schemas.
- [ ] You have at least one real, end-to-end signup integration test (not just unit tests with
  hand-constructed, non-Spring-managed objects).
- [ ] `spring.threads.virtual.enabled=true` is set (Java 21+) — check the app log at startup
  for `HolonSaasAutoConfiguration`'s WARN; without it, the platform-thread pool (not Virtual
  Threads) caps your real concurrency far below what the caching/service design assumes.
- [ ] Add `spring-boot-starter-actuator` (or any dependency that brings `micrometer-core`) so
  per-tenant HikariCP pools self-report `hikaricp.*` metrics (tagged by pool, i.e. by tenant) —
  otherwise hundreds of dynamically-created pools are a complete observability blind spot, and
  pool exhaustion for one tenant surfaces only as unexplained request timeouts. Also watch
  `holon.tenant.datasource.pools.active` vs. `.pools.max` — approaching the configured
  `holon.tenant.data.max-pools` bound means active tenants are being LRU-evicted and
  re-created, adding pool-creation latency to affected requests (see
  `BoundedTenantDataSourceRegistry`'s "10k-user sizing guidance").
- [ ] For deployments expecting high concurrency across **multiple app instances** behind a
  load balancer, be aware the tenant-permission caches are still per-instance (in-memory) —
  each instance independently re-resolves permissions after its own cache TTL expires.
  `TokenBucketRateLimiter` (the default rate limiter) is also per-instance and now
  memory-bounded (idle buckets are evicted; capacity/eviction are configurable via
  `TokenBucketRateLimiter(Duration idleEviction, long maxBuckets)`), but a tenant's effective
  rate limit still scales with instance count. If you need a single enforced limit shared
  across all instances, add `tenant-cache-redis` and a `StringRedisTemplate` bean
  (`spring.data.redis.*` properties) — its `TenantRateLimitRedisAutoConfiguration`
  auto-activates a Redis-backed `RedisTenantRateLimiter` that all instances share, the same
  way `tenantDetailsCache` upgrades to Redis when available.
