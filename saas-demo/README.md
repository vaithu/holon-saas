# Holon SaaS :: Demo Application

A small but complete multi-tenant SaaS application built on this workspace's modules, on H2.
It exists to demonstrate seven things end to end:

1. **Tenant sign-up** — self-service, using schema-per-tenant isolation
2. **Tenant login**
3. **Inviting other users** into a workspace
4. **Invited users signing up** by accepting an invitation
5. **Invited users signing in**
6. **View-level authorization** — Alice can open a view Bob cannot, and vice versa
7. **Method-level authorization** — Alice can call a method Bob cannot, and vice versa

## Running it

```
./mvnw -pl saas-demo spring-boot:run
```

Then open <http://localhost:8080>. The database is in-memory, so every restart starts clean.

### The seven-step walkthrough

| Step | What to do | What it demonstrates |
|---|---|---|
| 1 | Go to `/signup`, create workspace `acme` with owner `owner@acme.test` | A `tenant_acme` schema is created and migrated on the fly |
| 2 | Sign in at `/login` as workspace `acme`, `owner@acme.test` | Tenant bound from the authenticated principal |
| 3 | Open **Team**, invite `alice@acme.test` as SALESMAN and `bob@acme.test` as ACCOUNTANT | Invitation records written into the tenant's own schema |
| 4 | Open each generated invitation link and choose a password | Membership + credentials created for the invitee |
| 5 | Sign in as Alice, then (later) as Bob | Both are members of the same workspace |
| 6 | Compare the navigation drawers | Alice sees **Sales**, Bob sees **Accounting** — and neither sees the other's |
| 7 | On each view, press the action button | Alice may record a sale but not post a ledger entry; Bob the reverse |

Because there is no mail server, invitation links are displayed directly on the Team page.

An automated version of exactly this walkthrough lives in `SaasDemoFlowTest`, which drives the
services directly, and in `e2e/SignupToPermissionsE2ETest`, which drives the same journey through
a real browser. The browser journey now runs 26 ordered steps, from sign-up through per-role
permissions, plan gating and the audit trail, to the platform console — cross-tenant listing,
suspending a workspace and proving the other one is unaffected, billing, and the role matrix —
and finally the Internals view, where tenant resolution, cross-tenant refusal, thread
propagation and scoped events are each exercised against the running server.

### Browser end-to-end tests

The end-to-end test needs the application already running, because it talks to it over HTTP like
any other client:

```bash
mvn -pl saas-demo spring-boot:run                        # terminal 1
mvn -pl saas-demo test -Pe2e                             # terminal 2
mvn -pl saas-demo test -Pe2e -De2e.headed=true -De2e.slowMo=500   # ...or watch it happen
```

On PowerShell the `-D` arguments must be quoted, otherwise the shell splits them at the dot and
Maven reads `.headed=true` as a lifecycle phase:

```powershell
mvn -pl saas-demo test -Pe2e "-De2e.headed=true" "-De2e.slowMo=500"
```

The `e2e` profile exists so that an ordinary `mvn test` — which has no server to talk to — skips
these tests rather than failing. Each step screenshots itself into `target/e2e-screenshots/`, so a
failure can be looked at rather than only read about. Every run signs up a fresh workspace, which
is what lets the journey be repeated against a long-running server.

Two things about the demo had to change before a browser could complete the journey, and both are
worth knowing about when building a real application on this framework:

- `MainLayout` needs its own `@PermitAll`. Vaadin checks the access annotations of a route's
  parent layouts as well as the route's own, and refuses the route outright when a layout declares
  none — so every view was denied even though each carried `@PermitAll` itself.
- Vaadin's dev-tools overlay is disabled (`vaadin.devmode.devTools.enabled=false`). It floats above
  the page and absorbs pointer events, so clicks near it never reach the application.

## How permissions are set up

Sign-up seeds the workspace's permission table (`DemoTenantProvisioner`):

| Role | Permissions |
|---|---|
| `OWNER`, `ADMIN` | `*` |
| `MEMBER` | *(none)* |
| `SALESMAN` | `SALES_VIEW`, `SALES_RECORD` |
| `ACCOUNTANT` | `ACCOUNTING_VIEW`, `ACCOUNTING_POST` |

Invitees are given the platform role `MEMBER` and a **job role** of `SALESMAN` or `ACCOUNTANT`.
Permission checks consult the job role first and the platform role second, which is what produces
the asymmetry: neither Alice nor Bob inherits anything from `MEMBER`, so each one holds exactly
the permissions of their job role.

The owner needs the `*` seed for a practical reason: inviting users is itself permission-gated, so
without it the very first administrator could not invite anyone.

