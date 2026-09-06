package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.TenantDetails;
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

        String tenantId = request.tenantId();
        log.info("Starting onboarding for tenant '{}' (plan={})", tenantId, request.plan());

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

            // ── Rollback: mark DELETED (best-effort) ───────────────────────
            try {
                repository.save(provisioning.toBuilder().status(TenantStatus.DELETED).build());
                log.warn("Tenant '{}' marked DELETED after onboarding failure", tenantId);
            } catch (Exception rollbackEx) {
                log.error("Could not roll back tenant '{}' to DELETED state", tenantId, rollbackEx);
            }

            // ── Publish failure event ──────────────────────────────────────
            publishSafely(new TenantOnboardingFailedEvent(this, tenantId, ex));

            throw new TenantOnboardingException(tenantId, ex);
        }
    }

    @Override
    public boolean isOnboarded(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return repository.exists(tenantId);
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



