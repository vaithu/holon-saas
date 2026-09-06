package com.holonplatform.multitenant.audit.config;

import com.holonplatform.multitenant.audit.ActorResolver;
import com.holonplatform.multitenant.audit.AuditAction;
import com.holonplatform.multitenant.audit.AuditEntry;
import com.holonplatform.multitenant.audit.SecurityContextActorResolver;
import com.holonplatform.multitenant.audit.TenantAuditService;
import com.holonplatform.multitenant.audit.internal.AuditAspect;
import com.holonplatform.multitenant.audit.internal.AuditEntryDatastoreHelper;
import com.holonplatform.multitenant.audit.internal.DatabaseTenantAuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link TenantAuditAutoConfiguration}.
 *
 * <p>A mocked {@link AuditEntryDatastoreHelper} stands in for a real Holon Datastore
 * so the context can be exercised without a database.
 */
class TenantAuditAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantAuditAutoConfiguration.class))
            .withUserConfiguration(DatastoreHelperConfig.class);

    // ── TenantAuditService ────────────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers DatabaseTenantAuditService as default")
    void autoConfig_registersDefaultAuditService() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantAuditService.class);
            assertThat(ctx.getBean(TenantAuditService.class))
                    .isInstanceOf(DatabaseTenantAuditService.class);
        });
    }

    @Test
    @DisplayName("auto-config backs off TenantAuditService when custom bean is declared")
    void autoConfig_backsOff_whenCustomAuditServicePresent() {
        contextRunner
                .withUserConfiguration(CustomAuditServiceConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantAuditService.class);
                    assertThat(ctx.getBean(TenantAuditService.class))
                            .isInstanceOf(CustomAuditServiceConfig.CustomAuditService.class);
                });
    }

    // ── ActorResolver ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers SecurityContextActorResolver as default")
    void autoConfig_registersDefaultActorResolver() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(ActorResolver.class);
            assertThat(ctx.getBean(ActorResolver.class))
                    .isInstanceOf(SecurityContextActorResolver.class);
        });
    }

    @Test
    @DisplayName("default ActorResolver bean also satisfies AuditorAware<String> (named securityContextActorResolver)")
    void autoConfig_defaultActorResolverBean_alsoSatisfiesAuditorAware() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasBean("securityContextActorResolver");
            assertThat(ctx.getBean("securityContextActorResolver"))
                    .isInstanceOf(org.springframework.data.domain.AuditorAware.class);
        });
    }

    @Test
    @DisplayName("auto-config backs off ActorResolver when custom bean is declared")
    void autoConfig_backsOff_whenCustomActorResolverPresent() {
        contextRunner
                .withUserConfiguration(CustomActorResolverConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(ActorResolver.class);
                    assertThat(ctx.getBean(ActorResolver.class))
                            .isInstanceOf(CustomActorResolverConfig.CustomActorResolver.class);
                });
    }

    // ── AuditAspect ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers AuditAspect when AspectJ is on classpath")
    void autoConfig_registersAuditAspect() {
        contextRunner.run(ctx ->
                assertThat(ctx).hasSingleBean(AuditAspect.class));
    }

    @Test
    @DisplayName("auto-config backs off AuditAspect when custom bean is declared")
    void autoConfig_backsOff_whenCustomAuditAspectPresent() {
        contextRunner
                .withUserConfiguration(CustomAuditAspectConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(AuditAspect.class);
                    assertThat(ctx.getBean(AuditAspect.class))
                            .isInstanceOf(CustomAuditAspectConfig.CustomAuditAspect.class);
                });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link AuditEntryDatastoreHelper}
     * (which normally requires a Holon {@code Datastore} bean) with an in-memory-backed
     * mock, so the auto-configuration can be exercised without a real database.
     */
    static class DatastoreHelperConfig {

        @Bean
        AuditEntryDatastoreHelper auditEntryDatastoreHelper() {
            AuditEntryDatastoreHelper helper = mock(AuditEntryDatastoreHelper.class);
            Map<String, List<AuditEntry>> store = new ConcurrentHashMap<>();

            when(helper.save(any(AuditEntry.class))).thenAnswer(inv -> {
                AuditEntry entry = inv.getArgument(0);
                store.computeIfAbsent(entry.getTenantId(), t -> new CopyOnWriteArrayList<>()).add(entry);
                return entry;
            });
            when(helper.findByTenantIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv -> {
                List<AuditEntry> list = new ArrayList<>(store.getOrDefault(inv.getArgument(0), List.of()));
                list.sort(Comparator.comparing(AuditEntry::getCreatedAt).reversed());
                return list;
            });
            when(helper.countByTenantId(anyString())).thenAnswer(inv ->
                    (long) store.getOrDefault(inv.getArgument(0), List.of()).size());
            return helper;
        }
    }

    static class CustomAuditServiceConfig {
        @Bean
        TenantAuditService tenantAuditService() {
            return new CustomAuditService();
        }

        static class CustomAuditService implements TenantAuditService {
            @Override public void log(AuditEntry entry) { }
            @Override public List<AuditEntry> findByTenant(String tenantId, int offset, int limit) { return List.of(); }
            @Override public List<AuditEntry> findByTenantAndAction(String tenantId, AuditAction action, int offset, int limit) { return List.of(); }
            @Override public List<AuditEntry> findByTenantAndSeverity(String tenantId, com.holonplatform.multitenant.audit.AuditSeverity severity, int offset, int limit) { return List.of(); }
            @Override public java.util.Optional<AuditEntry> findLatest(String tenantId) { return java.util.Optional.empty(); }
            @Override public long count(String tenantId) { return 0; }
            @Override public long countBySeverity(String tenantId, com.holonplatform.multitenant.audit.AuditSeverity severity) { return 0; }
            @Override public void deleteAll(String tenantId) { }
        }
    }

    static class CustomActorResolverConfig {
        @Bean
        ActorResolver actorResolver() {
            return new CustomActorResolver();
        }

        static class CustomActorResolver implements ActorResolver {
            @Override
            public String resolveActor() { return "custom-actor"; }
        }
    }

    static class CustomAuditAspectConfig {
        @Bean
        AuditAspect auditAspect(TenantAuditService svc, ActorResolver resolver) {
            return new CustomAuditAspect(svc, resolver);
        }

        static class CustomAuditAspect extends AuditAspect {
            CustomAuditAspect(TenantAuditService svc, ActorResolver resolver) {
                super(svc, resolver);
            }
        }
    }
}
