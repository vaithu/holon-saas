# Vaadin Flow + Holon Auth Integration Guide

This guide shows how to put a **Vaadin Flow** UI on top of the Holon SaaS multi-tenant
framework, with **Holon Auth** driving route authorization and **Spring Security** managing
the login session.

It reflects the framework's built-in support for single-page UIs. Four concerns that early
adopters had to hand-wire in their application are now handled by the library out of the box:

| Concern | Handled by | Property (default) |
|---------|-----------|--------------------|
| A global `AuthContext` so `AuthContext.require()` resolves in route guards | `springSecurityAuthContext` bean, with `SpringSecurityAuthContextRegistrar` as a fallback (tenant-security) | `holon.tenant.security.auth-context-registration-enabled` (`true`) |
| Binding `TenantContext` from the authenticated user on UIDL/heartbeat POSTs | `AuthenticatedPrincipalTenantContextFilter` (tenant-security) | `holon.tenant.security.principal-fallback-enabled` (`true`) |
| Seeding the session tenant cache for plain (non-`/t/`) routes | `SessionCachingTenantResolver.cacheTenant(...)` helper (tenant-core) | — |
| Avoiding a hard `400` when a request carries no tenant identifier | `TenantContextFilter` defer option (tenant-core) | `holon.tenant.defer-missing-tenant-to-chain` (`false`) |

> The result: your login code no longer builds an `AuthContext` by hand, and plain in-app
> routes resolve the tenant automatically after sign-in.

## Table of Contents

