# Feature catalogue

Everything the framework offers, and where the demo stands on each. The demo is built up in
batches of five: a batch is implemented, tested end to end, and reviewed before the next starts.

Status values:

- **Done** — demonstrated in the running application and covered by a test
- **Next** — in the batch currently being built
- **Planned** — catalogued, not yet demonstrated

## Batch 1 — tenancy, identity and access (done)

| # | Feature | Module | Status |
|---|---------|--------|--------|
| F1 | Tenant sign-up provisioning a dedicated database schema | `tenant-signup`, `tenant-onboarding`, `tenant-data` | Done |
| F2 | Tenant-scoped login, with the tenant bound from the authenticated principal | `tenant-security` | Done |
| F3 | Inviting users to a workspace, with tokenised invitation links | `tenant-invitations` | Done |
| F4 | Invitation acceptance creating an active member | `tenant-invitations`, `tenant-users` | Done |
| F5 | Sign-in as an invited member of an existing workspace | `tenant-users`, `tenant-security` | Done |
| F6 | View-level access: a view is reachable only with the right permission | `tenant-permissions` | Done |
| F7 | Method-level access: `@RequiresPermission` on service methods | `tenant-permissions` | Done |

## Batch 2 — plan-driven limits and accountability (done)

| # | Feature | Module | Status |
|---|---------|--------|--------|
| F8 | Per-tenant settings with global → plan → tenant inheritance | `tenant-settings` | Done |
| F9 | Plan-based quotas enforced with `@EnforceQuota` | `tenant-quotas` | Done |
| F10 | Tenant-scoped rate limiting with `@RateLimit` | `tenant-limits` | Done |
| F11 | Automatic audit trail with `@Audited`, and an audit log view | `tenant-audit` | Done |
| F12 | Plan and feature gating with `@RequiresPlan` and `@CheckTenantFeature` | `tenant-security` | Done |

## Batch 3 — tenant lifecycle and administration (planned)

| # | Feature | Module | Status |
|---|---------|--------|--------|
| F13 | Cross-tenant administration: list, search, suspend, activate tenants | `tenant-admin` | Done |
| F14 | Tenant suspension refusing access for its members | `tenant-admin`, `tenant-security` | Done |
| F15 | Subscriptions and invoices, including plan changes | `tenant-billing` | Done |
| F16 | Custom roles and a role/permission matrix managed per tenant | `tenant-permissions` (`TenantRoleManagementService`) | Done |
| F17 | View registry and `@SecuredView` driving the navigation menu | `tenant-permissions` (`ViewRegistry`) | Done |

## Batch 4 — resolution strategies and isolation (planned)

| # | Feature | Module | Status |
|---|---------|--------|--------|
| F18 | Tenant resolution by subdomain, path prefix, header and JWT claim | `tenant-core` (`*TenantResolver`) | Done |
| F19 | Cross-tenant access attempts blocked and reported as events | `tenant-security` (`TenantIsolationFilter`) | Done |
| F20 | Tenant context propagation across threads and async tasks | `tenant-core` (`TenantAwareTaskDecorator`) | Done |
| F21 | Tenant-scoped application events | `tenant-core` (`TenantScopedEvent`) | Done |
| F22 | Database-per-tenant and discriminator-column strategies alongside schema-per-tenant | `tenant-data` (`TenantSchemaStrategy`) | Done |

## Batch 5 — scale-out and integrations (planned)

| # | Feature | Module | Status |
|---|---------|--------|--------|
| F23 | Redis-backed tenant cache and distributed rate limiting | `tenant-cache-redis` | Done |
| F24 | Bounded tenant data source registry with eviction and metrics | `tenant-data` (`BoundedTenantDataSourceRegistry`) | Done |
| F25 | WhatsApp Business messaging, conversation windows and webhooks | `tenant-whatsapp` | Skipped |
| F26 | Tenant onboarding events and failure handling | `tenant-onboarding` | Done |
| F27 | Entity mapping validation for tenant-aware entities | `tenant-core` (`HolonEntityMappingValidator`) | Done |
