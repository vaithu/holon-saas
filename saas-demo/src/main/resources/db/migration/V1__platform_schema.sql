-- ============================================================
-- Platform ("control plane") schema for the holon-saas demo.
--
-- These tables live in H2's default PUBLIC schema and are shared by every
-- tenant. The framework deliberately ships NO migration for this schema —
-- see tenant-onboarding's db/tenant-migration scripts, which are applied to
-- each per-tenant schema instead.
--
-- Column names/types mirror com.holonplatform.multitenant.TenantDetails
-- exactly, because the framework reads and writes these tables through JPA
-- with no tenant bound to the TenantContext.
-- ============================================================

CREATE TABLE IF NOT EXISTS tenant (
    tenant_id VARCHAR(64)  NOT NULL PRIMARY KEY,
    name      VARCHAR(255),
    plan      VARCHAR(32)  NOT NULL,
    status    VARCHAR(32)  NOT NULL,
    locale    VARCHAR(32),
    timezone  VARCHAR(64),
    theme     VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS tenant_features (
    tenant_id   VARCHAR(64)  NOT NULL REFERENCES tenant (tenant_id),
    feature_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (tenant_id, feature_key)
);

CREATE TABLE IF NOT EXISTS tenant_attributes (
    tenant_id  VARCHAR(64)  NOT NULL REFERENCES tenant (tenant_id),
    attr_key   VARCHAR(255) NOT NULL,
    attr_value TEXT,
    PRIMARY KEY (tenant_id, attr_key)
);

-- ------------------------------------------------------------
-- Login credentials.
--
-- The framework intentionally does not ship a credentials table (every app
-- has different auth requirements), so the demo provides its own and exposes
-- it through the AccountCredentialsStore SPI.
--
-- The primary key is (tenant_id, email): the same person may hold an account
-- in more than one workspace, which is why the login form asks for the
-- workspace as well as the email.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS demo_account (
    tenant_id        VARCHAR(64)  NOT NULL,
    email            VARCHAR(255) NOT NULL,
    password_hash    VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, email)
);
