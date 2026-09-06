package com.holonplatform.multitenant.security.config;

import com.holonplatform.multitenant.security.TenantIdExtractor;
import com.holonplatform.multitenant.security.aop.TenantFeatureAspect;
import com.holonplatform.multitenant.security.filter.TenantIsolationFilter;
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
	// Support config
	// -------------------------------------------------------------------------

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

