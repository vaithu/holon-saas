package com.holonplatform.multitenant.audit;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.audit.annotation.Audited;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link com.holonplatform.multitenant.audit.internal.AuditAspect}.
 *
 * <p>Bootstraps a minimal Spring context with AOP enabled and the auto-configuration applied.
 * A trivial in-memory {@link TenantAuditService} test fixture ({@link FakeTenantAuditService})
 * stands in for the database-backed production implementation, since this test only exercises
 * the AOP aspect's behavior, not persistence.
 */
@SpringBootTest(classes = AuditAspectTest.TestConfig.class)
class AuditAspectTest {

    @Autowired FakeTenantAuditService auditService;
    @Autowired AuditedService            target;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        auditService.deleteAll("acme");
        auditService.deleteAll("system");
    }

    // ── @Audited — success path ────────────────────────────────────────────────

    @Test
    @DisplayName("@Audited logs entry on successful method invocation")
    void audited_logsOnSuccess() {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(TenantPlan.FREE).status(TenantStatus.ACTIVE).build());

        target.doUpdate("resource-1");

        assertThat(auditService.count("acme")).isEqualTo(1);
        AuditEntry entry = auditService.findByTenant("acme", 0, 1).get(0);
        assertThat(entry.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(entry.getActor()).isEqualTo("system"); // default ActorResolver
        assertThat(entry.getResourceType()).isEqualTo("Item");
        assertThat(entry.getResourceId()).isEqualTo("resource-1");
        assertThat(entry.getSeverity()).isEqualTo(AuditSeverity.INFO);
    }

    @Test
    @DisplayName("@Audited with no tenant context logs under synthetic 'system' tenant")
    void audited_noTenant_usesSystemTenant() {
        target.doUpdate("x");

        assertThat(auditService.count("system")).isEqualTo(1);
    }

    // ── @Audited — failure path ────────────────────────────────────────────────

    @Test
    @DisplayName("@Audited(logOnFailure=true) logs WARNING entry when method throws")
    void audited_logsOnFailure() {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(TenantPlan.FREE).status(TenantStatus.ACTIVE).build());

        assertThatThrownBy(() -> target.doFailingOp())
                .isInstanceOf(RuntimeException.class);

        assertThat(auditService.count("acme")).isEqualTo(1);
        AuditEntry entry = auditService.findByTenant("acme", 0, 1).get(0);
        assertThat(entry.getSeverity()).isEqualTo(AuditSeverity.WARNING);
        assertThat(entry.getDescription()).contains("FAILED");
    }

    @Test
    @DisplayName("@Audited(logOnFailure=false) does NOT log when method throws")
    void audited_noLogOnFailure_skipsEntry() {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(TenantPlan.FREE).status(TenantStatus.ACTIVE).build());

        assertThatThrownBy(() -> target.doFailingOpNoLog())
                .isInstanceOf(RuntimeException.class);

        assertThat(auditService.count("acme")).isEqualTo(0);
    }

    // ── Inner helper — annotated service ──────────────────────────────────────

    interface AuditedService {
        void doUpdate(String id);
        void doFailingOp();
        void doFailingOpNoLog();
    }

    static class AuditedServiceImpl implements AuditedService {

        @Override
        @Audited(action = AuditAction.UPDATE, resourceType = "Item", resourceIdExpression = "#id")
        public void doUpdate(String id) { /* OK */ }

        @Override
        @Audited(action = AuditAction.DELETE, logOnFailure = true)
        public void doFailingOp() { throw new RuntimeException("boom"); }

        @Override
        @Audited(action = AuditAction.DELETE, logOnFailure = false)
        public void doFailingOpNoLog() { throw new RuntimeException("silent"); }
    }

    /**
     * Minimal in-memory {@link TenantAuditService} test fixture, scoped to this test only.
     * Not a shipped implementation — production code uses the Datastore-backed
     * {@code DatabaseTenantAuditService} registered by {@code TenantAuditAutoConfiguration}.
     */
    static class FakeTenantAuditService implements TenantAuditService {

        private final Map<String, List<AuditEntry>> entriesByTenant = new ConcurrentHashMap<>();

        @Override
        public void log(AuditEntry entry) {
            Objects.requireNonNull(entry, "entry must not be null");
            entriesByTenant.computeIfAbsent(entry.getTenantId(), t -> new CopyOnWriteArrayList<>()).add(entry);
        }

        @Override
        public List<AuditEntry> findByTenant(String tenantId, int offset, int limit) {
            return slice(streamFor(tenantId), offset, limit);
        }

        @Override
        public List<AuditEntry> findByTenantAndAction(String tenantId, AuditAction action, int offset, int limit) {
            return slice(streamFor(tenantId).filter(e -> e.getAction() == action), offset, limit);
        }

        @Override
        public List<AuditEntry> findByTenantAndSeverity(String tenantId, AuditSeverity severity, int offset, int limit) {
            return slice(streamFor(tenantId).filter(e -> e.getSeverity() == severity), offset, limit);
        }

        @Override
        public Optional<AuditEntry> findLatest(String tenantId) {
            return streamFor(tenantId).findFirst();
        }

        @Override
        public long count(String tenantId) {
            return streamFor(tenantId).count();
        }

        @Override
        public long countBySeverity(String tenantId, AuditSeverity severity) {
            return streamFor(tenantId).filter(e -> e.getSeverity() == severity).count();
        }

        @Override
        public void deleteAll(String tenantId) {
            entriesByTenant.remove(tenantId);
        }

        private java.util.stream.Stream<AuditEntry> streamFor(String tenantId) {
            List<AuditEntry> list = entriesByTenant.getOrDefault(tenantId, List.of());
            List<AuditEntry> snapshot = new ArrayList<>(list);
            snapshot.sort(Comparator.comparing(AuditEntry::getCreatedAt).reversed());
            return snapshot.stream();
        }

        private List<AuditEntry> slice(java.util.stream.Stream<AuditEntry> stream, int offset, int limit) {
            return stream.skip(Math.max(offset, 0)).limit(Math.max(limit, 0)).toList();
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        FakeTenantAuditService tenantAuditService() {
            return new FakeTenantAuditService();
        }

        @Bean
        ActorResolver actorResolver() {
            return () -> ActorResolver.SYSTEM;
        }

        @Bean
        com.holonplatform.multitenant.audit.internal.AuditAspect auditAspect(
                TenantAuditService s, ActorResolver r) {
            return new com.holonplatform.multitenant.audit.internal.AuditAspect(s, r);
        }

        @Bean
        AuditedService auditedService() {
            return new AuditedServiceImpl();
        }
    }
}


