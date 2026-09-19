package com.holonplatform.multitenant.security.config;

import com.holonplatform.auth.AuthContext;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.security.TenantIdExtractor;
import com.holonplatform.multitenant.security.aop.TenantFeatureAspect;
import com.holonplatform.multitenant.security.context.SpringSecurityAuthContextRegistrar;
import com.holonplatform.multitenant.security.filter.AuthenticatedPrincipalTenantContextFilter;
import com.holonplatform.multitenant.security.filter.TenantIsolationFilter;
import com.holonplatform.multitenant.security.resolver.AuthenticatedPrincipalTenantResolver;
import com.holonplatform.spring.security.SpringSecurity;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the multi-tenant security layer.
 *
 * <h2>Beans created</h2>
 * <ul>
 *   <li><strong>{@link TenantIdExtractor}</strong> — default: reads from
 *       {@link com.holonplatform.multitenant.security.TenantAwareUserDetails}</li>
 *   <li><strong>{@link TenantIsolationFilter}</strong> — validates user tenant == request tenant;
 *       only registered when {@code spring-security-web} is on the classpath</li>
 *   <li><strong>{@link AuthenticatedPrincipalTenantResolver}</strong> — resolves the tenant from the
 *       authenticated principal, so single-page-UI round-trips (e.g. Vaadin Flow UIDL/heartbeat
 *       POSTs) that carry no tenant signal still resolve; disable via
 *       {@code holon.tenant.security.principal-fallback-enabled=false}</li>
 *   <li><strong>{@link AuthenticatedPrincipalTenantContextFilter}</strong> — binds
 *       {@link com.holonplatform.multitenant.TenantContext} from that resolver after Spring Security
 *       has authenticated the user; only registered when a {@link TenantDetailsLoader} bean exists</li>
 *   <li><strong>{@link AuthContext}</strong> &mdash; a Spring bean backed by Spring Security's
 *       {@code SecurityContextHolder}, resolved through Holon's Spring {@code BeanFactory} context
 *       scope so {@code AuthContext.require()} works for Holon-Spring UIs (e.g. the Holon Vaadin Flow
 *       navigator); disable via {@code holon.tenant.security.auth-context-registration-enabled=false}</li>
 *   <li><strong>{@link SpringSecurityAuthContextRegistrar}</strong> &mdash; classloader-scope fallback
 *       for the same {@link AuthContext}, consulted only when no Holon Spring bean-context scope is
 *       registered; disable via the same property</li>
 *   <li><strong>{@link TenantFeatureAspect}</strong> — AOP gating for
 *       {@link com.holonplatform.multitenant.security.aop.CheckTenantFeature}
 *       and {@link com.holonplatform.multitenant.security.aop.RequiresPlan};
 *       only registered when {@code aspectjweaver} is on the classpath</li>
 * </ul>
 */
@AutoConfiguration(afterName = "com.holonplatform.multitenant.onboarding.config.TenantOnboardingAutoConfiguration")
@EnableConfigurationProperties(TenantSecurityProperties.class)
public class TenantSecurityAutoConfiguration {

	// -------------------------------------------------------------------------
	// TenantIdExtractor
	// -------------------------------------------------------------------------

	@Bean
	@ConditionalOnMissingBean(TenantIdExtractor.class)
	public TenantIdExtractor tenantIdExtractor() {
		return TenantIdExtractor.fromTenantAwareUserDetails();
	}

	// -------------------------------------------------------------------------
	// TenantIsolationFilter
	// -------------------------------------------------------------------------

	@Bean
	@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
	@ConditionalOnMissingBean(name = "tenantIsolationFilterRegistration")
	public FilterRegistrationBean<TenantIsolationFilter> tenantIsolationFilterRegistration(
			TenantIdExtractor tenantIdExtractor,
			TenantSecurityProperties properties,
			ApplicationEventPublisher eventPublisher) {

		TenantIsolationFilter filter = new TenantIsolationFilter(tenantIdExtractor);
		filter.setEnforced(properties.isIsolationEnforced());
		filter.setEventPublisher(eventPublisher);

		FilterRegistrationBean<TenantIsolationFilter> registration =
				new FilterRegistrationBean<>(filter);
		registration.setName("tenantIsolationFilter");
		registration.setOrder(properties.getIsolationFilterOrder());
		registration.addUrlPatterns("/*");
		return registration;
	}

	// -------------------------------------------------------------------------
	// Authenticated-principal tenant binding (single-page-UI fallback)
	// -------------------------------------------------------------------------

	/**
	 * Resolves the tenant from the authenticated principal. Used by
	 * {@link AuthenticatedPrincipalTenantContextFilter}; also usable standalone / composable into a
	 * {@link com.holonplatform.multitenant.resolver.CompositeTenantResolver}.
	 */
	@Bean
	@ConditionalOnMissingBean(AuthenticatedPrincipalTenantResolver.class)
	@ConditionalOnProperty(name = "holon.tenant.security.principal-fallback-enabled",
			havingValue = "true", matchIfMissing = true)
	public AuthenticatedPrincipalTenantResolver authenticatedPrincipalTenantResolver(
			TenantIdExtractor tenantIdExtractor) {
		return new AuthenticatedPrincipalTenantResolver(tenantIdExtractor);
	}

