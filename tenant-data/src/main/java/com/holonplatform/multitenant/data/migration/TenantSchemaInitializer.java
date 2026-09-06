package com.holonplatform.multitenant.data.migration;

import com.holonplatform.multitenant.data.DatabaseDialect;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates Flyway database migrations for tenant schemas.
 *
 * <p>For the {@code SCHEMA_PER_TENANT} strategy, this class:
 * <ol>
 *   <li>Ensures the tenant's schema exists (see {@link #setDialect(DatabaseDialect)} /
 *       {@link #setAutoCreateSchema(boolean)}) — <strong>required</strong> when schema
 *       switching is done via {@code connectionInitSql} (e.g. {@code SET SCHEMA x} on H2,
 *       {@code SET search_path TO x} on PostgreSQL): the pool's very first connection runs
 *       that init SQL eagerly, before Flyway ever gets a chance to create the schema
 *       itself, so the schema must already exist beforehand or pool creation fails.</li>
 *   <li>Runs Flyway migrations against the tenant's DataSource.</li>
 *   <li>Publishes a {@link TenantMigrationCompletedEvent} on success.</li>
 * </ol>
 *
 * <h2>Migration SQL location</h2>
 * <p>Default: {@code classpath:db/tenant-migration}. Override via
 * {@link #setMigrationLocations(String...)}.
 *
 * <h2>Idempotency</h2>
 * <p>Flyway's checksum-based migration tracking makes this safe to call multiple times
 * for the same tenant (e.g. on every application restart).
 *
 * <h2>New-tenant onboarding</h2>
 * <pre>{@code
 * @Service
 * public class TenantOnboardingService {
 *
 *     private final TenantSchemaInitializer initializer;
 *
 *     public void onboard(String tenantId) {
 *         initializer.migrate(tenantId);
 *         // ... create admin user, seed data
 *     }
 * }
 * }</pre>
 */
public class TenantSchemaInitializer {

	private static final Logger log = LoggerFactory.getLogger(TenantSchemaInitializer.class);

	private final TenantDataSourceRegistry registry;
	private final SchemaNameResolver schemaNameResolver;

	/** Flyway SQL migration locations. */
	private String[] migrationLocations = {"classpath:db/tenant-migration"};

	/** Whether to call {@code baselineOnMigrate} for pre-existing schemas. */
	private boolean baselineOnMigrate = true;

	/**
	 * SQL dialect used to derive create-schema DDL. {@code null} (the default) disables
	 * automatic schema creation entirely, regardless of {@link #autoCreateSchema}.
	 */
	private DatabaseDialect dialect;

	/**
	 * Whether to create the tenant's schema automatically (via a bootstrap/platform
	 * connection) before running Flyway. Default: {@code true}. Has no effect unless
	 * {@link #dialect} is also set.
	 */
	private boolean autoCreateSchema = true;

	/** Optional Spring event publisher. */
	private ApplicationEventPublisher eventPublisher;

	/**
	 * Creates a new initializer.
	 *
	 * @param registry          tenant DataSource registry; must not be null
	 * @param schemaNameResolver tenant ID → schema name mapping; must not be null
	 */
	public TenantSchemaInitializer(
			TenantDataSourceRegistry registry,
			SchemaNameResolver schemaNameResolver) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
		this.schemaNameResolver = Objects.requireNonNull(schemaNameResolver, "schemaNameResolver must not be null");
	}

	// -------------------------------------------------------------------------
	// Configuration
	// -------------------------------------------------------------------------

	/** Sets the Flyway migration file locations. Default: {@code classpath:db/tenant-migration}. */
	public void setMigrationLocations(String... locations) {
		Objects.requireNonNull(locations, "locations must not be null");
		if (locations.length == 0) {
			throw new IllegalArgumentException("At least one migration location is required");
		}
		this.migrationLocations = locations;
	}

	/** Controls {@code baselineOnMigrate}. Default: {@code true}. */
	public void setBaselineOnMigrate(boolean baselineOnMigrate) {
		this.baselineOnMigrate = baselineOnMigrate;
	}

	/**
	 * Sets the SQL dialect used to derive create-schema DDL, executed via a bootstrap
	 * connection ({@code registry.getDataSource(null)}) before Flyway runs. Required for
	 * automatic schema creation to take effect — see {@link #setAutoCreateSchema(boolean)}.
	 */
	public void setDialect(DatabaseDialect dialect) {
		this.dialect = dialect;
	}

	/**
	 * Controls whether the tenant's schema is created automatically before migration.
	 * Default: {@code true}. Has no effect unless {@link #setDialect(DatabaseDialect)} is
	 * also set.
	 */
	public void setAutoCreateSchema(boolean autoCreateSchema) {
		this.autoCreateSchema = autoCreateSchema;
	}

	/** Wires the optional Spring event publisher. */
	public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Provisions and migrates the schema for the given tenant.
	 *
	 * <p>Steps:
	 * <ol>
	 *   <li>Run Flyway migrations against the tenant's DataSource.</li>
	 *   <li>Publish {@link TenantMigrationCompletedEvent}.</li>
	 * </ol>
	 *
	 * @param tenantId the tenant to migrate; must not be null
	 * @throws TenantMigrationException if migration fails
	 */
	public void migrate(String tenantId) {
		Objects.requireNonNull(tenantId, "tenantId must not be null");
		String schema = schemaNameResolver.resolveSchema(tenantId);

		log.info("Starting schema migration for tenant '{}' (schema: '{}')", tenantId, schema);

		try {
			ensureSchemaExists(schema);

			// Get the tenant-specific DataSource
			DataSource tenantDs = registry.getDataSource(tenantId);

			// Run Flyway
			Flyway flyway = Flyway.configure()
					.dataSource(tenantDs)
					.schemas(schema)
					.locations(migrationLocations)
					.baselineOnMigrate(baselineOnMigrate)
					.baselineVersion("0")
					.load();

			int migrations = flyway.migrate().migrationsExecuted;
			log.info("Tenant '{}' migration complete — {} scripts applied", tenantId, migrations);

			// Publish event
			publishEvent(tenantId, schema, migrations);

		} catch (Exception e) {
			throw new TenantMigrationException(tenantId, schema, e);
		}
	}

	/**
	 * Migrates all tenants currently cached in the registry.
	 * Use at startup to bring all active tenants up to the latest schema version.
	 */
	public void migrateAll() {
		log.info("Running schema migrations for {} active tenants", registry.size());
		registry.activeTenantIds().forEach(this::migrate);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Creates the given schema if it doesn't already exist, using the platform/bootstrap
	 * connection ({@code registry.getDataSource(null)} — plain, no schema-switch SQL).
	 *
	 * <p>No-op when {@link #autoCreateSchema} is {@code false}, when {@link #dialect} is
	 * unset, or when the configured dialect has no create-schema DDL equivalent (see
	 * {@link DatabaseDialect#buildCreateSchemaSql(String)}, e.g. Oracle).
	 */
	private void ensureSchemaExists(String schema) throws SQLException {
		if (!autoCreateSchema || dialect == null) {
			return;
		}
		Optional<String> createSchemaSql = dialect.buildCreateSchemaSql(schema);
		if (createSchemaSql.isEmpty()) {
			return;
		}

		DataSource bootstrapDs = registry.getDataSource(null);
		try (Connection connection = bootstrapDs.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute(createSchemaSql.get());
			log.debug("Ensured schema '{}' exists (dialect: {})", schema, dialect);
		}
	}

	private void publishEvent(String tenantId, String schema, int migrationsApplied) {
		if (eventPublisher != null) {
			try {
				eventPublisher.publishEvent(
						new TenantMigrationCompletedEvent(this, tenantId, schema, migrationsApplied));
			} catch (Exception e) {
				log.debug("Failed to publish TenantMigrationCompletedEvent", e);
			}
		}
	}
}

