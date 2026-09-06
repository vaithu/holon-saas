package com.holonplatform.multitenant.onboarding.config;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.onboarding.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-JPA regression test for {@link TenantOnboardingAutoConfiguration}, reproducing the
 * exact failure a real application (e.g. {@code crm-minimal}) hit at startup:
 *
 * <pre>
 * Parameter 0 of method onboardingTenantDetailsDatastoreHelper in
 * TenantOnboardingAutoConfiguration required a bean of type 'TenantDetailsRepository'
 * that could not be found.
 * </pre>
 *
 * <h2>Root cause this test guards against</h2>
 * <p>Holon's own {@code JpaAutoConfiguration} (from {@code holon-datastore-jpa-spring-boot})
 * registers only an {@code EntityManagerFactory} bean, never a standalone {@code EntityManager}
 * bean — unlike Spring Boot's native JPA auto-configuration (which registers both). This
 * module's earlier {@code tenantDetailsRepository} bean method required an {@code EntityManager}
 * bean directly, which is why it silently backed off (via
 * {@code @ConditionalOnBean(EntityManager.class)}) whenever an application relied solely on
 * Holon's JPA auto-configuration — exactly {@code crm-minimal}'s setup.
 *
 * <p>Unlike every other test in this module (which also includes Spring Boot's own
 * {@code HibernateJpaAutoConfiguration}, which happens to independently register an
 * {@code EntityManager} bean and would mask this exact bug), this test deliberately wires
 * <strong>only</strong> Holon's {@code JpaAutoConfiguration} — reproducing the real
 * application's auto-configuration set precisely.
 */
class TenantOnboardingAutoConfigurationRealJpaTest {

	@Test
	void autoConfig_startsSuccessfully_withOnlyHolonJpaAutoConfiguration_noSpringBootJpaAutoConfiguration() {
		String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
		org.springframework.jdbc.datasource.SimpleDriverDataSource dataSource =
				new org.springframework.jdbc.datasource.SimpleDriverDataSource();
		dataSource.setDriverClass(org.h2.Driver.class);
		dataSource.setUrl(jdbcUrl);
		dataSource.setUsername("sa");
		dataSource.setPassword("");

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
						// Deliberately NOT including
						// org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
						// — that class independently registers an EntityManager bean, which would
						// mask the exact bug this test guards against.
						com.holonplatform.jpa.spring.boot.JpaAutoConfiguration.class,
						org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration.class,
						org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class,
						TenantOnboardingAutoConfiguration.class))
				.withUserConfiguration(EntityScanConfig.class)
				.withBean(DataSource.class, () -> dataSource)
				.withPropertyValues(
						"spring.jpa.hibernate.ddl-auto=none",
						"spring.jpa.open-in-view=false",
						"spring.jpa.properties.jakarta.persistence.validation.mode=none",
						"spring.jpa.properties.hibernate.integration.beanvalidation=false")
				.run(ctx -> {
					assertThat(ctx).hasNotFailed();
					assertThat(ctx).hasSingleBean(TenantRepository.class);

					TenantRepository repository = ctx.getBean(TenantRepository.class);
					TenantDetails tenant = TenantDetails.builder("acme-" + UUID.randomUUID())
							.name("Acme Inc.")
							.plan(TenantPlan.STARTER)
							.status(TenantStatus.ACTIVE)
							.build();

					// repository.save(tenant) must succeed on its own, with NO explicit transaction
					// wrapper here — DatabaseTenantRepository (a real @Repository bean) delegates to
					// TenantDetailsRepository, which itself must be genuinely transactional (built via
					// TenantDetailsRepositoryFactory / JpaRepositoryFactoryBean, not a bare
					// JpaRepositoryFactory — see that factory's Javadoc). An earlier version of this
					// test manually wrapped this call in a TransactionTemplate, which masked a real bug:
					// a bare JpaRepositoryFactory-built repository has no transactional behavior at all,
					// failing at runtime with "No EntityManager with actual transaction available for
					// current thread - cannot reliably process 'merge' call" the moment any caller (like
					// DefaultTenantOnboardingService.onboard(...)) invokes it without its own external
					// transaction.
					repository.save(tenant);

					assertThat(repository.findById(tenant.tenantId())).isPresent();
				});
	}

	@org.springframework.boot.persistence.autoconfigure.EntityScan("com.holonplatform.multitenant")
	static class EntityScanConfig {
	}
}
