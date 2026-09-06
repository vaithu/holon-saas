package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.data.migration.TenantSchemaInitializer;
import com.holonplatform.multitenant.onboarding.TenantOnboardingRequest;
import com.holonplatform.multitenant.onboarding.TenantOnboardingResult;
import com.holonplatform.multitenant.onboarding.event.TenantOnboardingCompletedEvent;
import com.holonplatform.multitenant.onboarding.event.TenantOnboardingFailedEvent;
import com.holonplatform.multitenant.onboarding.exception.TenantAlreadyExistsException;
import com.holonplatform.multitenant.onboarding.exception.TenantOnboardingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultTenantOnboardingService}.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTenantOnboardingServiceTest {

    @Mock TenantSchemaInitializer schemaInitializer;
    @Mock ApplicationEventPublisher eventPublisher;

    private FakeTenantRepository repository;
    private DefaultTenantOnboardingService service;

    @BeforeEach
    void setUp() {
        repository = new FakeTenantRepository();
        service = new DefaultTenantOnboardingService(repository, schemaInitializer, eventPublisher);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("onboard: success — tenant persisted as ACTIVE, schema migrated, event published")
    void onboard_happyPath() {
        var request = TenantOnboardingRequest.builder("acme")
                .name("Acme Corp")
                .plan(TenantPlan.PROFESSIONAL)
                .adminEmail("admin@acme.com")
                .locale("en-US")
                .timezone("America/New_York")
                .attribute("industry", "Tech")
                .build();

        TenantOnboardingResult result = service.onboard(request);

        // Result
        assertThat(result.success()).isTrue();
        assertThat(result.tenantId()).isEqualTo("acme");
        assertThat(result.tenant()).isNotNull();
        assertThat(result.tenant().status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(result.tenant().plan()).isEqualTo(TenantPlan.PROFESSIONAL);
        assertThat(result.tenant().name()).isEqualTo("Acme Corp");
        assertThat(result.tenant().locale()).isEqualTo("en-US");
        assertThat(result.tenant().attributes()).containsEntry("industry", "Tech");
        assertThat(result.tenant().attributes()).containsEntry("adminEmail", "admin@acme.com");

        // Flyway called exactly once
        verify(schemaInitializer, times(1)).migrate("acme");

        // Repository has active tenant
        assertThat(repository.findById("acme")).isPresent()
                .get().extracting(TenantDetails::status).isEqualTo(TenantStatus.ACTIVE);

        // Success event published — capture against ApplicationEvent overload (events extend ApplicationEvent)
        var captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TenantOnboardingCompletedEvent.class);
        assertThat(((TenantOnboardingCompletedEvent) captor.getValue()).getTenantId()).isEqualTo("acme");
    }

    @Test
    @DisplayName("onboard: adminEmail stored as attribute even when not explicitly in attributes map")
    void onboard_adminEmailStoredAsAttribute() {
        var result = service.onboard(TenantOnboardingRequest.builder("beta")
                .plan(TenantPlan.FREE)
                .adminEmail("admin@beta.io")
                .build());

        assertThat(result.tenant().attributes()).containsEntry("adminEmail", "admin@beta.io");
    }

    @Test
    @DisplayName("onboard: name defaults to tenantId when not supplied")
    void onboard_nameDefaultsToTenantId() {
        var result = service.onboard(TenantOnboardingRequest.builder("gamma")
                .plan(TenantPlan.STARTER)
                .build());

        assertThat(result.tenant().name()).isEqualTo("gamma");
    }

    @Test
    @DisplayName("isOnboarded returns false before onboarding, true after")
    void isOnboarded_lifecycle() {
        assertThat(service.isOnboarded("delta")).isFalse();

        service.onboard(TenantOnboardingRequest.builder("delta").plan(TenantPlan.FREE).build());

        assertThat(service.isOnboarded("delta")).isTrue();
    }

    // ── Shared-schema mode (no schemaInitializer) ─────────────────────────────

    @Test
    @DisplayName("onboard without schemaInitializer skips Flyway but activates tenant")
    void onboard_sharedSchema_noFlyway() {
        var sharedService = new DefaultTenantOnboardingService(repository, null, eventPublisher);

        var result = sharedService.onboard(TenantOnboardingRequest.builder("shared-tenant")
                .plan(TenantPlan.FREE)
                .build());

        assertThat(result.success()).isTrue();
        assertThat(result.tenant().status()).isEqualTo(TenantStatus.ACTIVE);
        verifyNoInteractions(schemaInitializer);
    }

    // ── Duplicate tenant ──────────────────────────────────────────────────────

    @Test
    @DisplayName("onboard throws TenantAlreadyExistsException for duplicate tenantId")
    void onboard_duplicate_throws() {
        service.onboard(TenantOnboardingRequest.builder("dup-tenant").plan(TenantPlan.FREE).build());

        assertThatExceptionOfType(TenantAlreadyExistsException.class)
                .isThrownBy(() -> service.onboard(
                        TenantOnboardingRequest.builder("dup-tenant").plan(TenantPlan.ENTERPRISE).build()))
                .satisfies(ex -> assertThat(ex.getTenantId()).isEqualTo("dup-tenant"));
    }

    @Test
    @DisplayName("onboard does NOT call schemaInitializer on duplicate")
    void onboard_duplicate_noMigrationCalled() {
        service.onboard(TenantOnboardingRequest.builder("dup2").plan(TenantPlan.FREE).build());
        verify(schemaInitializer, times(1)).migrate("dup2");

        assertThatExceptionOfType(TenantAlreadyExistsException.class)
                .isThrownBy(() -> service.onboard(
                        TenantOnboardingRequest.builder("dup2").plan(TenantPlan.FREE).build()));

        // Still only one call
        verify(schemaInitializer, times(1)).migrate("dup2");
    }

    // ── Migration failure → rollback ──────────────────────────────────────────

    @Test
    @DisplayName("onboard throws TenantOnboardingException when Flyway fails")
    void onboard_migrationFailure_throws() {
        doThrow(new RuntimeException("Flyway connection refused"))
                .when(schemaInitializer).migrate("fail-tenant");

        assertThatExceptionOfType(TenantOnboardingException.class)
                .isThrownBy(() -> service.onboard(
                        TenantOnboardingRequest.builder("fail-tenant").plan(TenantPlan.FREE).build()))
                .satisfies(ex -> {
                    assertThat(ex.getTenantId()).isEqualTo("fail-tenant");
                    assertThat(ex.getCause()).isInstanceOf(RuntimeException.class)
                            .hasMessageContaining("Flyway connection refused");
                });
    }

    @Test
    @DisplayName("onboard marks tenant DELETED in repository when Flyway fails")
    void onboard_migrationFailure_markedDeleted() {
        doThrow(new RuntimeException("DB down")).when(schemaInitializer).migrate("deleted-tenant");

        assertThatExceptionOfType(TenantOnboardingException.class).isThrownBy(() ->
                service.onboard(TenantOnboardingRequest.builder("deleted-tenant")
                        .plan(TenantPlan.FREE).build()));

        assertThat(repository.findById("deleted-tenant"))
                .isPresent()
                .get()
                .extracting(TenantDetails::status)
                .isEqualTo(TenantStatus.DELETED);
    }

    @Test
    @DisplayName("onboard publishes TenantOnboardingFailedEvent when Flyway fails")
    void onboard_migrationFailure_failedEventPublished() {
        doThrow(new RuntimeException("timeout")).when(schemaInitializer).migrate("event-fail");

        assertThatExceptionOfType(TenantOnboardingException.class).isThrownBy(() ->
                service.onboard(TenantOnboardingRequest.builder("event-fail")
                        .plan(TenantPlan.FREE).build()));

        var captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TenantOnboardingFailedEvent.class);
        TenantOnboardingFailedEvent failed = (TenantOnboardingFailedEvent) captor.getValue();
        assertThat(failed.getTenantId()).isEqualTo("event-fail");
        assertThat(failed.getCause()).hasMessageContaining("timeout");
    }

    @Test
    @DisplayName("onboard does NOT re-throw if event publishing fails (non-fatal)")
    void onboard_eventPublishingFailure_nonFatal() {
        doThrow(new RuntimeException("broker down")).when(eventPublisher).publishEvent(any());

        // Should NOT throw — event publishing is fire-and-forget
        assertThatNoException().isThrownBy(() ->
                service.onboard(TenantOnboardingRequest.builder("non-fatal-evt")
                        .plan(TenantPlan.FREE).build()));
    }

    // ── TenantOnboardingResult helpers ────────────────────────────────────────

    @Test
    @DisplayName("TenantOnboardingResult.success() carries active tenant")
    void result_success_factory() {
        TenantDetails t = TenantDetails.builder("x").plan(TenantPlan.FREE)
                .status(TenantStatus.ACTIVE).build();
        TenantOnboardingResult r = TenantOnboardingResult.success(t);
        assertThat(r.success()).isTrue();
        assertThat(r.tenant()).isSameAs(t);
        assertThat(r.errorMessage()).isNull();
        assertThat(r.cause()).isNull();
    }

    @Test
    @DisplayName("TenantOnboardingResult.failure() carries error message and cause")
    void result_failure_factory() {
        RuntimeException cause = new RuntimeException("oops");
        TenantOnboardingResult r = TenantOnboardingResult.failure("x", "Something went wrong", cause);
        assertThat(r.success()).isFalse();
        assertThat(r.tenant()).isNull();
        assertThat(r.errorMessage()).isEqualTo("Something went wrong");
        assertThat(r.cause()).isSameAs(cause);
    }

    @Test
    @DisplayName("TenantOnboardingResult.orThrow() does not throw on success")
    void result_orThrow_success() {
        TenantDetails t = TenantDetails.builder("x").plan(TenantPlan.FREE)
                .status(TenantStatus.ACTIVE).build();
        assertThatNoException().isThrownBy(() ->
                TenantOnboardingResult.success(t).orThrow());
    }

    @Test
    @DisplayName("TenantOnboardingResult.orThrow() throws on failure")
    void result_orThrow_failure() {
        assertThatException().isThrownBy(() ->
                TenantOnboardingResult.failure("x", "boom", null).orThrow());
    }

    // ── Request validation ────────────────────────────────────────────────────

    @Test
    @DisplayName("TenantOnboardingRequest.builder rejects null tenantId")
    void request_nullTenantId() {
        assertThatNullPointerException().isThrownBy(() ->
                TenantOnboardingRequest.builder(null).plan(TenantPlan.FREE).build());
    }

    @Test
    @DisplayName("TenantOnboardingRequest.builder rejects blank tenantId")
    void request_blankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                TenantOnboardingRequest.builder("  ").plan(TenantPlan.FREE).build());
    }

    @Test
    @DisplayName("TenantOnboardingRequest.builder rejects null plan")
    void request_nullPlan() {
        assertThatNullPointerException().isThrownBy(() ->
                TenantOnboardingRequest.builder("valid-id").plan(null).build());
    }

    @Test
    @DisplayName("onboard rejects null request")
    void onboard_nullRequest() {
        assertThatNullPointerException().isThrownBy(() -> service.onboard(null));
    }

    // ── isOnboarded null guard ─────────────────────────────────────────────────

    @Test
    @DisplayName("isOnboarded throws NullPointerException on null tenantId")
    void isOnboarded_null() {
        assertThatNullPointerException().isThrownBy(() -> service.isOnboarded(null));
    }
}




