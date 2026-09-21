package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantIdentifiers;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.data.migration.TenantSchemaInitializer;
import com.holonplatform.multitenant.onboarding.TenantOnboardingRequest;
import com.holonplatform.multitenant.onboarding.TenantOnboardingResult;
import com.holonplatform.multitenant.onboarding.TenantOnboardingService;
import com.holonplatform.multitenant.onboarding.TenantRepository;
import com.holonplatform.multitenant.onboarding.event.TenantOnboardingCompletedEvent;
import com.holonplatform.multitenant.onboarding.event.TenantOnboardingFailedEvent;
import com.holonplatform.multitenant.onboarding.exception.TenantAlreadyExistsException;
import com.holonplatform.multitenant.onboarding.exception.TenantOnboardingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link TenantOnboardingService} implementation.
 *
 * <h3>Thread safety</h3>
 * <p>This class is stateless. The duplicate-check + save sequence is NOT
 * atomic — for production deployments with concurrent signups, wrap
 * {@link #onboard} in a distributed lock (Redis, DB advisory lock) or
 * add a UNIQUE constraint on the tenant ID column in the repository.
 */
public class DefaultTenantOnboardingService implements TenantOnboardingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTenantOnboardingService.class);

    private final TenantRepository         repository;
    private final TenantSchemaInitializer  schemaInitializer;  // null → SHARED strategy (no schema creation)
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Full constructor (schema-per-tenant mode).
     *
     * @param repository       tenant persistence SPI; must not be null
     * @param schemaInitializer Flyway orchestrator; may be null for SHARED strategy
     * @param eventPublisher   Spring event publisher; must not be null
     */
    public DefaultTenantOnboardingService(TenantRepository repository,
                                          TenantSchemaInitializer schemaInitializer,
                                          ApplicationEventPublisher eventPublisher) {
        this.repository       = Objects.requireNonNull(repository,       "repository must not be null");
        this.schemaInitializer = schemaInitializer;    // intentionally nullable
        this.eventPublisher   = Objects.requireNonNull(eventPublisher,   "eventPublisher must not be null");
    }

    // -------------------------------------------------------------------------
    // TenantOnboardingService
    // -------------------------------------------------------------------------

    @Override
    public TenantOnboardingResult onboard(TenantOnboardingRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return inPlatformScope(() -> doOnboard(request));
    }

    private TenantOnboardingResult doOnboard(TenantOnboardingRequest request) {

        String tenantId = request.tenantId();
        log.info("Starting onboarding for tenant '{}' (plan={})", tenantId, request.plan());

        // ── 0. Identifier validation ───────────────────────────────────────
        // onboard() is a public SPI: unlike SignupService (which validates its own input),
        // a platform operator / admin console can call this with an arbitrary identifier.
        // The identifier ends up concatenated into a database schema name and interpolated
        // into DDL, so it must be validated here rather than trusted from the caller.
        if (!TenantIdentifiers.isValid(tenantId)) {
            log.warn("Onboarding rejected — invalid tenant identifier '{}'", tenantId);
            throw new TenantOnboardingException(String.valueOf(tenantId),
                    new IllegalArgumentException("Invalid tenant identifier '" + tenantId + "' — must be "
                            + TenantIdentifiers.RULE_DESCRIPTION));
        }

        // ── 1. Duplicate check ─────────────────────────────────────────────
        if (repository.exists(tenantId)) {
            log.warn("Onboarding rejected — tenant '{}' already exists", tenantId);
            throw new TenantAlreadyExistsException(tenantId);
        }

        // ── 2. Persist tenant in PROVISIONING state ────────────────────────
        TenantDetails provisioning = buildDetails(request, TenantStatus.PROVISIONING);
        repository.save(provisioning);
        log.debug("Tenant '{}' persisted with status PROVISIONING", tenantId);

        // ── 3. Schema provision + Flyway ───────────────────────────────────
        try {
            if (schemaInitializer != null) {
                log.info("Running schema migration for tenant '{}'", tenantId);
                schemaInitializer.migrate(tenantId);
            } else {
                log.debug("No TenantSchemaInitializer present — skipping schema provision for '{}'", tenantId);
            }

            // ── 4. Activate ────────────────────────────────────────────────
            TenantDetails active = provisioning.toBuilder()
                    .status(TenantStatus.ACTIVE)
                    .build();
            repository.save(active);
            log.info("Tenant '{}' onboarded and activated successfully", tenantId);

            // ── 5. Publish success event ───────────────────────────────────
            publishSafely(new TenantOnboardingCompletedEvent(this, active));

            return TenantOnboardingResult.success(active);

        } catch (Exception ex) {

            // ── Rollback: remove the record so the identifier is free again ───
            // The PROVISIONING row exists only to stop a concurrent signup claiming the same
            // identifier while provisioning runs. Once provisioning has failed it describes
            // nothing — and leaving it behind as a DELETED tombstone keeps exists() reporting
            // the name as taken, so the customer can never retry with the name they chose and
            // cannot be told why. Removing it is what makes the identifier genuinely reusable,
            // which is what TenantOnboardingFailedEvent tells listeners has happened.
            rollback(tenantId, provisioning);

            // ── Publish failure event ──────────────────────────────────────
            publishSafely(new TenantOnboardingFailedEvent(this, tenantId, ex));

            throw new TenantOnboardingException(tenantId, ex);
        }
    }

    /**
     * Undoes the {@code PROVISIONING} record written before schema provisioning was attempted.
     *
     * <p>Falls back to marking the record {@code DELETED} if the repository cannot remove it —
     * a {@link TenantRepository} implementation predating {@code deleteById} refuses by default
     * rather than risk destroying data it does not understand. The fallback restores the old
     * behaviour, tombstone and all, which is worse but never worse than failing outright.
     */
    private void rollback(String tenantId, TenantDetails provisioning) {
        try {
            repository.deleteById(tenantId);
            log.warn("Tenant '{}' record removed after onboarding failure — the identifier is free again",
                    tenantId);
            return;
        } catch (Exception deleteEx) {
            log.warn("Repository could not remove tenant '{}' after onboarding failure ({}) — "
                    + "falling back to marking it DELETED, which leaves the identifier unavailable",
                    tenantId, deleteEx.getMessage());
        }
        try {
            repository.save(provisioning.toBuilder().status(TenantStatus.DELETED).build());
            log.warn("Tenant '{}' marked DELETED after onboarding failure", tenantId);
        } catch (Exception rollbackEx) {
            log.error("Could not roll back tenant '{}' to DELETED state", tenantId, rollbackEx);
        }
    }

    @Override
    public boolean isOnboarded(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return inPlatformScope(() -> repository.exists(tenantId));
    }

    /**
     * Runs an operation with no tenant in scope, restoring whatever was in scope afterwards.
     *
     * <p>The tenant registry is platform-level data: one table listing every tenant, living in
     * the platform schema. Onboarding, however, can perfectly well be called from inside a
     * tenant-scoped request — an admin console signed in to one workspace creating another is
     * the obvious case. Without this, the tenant-aware {@code DataSource} routes the registry
     * read or write into the <em>calling</em> tenant's schema, where the table does not exist;
     * onboarding then fails with a bare "table not found" that says nothing about the real
     * cause. With a SHARED-schema strategy it would be worse than a failure: the write would
     * succeed against the wrong rows.
     *
     * <p>This is deliberately not left to callers. Every caller would have to remember it, and
     * forgetting produces a failure whose message points somewhere else entirely.
     */
    private <T> T inPlatformScope(java.util.function.Supplier<T> operation) {
        Optional<TenantDetails> previous = TenantContext.get();
        if (previous.isEmpty()) {
            return operation.get();
        }
        TenantContext.clear();
        try {
            return operation.get();
        } finally {
            TenantContext.set(previous.get());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static TenantDetails buildDetails(TenantOnboardingRequest req, TenantStatus status) {
        TenantDetails.Builder b = TenantDetails.builder(req.tenantId())
                .name(req.name() != null ? req.name() : req.tenantId())
                .plan(req.plan())
                .status(status)
                .locale(req.locale())
                .timezone(req.timezone())
                .attributes(req.attributes());
        if (req.adminEmail() != null) {
            b.attribute("adminEmail", req.adminEmail());
        }
        return b.build();
    }

    private void publishSafely(ApplicationEvent event) {
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.debug("Event publishing threw an exception (non-fatal): {}", e.getMessage(), e);
        }
    }
}



