package com.holonplatform.multitenant.demo.service;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.onboarding.TenantOnboardingRequest;
import com.holonplatform.multitenant.onboarding.TenantOnboardingService;

/**
 * F26 — what happens when provisioning a tenant fails halfway through.
 *
 * <h2>Why this has to be forced</h2>
 * <p>The failure path is the part of onboarding that matters most and runs least. It only
 * executes when a schema cannot be created or a migration will not apply, which on a healthy
 * machine is never — so it is the path most likely to be wrong, and the one no demo would show
 * by accident. This service arranges a genuine failure rather than simulating one: it writes a
 * failed migration into the tenant schema's Flyway history, so Flyway refuses to migrate for
 * exactly the reason it would refuse in production after a migration died mid-way.
 *
 * <h2>What the run is meant to show</h2>
 * <ol>
 *   <li>Onboarding fails, and says so by throwing rather than returning a half-made tenant.</li>
 *   <li>A {@code TenantOnboardingFailedEvent} is published, so anything watching finds out.</li>
 *   <li>The workspace name is free again — which is the whole point. A customer whose signup
 *       failed for a transient reason gets to retry with the name they chose.</li>
 *   <li>Retrying the identical request, with the cause removed, succeeds.</li>
 * </ol>
 *
 * <p>Point 3 is a fix this demo prompted. The failure path used to leave the record behind
 * marked {@code DELETED}, which still counts as existing — so the retry in point 4 failed with
 * "tenant already exists" and the name was burned permanently, even though
 * {@code TenantOnboardingFailedEvent}'s own documentation promised the opposite.
 */
@Service
public class OnboardingFailureProbe {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final TenantOnboardingService onboarding;

    private final JdbcTemplate platformJdbc;

    public OnboardingFailureProbe(TenantOnboardingService onboarding, JdbcTemplate platformJdbc) {
        this.onboarding   = onboarding;
        this.platformJdbc = platformJdbc;
    }

    /**
     * Runs the whole sequence and returns a transcript of it.
     *
     * <p>Every step reports what actually happened rather than what was expected, so a run that
     * goes differently than described reads as a surprise instead of quietly passing.
     */
    public String runFailureThenRetry() {
        String tenantId = "rigged-" + SEQUENCE.incrementAndGet() + "-" + (System.currentTimeMillis() % 100000);
        String schema   = "tenant_" + tenantId;
        StringBuilder transcript = new StringBuilder();

        rigFailedMigration(schema);
        transcript.append("1. rigged ").append(schema)
                .append(" with a failed Flyway migration in its history\n");

        try {
            onboarding.onboard(request(tenantId));
            transcript.append("2. onboarding SUCCEEDED — the rig did not take effect\n");
        } catch (Exception e) {
            transcript.append("2. onboarding failed as intended: ")
                    .append(e.getClass().getSimpleName()).append('\n');
        }

        boolean stillTaken = onboarding.isOnboarded(tenantId);
        transcript.append("3. is the workspace name still taken? ")
                .append(stillTaken ? "YES — the name is burned" : "no — it can be used again")
                .append('\n');

        removeRig(schema);
        transcript.append("4. removed the rig (the cause an operator would have fixed)\n");

        try {
            onboarding.onboard(request(tenantId));
            transcript.append("5. retried the same workspace name: succeeded\n");
        } catch (Exception e) {
            transcript.append("5. retried the same workspace name: FAILED — ")
                    .append(e.getClass().getSimpleName()).append(": ").append(e.getMessage())
                    .append('\n');
        }

        transcript.append("\nworkspace used for this run: ").append(tenantId);
        return transcript.toString();
    }

    private static TenantOnboardingRequest request(String tenantId) {
        return TenantOnboardingRequest.builder(tenantId)
                .name("Rigged Failure Demo")
                .plan(TenantPlan.FREE)
                .build();
    }

    /**
     * Creates the tenant schema and plants a Flyway history row marked unsuccessful. Flyway
     * refuses to run against a schema with a failed migration recorded, which is precisely the
     * state a migration interrupted by a crash or a lost connection leaves behind.
     */
    private void rigFailedMigration(String schema) {
        platformJdbc.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
        platformJdbc.execute("""
                CREATE TABLE IF NOT EXISTS "%s"."flyway_schema_history" (
                    installed_rank INT          NOT NULL,
                    version        VARCHAR(50),
                    description    VARCHAR(200) NOT NULL,
                    type           VARCHAR(20)  NOT NULL,
                    script         VARCHAR(1000) NOT NULL,
                    checksum       INT,
                    installed_by   VARCHAR(100) NOT NULL,
                    installed_on   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    execution_time INT          NOT NULL,
                    success        BOOLEAN      NOT NULL,
                    CONSTRAINT "%s_pk" PRIMARY KEY (installed_rank)
                )""".formatted(schema, schema.replace('-', '_')));
        platformJdbc.update("""
                INSERT INTO "%s"."flyway_schema_history"
                    (installed_rank, version, description, type, script, checksum,
                     installed_by, execution_time, success)
                VALUES (1, '1', 'tenant baseline', 'SQL', 'V1__tenant_baseline.sql', 0,
                        'demo', 0, FALSE)""".formatted(schema));
    }

    /**
     * Drops the history table rather than the schema. Dropping the schema would pull the ground
     * out from under any pooled connection still pinned to it; dropping just the history lets
     * Flyway start over, which is what an operator clearing a failed migration does anyway.
     */
    private void removeRig(String schema) {
        platformJdbc.execute("DROP TABLE IF EXISTS \"" + schema + "\".\"flyway_schema_history\"");
    }
}
