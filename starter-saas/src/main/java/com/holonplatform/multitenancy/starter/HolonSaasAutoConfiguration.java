package com.holonplatform.multitenancy.starter;

import com.holonplatform.multitenant.admin.TenantAdminAutoConfiguration;
import com.holonplatform.multitenant.audit.config.TenantAuditAutoConfiguration;
import com.holonplatform.multitenant.billing.config.TenantBillingAutoConfiguration;
import com.holonplatform.multitenant.config.TenantCoreAutoConfiguration;
import com.holonplatform.multitenant.data.config.TenantDataAutoConfiguration;
import com.holonplatform.multitenant.invitations.config.TenantInvitationAutoConfiguration;
import com.holonplatform.multitenant.limits.config.TenantRateLimitAutoConfiguration;
import com.holonplatform.multitenant.onboarding.config.TenantOnboardingAutoConfiguration;
import com.holonplatform.multitenant.permissions.config.TenantPermissionAutoConfiguration;
import com.holonplatform.multitenant.quotas.config.TenantQuotaAutoConfiguration;
import com.holonplatform.multitenant.security.config.TenantSecurityAutoConfiguration;
import com.holonplatform.multitenant.settings.config.TenantSettingsAutoConfiguration;
import com.holonplatform.multitenant.signup.config.SignupAutoConfiguration;
import com.holonplatform.multitenant.users.config.TenantUserAutoConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Aggregating Spring Boot auto-configuration for the <strong>Holon SaaS Starter</strong>.
 *
 * <p>This starter (and every {@code tenant-*} module it aggregates) is UI-agnostic: it
 * only exposes backend services, repositories and Spring beans. No module here depends
 * on any UI framework — consuming applications are free to build a Vaadin, React, REST
 * or any other UI on top.
 *
 * <p>Its only responsibility is to declare ordering via {@code @AutoConfiguration(after = {...})}
 * so that all module auto-configurations are guaranteed to be fully processed before this one
 * runs, regardless of the order in which Spring Boot discovers the individual module JARs on
 * the classpath.
 *
 * <p><b>Note:</b> {@code @AutoConfigureAfter} was removed in Spring Boot 4.0.
 * Use the {@code after} attribute of {@code @AutoConfiguration} instead.
 *
 * <h2>Module initialisation order</h2>
 * <ol>
 *   <li>{@link TenantCoreAutoConfiguration}      — tenant context, resolver, filter</li>
 *   <li>{@link TenantDataAutoConfiguration}       — schema isolation, DataSource routing</li>
 *   <li>{@link TenantSecurityAutoConfiguration}   — isolation filter, AOP feature guards</li>
 *   <li>{@link TenantOnboardingAutoConfiguration} — onboarding pipeline, tenant repository</li>
 *   <li>{@link TenantAuditAutoConfiguration}      — audit log service</li>
 *   <li>{@link TenantSettingsAutoConfiguration}   — per-tenant key-value settings</li>
 *   <li>{@link TenantRateLimitAutoConfiguration}  — rate-limit AOP aspect and registry</li>
 *   <li>{@link TenantUserAutoConfiguration}       — user invite / activate lifecycle</li>
 *   <li>{@link SignupAutoConfiguration}           — self-service sign-up orchestration (needs app's AccountCredentialsStore)</li>
 *   <li>{@link TenantBillingAutoConfiguration}    — subscription &amp; invoice tracking</li>
 *   <li>{@link TenantAdminAutoConfiguration}      — super-admin backend service</li>
 *   <li>{@link HolonSaasAutoConfiguration}        — <em>this class</em> — emits ready banner</li>
 * </ol>
 *
 * <h2>Customisation</h2>
 * <p>Every individual module auto-configuration uses {@code @ConditionalOnMissingBean}
 * so any application bean of the same type silently overrides the default.  No changes
 * to this class are required for typical customisations.
 */
@AutoConfiguration(after = {
        TenantCoreAutoConfiguration.class,
        TenantDataAutoConfiguration.class,
        TenantSecurityAutoConfiguration.class,
        TenantOnboardingAutoConfiguration.class,
        TenantAuditAutoConfiguration.class,
        TenantSettingsAutoConfiguration.class,
        TenantRateLimitAutoConfiguration.class,
        TenantUserAutoConfiguration.class,
        SignupAutoConfiguration.class,
        TenantBillingAutoConfiguration.class,
        TenantAdminAutoConfiguration.class,
        TenantPermissionAutoConfiguration.class,
        TenantInvitationAutoConfiguration.class,
        TenantQuotaAutoConfiguration.class
        // TenantWhatsAppAutoConfiguration auto-configures independently via its own
        // META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
})
public class HolonSaasAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HolonSaasAutoConfiguration.class);

    /**
     * Logs a single INFO line once all SaaS modules have been initialised.
     * The bean is intentionally {@code static} so it can be created before any
     * other beans in this configuration are processed.
     *
     * <p>Also checks whether the host application has enabled Java Virtual Threads and warns
     * if not — see {@link #checkVirtualThreadsEnabled(Environment)}.
     */
    @Bean
    public static HolonSaasReadyMarker holonSaasReadyMarker(Environment environment) {
        log.info("Holon SaaS starter is ready — all multi-tenant modules initialised.");
        checkVirtualThreadsEnabled(environment);
        return new HolonSaasReadyMarker();
    }

    /**
     * Several modules in this starter (e.g. {@code VirtualThreadOptimizedTenantPermissionService},
     * the request-scoped user-resolution/permission-evaluation caches, {@code TenantAwareTaskDecorator})
     * are designed around Java Virtual Threads and are only expected to sustain very high concurrency
     * (thousands of concurrent requests) when the embedded servlet container actually dispatches
     * requests on virtual threads rather than a bounded platform-thread pool (Tomcat's default is
     * ~200 threads).
     *
     * <p>Spring Boot only enables this when the application explicitly sets
     * {@code spring.threads.virtual.enabled=true} (and runs on Java 21+). This starter cannot force
     * that setting itself — it is a JVM/servlet-container-wide toggle owned by the host application,
     * not by an individual auto-configuration — so it logs a one-time warning instead, to avoid a
     * silent, easy-to-miss capacity ceiling in production.
     */
    private static void checkVirtualThreadsEnabled(Environment environment) {
        boolean virtualThreadsEnabled = environment.getProperty(
                "spring.threads.virtual.enabled", Boolean.class, Boolean.FALSE);
        if (!virtualThreadsEnabled) {
            log.warn("Virtual Threads are NOT enabled ('spring.threads.virtual.enabled' is not "
                    + "'true'). This starter's high-concurrency design (tenant permission/user "
                    + "resolution caches, Virtual Thread-optimized services) assumes requests run on "
                    + "Virtual Threads. Without it, the embedded servlet container falls back to a "
                    + "bounded platform-thread pool (e.g. Tomcat's default ~200 threads), which will "
                    + "cap concurrency well below what this platform is designed to sustain. Set "
                    + "'spring.threads.virtual.enabled=true' in the application configuration "
                    + "(requires Java 21+).");
            return;
        }
        int javaFeatureVersion = Runtime.version().feature();
        if (javaFeatureVersion < 21) {
            log.warn("'spring.threads.virtual.enabled' is set to 'true' but the running JVM is "
                    + "Java {}. Virtual Threads require Java 21+ — this setting has no effect and "
                    + "the application is still running on a bounded platform-thread pool.",
                    javaFeatureVersion);
        }
    }

    /**
     * Marker object returned by {@link #holonSaasReadyMarker()}.
     * Applications can inject this bean to express a dependency on the full SaaS stack:
     * <pre>{@code
     * @Autowired HolonSaasReadyMarker saasReady; // guarantees all modules are up
     * }</pre>
     */
    public static final class HolonSaasReadyMarker {
        private HolonSaasReadyMarker() {}
    }
}

