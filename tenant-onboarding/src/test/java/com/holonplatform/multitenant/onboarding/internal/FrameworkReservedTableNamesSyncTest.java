package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.FrameworkReservedTableNames;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

/**
 * Guards {@link FrameworkReservedTableNames} — a constant list declared in {@code tenant-core}
 * — against drifting out of sync with the actual Flyway migration scripts that create those
 * tables, which live here in {@code tenant-onboarding} (the module {@code tenant-core}
 * intentionally does not depend on).
 *
 * <p>This test parses the real {@code classpath:db/tenant-migration/V1__tenant_baseline.sql} and
 * {@code V1.1__tenant_framework_module_tables.sql} scripts for {@code CREATE TABLE ...} table
 * names and asserts they exactly match {@link FrameworkReservedTableNames#BASELINE} and
 * {@link FrameworkReservedTableNames#MODULE_TABLES} respectively. If a future migration script
 * adds, renames, or removes a table, this test fails until {@code FrameworkReservedTableNames}
 * is updated to match — the two can never legitimately diverge.
 */
class FrameworkReservedTableNamesSyncTest {

	private static final Pattern CREATE_TABLE = Pattern.compile(
			"CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

	@Test
	void baseline_matchesActualMigrationScript() throws IOException {
		Set<String> tablesInScript = tableNamesIn("db/tenant-migration/V1__tenant_baseline.sql");

		assertThat(tablesInScript).isEqualTo(FrameworkReservedTableNames.BASELINE);
	}

	@Test
	void moduleTables_matchActualMigrationScript() throws IOException {
		Set<String> tablesInScript = tableNamesIn("db/tenant-migration/V1.1__tenant_framework_module_tables.sql");

		assertThat(tablesInScript).isEqualTo(FrameworkReservedTableNames.MODULE_TABLES);
	}

	private static Set<String> tableNamesIn(String classpathResource) throws IOException {
		Set<String> tableNames = new LinkedHashSet<>();
		try (InputStream in = FrameworkReservedTableNamesSyncTest.class.getClassLoader()
				.getResourceAsStream(classpathResource)) {
			assertThat(in).as("classpath resource " + classpathResource).isNotNull();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
				String sql = reader.lines().reduce("", (a, b) -> a + "\n" + b);
				Matcher matcher = CREATE_TABLE.matcher(sql);
				while (matcher.find()) {
					tableNames.add(matcher.group(1).toLowerCase());
				}
			}
		}
		return tableNames;
	}
}
