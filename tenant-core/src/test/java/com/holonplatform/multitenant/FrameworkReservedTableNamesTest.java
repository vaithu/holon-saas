package com.holonplatform.multitenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Sanity checks for {@link FrameworkReservedTableNames}. The authoritative check that this list
 * matches the actual Flyway migration scripts lives in {@code tenant-onboarding}'s
 * {@code FrameworkReservedTableNamesSyncTest} (this module doesn't depend on the migration
 * scripts, which live in {@code tenant-onboarding}).
 */
class FrameworkReservedTableNamesTest {

	@Test
	void all_isUnionOfBaselineAndModuleTables() {
		assertThat(FrameworkReservedTableNames.ALL)
				.containsAll(FrameworkReservedTableNames.BASELINE)
				.containsAll(FrameworkReservedTableNames.MODULE_TABLES)
				.hasSize(FrameworkReservedTableNames.BASELINE.size() + FrameworkReservedTableNames.MODULE_TABLES.size());
	}

	@Test
	void baseline_containsExpectedTables() {
		assertThat(FrameworkReservedTableNames.BASELINE)
				.containsExactlyInAnyOrder("tenant_settings", "tenant_feature_flags", "tenant_audit_log");
	}

	@Test
	void moduleTables_containsExpectedTables() {
		assertThat(FrameworkReservedTableNames.MODULE_TABLES)
				.containsExactlyInAnyOrder(
						"tenant_users",
						"audit_entries",
						"tenant_billing_invoice",
						"tenant_billing_subscription",
						"user_invitation",
						"whatsapp_conversation",
						"whatsapp_message",
						"tenant_permissions",
						"tenant_setting_override",
						"tenant_plan_setting_default");
	}

	@Test
	void all_containsNoBlankOrNullEntries() {
		assertThat(FrameworkReservedTableNames.ALL).allSatisfy(name -> assertThat(name).isNotBlank());
	}
}
