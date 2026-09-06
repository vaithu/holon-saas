package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.data.DatabaseDialect;
import com.holonplatform.multitenant.data.SchemaInitSqlProvider;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.datasource.BoundedTenantDataSourceRegistry;
import com.holonplatform.multitenant.data.datasource.SchemaPerTenantDataSourceProvider;
import com.holonplatform.multitenant.data.migration.TenantSchemaInitializer;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end regression test that runs the actual bundled
 * {@code classpath:db/tenant-migration/V1__tenant_baseline.sql} script — via
 * {@link TenantSchemaInitializer}, exactly as {@link DefaultTenantOnboardingService} does in
 * production — against a real H2 database.
 *
 * <p>This closes a real coverage gap: {@link DefaultTenantOnboardingServiceTest} only ever
 * mocks {@link TenantSchemaInitializer}, so neither the script's actual SQL content nor the
 * schema-identifier handling around it was ever executed by any test. That gap let two bugs
 * ship unnoticed:
 * <ol>
 *   <li>Two reserved-word column names ({@code key} and {@code value}, both unquoted) —
 *       fails at runtime on H2 with {@code Syntax error ... expected "identifier"}.</li>
 *   <li>A schema-identifier case-folding mismatch: {@code TenantSchemaInitializer}'s
 *       auto-create-schema step and {@code SchemaInitSqlProvider}'s connection-init SQL used
 *       to build <em>unquoted</em> DDL, which most dialects silently fold to a canonical case
 *       (H2/Oracle uppercase, PostgreSQL lowercase). Flyway, however, preserves the exact
 *       case given to {@code .schemas(...)}. The result was two entirely separate schema
 *       objects — e.g. empty {@code TENANT_ACME} (uppercase, from this framework's own DDL)
 *       and populated {@code tenant_acme} (lowercase, created and filled by Flyway) — so the
 *       tenant pool's connection landed on the empty one, and every real query failed with
 *       {@code Table "TENANT_SETTINGS" not found}. Fixed by quoting every schema identifier
 *       consistently in {@link DatabaseDialect}.</li>
 * </ol>
 */
class TenantBaselineMigrationScriptTest {

	@Test
	void v1TenantBaselineScript_appliesCleanly_onH2() throws Exception {
		String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

		SchemaNameResolver schemaNameResolver = SchemaNameResolver.withPrefix("tenant_");
		SchemaInitSqlProvider initSqlProvider = SchemaInitSqlProvider.forDialect(DatabaseDialect.H2);

		SchemaPerTenantDataSourceProvider provider = new SchemaPerTenantDataSourceProvider(
				jdbcUrl, "sa", "", schemaNameResolver, initSqlProvider);

		try (TenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(provider, 10)) {
			TenantSchemaInitializer initializer = new TenantSchemaInitializer(registry, schemaNameResolver);
			initializer.setDialect(DatabaseDialect.H2);
			initializer.setAutoCreateSchema(true);
			// Default migrationLocations ("classpath:db/tenant-migration") already points at
			// the real, bundled V1__tenant_baseline.sql — intentionally not overridden here.

			assertThatCode(() -> initializer.migrate("acme")).doesNotThrowAnyException();

			// Query through the tenant-specific pool (registry.getDataSource("acme")) — its
			// connectionInitSql already applied SET SCHEMA "tenant_acme" (quoted, exact case),
			// which must be the very same schema Flyway just populated — see class javadoc:
			// this regression test also guards against the two diverging into separate
			// schema objects due to inconsistent identifier quoting/case-folding.
			try (Connection connection = registry.getDataSource("acme").getConnection();
					Statement statement = connection.createStatement()) {
				assertThatCode(() -> statement.execute("SELECT COUNT(*) FROM tenant_settings"))
						.doesNotThrowAnyException();
				assertThatCode(() -> statement.execute("SELECT COUNT(*) FROM tenant_feature_flags"))
						.doesNotThrowAnyException();
				assertThatCode(() -> statement.execute("SELECT COUNT(*) FROM tenant_audit_log"))
						.doesNotThrowAnyException();
			}
		}
	}

	@Test
	void v1_1FrameworkModuleTablesScript_appliesCleanly_onH2() throws Exception {
		String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

		SchemaNameResolver schemaNameResolver = SchemaNameResolver.withPrefix("tenant_");
		SchemaInitSqlProvider initSqlProvider = SchemaInitSqlProvider.forDialect(DatabaseDialect.H2);

		SchemaPerTenantDataSourceProvider provider = new SchemaPerTenantDataSourceProvider(
				jdbcUrl, "sa", "", schemaNameResolver, initSqlProvider);

		try (TenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(provider, 10)) {
			TenantSchemaInitializer initializer = new TenantSchemaInitializer(registry, schemaNameResolver);
			initializer.setDialect(DatabaseDialect.H2);
			initializer.setAutoCreateSchema(true);

			assertThatCode(() -> initializer.migrate("acme")).doesNotThrowAnyException();

			String[] tables = {
					"tenant_users",
					"audit_entries",
					"tenant_billing_invoice", "tenant_billing_subscription",
					"user_invitation",
					"whatsapp_conversation", "whatsapp_message",
					"tenant_permissions",
					"tenant_setting_override", "tenant_plan_setting_default"
			};

			try (Connection connection = registry.getDataSource("acme").getConnection();
					Statement statement = connection.createStatement()) {
				for (String table : tables) {
					assertThatCode(() -> statement.execute("SELECT COUNT(*) FROM " + table))
							.as("table " + table + " should be queryable")
							.doesNotThrowAnyException();
				}

				// Column-level checks for the two entities whose @Column has no explicit
				// name attribute, where Spring Boot's default naming strategy (camelCase ->
				// snake_case) must be guessed correctly rather than copied verbatim from the
				// Java field name — the exact class of mistake this test is designed to catch.
				assertThatCode(() -> statement.execute(
						"SELECT tenant_id, created_at FROM audit_entries"))
						.doesNotThrowAnyException();
				assertThatCode(() -> statement.execute(
						"SELECT tenant_id, created_at FROM tenant_permissions"))
						.doesNotThrowAnyException();

				// Column-level checks for the 3 entities whose Map<String,String> field is
				// stored as a single JSON text column (attributes / template_params) rather
				// than a separate @ElementCollection join table — see the identical rationale
				// comment in V1.1__tenant_framework_module_tables.sql on tenant_users.attributes.
				assertThatCode(() -> statement.execute(
						"SELECT attributes FROM tenant_users"))
						.doesNotThrowAnyException();
				assertThatCode(() -> statement.execute(
						"SELECT attributes FROM user_invitation"))
						.doesNotThrowAnyException();
				assertThatCode(() -> statement.execute(
						"SELECT template_params FROM whatsapp_message"))
						.doesNotThrowAnyException();
			}
		}
	}
}