## Notable design decisions

**The permission keys contain no colons.** A permission containing `:` is interpreted as
`viewId:ACTION` and routed to the view/action matrix rather than the flat permission store. The
demo uses the flat store, so its keys are plain identifiers.

**The platform tables use their own DataSource, which is deliberately not a Spring bean.**
The framework registers `TenantAwareDataSource` as `@Primary`; it routes to `tenant_<id>` whenever
a tenant is bound. Platform-wide data (the `tenant` registry, and `demo_account` credentials) must
not move with the bound tenant, so it is read through a separate, plain DataSource created inside
`PlatformDataSourceConfig`. That DataSource is *not* exposed as a bean because Holon's JPA
auto-configuration picks the `EntityManagerFactory`'s DataSource by type and does not honour
`@Primary` — with two DataSource beans present it binds to the wrong one and silently sends every
tenant-scoped query to the default schema.

**Spring Boot's Flyway auto-configuration is switched off.** It would bind to the `@Primary`
tenant-aware DataSource. The platform schema is migrated by an explicit `Flyway` bean instead;
per-tenant schemas are migrated by the framework's own `TenantSchemaInitializer`.

**Invitation links carry the workspace id.** Invitations live inside each tenant's schema, so the
token cannot be looked up until the tenant is bound — and the invitee is not logged in and sends no
tenant signal. The workspace therefore travels in the URL: `/accept-invite/{workspace}/{token}`.

**Authorization is checked twice in the UI.** `MainLayout` hides navigation entries the user cannot
use, and each guarded view re-checks the same permission on entry. Hiding a link is usability;
the entry check is the actual access control.

## Framework defects this demo exposed (batch 2)

Assembling the F8-F12 features into a running application surfaced five framework bugs that unit
tests had not caught. All are fixed in the parent modules.

**Enum-backed JPA fields must be declared as `PathProperty<TheEnum>`, never `StringProperty`.**
Hibernate validates a query argument against the *field's* Java type, so binding a `String` to a
field annotated `@Enumerated(EnumType.STRING)` fails at runtime with `QueryArgumentException`.
Fixed in `AuditEntryProperties` (`ACTION`, `SEVERITY`) and `TenantPlanSettingDefaultProperties`
(`PLAN`). `TenantUserProperties.ROLE`/`STATUS` still have this shape but are not yet exercised.

**A `@Transactional` method reached through an un-annotated interface default method bypasses the
proxy.** `TenantSettingsService.set(tenantId, TenantSetting, value)` is a default method, so it is
the proxied entry point; its delegation to the annotated overload is a self-invocation and runs with
no transaction. `DatabaseTenantSettingsService` now overrides it explicitly.

**Quota usage was cached for 30 s and never invalidated.** `QuotaAspect` was `@Before`-only
despite its Javadoc claiming otherwise, so the seat count stayed stale after an invitation was
accepted. It is now `@Around` and evicts the cache on success. `invalidateUsageCache` and
`invalidateUsageCacheForTenant` were also promoted onto the `TenantQuotaService` interface —
previously they existed only on the impl in an `internal` package, making the documented advice
impossible to follow. Any code path that changes a counted resource outside `@EnforceQuota` must
call them; `AcceptInvitationView` now does.

**`@Audited(resourceIdExpression = "#email")` silently resolved to null.** The root POM never
passed `-parameters` to javac, and Spring 6.1 removed `LocalVariableTableParameterNameDiscoverer`,
so named SpEL arguments could not be resolved and every audit entry had an empty resource id. The
root POM now sets `<parameters>true</parameters>` and `AuditAspect` uses Spring's
`DefaultParameterNameDiscoverer`. This affects every annotation in the framework that takes a
SpEL expression over method arguments.

**`AuditDatastoreService` was a dead duplicate** of `DatabaseTenantAuditService` and has been
deleted.

## Framework defects this demo exposed (batch 3)

Batch 3 — the platform console, suspension, billing, the role matrix and the view registry —
turned up four more problems, all of them fixed or worked around in this module.

### `tenant-billing` was non-functional at runtime

`Subscription` and `Invoice` declared only record-style accessors (`tenantId()`,
`plan()`). Holon's `BeanIntrospector` builds its `PropertySet` through
`java.beans.Introspector`, which recognises only `getXxx`/`setXxx`. The introspected
property set was therefore **empty**: every read failed with `Null or empty selection` and
every write persisted nothing. Both classes now carry JavaBean accessors alongside the
record-style ones, with a comment saying why they must not be removed.

### Two more enum-typed properties declared as strings

