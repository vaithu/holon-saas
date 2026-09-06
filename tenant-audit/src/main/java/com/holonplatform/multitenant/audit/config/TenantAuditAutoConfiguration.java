package com.holonplatform.multitenant.audit.config;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.audit.ActorResolver;
import com.holonplatform.multitenant.audit.SecurityContextActorResolver;
import com.holonplatform.multitenant.audit.TenantAuditService;
import com.holonplatform.multitenant.audit.internal.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot auto-configuration for the tenant audit subsystem.
 *
 * <p>Registers the following beans when none of the same type is already present:
 * <ul>
 *   <li>{@link TenantAuditService} — {@link DatabaseTenantAuditService}, backed by Holon
 *       Datastore via {@link AuditEntryDatastoreHelper}. A {@code Datastore} bean must be
 *       configured (e.g. via {@code holon-datastore-jpa}) for this module to start.</li>
 *   <li>{@link ActorResolver} — {@link SecurityContextActorResolver} (named
 *       {@code securityContextActorResolver}), reading the current username from Spring
 *       Security's {@code SecurityContextHolder} (falling back to {@link ActorResolver#SYSTEM}
 *       when nothing is authenticated). This same bean also satisfies Spring Data JPA's
 *       {@code AuditorAware<String>} — see {@link SecurityContextActorResolver}'s Javadoc for
 *       how to wire {@code @EnableJpaAuditing} to it without writing your own
 *       {@code AuditorAware}. {@link SystemActorResolver} (always {@code "system"}, no Spring
 *       Security lookup at all) remains available for explicit use if preferred.</li>
 *   <li>{@link AuditAspect} — conditionally, only when AspectJ is on the classpath</li>
 * </ul>
 */
@AutoConfiguration
@Import(TenantAuditEntityScanRegistrar.class)
public class TenantAuditAutoConfiguration {

    /**
     * Registers the {@link AuditEntryDatastoreHelper} explicitly — it is not
     * {@code @Component}-scanned (it lives in an internal package outside any consuming
     * application's own base package), so this module must construct it itself.
     */
    @Bean
    @ConditionalOnMissingBean(AuditEntryDatastoreHelper.class)
    public AuditEntryDatastoreHelper auditEntryDatastoreHelper(Datastore datastore) {
        return new AuditEntryDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(TenantAuditService.class)
    public TenantAuditService databaseTenantAuditService(AuditEntryDatastoreHelper datastoreHelper) {
        return new DatabaseTenantAuditService(datastoreHelper);
    }

    /**
     * See the class Javadoc — named explicitly ({@code securityContextActorResolver}) so
     * applications can reference it from {@code @EnableJpaAuditing(auditorAwareRef = "...")}
     * without needing to declare their own {@code AuditorAware<String>} bean.
     */
    @Bean(name = "securityContextActorResolver")
    @ConditionalOnMissingBean(ActorResolver.class)
    public SecurityContextActorResolver securityContextActorResolver() {
        return new SecurityContextActorResolver();
    }

    @Bean
    @ConditionalOnMissingBean(AuditAspect.class)
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public AuditAspect auditAspect(TenantAuditService auditService, ActorResolver actorResolver) {
        return new AuditAspect(auditService, actorResolver);
    }
}

