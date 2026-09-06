package com.holonplatform.multitenant.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DatabaseDialect}.
 */
class DatabaseDialectTest {

	@Test
	void postgresql_buildInitSql() {
		assertThat(DatabaseDialect.POSTGRESQL.buildInitSql("tenant_acme"))
				.isEqualTo("SET search_path TO \"tenant_acme\"");
	}

	@Test
	void h2_buildInitSql() {
		assertThat(DatabaseDialect.H2.buildInitSql("tenant_acme"))
				.isEqualTo("SET SCHEMA \"tenant_acme\"");
	}

	@Test
	void oracle_buildInitSql() {
		assertThat(DatabaseDialect.ORACLE.buildInitSql("my_schema"))
				.isEqualTo("ALTER SESSION SET CURRENT_SCHEMA = \"my_schema\"");
	}

	@Test
	void schemaInitSqlProvider_forDialect_delegatesToDialect() {
		SchemaInitSqlProvider provider = SchemaInitSqlProvider.forDialect(DatabaseDialect.POSTGRESQL);
		assertThat(provider.buildInitSql("t_acme"))
				.isEqualTo(DatabaseDialect.POSTGRESQL.buildInitSql("t_acme"));
	}

	@Test
	void schemaInitSqlProvider_throwsOnNullDialect() {
		assertThatThrownBy(() -> SchemaInitSqlProvider.forDialect(null))
				.isInstanceOf(NullPointerException.class);
	}

	// -------------------------------------------------------------------------
	// buildCreateSchemaSql
	// -------------------------------------------------------------------------

	@Test
	void postgresql_buildCreateSchemaSql() {
		assertThat(DatabaseDialect.POSTGRESQL.buildCreateSchemaSql("tenant_acme"))
				.contains("CREATE SCHEMA IF NOT EXISTS \"tenant_acme\"");
	}

	@Test
	void h2_buildCreateSchemaSql() {
		assertThat(DatabaseDialect.H2.buildCreateSchemaSql("tenant_acme"))
				.contains("CREATE SCHEMA IF NOT EXISTS \"tenant_acme\"");
	}

	@Test
	void sqlserver_buildCreateSchemaSql_isGuardedBatch() {
		assertThat(DatabaseDialect.SQLSERVER.buildCreateSchemaSql("tenant_acme"))
				.hasValueSatisfying(sql -> {
					assertThat(sql).contains("sys.schemas");
					assertThat(sql).contains("tenant_acme");
				});
	}

	@Test
	void oracle_buildCreateSchemaSql_isUnsupported() {
		assertThat(DatabaseDialect.ORACLE.buildCreateSchemaSql("my_schema")).isEmpty();
	}
}