`SubscriptionProperties.PLAN`/`STATUS` and `InvoiceProperties.STATUS` were
`StringProperty`. Hibernate validates a query argument against the *field's* Java type, so any
filter on them failed. They are now `PathProperty<TenantPlan>`,
`PathProperty<SubscriptionStatus>` and `PathProperty<InvoiceStatus>`, and the helper
signatures moved to the enums with them. `TenantUserProperties.ROLE`/`STATUS` still carry
the same defect and are worth fixing before anyone filters on them.

### `@SecuredView` auto-discovery finds nothing in a Vaadin application

`DefaultViewRegistry` discovers views with
`ApplicationContext.getBeansWithAnnotation(SecuredView.class)`. Vaadin Flow `@Route` classes
are not Spring bean definitions — `SpringInstantiator` autowires a fresh instance per
navigation — so the registry logs `initialized with 0 view resources` however many views are
annotated. `DemoViewRegistration` registers them explicitly by reading the annotation off the
classes. A framework fix would mean scanning the classpath rather than the bean registry.

### Suspension is a refusal to bind, not an error

Neither `AuthenticatedPrincipalTenantContextFilter` nor `TenantContextFilter` throws when a
tenant is suspended: they simply decline to bind it and let the request continue **unbound**. An
authenticated user with no tenant is a state the application has to recognise for itself, which
is what `MainLayout`'s `beforeEnter` and `WorkspaceSuspendedView` do here.

## What batch 4 exposed about the framework

Batch 4 covers the tenant plumbing — resolution, isolation, thread propagation, scoped events
and data strategies. None of it needed a framework fix, but four things are easy to get wrong
and are worth stating plainly.

### The decorator is published but attached to nothing

`TenantCoreAutoConfiguration` creates a `TenantAwareTaskDecorator` bean and wires it onto no
executor, because Spring gives a library no way to retrofit a `TaskDecorator` onto executors
the application defines. An application that never calls `setTaskDecorator` loses the tenant on
every hand-off to a pool — with no exception and no warning, just queries running against the
wrong schema. `DemoAsyncProbe` runs two otherwise identical pools side by side so the
difference is visible rather than asserted.

### `@SecuredView` and `strategy` both look like configuration and are not

`holon.tenant.strategy` selects exactly one resolver, and its `COMPOSITE` value chains only
three of the four — the JWT claim resolver is left out. Declaring the `TenantResolver` bean
directly replaces that choice wholesale, which is what `DemoTenantResolverConfig` does so a
single running application can demonstrate all four and the ordering between them.

### `JwtClaimTenantResolver` does not verify anything

It Base64-decodes the payload and reads the claim, trusting something upstream to have validated
the signature. That is a defensible division of labour and a dangerous thing to forget: on its
own the resolver will believe any tenant id a client cares to encode. The demo forges exactly
such a token to make the point.

### `SHARED` gives you no isolation at all

`TenantSchemaStrategy.SHARED` means one pool for every tenant and a `tenant_id` column — and
the framework adds **no** query predicate, filter or interceptor to enforce it. With `SHARED`
the isolation is entirely the application's to write and entirely the application's to get
wrong. The two isolated strategies route identically; only the provider that builds the pool
differs.

### An in-memory demo log is itself a leak

Both batch-4 logs started out as plain singletons and had to be filtered by the bound tenant
before being shown in a tenant-facing view — otherwise the screen demonstrating tenant scoping
would have been leaking one workspace's activity to another.

## What batch 5 exposed about the framework (F23-F24, F26-F27)

Batch 5 swapped in-memory infrastructure for shared infrastructure: Redis for the tenant cache
and the rate limiter, and a bounded registry for connection pools. Both are features that look
switched on when they are switched off, which is how most of these went unnoticed.

### `@ConditionalOnBean` without `@AutoConfigureAfter` disables a feature silently

Three auto-configurations - the Redis cache, the Redis rate limiter and the pool metrics -
conditioned on a bean that another auto-configuration contributes, without declaring that they
run after it. Conditions are evaluated in auto-configuration order, so all three were tested
too early, found nothing, and never activated. Nothing failed: the application simply kept the
in-memory implementations and registered no gauges.

Module tests did not catch this because `ApplicationContextRunner.withUserConfiguration(...)`
registers the dependency as a *user* bean, which exists before any condition is evaluated. The
tests passed against a wiring arrangement the real application never has. Any
`@ConditionalOnBean` on a bean the application does not define itself needs a matching
`@AutoConfigureAfter`, and a test that proves it in a real context.

### `Cache.evict` is allowed to be asynchronous, and Redis's is

