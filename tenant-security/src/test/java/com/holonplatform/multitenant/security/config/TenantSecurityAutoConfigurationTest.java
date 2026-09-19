package com.holonplatform.multitenant.security.config;

import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.config.TenantCoreAutoConfiguration;
import com.holonplatform.multitenant.security.TenantIdExtractor;
import com.holonplatform.auth.AuthContext;
import com.holonplatform.multitenant.security.aop.TenantFeatureAspect;
import com.holonplatform.multitenant.security.context.SpringSecurityAuthContextRegistrar;
import com.holonplatform.multitenant.security.filter.AuthenticatedPrincipalTenantContextFilter;
import com.holonplatform.multitenant.security.filter.TenantIsolationFilter;
import com.holonplatform.multitenant.security.resolver.AuthenticatedPrincipalTenantResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TenantSecurityAutoConfiguration}.
 */
class TenantSecurityAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TenantSecurityAutoConfiguration.class));

	// -------------------------------------------------------------------------
	// Co-existence with tenant-core (regression: two TenantResolver beans)
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_coexistsWithTenantCore_whenBothResolverBeansPresent() {
		// tenant-core's tenantContextFilterRegistration injects a single TenantResolver by type;
		// tenant-security adds authenticatedPrincipalTenantResolver. The context must still start
		// because tenant-core's composite resolver is @Primary.
		new WebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(
						TenantCoreAutoConfiguration.class, TenantSecurityAutoConfiguration.class))
				.run(ctx -> {
					assertThat(ctx).hasNotFailed();
					assertThat(ctx.getBeansOfType(TenantResolver.class)).hasSizeGreaterThanOrEqualTo(2);
					assertThat(ctx).hasSingleBean(AuthenticatedPrincipalTenantResolver.class);
					// The by-type TenantResolver injection resolves to the @Primary composite.
					assertThat(ctx.getBean(TenantResolver.class))
							.isNotInstanceOf(AuthenticatedPrincipalTenantResolver.class);
				});
	}

	// -------------------------------------------------------------------------
	// TenantIdExtractor
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_registersTenantIdExtractor() {
		contextRunner.run(ctx ->
				assertThat(ctx).hasSingleBean(TenantIdExtractor.class));
	}

	@Test
	void autoConfig_respectsCustomTenantIdExtractor() {
		contextRunner
				.withUserConfiguration(CustomExtractorConfig.class)
				.run(ctx -> {
					TenantIdExtractor extractor = ctx.getBean(TenantIdExtractor.class);
					assertThat(extractor).isInstanceOf(CustomExtractorConfig.FixedExtractor.class);
				});
	}

	// -------------------------------------------------------------------------
	// TenantIsolationFilter
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_registersTenantIsolationFilter() {
		contextRunner.run(ctx ->
				assertThat(ctx).hasSingleBean(FilterRegistrationBean.class));
	}

	@Test
	void autoConfig_isolationFilter_usesConfiguredOrder() {
		contextRunner
				.withPropertyValues("holon.tenant.security.isolation-filter-order=-200")
				.run(ctx -> {
					FilterRegistrationBean<?> reg = ctx.getBean(FilterRegistrationBean.class);
					assertThat(reg.getOrder()).isEqualTo(-200);
					assertThat(reg.getFilter()).isInstanceOf(TenantIsolationFilter.class);
				});
	}

	// -------------------------------------------------------------------------
	// TenantFeatureAspect
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_registersTenantFeatureAspect() {
		contextRunner.run(ctx ->
				assertThat(ctx).hasSingleBean(TenantFeatureAspect.class));
	}

	@Test
	void autoConfig_aspectDisabled_whenPropertyFalse() {
		contextRunner
				.withPropertyValues("holon.tenant.security.aop-enabled=false")
				.run(ctx ->
						assertThat(ctx).doesNotHaveBean(TenantFeatureAspect.class));
	}

	// -------------------------------------------------------------------------
	// Authenticated-principal tenant binding
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_registersPrincipalTenantResolver_byDefault() {
		contextRunner.run(ctx ->
				assertThat(ctx).hasSingleBean(AuthenticatedPrincipalTenantResolver.class));
	}

	@Test
	void autoConfig_principalResolverDisabled_whenPropertyFalse() {
		contextRunner
				.withPropertyValues("holon.tenant.security.principal-fallback-enabled=false")
				.run(ctx ->
						assertThat(ctx).doesNotHaveBean(AuthenticatedPrincipalTenantResolver.class));
	}

	@Test
	void autoConfig_bindingFilterAbsent_whenNoTenantDetailsLoader() {
		// Without a TenantDetailsLoader bean the binding filter must not be registered.
		contextRunner.run(ctx ->
				assertThat(ctx.getBeansOfType(FilterRegistrationBean.class).values())
						.map(FilterRegistrationBean::getFilter)
						.noneMatch(f -> f instanceof AuthenticatedPrincipalTenantContextFilter));
	}

	@Test
	void autoConfig_registersBindingFilter_whenTenantDetailsLoaderPresent() {
		contextRunner
				.withUserConfiguration(LoaderConfig.class)
				.withPropertyValues("holon.tenant.security.principal-fallback-filter-order=-77")
				.run(ctx -> {
					FilterRegistrationBean<?> binding =
							ctx.getBean("authenticatedPrincipalTenantContextFilterRegistration",
									FilterRegistrationBean.class);
					assertThat(binding.getFilter())
							.isInstanceOf(AuthenticatedPrincipalTenantContextFilter.class);
					assertThat(binding.getOrder()).isEqualTo(-77);
				});
	}

	@Test
	void autoConfig_bindingFilterDisabled_whenPropertyFalse() {
		contextRunner
				.withUserConfiguration(LoaderConfig.class)
				.withPropertyValues("holon.tenant.security.principal-fallback-enabled=false")
				.run(ctx ->
						assertThat(ctx.getBeansOfType(FilterRegistrationBean.class).values())
								.map(FilterRegistrationBean::getFilter)
								.noneMatch(f -> f instanceof AuthenticatedPrincipalTenantContextFilter));
	}

	// -------------------------------------------------------------------------
	// Global Holon AuthContext registrar
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_registersAuthContextBean_byDefault() {
		contextRunner.run(ctx ->
				assertThat(ctx).hasSingleBean(AuthContext.class));
	}

	@Test
	void autoConfig_registersAuthContextRegistrar_byDefault() {
		contextRunner.run(ctx ->
				assertThat(ctx).hasSingleBean(SpringSecurityAuthContextRegistrar.class));
	}

	@Test
	void autoConfig_authContextRegistrarDisabled_whenPropertyFalse() {
		contextRunner
				.withPropertyValues("holon.tenant.security.auth-context-registration-enabled=false")
				.run(ctx -> {
					assertThat(ctx).doesNotHaveBean(SpringSecurityAuthContextRegistrar.class);
					assertThat(ctx).doesNotHaveBean(AuthContext.class);
				});
	}

	// -------------------------------------------------------------------------
	// Support config
	// -------------------------------------------------------------------------

	static class LoaderConfig {
		@org.springframework.context.annotation.Bean
		com.holonplatform.multitenant.TenantDetailsLoader tenantDetailsLoader() {
			return id -> com.holonplatform.multitenant.TenantDetails.builder(id).build();
		}
	}

	static class CustomExtractorConfig {
		@org.springframework.context.annotation.Bean
		TenantIdExtractor tenantIdExtractor() {
			return new FixedExtractor();
		}

		static class FixedExtractor implements TenantIdExtractor {
			@Override
			public java.util.Optional<String> extract(org.springframework.security.core.Authentication auth) {
				return java.util.Optional.of("fixed");
			}
		}
	}
}