	/**
	 * Registers the {@link AuthenticatedPrincipalTenantContextFilter}, which binds the tenant
	 * context from the authenticated principal for requests that could not otherwise resolve one.
	 *
	 * <p>Only created when a {@link TenantDetailsLoader} bean is present (the same trigger that
	 * activates tenant-core's own filter) and Spring Security web is on the classpath.
	 */
	@Bean
	@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
	@ConditionalOnBean(TenantDetailsLoader.class)
	@ConditionalOnMissingBean(name = "authenticatedPrincipalTenantContextFilterRegistration")
	@ConditionalOnProperty(name = "holon.tenant.security.principal-fallback-enabled",
			havingValue = "true", matchIfMissing = true)
	public FilterRegistrationBean<AuthenticatedPrincipalTenantContextFilter>
			authenticatedPrincipalTenantContextFilterRegistration(
				AuthenticatedPrincipalTenantResolver principalTenantResolver,
				TenantDetailsLoader tenantDetailsLoader,
				TenantSecurityProperties properties) {

		AuthenticatedPrincipalTenantContextFilter filter =
				new AuthenticatedPrincipalTenantContextFilter(principalTenantResolver, tenantDetailsLoader);

		FilterRegistrationBean<AuthenticatedPrincipalTenantContextFilter> registration =
				new FilterRegistrationBean<>(filter);
		registration.setName("authenticatedPrincipalTenantContextFilter");
		registration.setOrder(properties.getPrincipalFallbackFilterOrder());
		registration.addUrlPatterns("/*");
		return registration;
	}

	// -------------------------------------------------------------------------
	// Global Holon AuthContext (Spring Security-backed)
	// -------------------------------------------------------------------------

	/**
	 * Exposes a Holon {@link AuthContext} backed by Spring Security's {@code SecurityContextHolder} as
	 * a Spring bean, so that {@code AuthContext.getCurrent()} / {@code AuthContext.require()} resolve
	 * for Holon-aware UIs (most notably the Holon Vaadin Flow navigator's route guards) through Holon's
	 * Spring {@code BeanFactory} context scope.
	 *
	 * <p>This is the primary mechanism for Holon-Spring applications (the classloader-scope
	 * {@link SpringSecurityAuthContextRegistrar} below is only consulted when no Holon Spring
	 * bean-context scope is registered). The returned {@link AuthContext} is stateless &mdash; it reads
	 * the current thread's {@code SecurityContextHolder} on each call &mdash; so a single shared bean
	 * correctly reflects the authenticated principal of whichever request thread asks for it.
	 *
	 * <p>Backs off if the application already defines its own {@link AuthContext} bean. Only created
	 * when Spring Security and Holon Spring Security are on the classpath. Disable via
	 * {@code holon.tenant.security.auth-context-registration-enabled=false}.
	 */
	@Bean
	@ConditionalOnClass(name = {
			"org.springframework.security.core.context.SecurityContextHolder",
			"com.holonplatform.spring.security.SpringSecurity"})
	@ConditionalOnMissingBean(AuthContext.class)
	@ConditionalOnProperty(name = "holon.tenant.security.auth-context-registration-enabled",
			havingValue = "true", matchIfMissing = true)
	public AuthContext springSecurityAuthContext() {
		return SpringSecurity.authContext();
	}

	/**
	 * Fallback that publishes the Spring Security-backed {@link AuthContext} into Holon's
	 * <em>classloader</em> context scope, for Holon contexts that have no Spring {@code BeanFactory}
	 * context scope registered (in which case the {@link #springSecurityAuthContext()} bean above,
	 * though present in the Spring context, would not be resolvable through the Holon
	 * {@link com.holonplatform.core.Context}). Publication is non-destructive (see
	 * {@link SpringSecurityAuthContextRegistrar}); in a standard Holon-Spring application this simply
	 * complements the bean above. Disable via
	 * {@code holon.tenant.security.auth-context-registration-enabled=false}.
	 */
	@Bean
	@ConditionalOnClass(name = {
			"org.springframework.security.core.context.SecurityContextHolder",
			"com.holonplatform.spring.security.SpringSecurity"})
	@ConditionalOnMissingBean(SpringSecurityAuthContextRegistrar.class)
	@ConditionalOnProperty(name = "holon.tenant.security.auth-context-registration-enabled",
			havingValue = "true", matchIfMissing = true)
	public SpringSecurityAuthContextRegistrar springSecurityAuthContextRegistrar() {
		return new SpringSecurityAuthContextRegistrar();
	}

	// -------------------------------------------------------------------------
	// TenantFeatureAspect
	// -------------------------------------------------------------------------

	@Bean
	@ConditionalOnClass(Aspect.class)
	@ConditionalOnMissingBean(TenantFeatureAspect.class)
	@ConditionalOnProperty(name = "holon.tenant.security.aop-enabled", havingValue = "true", matchIfMissing = true)
	public TenantFeatureAspect tenantFeatureAspect() {
		return new TenantFeatureAspect();
	}
}

