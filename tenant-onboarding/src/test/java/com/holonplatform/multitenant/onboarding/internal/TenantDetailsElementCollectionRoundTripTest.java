package com.holonplatform.multitenant.onboarding.internal;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsRepository;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real, H2-backed regression test proving that {@link TenantDetails}'s reverted
 * {@code @ElementCollection} mappings for {@code features}/{@code attributes} are safe now
 * that persistence goes through a plain {@link TenantDetailsRepository} (a standard
 * {@code JpaRepository}) rather than Holon's {@code BeanDatastoreHelper}.
 *
 * <h2>What this specifically guards against</h2>
 * <p>Earlier this session, {@code TenantUser}/{@code TenantDetails}/{@code UserInvitation}/
 * {@code WhatsAppMessage} all hit a genuine bug: Holon's {@code BeanDatastoreHelper} builds its
 * own JPQL referencing {@code Map}/{@code Set}-valued {@code @ElementCollection} properties
 * directly in the {@code SELECT} list, which Hibernate always translates to an <strong>inner
 * join</strong> — silently dropping the entire parent row whenever the collection was empty
 * (the common case). That bug is specific to how Holon constructs queries, not a general JPA
 * defect: a plain {@code JpaRepository#findById}/{@code EntityManager#find} loads element
 * collections via Hibernate's own default {@code FetchMode.SELECT} (a separate, FK-keyed
 * query), which can never drop the owning row. This test proves that empirically rather than
 * relying on that reasoning alone.
 */
class TenantDetailsElementCollectionRoundTripTest {

	@Test
	void findById_returnsEntity_whenElementCollectionsAreEmpty() {
		runWithRealH2(ctx -> inTransaction(ctx, () -> {
			TenantDetailsRepository repository = tenantDetailsRepository(ctx);

			// The exact scenario that caused the original bug: no features, no attributes.
			TenantDetails tenant = TenantDetails.builder("acme-" + UUID.randomUUID())
					.name("Acme Inc.")
					.plan(TenantPlan.STARTER)
					.status(TenantStatus.ACTIVE)
					.build();
			assertThat(tenant.features()).isEmpty();
			assertThat(tenant.attributes()).isEmpty();

			repository.save(tenant);
			repository.flush();

			assertThat(repository.findById(tenant.tenantId()))
					.as("the tenant row must still be found even though its element collections are empty")
					.isPresent()
					.get()
					.satisfies(found -> {
						assertThat(found.name()).isEqualTo("Acme Inc.");
						assertThat(found.features()).isEmpty();
						assertThat(found.attributes()).isEmpty();
					});
		}));
	}

	@Test
	void findById_returnsEntity_withPopulatedElementCollections() {
		runWithRealH2(ctx -> inTransaction(ctx, () -> {
			TenantDetailsRepository repository = tenantDetailsRepository(ctx);

			TenantDetails tenant = TenantDetails.builder("acme-" + UUID.randomUUID())
					.name("Acme Inc.")
					.plan(TenantPlan.STARTER)
					.status(TenantStatus.ACTIVE)
					.features(Set.of("beta_ui", "advanced_reports"))
					.attributes(Map.of("source", "signup", "referrer", "google"))
					.build();

			repository.save(tenant);
			repository.flush();

			assertThat(repository.findById(tenant.tenantId()))
					.isPresent()
					.get()
					.satisfies(found -> {
						assertThat(found.features()).containsExactlyInAnyOrder("beta_ui", "advanced_reports");
						assertThat(found.attributes())
								.containsExactlyInAnyOrderEntriesOf(Map.of(
										"source", "signup",
										"referrer", "google"));
					});
		}));
	}

	// -------------------------------------------------------------------------
	// Support
	// -------------------------------------------------------------------------

	private static TenantDetailsRepository tenantDetailsRepository(
			org.springframework.context.ApplicationContext ctx) {
		EntityManager entityManager = ctx.getBean(EntityManager.class);
		return new JpaRepositoryFactory(entityManager).getRepository(TenantDetailsRepository.class);
	}

	/**
	 * Runs {@code body} within a real Spring-managed transaction. Necessary because a
	 * {@link TenantDetailsRepository} built directly via {@link JpaRepositoryFactory} (rather
	 * than through {@code @EnableJpaRepositories} component scanning) is <em>not</em> wrapped
	 * with Spring Data's usual {@code @Transactional} proxy, and {@code JpaRepository#save}
	 * (which calls {@code EntityManager#merge}) requires an active transaction.
	 */
	private static void inTransaction(org.springframework.context.ApplicationContext ctx, Runnable body) {
		org.springframework.transaction.PlatformTransactionManager txManager =
				ctx.getBean(org.springframework.transaction.PlatformTransactionManager.class);
		new org.springframework.transaction.support.TransactionTemplate(txManager).executeWithoutResult(
				status -> body.run());
	}

	private static void runWithRealH2(
			java.util.function.Consumer<org.springframework.context.ApplicationContext> assertions) {
		String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
		org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
				new org.springframework.jdbc.datasource.SimpleDriverDataSource();
		dataSource.setDriverClass(org.h2.Driver.class);
		dataSource.setUrl(jdbcUrl);
		dataSource.setUsername("sa");
		dataSource.setPassword("");

		// Holon's ConfigurableLocalContainerEntityManagerFactoryBean does not honor
		// spring.jpa.hibernate.ddl-auto (verified empirically — no DDL export log appears at
		// startup), so the schema is created directly here, mirroring exactly the mapping
		// currently declared on TenantDetails (reverted @ElementCollection tables). This is
		// deliberately real DDL, not a mock — the whole point of this test is to prove the
		// real Hibernate SQL generated for these mappings behaves correctly.
		try (java.sql.Connection connection = dataSource.getConnection();
				java.sql.Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE tenant (" +
					"tenant_id VARCHAR(64) NOT NULL PRIMARY KEY, " +
					"name VARCHAR(255), " +
					"plan VARCHAR(32) NOT NULL, " +
					"status VARCHAR(32) NOT NULL, " +
					"locale VARCHAR(32), " +
					"timezone VARCHAR(64), " +
					"theme VARCHAR(64))");
			statement.execute("CREATE TABLE tenant_features (" +
					"tenant_id VARCHAR(64) NOT NULL REFERENCES tenant (tenant_id), " +
					"feature_key VARCHAR(100) NOT NULL, " +
					"PRIMARY KEY (tenant_id, feature_key))");
			statement.execute("CREATE TABLE tenant_attributes (" +
					"tenant_id VARCHAR(64) NOT NULL REFERENCES tenant (tenant_id), " +
					"attr_key VARCHAR(255) NOT NULL, " +
					"attr_value TEXT, " +
					"PRIMARY KEY (tenant_id, attr_key))");
		} catch (java.sql.SQLException e) {
			throw new IllegalStateException("Failed to create test schema", e);
		}

		new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(
						com.holonplatform.jpa.spring.boot.JpaAutoConfiguration.class,
						org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
						org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration.class,
						org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class))
				.withUserConfiguration(EntityScanConfig.class)
				.withBean(DataSource.class, () -> dataSource)
				.withPropertyValues(
						"spring.jpa.hibernate.ddl-auto=none",
						"spring.jpa.open-in-view=false",
						"spring.jpa.properties.jakarta.persistence.validation.mode=none",
						"spring.jpa.properties.hibernate.integration.beanvalidation=false")
				.run(ctx -> {
					assertThat(ctx).hasNotFailed();
					assertions.accept(ctx);
				});
	}

	@org.springframework.boot.persistence.autoconfigure.EntityScan("com.holonplatform.multitenant")
	static class EntityScanConfig {
	}
}
