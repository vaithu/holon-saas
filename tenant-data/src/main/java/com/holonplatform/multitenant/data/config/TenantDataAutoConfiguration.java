package com.holonplatform.multitenant.data.config;

import com.holonplatform.jdbc.TenantDataSourceProvider;
import com.holonplatform.multitenant.data.SchemaInitSqlProvider;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.datasource.BoundedTenantDataSourceRegistry;
import com.holonplatform.multitenant.data.datasource.SchemaPerTenantDataSourceProvider;
import com.holonplatform.multitenant.data.datasource.TenantAwareDataSource;
import com.holonplatform.multitenant.data.migration.TenantSchemaInitializer;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Spring Boot auto-configuration for the multi-tenant data layer.
 *
 * <h2>What is auto-configured</h2>
 * <ul>
 *   <li><strong>{@link SchemaNameResolver}</strong> — default prefix/suffix-based resolver</li>
 *   <li><strong>{@link SchemaInitSqlProvider}</strong> — dialect-specific SQL provider</li>
 *   <li><strong>{@link TenantDataSourceProvider}</strong> — built from {@code spring.datasource.*}
 *       properties (via {@code DataSourceProperties}), when {@code spring-boot-jdbc} is on the
 *       classpath. Skipped when the application declares its own bean of this type instead.</li>
 *   <li><strong>{@link TenantDataSourceRegistry}</strong> — bounded LRU pool registry
 *       (registered only when {@link TenantDataSourceProvider} bean is present)</li>
 *   <li><strong>{@link TenantAwareDataSource}</strong> — routing DataSource registered as
 *       {@code @Primary} (can be disabled via {@code holon.tenant.data.primary-data-source=false})</li>
 *   <li><strong>{@link TenantSchemaInitializer}</strong> — Flyway initializer (only when
 *       Flyway is on the classpath and {@link TenantDataSourceRegistry} exists)</li>
 * </ul>
 *
 * <h2>Application responsibilities</h2>
 * <p>None, for the common case: the default {@link TenantDataSourceProvider} above covers
 * SCHEMA_PER_TENANT / DATABASE_PER_TENANT strategies using the application's own
 * {@code spring.datasource.*} connection details. Register a custom
 * {@link TenantDataSourceProvider} bean instead only if per-tenant connections need something
 * the default can't express (e.g. per-tenant credentials from a secrets manager).
 */
@AutoConfiguration
@EnableConfigurationProperties(TenantDataProperties.class)
public class TenantDataAutoConfiguration {

	// -------------------------------------------------------------------------
	// SchemaNameResolver
	// -------------------------------------------------------------------------

	@Bean
	@ConditionalOnMissingBean(SchemaNameResolver.class)
	public SchemaNameResolver schemaNameResolver(TenantDataProperties properties) {
		return SchemaNameResolver.withPrefixAndSuffix(
				properties.getSchemaPrefix(), properties.getSchemaSuffix());
	}

	// -------------------------------------------------------------------------
	// SchemaInitSqlProvider
	// -------------------------------------------------------------------------

	@Bean
	@ConditionalOnMissingBean(SchemaInitSqlProvider.class)
	public SchemaInitSqlProvider schemaInitSqlProvider(TenantDataProperties properties) {
		return SchemaInitSqlProvider.forDialect(properties.getDialect());
	}

	// -------------------------------------------------------------------------
	// TenantDataSourceProvider — default, built from spring.datasource.* properties
	// -------------------------------------------------------------------------

