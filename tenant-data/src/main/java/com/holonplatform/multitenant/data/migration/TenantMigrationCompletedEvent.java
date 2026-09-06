package com.holonplatform.multitenant.data.migration;

import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Spring application event published by {@link TenantSchemaInitializer} after a
 * tenant's schema has been successfully provisioned and migrated.
 *
 * <p>Listen for this event to trigger post-migration setup (seed data, admin user creation,
 * billing system registration, etc.).
 *
 * <pre>{@code
 * @Component
 * public class TenantPostMigrationHandler {
 *
 *     @EventListener
 *     @Async
 *     public void onMigrationCompleted(TenantMigrationCompletedEvent event) {
 *         seedDataService.populate(event.getTenantId());
 *     }
 * }
 * }</pre>
 */
public class TenantMigrationCompletedEvent extends ApplicationEvent {

	private static final long serialVersionUID = 1L;

	private final String tenantId;
	private final String schemaName;
	private final int    migrationsApplied;

	public TenantMigrationCompletedEvent(Object source,
			String tenantId,
			String schemaName,
			int migrationsApplied) {
		super(source);
		this.tenantId          = Objects.requireNonNull(tenantId);
		this.schemaName        = Objects.requireNonNull(schemaName);
		this.migrationsApplied = migrationsApplied;
	}

	/** The tenant that was migrated. */
	public String getTenantId() {
		return tenantId;
	}

	/** The database schema that was created/migrated. */
	public String getSchemaName() {
		return schemaName;
	}

	/** Number of Flyway migration scripts that were applied (0 = already up-to-date). */
	public int getMigrationsApplied() {
		return migrationsApplied;
	}
}