`CachingTenantDetailsLoader.invalidate` called `evict`, which for Redis may return before the
key is gone. Suspending a tenant therefore left a window in which the cache still served
`ACTIVE`. `evictIfPresent` and `invalidate` are the variants that guarantee the operation
landed; the plain ones do not.

### Caching a JPA entity as JSON serialises Hibernate's internals

`GenericJackson2JsonRedisSerializer` writes runtime types into the document, so a lazy
`PersistentSet` went into Redis and came back out as one - throwing
`LazyInitializationException` on read, long after the session had closed. Pinning the
serializer to the target type fixes it, and tolerating unknown fields is then required because
derived read-only getters serialise but cannot be read back.

### Spring parses Redis `INFO` with `Properties.load`

On Windows the reply contains backslash paths, which `Properties.load` reads as escape
sequences and rejects with `Malformed \uxxxx encoding`. Anything needing `INFO` on Windows has
to parse the raw reply.

### The pool registry evicted the busiest tenant first

`BoundedTenantDataSourceRegistry` was documented as an LRU cache and implemented as a FIFO
one: eviction followed insertion order, so the tenant that signed up earliest was always the
first pool closed. That is the wrong tenant almost by definition - the oldest account is usually
the busiest - so at capacity its pool was closed, re-created by the next request and closed
again, turning a memory bound into pool thrashing exactly when the system was most loaded.

The rationale recorded in the tests was that tracking access order would require a lock on every
cache hit. It does not: each entry now carries its own access stamp, written without a lock, and
choosing a victim is an O(n) scan on the cold path where a connection pool is being opened
anyway. The read path is still lock-free and the policy is now the one the javadoc claimed.

### `size()` and `activeTenantIds()` disagree by design

The registry also caches a pool for the `null` tenant, used for platform work before any tenant
is bound. It counts against the ceiling but is deliberately not reported as a tenant, so
`size()` can exceed `activeTenantIds().size()` by one. Both are now documented as such rather
than looking like an off-by-one. Under LRU that pool is also evictable, which is safe only
because the registry re-creates it on demand.

### A failed signup burned the workspace name forever (F26)

`TenantOnboardingFailedEvent` promised in its own javadoc that after a failure "the tenant ID is
available for re-use". It was not. The failure path saved a `DELETED` tombstone, and `exists()`
is `existsById()`, which matches a tombstone perfectly well — so retrying the same name threw
`TenantAlreadyExistsException`. A customer whose signup failed because the database blinked
could never have the name they chose, and nothing would tell them why.

The obvious fix - reclaim any `DELETED` tenant - was rejected. Once an admin can delete a real
tenant, its schema may still hold that customer's data, and reclaiming the name would hand a
stranger the old rows. The narrow fix is to remove the record on the failure path only, because
a tenant that never finished provisioning never became a tenant. `TenantRepository` gained a
`default deleteById` that refuses, so implementations predating it keep working: onboarding
falls back to the tombstone and says so in the log.

### Onboarding inherited the caller's tenant (F26)

This one only appeared once the feature was driven from the UI rather than from a test. The
tenant registry is platform-level data, one table in the platform schema. Onboarding called from
inside a tenant-scoped request had that tenant in scope, so the tenant-aware `DataSource` routed
the registry write into the *calling* tenant's schema and Hibernate reported `Table "TENANT" not
found` - a message that points nowhere near the cause. Under a SHARED-schema strategy it would
have been worse than an error: the write would have succeeded against the wrong rows.
`onboard()` and `isOnboarded()` now run with the tenant context cleared and restore it
afterwards. Leaving that to callers is not an option; forgetting it produces a failure that
blames something else.

### The entity validator ran only by accident (F27)

`HolonEntityMappingValidator` catches the mapping mistakes this framework keeps producing -
notably a Holon entity that has record accessors but no JavaBean getters, which `BeanIntrospector`
silently ignores. It runs at `ApplicationReadyEvent` and is wired by `TenantCoreAutoConfiguration`,
which declared `@AutoConfigureAfter(TenantOnboardingAutoConfiguration.class)` and nothing about
JPA. It worked only because that chain happened to order it after Hibernate. It now names
Hibernate's auto-configuration explicitly.

Worth recording alongside it: the validator acknowledges `TenantDetails#features` and
`#attributes`, both `@ElementCollection`, which would otherwise be flagged because Holon's
`BeanDatastoreHelper` inner-joins the collection table and silently drops parents with an empty
collection. They are safe because `TenantDetails` is only ever read through a plain
`JpaRepository`. The class that does so is called `TenantDetailsDatastoreHelper`, which is
misleading - it wraps a repository, not Holon's Datastore.