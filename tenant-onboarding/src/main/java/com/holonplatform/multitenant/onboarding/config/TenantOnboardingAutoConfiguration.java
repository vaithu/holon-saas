package com.holonplatform.multitenant.onboarding.config;

import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantDetailsRepository;
import com.holonplatform.multitenant.TenantDetailsRepositoryFactory;
import com.holonplatform.multitenant.data.migration.TenantSchemaInitializer;
import com.holonplatform.multitenant.exception.TenantNotFoundException;
import com.holonplatform.multitenant.onboarding.TenantOnboardingService;
import com.holonplatform.multitenant.onboarding.TenantRepository;
import com.holonplatform.multitenant.onboarding.internal.DatabaseTenantRepository;
import com.holonplatform.multitenant.onboarding.internal.DefaultTenantOnboardingService;
import com.holonplatform.multitenant.onboarding.internal.TenantDetailsDatastoreHelper;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

/**
 * Spring Boot auto-configuration for the tenant onboarding module.
 *
 * <h2>What is registered</h2>
 * <ul>
 *   <li>{@link TenantRepository} — {@link DatabaseTenantRepository}, backed by a plain Spring
 *       Data {@link TenantDetailsRepository} via {@link TenantDetailsDatastoreHelper} (override
 *       with a custom bean). An {@code EntityManager} bean must be configured (e.g. via
 *       {@code holon-starter-jpa-hibernate}) for this module to start.</li>
 *   <li>{@link TenantDetailsLoader} — a trivial delegate to {@link TenantRepository#findById},
 *       which is what activates {@code TenantCoreAutoConfiguration}'s
 *       {@code TenantContextFilter}. Override with a custom bean if needed.</li>
 *   <li>{@link TenantOnboardingService} — {@link DefaultTenantOnboardingService}</li>
 * </ul>
 *
 * <h2>Schema-per-tenant (recommended)</h2>
 * <p>When a {@link TenantSchemaInitializer} bean is present (auto-configured by
 * {@code TenantDataAutoConfiguration} when Flyway is on the classpath), it is
 * injected into {@link DefaultTenantOnboardingService} so that every call to
 * {@link TenantOnboardingService#onboard} automatically:
 * <ol>
 *   <li>Creates the tenant schema ({@code CREATE SCHEMA IF NOT EXISTS tenant_<id>})</li>
 *   <li>Runs all Flyway scripts at {@code classpath:db/tenant-migration/}</li>
 * </ol>
 *
 * <h2>Shared-schema mode</h2>
 * <p>When no {@link TenantSchemaInitializer} is present (no Flyway on classpath, or
 * {@code holon.tenant.data.auto-create-schema=false}), the schema step is silently
 * skipped — only the tenant record is persisted and activated.
 */
@AutoConfiguration
@org.springframework.boot.autoconfigure.AutoConfigureAfter(com.holonplatform.jpa.spring.boot.JpaAutoConfiguration.class)
public class TenantOnboardingAutoConfiguration {

    /**
     * Constructs the {@link TenantDetailsRepository} proxy via {@link TenantDetailsRepositoryFactory}
     * — see that factory's Javadoc for why a proper {@code JpaRepositoryFactoryBean}-based
     * construction (rather than a bare {@code JpaRepositoryFactory}) is required for the
     * resulting repository to actually be transactional.
     *
     * <p>{@link TenantDetailsRepository} lives in {@code tenant-core}, outside any consuming
     * application's own base package, so ordinary {@code @EnableJpaRepositories} component
     * scanning would never discover it.
     *
     * <p>Guarded with {@code @ConditionalOnBean(EntityManagerFactory.class)} (not just
     * {@code @ConditionalOnMissingBean(TenantDetailsRepository.class)}) so this bean method is
     * skipped entirely — rather than failing with an unsatisfied dependency — in test/override
     * scenarios that supply their own {@link TenantDetailsDatastoreHelper} bean without a real
     * JPA {@code EntityManagerFactory} on the context.
     */
    @Bean
    @ConditionalOnBean(EntityManagerFactory.class)
    @ConditionalOnMissingBean(TenantDetailsRepository.class)
    public TenantDetailsRepository tenantDetailsRepository(
            EntityManagerFactory entityManagerFactory, BeanFactory beanFactory) {
        return TenantDetailsRepositoryFactory.create(entityManagerFactory, beanFactory);
    }

    /**
     * Registers the {@link TenantDetailsDatastoreHelper} explicitly — it is not
     * {@code @Component}-scanned (it lives in an internal package outside any consuming
     * application's own base package), so this module must construct it itself.
     *
     * <p>The bean method is named distinctly ({@code onboardingTenantDetailsDatastoreHelper})
     * because {@code tenant-admin} declares its own, unrelated class with the identical simple
     * name {@code TenantDetailsDatastoreHelper} (each module owns its own helper
     * independently) — Spring bean names are a flat namespace regardless of type, so the
     * method names must not collide even though the types themselves are distinct.
     */
    @Bean
    @ConditionalOnMissingBean(TenantDetailsDatastoreHelper.class)
    public TenantDetailsDatastoreHelper onboardingTenantDetailsDatastoreHelper(TenantDetailsRepository repository) {
        return new TenantDetailsDatastoreHelper(repository);
    }

    @Bean
    @ConditionalOnMissingBean(TenantRepository.class)
    public TenantRepository tenantRepository(TenantDetailsDatastoreHelper datastoreHelper) {
        return new DatabaseTenantRepository(datastoreHelper);
    }

    /**
     * Default {@link TenantDetailsLoader} — a trivial delegate to {@link TenantRepository#findById},
     * throwing {@link TenantNotFoundException} when the tenant doesn't exist. This is the exact
     * one-line adapter every application otherwise has to write by hand to activate
     * {@code TenantCoreAutoConfiguration}'s {@code TenantContextFilter} (which only registers
     * once a {@link TenantDetailsLoader} bean is present).
     *
     * <p>Skipped when the application already declares its own {@link TenantDetailsLoader}
     * bean (e.g. one backed by a cache, or a completely different tenant store than
     * {@link TenantRepository}).
     */
    @Bean
    @ConditionalOnBean(TenantRepository.class)
    @ConditionalOnMissingBean(TenantDetailsLoader.class)
    public TenantDetailsLoader tenantDetailsLoader(TenantRepository tenantRepository) {
        return tenantId -> tenantRepository.findById(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    @Bean
    @ConditionalOnMissingBean(TenantOnboardingService.class)
    public TenantOnboardingService tenantOnboardingService(
            TenantRepository repository,
            Optional<TenantSchemaInitializer> schemaInitializer,
            ApplicationEventPublisher eventPublisher) {

        return new DefaultTenantOnboardingService(
                repository,
                schemaInitializer.orElse(null),
                eventPublisher);
    }
}


