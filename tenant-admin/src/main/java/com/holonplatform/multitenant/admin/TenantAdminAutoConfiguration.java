package com.holonplatform.multitenant.admin;

import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantDetailsRepository;
import com.holonplatform.multitenant.TenantDetailsRepositoryFactory;
import com.holonplatform.multitenant.admin.internal.DatabaseTenantAdminService;
import com.holonplatform.multitenant.admin.internal.TenantDetailsDatastoreHelper;
import com.holonplatform.multitenant.admin.service.TenantAdminService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

import java.util.Optional;

/**
 * Spring Boot auto-configuration for the tenant-admin module.
 *
 * <p>Registers the {@link DatabaseTenantAdminService} as the default
 * {@link TenantAdminService} implementation when no other bean of that type
 * is present in the application context. It is backed by a plain Spring Data
 * {@link TenantDetailsRepository} via {@link TenantDetailsDatastoreHelper} and requires a
 * JPA {@code EntityManagerFactory} bean to be configured (e.g. via
 * {@code holon-starter-jpa-hibernate}).</p>
 *
 * <p>To use a custom persistence-backed implementation, declare a
 * {@code @Bean} of type {@link TenantAdminService} in your application
 * configuration — this auto-configuration will back off automatically.</p>
 *
 * <p><b>Note:</b> {@code @ComponentScan} must NOT be used inside an
 * {@code @AutoConfiguration} class (Spring Boot 4.x enforces this).
 * This module exposes only the {@link TenantAdminService} backend API; any
 * UI (Vaadin, React, REST clients, ...) built on top must be discovered by
 * the application's own component scan.</p>
 */
@AutoConfiguration
@org.springframework.boot.autoconfigure.AutoConfigureAfter(com.holonplatform.jpa.spring.boot.JpaAutoConfiguration.class)
public class TenantAdminAutoConfiguration {

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
     * <p>The bean method is named distinctly ({@code adminTenantDetailsDatastoreHelper})
     * because {@code tenant-onboarding} declares its own, unrelated class with the identical
     * simple name {@code TenantDetailsDatastoreHelper} (each module owns its own helper
     * independently) — Spring bean names are a flat namespace regardless of type, so the
     * method names must not collide even though the types themselves are distinct.
     *
     * <p>Builds its own inline shared {@code EntityManager} (for the raw JPQL used by
     * {@code TenantDetailsDatastoreHelper#findSlice}) — see {@link #tenantDetailsRepository}'s
     * Javadoc for why this is deliberate rather than depending on an {@code EntityManager} bean.
     */
    @Bean
    @ConditionalOnMissingBean(TenantDetailsDatastoreHelper.class)
    public TenantDetailsDatastoreHelper adminTenantDetailsDatastoreHelper(
            TenantDetailsRepository repository, EntityManagerFactory entityManagerFactory) {
        EntityManager entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        return new TenantDetailsDatastoreHelper(repository, entityManager);
    }

    @Bean
    @ConditionalOnMissingBean(TenantAdminService.class)
    public TenantAdminService tenantAdminService(TenantDetailsDatastoreHelper datastoreHelper,
            Optional<TenantDetailsLoader> tenantDetailsLoader) {
        return new DatabaseTenantAdminService(datastoreHelper, tenantDetailsLoader);
    }
}


