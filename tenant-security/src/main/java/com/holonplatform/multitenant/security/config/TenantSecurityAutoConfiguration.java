package com.holonplatform.multitenant.security.config;

import com.holonplatform.multitenant.security.TenantIdExtractor;
import com.holonplatform.multitenant.security.aop.TenantFeatureAspect;
import com.holonplatform.multitenant.security.filter.TenantIsolationFilter;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
 *   <li><strong>{@link TenantFeatureAspect}</strong> — AOP gating for
 *       {@link com.holonplatform.multitenant.security.aop.CheckTenantFeature}
 *       and {@link com.holonplatform.multitenant.security.aop.RequiresPlan};
 *       only registered when {@code aspectjweaver} is on the classpath</li>
 * </ul>
 */
@AutoConfiguration
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

