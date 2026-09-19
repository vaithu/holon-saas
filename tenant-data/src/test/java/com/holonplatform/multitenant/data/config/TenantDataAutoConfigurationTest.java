package com.holonplatform.multitenant.data.config;

import com.holonplatform.jdbc.TenantDataSourceProvider;
import com.holonplatform.multitenant.data.SchemaInitSqlProvider;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.TenantSchemaStrategy;
import com.holonplatform.multitenant.data.datasource.BoundedTenantDataSourceRegistry;
import com.holonplatform.multitenant.data.datasource.SchemaPerTenantDataSourceProvider;
import com.holonplatform.multitenant.data.datasource.TenantAwareDataSource;
import com.holonplatform.multitenant.data.migration.TenantSchemaInitializer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TenantDataAutoConfiguration}.
 */
class TenantDataAutoConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(TenantDataAutoConfiguration.class));

	// -------------------------------------------------------------------------
	// SchemaNameResolver
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_registersSchemaNameResolver_withDefaultPrefix() {
		contextRunner.run(ctx -> {
			assertThat(ctx).hasSingleBean(SchemaNameResolver.class);
			SchemaNameResolver resolver = ctx.getBean(SchemaNameResolver.class);
			assertThat(resolver.resolveSchema("acme")).isEqualTo("tenant_acme");
		});
	}

	@Test
	void autoConfig_respectsCustomSchemaPrefix() {
		contextRunner
				.withPropertyValues("holon.tenant.data.schema-prefix=t_",
						"holon.tenant.data.schema-suffix=_db")
				.run(ctx -> {
					SchemaNameResolver resolver = ctx.getBean(SchemaNameResolver.class);
					assertThat(resolver.resolveSchema("acme")).isEqualTo("t_acme_db");
				});
	}

	@Test
	void autoConfig_respectsCustomSchemaNameResolverBean() {
		contextRunner
				.withUserConfiguration(CustomResolverConfig.class)
				.run(ctx -> {
					SchemaNameResolver resolver = ctx.getBean(SchemaNameResolver.class);
					assertThat(resolver.resolveSchema("acme")).isEqualTo("fixed_schema");
				});
	}

	// -------------------------------------------------------------------------
	// SchemaInitSqlProvider
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_registersPostgresqlInitSqlByDefault() {
		contextRunner.run(ctx -> {
			assertThat(ctx).hasSingleBean(SchemaInitSqlProvider.class);
			SchemaInitSqlProvider provider = ctx.getBean(SchemaInitSqlProvider.class);
			assertThat(provider.buildInitSql("tenant_acme"))
					.isEqualTo("SET search_path TO \"tenant_acme\"");
		});
	}

	// -------------------------------------------------------------------------
	// TenantDataSourceRegistry (only with TenantDataSourceProvider)
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_doesNotCreateRegistry_withoutProvider() {
		contextRunner.run(ctx ->
				assertThat(ctx).doesNotHaveBean(TenantDataSourceRegistry.class));
	}

	@Test
	void autoConfig_createsRegistry_whenProviderPresent() {
		contextRunner
				.withUserConfiguration(ProviderConfig.class)
				.run(ctx -> {
					assertThat(ctx).hasSingleBean(TenantDataSourceRegistry.class);
					assertThat(ctx.getBean(TenantDataSourceRegistry.class))
							.isInstanceOf(BoundedTenantDataSourceRegistry.class);
				});
	}

	@Test
	void autoConfig_maxPools_isConfigurable() {
		contextRunner
				.withUserConfiguration(ProviderConfig.class)
				.withPropertyValues("holon.tenant.data.max-pools=50")
				.run(ctx -> {
					// Registry is created with the configured max size
					assertThat(ctx).hasSingleBean(TenantDataSourceRegistry.class);
				});
	}

	// -------------------------------------------------------------------------
	// TenantSchemaInitializer (Flyway-based migration, replaces old TenantSchemaCreator)
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_doesNotRegisterSchemaInitializer_withoutRegistry() {
		// No TenantDataSourceProvider -> no TenantDataSourceRegistry -> no initializer
		contextRunner.run(ctx ->
				assertThat(ctx).doesNotHaveBean(TenantSchemaInitializer.class));
	}

	@Test
	void autoConfig_registersSchemaInitializer_whenRegistryPresentAndFlywayOnClasspath() {
		contextRunner
				.withUserConfiguration(ProviderConfig.class)
				.run(ctx ->
						assertThat(ctx).hasSingleBean(TenantSchemaInitializer.class));
	}

	// -------------------------------------------------------------------------
	// TenantAwareDataSource (primary)
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_doesNotCreateTenantAwareDataSource_withoutRegistry() {
		contextRunner.run(ctx ->
				assertThat(ctx).doesNotHaveBean(TenantAwareDataSource.class));
	}

	@Test
	void autoConfig_createsTenantAwareDataSourceAsPrimary_whenProviderPresent() {
		contextRunner
				.withUserConfiguration(ProviderConfig.class)
				.run(ctx -> {
					assertThat(ctx).hasSingleBean(TenantAwareDataSource.class);
					// Verify it is the primary DataSource
					assertThat(ctx.getBean(DataSource.class))
							.isInstanceOf(TenantAwareDataSource.class);
				});
	}

	@Test
	void tenantAwareDataSource_usesConfiguredStrategy() {
		contextRunner
				.withUserConfiguration(ProviderConfig.class)
				.withPropertyValues("holon.tenant.data.schema-strategy=SHARED")
				.run(ctx -> {
					TenantAwareDataSource ds = ctx.getBean(TenantAwareDataSource.class);
					assertThat(ds.getStrategy()).isEqualTo(TenantSchemaStrategy.SHARED);
				});
	}

	// -------------------------------------------------------------------------
	// Metrics (optional, only when a MeterRegistry bean is present)
	// -------------------------------------------------------------------------

	@Test
	void autoConfig_doesNotRegisterMetricsBinder_withoutMeterRegistry() {
		contextRunner
				.withUserConfiguration(ProviderConfig.class)
				.run(ctx -> assertThat(ctx)
						.doesNotHaveBean(TenantDataAutoConfiguration.TenantDataSourceMetricsBinder.class));
	}

	@Test
	void autoConfig_registersMetricsBinder_whenMeterRegistryPresent() {
		contextRunner
				.withUserConfiguration(ProviderConfig.class, MeterRegistryConfig.class)
				.run(ctx -> assertThat(ctx)
						.hasSingleBean(TenantDataAutoConfiguration.TenantDataSourceMetricsBinder.class));
	}

	@Test
	void autoConfig_registersPoolGauges_whenMeterRegistryPresent() {
		contextRunner
				.withUserConfiguration(ProviderConfig.class, MeterRegistryConfig.class)
				.withPropertyValues("holon.tenant.data.max-pools=42")
				.run(ctx -> {
					MeterRegistry meterRegistry = ctx.getBean(MeterRegistry.class);
					// Force the binder bean to actually be created (Spring beans are lazy by
					// default only under @Lazy; ApplicationContextRunner eagerly instantiates
					// all beans on refresh, so simply reading the gauges below is sufficient).
					assertThat(meterRegistry.find("holon.tenant.datasource.pools.max").gauge())
							.isNotNull();
					assertThat(meterRegistry.find("holon.tenant.datasource.pools.max").gauge().value())
							.isEqualTo(42d);
					assertThat(meterRegistry.find("holon.tenant.datasource.pools.active").gauge())
							.isNotNull();
					assertThat(meterRegistry.find("holon.tenant.datasource.pools.active").gauge().value())
							.isEqualTo(0d);
				});
	}

	@Test
	void autoConfig_wiresMeterRegistryIntoSchemaPerTenantProvider() {
		// Uses the REAL default provider (built from DataSourceProperties), not the
		// test's simplified ProviderConfig, so the concrete SchemaPerTenantDataSourceProvider
		// type is actually exercised.
		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(TenantDataAutoConfiguration.class))
				.withUserConfiguration(MeterRegistryConfig.class, DataSourcePropertiesConfig.class)
				.withPropertyValues("holon.tenant.data.dialect=H2")
				.run(ctx -> {
					TenantDataSourceProvider provider = ctx.getBean(TenantDataSourceProvider.class);
					assertThat(provider)
							.isInstanceOf(SchemaPerTenantDataSourceProvider.class);
					// Creating the bootstrap/platform pool (tenantId == null, no schema-switch
					// SQL applied — see class Javadoc) now publishes hikaricp.* metrics tagged
					// with this pool's name, proving the MeterRegistry was actually wired in
					// (not merely present in the context). A per-tenant pool would behave
					// identically but additionally requires the tenant schema to already exist.
					provider.getDataSource(null);
					MeterRegistry meterRegistry = ctx.getBean(MeterRegistry.class);
					assertThat(meterRegistry.find("hikaricp.connections.active")
							.tag("pool", "platform-master").gauge())
							.isNotNull();
				});
	}

	// -------------------------------------------------------------------------
	// Support configurations
	// -------------------------------------------------------------------------

	@Configuration
	static class MeterRegistryConfig {
		@Bean
		MeterRegistry meterRegistry() {
			return new SimpleMeterRegistry();
		}
	}

	@Configuration
	static class DataSourcePropertiesConfig {
		@Bean
		org.springframework.boot.jdbc.autoconfigure.DataSourceProperties dataSourceProperties() {
			org.springframework.boot.jdbc.autoconfigure.DataSourceProperties properties =
					new org.springframework.boot.jdbc.autoconfigure.DataSourceProperties();
			properties.setUrl("jdbc:h2:mem:metricswiring;DB_CLOSE_DELAY=-1");
			properties.setDriverClassName("org.h2.Driver");
			return properties;
		}
	}

	@Configuration
	static class CustomResolverConfig {
		@Bean
		SchemaNameResolver schemaNameResolver() {
			return tenantId -> "fixed_schema";
		}
	}

	@Configuration
	static class ProviderConfig {
		@Bean
		TenantDataSourceProvider tenantDataSourceProvider() {
			return id -> new EmbeddedDatabaseBuilder()
					.setType(EmbeddedDatabaseType.H2)
					.setName("tenant_" + id + "_" + System.nanoTime())
					.build();
		}
	}
}

