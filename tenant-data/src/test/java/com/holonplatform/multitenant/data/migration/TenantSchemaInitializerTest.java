package com.holonplatform.multitenant.data.migration;

import com.holonplatform.multitenant.data.DatabaseDialect;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantSchemaInitializer}, focused on the schema-auto-creation step
 * (the actual Flyway migration behavior is exercised end-to-end elsewhere; a full
 * integration-style {@code migrate()} test would require real migration scripts on the
 * classpath, which this module intentionally has none of).
 */
class TenantSchemaInitializerTest {

	@Test
	void migrate_createsSchemaBeforeRequestingTenantDataSource_whenDialectAndAutoCreateEnabled() throws Exception {
		DataSource bootstrapDs = h2InMemoryDataSource();

		TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
		when(registry.getDataSource(null)).thenReturn(bootstrapDs);
		// Tenant DataSource creation is expected to fail after schema creation succeeds —
		// irrelevant to this test, which only verifies the schema was created beforehand.
		when(registry.getDataSource("acme")).thenThrow(new RuntimeException("stop here"));

		SchemaNameResolver resolver = tenantId -> "tenant_" + tenantId;

		TenantSchemaInitializer initializer = new TenantSchemaInitializer(registry, resolver);
		initializer.setDialect(DatabaseDialect.H2);
		initializer.setAutoCreateSchema(true);

		assertThatThrownBy(() -> initializer.migrate("acme"))
				.isInstanceOf(TenantMigrationException.class);

		assertThat(schemaExists(bootstrapDs, "TENANT_ACME")).isTrue();
	}

	@Test
	void migrate_doesNotAttemptSchemaCreation_whenDialectNotSet() {
		TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
		when(registry.getDataSource("acme")).thenThrow(new RuntimeException("stop here"));

		SchemaNameResolver resolver = tenantId -> "tenant_" + tenantId;

		TenantSchemaInitializer initializer = new TenantSchemaInitializer(registry, resolver);
		// dialect intentionally left unset

		assertThatThrownBy(() -> initializer.migrate("acme"))
				.isInstanceOf(TenantMigrationException.class);

		// registry.getDataSource(null) (the bootstrap connection) must never be requested
		// when no dialect is configured — nothing to run schema-creation SQL with.
		verify(registry, never()).getDataSource(null);
	}

	@Test
	void migrate_doesNotAttemptSchemaCreation_whenAutoCreateSchemaDisabled() {
		TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
		when(registry.getDataSource("acme")).thenThrow(new RuntimeException("stop here"));

		SchemaNameResolver resolver = tenantId -> "tenant_" + tenantId;

		TenantSchemaInitializer initializer = new TenantSchemaInitializer(registry, resolver);
		initializer.setDialect(DatabaseDialect.H2);
		initializer.setAutoCreateSchema(false);

		assertThatThrownBy(() -> initializer.migrate("acme"))
				.isInstanceOf(TenantMigrationException.class);

		verify(registry, never()).getDataSource(null);
	}

	@Test
	void migrate_doesNotAttemptSchemaCreation_forDialectWithNoCreateSchemaSupport() throws SQLException {
		DataSource bootstrapDs = mock(DataSource.class);
		Statement statement = mock(Statement.class);
		Connection connection = mock(Connection.class);
		when(connection.createStatement()).thenReturn(statement);
		when(bootstrapDs.getConnection()).thenReturn(connection);

		TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
		when(registry.getDataSource(null)).thenReturn(bootstrapDs);
		when(registry.getDataSource("acme")).thenThrow(new RuntimeException("stop here"));

		SchemaNameResolver resolver = tenantId -> "tenant_" + tenantId;

		TenantSchemaInitializer initializer = new TenantSchemaInitializer(registry, resolver);
		initializer.setDialect(DatabaseDialect.ORACLE); // buildCreateSchemaSql() is always empty
		initializer.setAutoCreateSchema(true);

		assertThatThrownBy(() -> initializer.migrate("acme"))
				.isInstanceOf(TenantMigrationException.class);

		verify(statement, never()).execute(anyString());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static DataSource h2InMemoryDataSource() {
		JdbcDataSource ds = new JdbcDataSource();
		ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
		ds.setUser("sa");
		ds.setPassword("");
		return ds;
	}

	private static boolean schemaExists(DataSource dataSource, String schemaName) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				ResultSet rs = connection.getMetaData().getSchemas()) {
			while (rs.next()) {
				if (schemaName.equalsIgnoreCase(rs.getString("TABLE_SCHEM"))) {
					return true;
				}
			}
		}
		return false;
	}
}