- [Why Vaadin needs special handling](#why-vaadin-needs-special-handling)
- [Step 1 — Choose plain routes over `/t/{id}/` prefixes](#step-1--choose-plain-routes-over-tid-prefixes)
- [Step 2 — Let the framework publish the AuthContext](#step-2--let-the-framework-publish-the-authcontext)
- [Step 3 — Authenticate at login and seed the tenant cache](#step-3--authenticate-at-login-and-seed-the-tenant-cache)
- [Step 4 — Let UIDL requests bind the tenant from the principal](#step-4--let-uidl-requests-bind-the-tenant-from-the-principal)
- [Step 5 — Keep the login/landing pages reachable](#step-5--keep-the-loginlanding-pages-reachable)
- [Filter ordering reference](#filter-ordering-reference)
- [Secure a view with Holon Auth](#secure-a-view-with-holon-auth)
- [Troubleshooting](#troubleshooting)

## Why Vaadin needs special handling

A Vaadin Flow app is a single page. After the initial navigation, the browser talks to the
server through **UIDL** and **heartbeat** POSTs to the servlet root (`/`). Those requests
carry no tenant signal in the URL — no `/t/{id}/` prefix, no subdomain, no tenant header.
Two things follow:

1. **The tenant must be recovered from something other than the URL** — either a session-cached
   value or the authenticated principal.
2. **Route authorization runs on those request threads.** The Holon Vaadin navigator's
   `DefaultNavigationTargetBeforeEnterListener` calls `AuthContext.require()` for
   `@PermitAll` / `@RolesAllowed` / `@RequiresPermissions` views. If no `AuthContext` is
   published, authenticated views render blank.

The framework now addresses both without application code.

## Step 1 — Choose plain routes over `/t/{id}/` prefixes

`PathPrefixTenantResolver` *reads* a `/t/{id}/` prefix but never rewrites the request URI, so
a plain `@Route("customers")` view cannot be matched under `/t/acme/customers`. For a Vaadin
Flow app, prefer **plain routes** plus a **session-cached tenant**:

```java
@Route(value = "customers", layout = MainLayout.class)
@RolesAllowed("USER")
public class CustomerListView extends VerticalLayout { /* ... */ }
```

Make sure `SessionCachingTenantResolver` is in your resolver chain (it is registered by
default). It caches the tenant in the HTTP session and returns it on later requests that carry
no tenant prefix — which is every in-app request for a plain-route Vaadin UI.

## Step 2 — Let the framework publish the AuthContext

Nothing to do — as long as `tenant-security` and Spring Security are on the classpath, the
framework publishes a Holon `AuthContext` backed by Spring's `SecurityContextHolder`.
`AuthContext.getCurrent()` / `AuthContext.require()` then resolve on any thread that carries an
authenticated Spring Security context, including Vaadin's UIDL request threads.

How it is published:

- **Primary — a Spring bean.** `TenantSecurityAutoConfiguration` exposes an `AuthContext` bean
  (`springSecurityAuthContext`), resolved through Holon's Spring `BeanFactory` context scope. This is
  what a Holon-Spring app (including a Holon Vaadin Flow app) uses. It backs off with
  `@ConditionalOnMissingBean(AuthContext.class)`, so if your app already defines its own
  `AuthContext` bean, yours wins.
- **Fallback — the classloader scope.** `SpringSecurityAuthContextRegistrar` publishes the same
  `AuthContext` into Holon's classloader context scope for Holon contexts that have no Spring
  bean-context scope registered. Publication is non-destructive (`putIfAbsent`).

> **Why not the classloader scope alone?** In a Holon-Spring application, `AuthContext.require()`
> resolves context resources through the registered Spring `BeanFactory` scope, not the classloader
> scope — so a classloader-scope-only registration is not seen by the Vaadin navigator's route
> guards. The `AuthContext` bean closes that gap; the registrar remains for non-Spring Holon
> contexts.

To opt out (for example, if you bind your own `AuthContext` a different way):

```yaml
holon:
  tenant:
    security:
      auth-context-registration-enabled: false
```

> **Before this framework support**, apps built an authenticated `AuthContext` from a
> hand-written `Realm` at login and stored it under `AuthContext.CONTEXT_KEY` on the
> `VaadinSession`. That code is no longer necessary.

## Step 3 — Authenticate at login and seed the tenant cache

Your login page runs *before* any tenant is bound, so authenticate with a pre-verified token
rather than going through `AuthenticationManager` (which would need `TenantContext` already
set). After a successful sign-in, seed the session tenant cache with the
`SessionCachingTenantResolver.cacheTenant(...)` helper:

```java
private void signIn(TenantDetails tenant, CrmAccount account, HttpSession session) {
    UserDetails principal = TenantUserDetails.builder(account.getEmail(), tenant)
            .password(account.getPasswordHash())
            .role("USER")
            .build();

    // Password already verified above → build a pre-authenticated token directly.
    var authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    // Seed the session tenant cache so later plain-route requests resolve this tenant.
    SessionCachingTenantResolver.cacheTenant(session, tenant.tenantId());
}
```

`cacheTenant` validates its arguments (non-null session, non-blank tenant id) and writes the
canonical session attribute — no need to touch `SESSION_ATTRIBUTE` directly. On logout, call
`SessionCachingTenantResolver.clearCachedTenant(session)`.

No `bindHolonAuthContext(...)` call is needed — Step 2 handles the `AuthContext`.

## Step 4 — Let UIDL requests bind the tenant from the principal

For any authenticated request that still cannot resolve a tenant from the request itself,
`AuthenticatedPrincipalTenantContextFilter` binds `TenantContext` from the signed-in principal
(via `TenantAwareUserDetails.getTenantId()` → `TenantDetailsLoader.load(...)`). This runs
**after** Spring Security has populated the `SecurityContextHolder`, so the principal is
available.

It is enabled by default and fires only when:

- no `TenantContext` is already bound for the request, **and**
- a user is authenticated, **and**
- a `TenantDetailsLoader` bean is present.

The binding is additive and safe — `TenantIsolationFilter` still validates that the resolved
tenant matches the user's tenant. Disable if you don't want it:

```yaml
holon:
  tenant:
    security:
      principal-fallback-enabled: false
      principal-fallback-filter-order: -60   # after Spring Security (-100), before isolation (-50)
```

## Step 5 — Keep the login/landing pages reachable

The login, sign-up and Vaadin static-resource routes must be reachable with no tenant. Two
options:

**Option A (recommended): keep them in `ignored-paths`.**

```yaml
holon:
  tenant:
    required: true
    ignored-paths:
      - /
      - /login
      - /signup
      - /VAADIN/**
      - /HILLA/**
```

**Option B: defer instead of `400`.** When `required: true` and a request carries no tenant
identifier at all, `TenantContextFilter` returns `400` by default. If you would rather let such
requests continue down the chain (so an authenticated one can still be bound in Step 4, or a
public page can render), enable the defer option:

```yaml
holon:
  tenant:
    required: true
    defer-missing-tenant-to-chain: true   # no tenant identifier → continue instead of 400
```

> `defer-missing-tenant-to-chain` applies **only** to the "no tenant identifier at all" case. An
> *invalid* tenant (unknown id, inactive tenant) still produces the normal `404` / `403`.

## Filter ordering reference

| Order | Filter | Provided by |
|------:|--------|-------------|
| `-100` | Spring Security `FilterChainProxy` | Spring Security |
| `-100` | `TenantContextFilter` | tenant-core |
| `-60`  | `AuthenticatedPrincipalTenantContextFilter` | tenant-security |
| `-50`  | `TenantIsolationFilter` | tenant-security |

Filters registered at an order **greater than** `-100` run downstream of Spring Security and
therefore see the populated `SecurityContextHolder`. That is why the principal-based binding
filter sits at `-60` rather than inside the `-100` `TenantContextFilter`.

## Secure a view with Holon Auth

Because the framework publishes a Spring Security-backed `AuthContext`, standard Holon Vaadin
navigator annotations work directly:

```java
@Route(value = "admin", layout = MainLayout.class)
@RolesAllowed("ADMIN")            // navigator calls AuthContext.require()
public class AdminView extends VerticalLayout { /* ... */ }
```

You can also query the context imperatively inside a view:

```java
AuthContext auth = AuthContext.require();
boolean isAdmin = auth.isPermitted("ADMIN");
String user = auth.getAuthentication().map(Authentication::getName).orElse("anonymous");
```

## Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Authenticated views render blank; log shows `AuthContext.require()` failed | `AuthContext` not resolvable | Confirm `auth-context-registration-enabled` is not `false`; confirm Spring Security is on the classpath; ensure the Holon Spring bean-context scope is enabled (any Holon Spring Boot starter) so the `springSecurityAuthContext` bean resolves |
| `Table X not found` on a plain in-app route after login | Tenant not bound for UIDL requests | Ensure `SessionCachingTenantResolver` is in the chain and you called `cacheTenant(...)` at login; keep `principal-fallback-enabled: true` |
| Home/login page returns `400` | `required: true` and the request has no tenant identifier | Add the page to `ignored-paths`, or set `deferMissingTenantToChain: true` |
| Cross-tenant `403` after switching accounts | Stale session tenant cache | Call `SessionCachingTenantResolver.clearCachedTenant(session)` on logout |
