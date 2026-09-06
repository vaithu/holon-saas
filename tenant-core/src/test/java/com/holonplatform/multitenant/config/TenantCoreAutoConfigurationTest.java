package com.holonplatform.multitenant.config;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.async.TenantAwareTaskDecorator;
import com.holonplatform.multitenant.filter.TenantContextFilter;
import com.holonplatform.multitenant.resolver.CompositeTenantResolver;
import com.holonplatform.multitenant.resolver.HeaderTenantResolver;
import com.holonplatform.multitenant.resolver.PathPrefixTenantResolver;
import com.holonplatform.multitenant.resolver.SessionCachingTenantResolver;
import com.holonplatform.multitenant.resolver.SubdomainTenantResolver;
import com.holonplatform.multitenant.testing.EntityMappingValidationRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TenantCoreAutoConfiguration}.
 */
class TenantCoreAutoConfigurationTest {

	private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TenantCoreAutoConfiguration.class));

	// -------------------------------------------------------------------------
	// TenantResolver
	// -------------------------------------------------------------------------

	@Test
	void autoConfiguration_createsTenantResolver_withDefaultSubdomainStrategy() {
		contextRunner.run(ctx -> {
			assertThat(ctx).hasSingleBean(TenantResolver.class);
			assertThat(ctx.getBean(TenantResolver.class))
					.isInstanceOf(SubdomainTenantResolver.class);
		});
	}

	@Test
	void autoConfiguration_usesHeaderStrategy_whenConfigured() {
		contextRunner
				.withPropertyValues("holon.tenant.strategy=HEADER")
				.run(ctx -> {
					assertThat(ctx).hasSingleBean(TenantResolver.class);
					assertThat(ctx.getBean(TenantResolver.class))
							.isInstanceOf(HeaderTenantResolver.class);
				});
	}

	@Test
	void autoConfiguration_usesCompositeStrategy_whenConfigured() {
		contextRunner
				.withPropertyValues("holon.tenant.strategy=COMPOSITE")
				.run(ctx -> {
					assertThat(ctx.getBean(TenantResolver.class))
							.isInstanceOf(CompositeTenantResolver.class);
				});
	}

	@Test
	void autoConfiguration_usesPathStrategy_wrappedWithSessionCaching_byDefault() {
		contextRunner
				.withPropertyValues("holon.tenant.strategy=PATH")
				.run(ctx -> {
					TenantResolver bean = ctx.getBean(TenantResolver.class);
					assertThat(bean).isInstanceOf(SessionCachingTenantResolver.class);
					assertThat(((SessionCachingTenantResolver) bean).getDelegate())
							.isInstanceOf(PathPrefixTenantResolver.class);
				});
	}

	@Test
	void autoConfiguration_pathStrategy_sessionCachingCanBeDisabledExplicitly() {
		contextRunner
				.withPropertyValues("holon.tenant.strategy=PATH", "holon.tenant.session-cache=false")
				.run(ctx -> assertThat(ctx.getBean(TenantResolver.class))
						.isInstanceOf(PathPrefixTenantResolver.class));
	}

	@Test
	void autoConfiguration_sessionCachingCanBeForcedOn_forOtherStrategies() {
		contextRunner
				.withPropertyValues("holon.tenant.strategy=HEADER", "holon.tenant.session-cache=true")
				.run(ctx -> {
					TenantResolver bean = ctx.getBean(TenantResolver.class);
					assertThat(bean).isInstanceOf(SessionCachingTenantResolver.class);
					assertThat(((SessionCachingTenantResolver) bean).getDelegate())
							.isInstanceOf(HeaderTenantResolver.class);
				});
	}

	@Test
	void autoConfiguration_respectsCustomTenantResolverBean() {
		contextRunner
				.withUserConfiguration(CustomResolverConfig.class)
				.run(ctx -> {
					assertThat(ctx).hasSingleBean(TenantResolver.class);
					TenantResolver bean = ctx.getBean(TenantResolver.class);
					assertThat(bean).isInstanceOf(CustomResolverConfig.FixedResolver.class);
				});
	}

	// -------------------------------------------------------------------------
	// TenantContextFilter
	// -------------------------------------------------------------------------

	@Test
	void autoConfiguration_doesNotRegisterFilter_withoutTenantDetailsLoader() {
		contextRunner.run(ctx -> {
			assertThat(ctx).doesNotHaveBean("tenantContextFilterRegistration");
		});
	}

	@Test
	void autoConfiguration_registersFilter_whenTenantDetailsLoaderPresent() {
		contextRunner
				.withUserConfiguration(LoaderConfig.class)
				.run(ctx -> {
					assertThat(ctx).hasSingleBean(FilterRegistrationBean.class);
					FilterRegistrationBean<?> registration = ctx.getBean(FilterRegistrationBean.class);
					assertThat(registration.getFilter()).isInstanceOf(TenantContextFilter.class);
				});
	}

	@Test
	void autoConfiguration_filterOrder_isConfigurable() {
		contextRunner
				.withUserConfiguration(LoaderConfig.class)
				.withPropertyValues("holon.tenant.filter-order=-200")
				.run(ctx -> {
					FilterRegistrationBean<?> registration = ctx.getBean(FilterRegistrationBean.class);
					assertThat(registration.getOrder()).isEqualTo(-200);
				});
	}

	// -------------------------------------------------------------------------
	// TenantAwareTaskDecorator
	// -------------------------------------------------------------------------

	@Test
	void autoConfiguration_createsTenantAwareTaskDecorator() {
		contextRunner.run(ctx ->
				assertThat(ctx).hasSingleBean(TenantAwareTaskDecorator.class));
	}

	@Test
	void autoConfiguration_respectsCustomTaskDecoratorBean() {
		contextRunner
				.withUserConfiguration(CustomDecoratorConfig.class)
				.run(ctx -> {
					assertThat(ctx).hasSingleBean(TenantAwareTaskDecorator.class);
					// Should be the custom one, not the auto-configured one
					assertThat(ctx.getBean(TenantAwareTaskDecorator.class))
							.isSameAs(CustomDecoratorConfig.INSTANCE);
				});
	}

	// -------------------------------------------------------------------------
	// EntityMappingValidationRunner
	// -------------------------------------------------------------------------

	@Test
	void autoConfiguration_doesNotRegisterEntityMappingValidationRunner_withoutEntityManagerFactory() {
		contextRunner.run(ctx -> assertThat(ctx).doesNotHaveBean(EntityMappingValidationRunner.class));
	}

	@Test
	void autoConfiguration_registersEntityMappingValidationRunner_whenEntityManagerFactoryPresent() {
		contextRunner
				.withUserConfiguration(EntityManagerFactoryConfig.class)
				.run(ctx -> assertThat(ctx).hasSingleBean(EntityMappingValidationRunner.class));
	}

	@Test
	void autoConfiguration_entityMappingValidationRunner_canBeDisabledViaProperty() {
		contextRunner
				.withUserConfiguration(EntityManagerFactoryConfig.class)
				.withPropertyValues("holon.tenant.entity-validation.enabled=false")
				.run(ctx -> assertThat(ctx).doesNotHaveBean(EntityMappingValidationRunner.class));
	}

	// -------------------------------------------------------------------------
	// TenantScopedEventListenerAspect
	// -------------------------------------------------------------------------

	@Test
	void autoConfiguration_registersTenantScopedEventListenerAspect_whenAspectJOnClasspath() {
		// aspectjweaver is an optional (but present-for-this-module's-own-tests) dependency —
		// see tenant-core/pom.xml.
		contextRunner.run(ctx -> assertThat(ctx)
				.hasSingleBean(com.holonplatform.multitenant.event.TenantScopedEventListenerAspect.class));
	}

	@Test
	void autoConfiguration_respectsCustomTenantScopedEventListenerAspectBean() {
		contextRunner
				.withUserConfiguration(CustomAspectConfig.class)
				.run(ctx -> assertThat(ctx)
						.hasSingleBean(com.holonplatform.multitenant.event.TenantScopedEventListenerAspect.class));
	}

	// -------------------------------------------------------------------------
	// Support configurations
	// -------------------------------------------------------------------------

	@Configuration
	static class CustomResolverConfig {
		@Bean
		TenantResolver tenantResolver() {
			return new FixedResolver();
		}

		static class FixedResolver implements TenantResolver {
			@Override
			public Optional<String> resolveTenantId(jakarta.servlet.http.HttpServletRequest request) {
				return Optional.of("fixed");
			}
		}
	}

	@Configuration
	static class LoaderConfig {
		@Bean
		TenantDetailsLoader tenantDetailsLoader() {
			return id -> TenantDetails.builder(id).build();
		}
	}

	@Configuration
	static class CustomDecoratorConfig {
		static final TenantAwareTaskDecorator INSTANCE = new TenantAwareTaskDecorator();

		@Bean
		TenantAwareTaskDecorator tenantAwareTaskDecorator() {
			return INSTANCE;
		}
	}

	@Configuration
	static class EntityManagerFactoryConfig {
		@Bean
		jakarta.persistence.EntityManagerFactory entityManagerFactory() {
			jakarta.persistence.EntityManagerFactory emf = org.mockito.Mockito.mock(
					jakarta.persistence.EntityManagerFactory.class);
			jakarta.persistence.metamodel.Metamodel metamodel = org.mockito.Mockito.mock(
					jakarta.persistence.metamodel.Metamodel.class);
			org.mockito.Mockito.when(metamodel.getEntities()).thenReturn(java.util.Collections.emptySet());
			org.mockito.Mockito.when(emf.getMetamodel()).thenReturn(metamodel);
			return emf;
		}
	}

	@Configuration
	static class CustomAspectConfig {
		@Bean
		com.holonplatform.multitenant.event.TenantScopedEventListenerAspect tenantScopedEventListenerAspect() {
			return new com.holonplatform.multitenant.event.TenantScopedEventListenerAspect();
		}
	}
}