	/**
	 * Builds a default {@link TenantDataSourceProvider} from Spring Boot's own
	 * {@link DataSourceProperties} (the plain POJO bound from {@code spring.datasource.*}) —
	 * deliberately <em>not</em> from a {@code DataSource}/{@code HikariDataSource}-typed bean.
	 * A {@code spring-boot-data-source-decorator} (or similar) library transitively present on
	 * an application's classpath wraps every {@code DataSource} bean in a proxy via a
	 * {@code BeanPostProcessor}, silently making any bean declared as concrete type
	 * {@code HikariDataSource} an incompatible type once injected elsewhere. Reading the
	 * connection details as plain strings and building the Hikari template inline — never
	 * registering it as its own bean — sidesteps that entirely. This is exactly the workaround
	 * a real consuming application had to discover independently before this default existed.
	 *
	 * <p>Skipped when the application already declares its own {@link TenantDataSourceProvider}
	 * bean, or when no {@link DataSourceProperties} bean is present (e.g.
	 * {@code spring-boot-jdbc} isn't on the classpath at all, or Spring Boot's own
	 * {@code DataSourceAutoConfiguration} hasn't run for some reason).
	 */
	@Bean
	@ConditionalOnBean(DataSourceProperties.class)
	@ConditionalOnMissingBean(TenantDataSourceProvider.class)
	public TenantDataSourceProvider tenantDataSourceProvider(
			DataSourceProperties dataSourceProperties,
			SchemaNameResolver schemaNameResolver,
			SchemaInitSqlProvider schemaInitSqlProvider,
			TenantDataProperties tenantDataProperties) {

		HikariDataSource template = new HikariDataSource();
		template.setJdbcUrl(dataSourceProperties.determineUrl());
		template.setUsername(dataSourceProperties.getUsername());
		template.setPassword(dataSourceProperties.getPassword());
		template.setDriverClassName(dataSourceProperties.determineDriverClassName());

		return SchemaPerTenantDataSourceProvider.from(
				template, schemaNameResolver, schemaInitSqlProvider, tenantDataProperties);
	}

	// -------------------------------------------------------------------------
	// TenantDataSourceRegistry  (only when a TenantDataSourceProvider bean exists)
	// -------------------------------------------------------------------------

	@Bean
	@ConditionalOnBean(TenantDataSourceProvider.class)
	@ConditionalOnMissingBean(TenantDataSourceRegistry.class)
	public TenantDataSourceRegistry tenantDataSourceRegistry(TenantDataSourceProvider provider,
			TenantDataProperties properties) {
		return new BoundedTenantDataSourceRegistry(provider, properties.getMaxPools());
	}

	// -------------------------------------------------------------------------
	// TenantAwareDataSource  — primary DataSource
	// -------------------------------------------------------------------------

	/**
	 * Registers the {@link TenantAwareDataSource} as the {@code @Primary} DataSource
	 * for all strategies.
	 *
	 * <p>Skipped when {@code holon.tenant.data.primary-data-source=false} or when
	 * no {@link TenantDataSourceRegistry} is available (i.e. no
	 * {@code com.holonplatform.jdbc.TenantDataSourceProvider} bean is registered).
	 */
	@Bean("tenantAwareDataSource")
	@Primary
	@ConditionalOnBean(TenantDataSourceRegistry.class)
	@ConditionalOnMissingBean(name = "tenantAwareDataSource")
	@ConditionalOnProperty(name = "holon.tenant.data.primary-data-source", havingValue = "true", matchIfMissing = true)
	public TenantAwareDataSource tenantAwareDataSource(
			TenantDataSourceRegistry registry,
			TenantDataProperties properties) {
		return new TenantAwareDataSource(properties.getSchemaStrategy(), registry);
	}


	// -------------------------------------------------------------------------
	// TenantSchemaInitializer  — only when Flyway is on the classpath
	// -------------------------------------------------------------------------

	@Bean
	@ConditionalOnClass(Flyway.class)
	@ConditionalOnBean(TenantDataSourceRegistry.class)
	@ConditionalOnMissingBean(TenantSchemaInitializer.class)
	public TenantSchemaInitializer tenantSchemaInitializer(
			TenantDataSourceRegistry registry,
			SchemaNameResolver schemaNameResolver,
			TenantDataProperties properties,
			ApplicationEventPublisher eventPublisher) {

		TenantSchemaInitializer initializer = new TenantSchemaInitializer(
				registry, schemaNameResolver);
		initializer.setMigrationLocations(properties.getMigrationLocations());
		initializer.setEventPublisher(eventPublisher);
		initializer.setDialect(properties.getDialect());
		initializer.setAutoCreateSchema(properties.isAutoCreateSchema());
		return initializer;
	}
}





