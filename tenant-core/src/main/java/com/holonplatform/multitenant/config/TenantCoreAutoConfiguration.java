package com.holonplatform.multitenant.config;

import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.async.TenantAwareTaskDecorator;
import com.holonplatform.multitenant.filter.TenantContextFilter;
import com.holonplatform.multitenant.resolver.CompositeTenantResolver;
import com.holonplatform.multitenant.resolver.HeaderTenantResolver;
import com.holonplatform.multitenant.resolver.JwtClaimTenantResolver;
import com.holonplatform.multitenant.resolver.PathPrefixTenantResolver;
import com.holonplatform.multitenant.resolver.SessionCachingTenantResolver;
import com.holonplatform.multitenant.resolver.SubdomainTenantResolver;
import com.holonplatform.multitenant.testing.EntityMappingValidationRunner;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Spring Boot auto-configuration for the multi-tenant core.
 *
 * <h2>What gets created automatically</h2>
 * <ul>
 *   <li><strong>{@link TenantResolver}</strong> — built from
 *       {@link TenantProperties#getStrategy()}, unless a custom bean is already present.
 *       Automatically wrapped with {@link com.holonplatform.multitenant.resolver.SessionCachingTenantResolver}
 *       for the {@code PATH} strategy (see {@link TenantProperties#getSessionCache()} to
 *       override this for any strategy).</li>
 *   <li><strong>{@link TenantContextFilter}</strong> — registered as a Servlet filter
 *       with order {@link TenantProperties#getFilterOrder()}, but <em>only</em> when a
 *       {@link TenantDetailsLoader} bean is available in the context. Without a loader,
 *       no filter is registered and no error is thrown.</li>
 *   <li><strong>{@link TenantAwareTaskDecorator}</strong> — a bean for wiring into
 *       async executors, unless already present.</li>
 *   <li><strong>{@link EntityMappingValidationRunner}</strong> — automatically validates every
 *       JPA entity via {@link com.holonplatform.multitenant.testing.HolonEntityMappingValidator}
 *       at {@code ApplicationReadyEvent}, when an {@code EntityManagerFactory} bean is present.
 *       Disable with {@code holon.tenant.entity-validation.enabled=false}; see
 *       {@link EntityValidationProperties} for all options.</li>
 *   <li><strong>{@link com.holonplatform.multitenant.event.TenantScopedEventListenerAspect}</strong>
 *       — auto-binds {@code TenantContext} around {@code @EventListener} methods receiving a
 *       {@link com.holonplatform.multitenant.event.TenantScopedEvent}, when AspectJ is on the
 *       classpath.</li>
 * </ul>
 *
 * <h2>Application responsibilities</h2>
 * <p>None, if {@code tenant-onboarding} is on the classpath: its auto-configuration registers
 * a default {@link TenantDetailsLoader} bean (a trivial delegate to its own
 * {@code TenantRepository}), which is what activates the filter. Register a custom
 * {@link TenantDetailsLoader} bean yourself only to override that default:
 * <pre>{@code
 * @Service
 * public class DatabaseTenantLoader implements TenantDetailsLoader {
 *     public TenantDetails load(String tenantId) { ... }
 * }
 * }</pre>
 */
@AutoConfiguration
// The entity-mapping validator below conditions on an EntityManagerFactory, which Hibernate's
// auto-configuration contributes. @ConditionalOnBean is evaluated in auto-configuration order,
// so that ordering has to be declared: today it happens to hold indirectly, via the onboarding
// auto-configuration, which means it would quietly stop holding if that module were removed and
// the boot-time validation would vanish without a word. Listed by name because the package moved
// between Boot 3 and Boot 4; unknown names are ignored.
@org.springframework.boot.autoconfigure.AutoConfigureAfter(
		name = { "com.holonplatform.multitenant.onboarding.config.TenantOnboardingAutoConfiguration",
				"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
				"org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration" })
@EnableConfigurationProperties({ TenantProperties.class, EntityValidationProperties.class })
@Import(TenantCoreEntityScanRegistrar.class)
public class TenantCoreAutoConfiguration {

	static {
		// Must run before any holon-saas entity (TenantDetails and friends) is ever
		// introspected by Holon's Datastore — see TenantCoreBeanPropertyFix for the full
		// rationale. Safe here: Spring Boot loads @AutoConfiguration classes (triggering
		// this static initializer) during context startup, well before any HTTP request
		// (and therefore any entity save) can occur.
		TenantCoreBeanPropertyFix.ensureRegistered();
	}

	// -------------------------------------------------------------------------
	// TenantResolver
	// -------------------------------------------------------------------------

	/**
	 * Creates the default {@link TenantResolver} based on the configured strategy.
	 * Skipped when the application already declares a {@code TenantResolver} bean.
	 *
	 * <p>Marked {@link Primary} so it remains the resolver injected by type (e.g. into
	 * {@link #tenantContextFilterRegistration}) even when auxiliary {@code TenantResolver} beans are
	 * present — for example tenant-security's authenticated-principal resolver.
	 */
	@Bean
	@Primary
	@ConditionalOnMissingBean(TenantResolver.class)
	public TenantResolver tenantResolver(TenantProperties properties) {
		TenantResolver resolver = switch (properties.getStrategy()) {
			case SUBDOMAIN -> new SubdomainTenantResolver();
			case HEADER    -> new HeaderTenantResolver(properties.getHeaderName());
			case PATH      -> new PathPrefixTenantResolver(properties.getPathPrefix());
			case JWT       -> new JwtClaimTenantResolver(properties.getJwtClaim());
			case COMPOSITE -> new CompositeTenantResolver(
					new SubdomainTenantResolver(),
					new HeaderTenantResolver(properties.getHeaderName()),
					new PathPrefixTenantResolver(properties.getPathPrefix())
			);
		};
		return isSessionCacheEnabled(properties) ? new SessionCachingTenantResolver(resolver) : resolver;
	}

	/**
	 * Determines whether the resolved {@link TenantResolver} should be wrapped with
	 * {@link SessionCachingTenantResolver}. See {@link TenantProperties#getSessionCache()}
	 * for the auto-detection rule.
	 */
	private static boolean isSessionCacheEnabled(TenantProperties properties) {
		Boolean explicit = properties.getSessionCache();
		if (explicit != null) {
			return explicit;
		}
		return properties.getStrategy() == TenantProperties.ResolutionStrategy.PATH;
	}

	// -------------------------------------------------------------------------
	// TenantContextFilter
	// -------------------------------------------------------------------------

	/**
	 * Registers the {@link TenantContextFilter} as a Servlet filter.
	 *
	 * <p>The filter is only created when a {@link TenantDetailsLoader} bean is present —
	 * this keeps the auto-configuration inert in projects that haven't wired a loader yet.
	 */
	@Bean
	@ConditionalOnBean(TenantDetailsLoader.class)
	@ConditionalOnMissingBean(name = "tenantContextFilterRegistration")
	public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration(
			TenantResolver tenantResolver,
			TenantDetailsLoader tenantDetailsLoader,
			TenantProperties properties,
			ApplicationEventPublisher eventPublisher) {

		TenantContextFilter filter = new TenantContextFilter(tenantResolver, tenantDetailsLoader);
		filter.setRequired(properties.isRequired());
		filter.setDeferMissingTenantToChain(properties.isDeferMissingTenantToChain());
		filter.setIgnoredPaths(properties.getIgnoredPaths());
		filter.setEventPublisher(eventPublisher);

		FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setName("tenantContextFilter");
		registration.setOrder(properties.getFilterOrder());
		registration.addUrlPatterns("/*");
		return registration;
	}

	// -------------------------------------------------------------------------
	// TenantAwareTaskDecorator
	// -------------------------------------------------------------------------

	/**
	 * Registers the {@link TenantAwareTaskDecorator} for wiring into async executors.
	 * Skipped when the application already declares one.
	 */
	@Bean
	@ConditionalOnMissingBean(TenantAwareTaskDecorator.class)
	public TenantAwareTaskDecorator tenantAwareTaskDecorator() {
		return new TenantAwareTaskDecorator();
	}

	// -------------------------------------------------------------------------
	// holon-core bridge
	// -------------------------------------------------------------------------

	/**
	 * Bridges the already-resolved tenant into holon-core's {@code Context}-based
	 * {@link com.holonplatform.core.tenancy.TenantResolver} so {@code @EnableTenantScope}
	 * / {@code @ScopeTenant} (and anything else in the Holon ecosystem that relies on it,
	 * e.g. {@code holon-jdbc}'s {@code MultiTenantDataSource}) work automatically, with
	 * zero extra wiring from the consuming application.
	 *
	 * <p>Skipped gracefully when {@code holon-core} is not on the classpath, and when the
	 * application already declares its own {@code com.holonplatform.core.tenancy.TenantResolver}
	 * bean.
	 */
	@Bean
	@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(com.holonplatform.core.tenancy.TenantResolver.class)
	@ConditionalOnMissingBean(com.holonplatform.core.tenancy.TenantResolver.class)
	public com.holonplatform.core.tenancy.TenantResolver holonCoreTenantResolver() {
		return () -> java.util.Optional.ofNullable(com.holonplatform.multitenant.TenantContext.peekTenantId());
	}

	// -------------------------------------------------------------------------
	// Boot-time entity mapping validation
	// -------------------------------------------------------------------------

	/**
	 * Registers {@link EntityMappingValidationRunner}, which automatically runs
	 * {@link com.holonplatform.multitenant.testing.HolonEntityMappingValidator} against every
	 * entity discoverable via the application's {@code EntityManagerFactory} metamodel at
	 * {@code ApplicationReadyEvent} — giving every {@code holon-saas} application this
	 * protection "for free", without needing to remember to write the recommended dedicated
	 * test.
	 *
	 * <p>Only registered when an {@code EntityManagerFactory} bean is present (skipped
	 * gracefully in non-JPA applications) and
	 * {@code holon.tenant.entity-validation.enabled} is {@code true} (the default). See
	 * {@link EntityValidationProperties} for the full set of {@code holon.tenant.entity-validation.*}
	 * settings.
	 */
	@Bean
	@ConditionalOnClass(EntityManagerFactory.class)
	@ConditionalOnBean(EntityManagerFactory.class)
	@ConditionalOnProperty(prefix = "holon.tenant.entity-validation", name = "enabled", havingValue = "true",
			matchIfMissing = true)
	@ConditionalOnMissingBean(EntityMappingValidationRunner.class)
	public EntityMappingValidationRunner entityMappingValidationRunner(EntityManagerFactory entityManagerFactory,
			EntityValidationProperties entityValidationProperties) {
		return new EntityMappingValidationRunner(entityManagerFactory, entityValidationProperties);
	}

	// -------------------------------------------------------------------------
	// Tenant-scoped @EventListener auto-binding
	// -------------------------------------------------------------------------

	/**
	 * Registers {@link com.holonplatform.multitenant.event.TenantScopedEventListenerAspect},
	 * which automatically binds {@link com.holonplatform.multitenant.TenantContext} around any
	 * {@code @EventListener} method receiving a
	 * {@link com.holonplatform.multitenant.event.TenantScopedEvent} — see that interface's
	 * Javadoc for the full rationale.
	 *
	 * <p>Only registered when AspectJ is on the classpath (optional dependency) — skipped
	 * gracefully otherwise. This is a safety net on top of, not a replacement for, explicit
	 * {@code TenantContextTemplate} binding in listener code — see
	 * {@code new-saas-app-checklist.md}.
	 */
	@Bean
	@ConditionalOnMissingBean(com.holonplatform.multitenant.event.TenantScopedEventListenerAspect.class)
	@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
	public com.holonplatform.multitenant.event.TenantScopedEventListenerAspect tenantScopedEventListenerAspect() {
		return new com.holonplatform.multitenant.event.TenantScopedEventListenerAspect();
	}
}

