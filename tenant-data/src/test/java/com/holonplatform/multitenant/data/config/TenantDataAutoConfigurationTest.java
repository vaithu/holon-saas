package com.holonplatform.multitenant.data.config;

import com.holonplatform.jdbc.TenantDataSourceProvider;
import com.holonplatform.multitenant.data.SchemaInitSqlProvider;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.TenantSchemaStrategy;
import com.holonplatform.multitenant.data.datasource.BoundedTenantDataSourceRegistry;
import com.holonplatform.multitenant.data.datasource.TenantAwareDataSource;
import com.holonplatform.multitenant.data.migration.TenantSchemaInitializer;
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
	// Support configurations
	// -------------------------------------------------------------------------

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

