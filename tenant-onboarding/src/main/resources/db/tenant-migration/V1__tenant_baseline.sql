-- ============================================================
-- V1__tenant_baseline.sql
--
-- Framework-level baseline tables created in every tenant schema
-- by the automatic onboarding process.
--
-- These tables are managed by the Holon multi-tenant framework.
-- Add application-specific tables in V2__ and higher scripts.
-- ============================================================

-- Per-tenant key-value configuration store
CREATE TABLE IF NOT EXISTS tenant_settings (
    setting_key   VARCHAR(255)  NOT NULL,
    setting_value TEXT,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tenant_settings PRIMARY KEY (setting_key)
);

-- Per-tenant feature-flag overrides (complements plan-level defaults)
CREATE TABLE IF NOT EXISTS tenant_feature_flags (
    feature_key VARCHAR(255)  NOT NULL,
    enabled     BOOLEAN       NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tenant_feature_flags PRIMARY KEY (feature_key)
);

-- Per-tenant audit log for security and compliance
CREATE TABLE IF NOT EXISTS tenant_audit_log (
    id          BIGSERIAL     PRIMARY KEY,
    action      VARCHAR(255)  NOT NULL,
    actor       VARCHAR(255),
    resource    VARCHAR(500),
    detail      TEXT,
    occurred_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tenant_audit_log_occurred_at ON tenant_audit_log (occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_tenant_audit_log_actor       ON tenant_audit_log (actor);

