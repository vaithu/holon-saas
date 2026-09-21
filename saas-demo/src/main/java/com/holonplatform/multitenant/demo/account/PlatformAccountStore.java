package com.holonplatform.multitenant.demo.account;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.holonplatform.multitenant.signup.AccountCredentialsStore;

/**
 * The demo's implementation of the framework's {@link AccountCredentialsStore} SPI, backed by
 * the platform-schema {@code demo_account} table.
 *
 * <p>The framework ships no credentials table of its own — applications differ too much in how
 * they authenticate (shared login, per-tenant login, SSO, external IdPs) — so sign-up delegates
 * storage of the already-hashed password here.
 *
 * <p>All statements run through {@code platformJdbcTemplate}, which is pinned to the default
 * schema. That matters because this store is called both with and without a tenant bound to the
 * {@code TenantContext}: sign-up saves credentials with no tenant bound, whereas invitation
 * acceptance saves them while the invitee's tenant is bound. Using the tenant-aware DataSource
 * here would send the second case to {@code tenant_<id>.demo_account}, which does not exist.
 */
@Component
public class PlatformAccountStore implements AccountCredentialsStore {

    private final JdbcTemplate jdbc;

    public PlatformAccountStore(JdbcTemplate platformJdbcTemplate) {
        this.jdbc = Objects.requireNonNull(platformJdbcTemplate, "platformJdbcTemplate must not be null");
    }

    @Override
    public boolean existsByEmail(String tenantId, String email) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM demo_account WHERE tenant_id = ? AND email = ?",
                Integer.class, tenantId, normalize(email));
        return count != null && count > 0;
    }

    @Override
    public void save(String tenantId, String email, String encodedPassword) {
        jdbc.update("MERGE INTO demo_account (tenant_id, email, password_hash) KEY (tenant_id, email) VALUES (?, ?, ?)",
                tenantId, normalize(email), encodedPassword);
    }

    /**
     * Reads the stored password hash, for the {@code UserDetailsService} to verify against.
     *
     * @return the BCrypt hash, or empty if this workspace/email pair has no account
     */
    public Optional<String> findPasswordHash(String tenantId, String email) {
        List<String> hashes = jdbc.queryForList(
                "SELECT password_hash FROM demo_account WHERE tenant_id = ? AND email = ?",
                String.class, tenantId, normalize(email));
        return hashes.isEmpty() ? Optional.empty() : Optional.of(hashes.getFirst());
    }

    /** Emails are stored and compared in a normalized form so login is case-insensitive. */
    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
