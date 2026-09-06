package com.holonplatform.multitenant.onboarding.config;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.config.TenantCoreAutoConfiguration;
import com.holonplatform.multitenant.onboarding.internal.TenantDetailsDatastoreHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves the cross-module default {@link TenantDetailsLoader} bean (registered by
 * {@link TenantOnboardingAutoConfiguration}) actually activates
 * {@code TenantCoreAutoConfiguration}'s {@code TenantContextFilter} — i.e. that the
 * {@code @AutoConfigureAfter(name = ...)} ordering hint on {@link TenantCoreAutoConfiguration}
 * is correct. Without that ordering, {@code @ConditionalOnBean(TenantDetailsLoader.class)}
 * would evaluate before this module's default bean is registered, silently leaving the filter
 * unregistered — exactly the kind of auto-configuration-ordering bug that produces no error at
 * all, just a filter that's mysteriously never active.
 *
 * <p>Uses the same mocked-{@link TenantDetailsDatastoreHelper} pattern as
 * {@link TenantOnboardingAutoConfigurationTest} to avoid needing a real JPA stack.
 */
class TenantDetailsLoaderAutoConfigurationOrderingTest {

	@Test
	void tenantContextFilter_isRegistered_usingOnboardingModulesDefaultTenantDetailsLoader() {
		new WebApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(
						TenantOnboardingAutoConfiguration.class,
						TenantCoreAutoConfiguration.class))
				.withUserConfiguration(DatastoreHelperConfig.class)
				.run(ctx -> {
					assertThat(ctx).hasNotFailed();

					// The default TenantDetailsLoader from TenantOnboardingAutoConfiguration —
					// no user-supplied bean anywhere in this test.
					assertThat(ctx).hasSingleBean(TenantDetailsLoader.class);

					// Which is exactly what must be present for TenantCoreAutoConfiguration to
					// register the filter at all.
					assertThat(ctx).hasSingleBean(FilterRegistrationBean.class);
					FilterRegistrationBean<?> registration = ctx.getBean(FilterRegistrationBean.class);
					assertThat(registration.getFilter().getClass().getSimpleName())
							.isEqualTo("TenantContextFilter");
				});
	}

	@Configuration
	static class DatastoreHelperConfig {
		@Bean
		TenantDetailsDatastoreHelper tenantDetailsDatastoreHelper() {
			TenantDetailsDatastoreHelper helper = mock(TenantDetailsDatastoreHelper.class);
			Map<String, TenantDetails> store = new ConcurrentHashMap<>();
			when(helper.save(any(TenantDetails.class))).thenAnswer(inv -> {
				TenantDetails tenant = inv.getArgument(0);
				store.put(tenant.tenantId(), tenant);
				return tenant;
			});
			when(helper.findById(org.mockito.ArgumentMatchers.anyString()))
					.thenAnswer(inv -> java.util.Optional.ofNullable(store.get(inv.getArgument(0))));
			when(helper.exists(org.mockito.ArgumentMatchers.anyString()))
					.thenAnswer(inv -> store.containsKey(inv.getArgument(0)));
			return helper;
		}
	}
}
