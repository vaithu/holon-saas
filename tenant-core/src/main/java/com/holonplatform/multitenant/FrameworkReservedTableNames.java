package com.holonplatform.multitenant;

import java.util.Set;

/**
 * The exact set of table names created inside every tenant schema by the {@code holon-saas}
 * framework's own Flyway migration scripts (in {@code tenant-onboarding}'s
 * {@code db/tenant-migration/} classpath location, applied automatically during tenant
 * provisioning).
 *
 * <h2>Why this exists</h2>
 * <p>Every consuming application adds its own {@code V2__}-and-higher Flyway scripts to the same
 * tenant schema, alongside the framework's {@code V1__}/{@code V1.1__} scripts. Picking an
 * application table name that collides with one of these reserved names causes a Flyway
 * migration failure (or worse, silent data corruption if checksums/validation are disabled) —
 * and, until now, discovering the exact reserved list meant reading the actual {@code .sql}
 * files or the (Markdown, therefore not mechanically checkable) new-app checklist. This class
 * makes the list a compile-time-checkable Java constant instead.
 *
 * <h2>Usage</h2>
 * <p>In a new application's own test suite:
 * <pre>{@code
 * @Test
 * void myAppTablesDoNotCollideWithFrameworkTables() {
 *     Set<String> myTables = Set.of("crm_account", "crm_contact", "crm_opportunity");
 *     assertThat(myTables).doesNotContainAnyElementsOf(FrameworkReservedTableNames.ALL);
 * }
 * }</pre>
 *
 * <p><strong>Keeping this list in sync:</strong> {@code tenant-onboarding} has a test
 * ({@code FrameworkReservedTableNamesSyncTest}) that parses the actual {@code V1__}/{@code V1.1__}
 * migration scripts and asserts the table names found there exactly match this constant. If a
 * future migration script adds, renames, or removes a table, that test will fail until this list
 * is updated to match — this list can never legitimately drift out of sync with the SQL that
 * actually runs.
 */
public final class FrameworkReservedTableNames {

	private FrameworkReservedTableNames() {
	}

	/**
	 * Tables created by {@code V1__tenant_baseline.sql}.
	 *
	 * <p>Note: as of this writing, none of these three tables is backed by any JPA entity or
	 * Holon Datastore mapping anywhere in the framework — they are reserved schema real estate
	 * for future framework features, not currently read or written by any framework code path.
	 * They are still reserved (not safe to reuse) because they physically exist in every
	 * provisioned tenant schema today.
	 */
	public static final Set<String> BASELINE = Set.of(
			"tenant_settings",
			"tenant_feature_flags",
			"tenant_audit_log");

	/**
	 * Tables created by {@code V1.1__tenant_framework_module_tables.sql}, backing the optional
	 * framework modules (tenant-users, tenant-audit, tenant-billing, tenant-permissions,
	 * tenant-quotas/plan-settings, tenant-whatsapp-messaging).
	 */
	public static final Set<String> MODULE_TABLES = Set.of(
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

	/**
	 * The complete set of reserved tenant-schema table names — the union of {@link #BASELINE}
	 * and {@link #MODULE_TABLES}. This is the set a new application should check its own table
	 * names against.
	 */
	public static final Set<String> ALL = java.util.stream.Stream.concat(BASELINE.stream(), MODULE_TABLES.stream())
			.collect(java.util.stream.Collectors.toUnmodifiableSet());
}
