package com.holonplatform.multitenant;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Plain Spring Data {@link JpaRepository} for {@link TenantDetails} persistence — used
 * <strong>instead of</strong> Holon's {@code BeanDatastoreHelper} (the pattern every other
 * entity in {@code holon-saas} uses).
 *
 * <h2>Why this entity is different</h2>
 * <p>{@link TenantDetails} is the single most critical, most-frequently-loaded entity in the
 * whole framework: it is read on every tenant-context resolution (via
 * {@code TenantDetailsLoader}) and written on every onboarding/admin lifecycle transition.
 * Holon's {@code BeanDatastoreHelper} translates bean property sets into JPQL by reflectively
 * projecting every mapped property, which this session's debugging uncovered as a source of
 * subtle, hard-to-diagnose bugs for this class specifically (e.g. an inner-join silently
 * dropping the whole row when a {@code Map}/{@code Set}-valued property was empty). A plain,
 * standard {@code JpaRepository} sidesteps that translation layer entirely — it is the most
 * battle-tested, simplest persistence mechanism available, appropriate for an entity this
 * central to the framework's correctness.
 *
 * <h2>Usage</h2>
 * <p>This interface is intentionally <strong>not</strong> discovered via
 * {@code @EnableJpaRepositories} component scanning (it lives in {@code tenant-core}, outside
 * any consuming application's own base package, exactly like Holon's datastore helpers).
 * Instead, each module that needs it (currently {@code tenant-onboarding} and
 * {@code tenant-admin}) constructs its own proxy instance directly in its
 * {@code @AutoConfiguration} class via:
 * <pre>{@code
 * new JpaRepositoryFactory(entityManager).getRepository(TenantDetailsRepository.class)
 * }</pre>
 * This mirrors the existing "each module owns its own copy of the same helper pattern"
 * self-containment philosophy already used for {@code TenantDetailsDatastoreHelper}, without
 * needing a shared cross-module bean.
 */
public interface TenantDetailsRepository extends JpaRepository<TenantDetails, String> {

	/**
	 * Case-insensitive search on {@code name} or {@code tenantId} — backs
	 * {@code TenantDetailsDatastoreHelper#search(String)} / {@code #findSlice(...)}.
	 */
	java.util.List<TenantDetails> findByNameContainingIgnoreCaseOrTenantIdContainingIgnoreCase(String name,
			String tenantId);

	/**
	 * Count variant of {@link #findByNameContainingIgnoreCaseOrTenantIdContainingIgnoreCase}.
	 */
	long countByNameContainingIgnoreCaseOrTenantIdContainingIgnoreCase(String name, String tenantId);

	/**
	 * Backs {@code TenantDetailsDatastoreHelper#countByStatus(TenantStatus)}.
	 */
	long countByStatus(TenantStatus status);

	/**
	 * Backs {@code TenantDetailsDatastoreHelper#countByPlan(TenantPlan)}.
	 */
	long countByPlan(TenantPlan plan);
}
