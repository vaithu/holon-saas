package com.holonplatform.multitenant.demo.config;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the platform operator account on start-up.
 *
 * <p>Seeding happens in Java rather than in the Flyway migration because the password has to be
 * run through the application's {@link PasswordEncoder}; a hard-coded BCrypt hash in SQL would
 * silently stop working the moment the encoder is changed.
 *
 * <p>The row is written through {@code platformJdbcTemplate}, which is pinned to the default
 * schema, and keyed on the reserved {@link PlatformAdmin#WORKSPACE} pseudo-workspace so it
 * shares the {@code demo_account} table with real tenant members without colliding with any of
 * them.
 */
@Component
public class PlatformAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminSeeder.class);

    private final JdbcTemplate    jdbc;
    private final PasswordEncoder passwordEncoder;

    public PlatformAdminSeeder(JdbcTemplate platformJdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbc            = Objects.requireNonNull(platformJdbcTemplate, "platformJdbcTemplate must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbc.update("MERGE INTO demo_account (tenant_id, email, password_hash) KEY (tenant_id, email) VALUES (?, ?, ?)",
                PlatformAdmin.WORKSPACE, PlatformAdmin.EMAIL, passwordEncoder.encode(PlatformAdmin.PASSWORD));
        log.info("Platform operator seeded: workspace '{}', email '{}'",
                PlatformAdmin.WORKSPACE, PlatformAdmin.EMAIL);
    }
}
