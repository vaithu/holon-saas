package com.holonplatform.multitenant.signup.internal;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.data.DatabaseDialect;
import com.holonplatform.multitenant.data.SchemaInitSqlProvider;
import com.holonplatform.multitenant.data.SchemaNameResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.TenantSchemaStrategy;
import com.holonplatform.multitenant.data.datasource.BoundedTenantDataSourceRegistry;
import com.holonplatform.multitenant.data.datasource.SchemaPerTenantDataSourceProvider;
import com.holonplatform.multitenant.data.datasource.TenantAwareDataSource;
import com.holonplatform.multitenant.data.migration.TenantSchemaInitializer;
import com.holonplatform.multitenant.onboarding.TenantOnboardingService;
import com.holonplatform.multitenant.onboarding.TenantRepository;
import com.holonplatform.multitenant.onboarding.internal.DefaultTenantOnboardingService;
import com.holonplatform.multitenant.signup.AccountCredentialsStore;
import com.holonplatform.multitenant.signup.SignupRequest;
import com.holonplatform.multitenant.signup.SignupService;
import com.holonplatform.multitenant.signup.config.SignupAutoConfiguration;
import com.holonplatform.multitenant.users.TenantUserService;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test proving {@link DefaultSignupService#signup} works correctly when
 * {@link SignupService} is a <strong>genuine Spring-managed bean</strong> (via
 * {@link SignupAutoConfiguration}, exactly as {@code crm-minimal} wires it) AND
 * {@link AccountCredentialsStore#existsByEmail} is backed by a real JPA entity in the
 * <em>platform</em> schema, sharing the same {@code EntityManagerFactory}/{@code Datastore}
 * as the tenant-scoped operations.
 *
 * <h2>Why {@link DefaultSignupServiceTest} did not catch the real production bug</h2>
 * <p>That test constructs {@code DefaultSignupService} via plain {@code new(...)}, so any
 * {@code @Transactional} annotation on {@code signup(...)} is never actually applied (no
 * Spring AOP proxy exists for a hand-constructed object) — and its
 * {@code InMemoryAccountCredentialsStore} is a bare {@code HashMap}, never touching the JPA
 * infrastructure at all. Neither gap exists here: {@link SignupAutoConfiguration} registers
 * a real, potentially-transactional bean, and {@link JpaAccountCredentialsStore} performs a
 * genuine platform-schema JPA read before onboarding even starts — precisely reproducing the
 * conditions that caused the real bug:
 *
 * <p>An earlier version of {@code DefaultSignupService.signup(...)} was annotated
 * {@code @Transactional}. Since {@code credentialsStore.existsByEmail(...)} (a platform-schema
 * read) is the very first SQL statement in that method, Hibernate acquired a physical JDBC
 * connection for the whole transaction right then — with {@link TenantContext} correctly
 * empty at that moment (no tenant exists yet) — and reused that same connection for every
 * subsequent statement in the transaction, including {@code tenantUserService.invite(...)},
 * regardless of {@link TenantContext} being (correctly, in isolation) bound later in the same
 * method. This test would fail with {@code Table "TENANT_USERS" not found} if
 * {@code @Transactional} were ever reintroduced on {@code signup(...)}.
 */
class DefaultSignupServiceTransactionalRegressionTest {

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void signup_succeeds_asRealSpringBean_withJpaBackedCredentialsStore() throws Exception {
		String tenantId = "acme-" + UUID.randomUUID().toString().substring(0, 8);
		String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

		SchemaNameResolver schemaNameResolver = SchemaNameResolver.withPrefix("tenant_");
		SchemaInitSqlProvider initSqlProvider = SchemaInitSqlProvider.forDialect(DatabaseDialect.H2);
		SchemaPerTenantDataSourceProvider provider = new SchemaPerTenantDataSourceProvider(
				jdbcUrl, "sa", "", schemaNameResolver, initSqlProvider);

		try (TenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(provider, 10)) {
			TenantAwareDataSource dataSource =
					new TenantAwareDataSource(TenantSchemaStrategy.SCHEMA_PER_TENANT, registry);

			// Platform-schema table for the JPA-backed credentials store below — created
			// directly via the platform (no-tenant) connection, mirroring how crm-minimal's
			// own account table lives in the platform schema, not any tenant schema.
			try (java.sql.Connection connection = registry.getDataSource(null).getConnection();
					java.sql.Statement statement = connection.createStatement()) {
				statement.execute("CREATE TABLE test_platform_account (" +
						"id VARCHAR(512) NOT NULL PRIMARY KEY, " +
						"encoded_password VARCHAR(255) NOT NULL)");
			}

			TenantSchemaInitializer schemaInitializer = new TenantSchemaInitializer(registry, schemaNameResolver);
			schemaInitializer.setDialect(DatabaseDialect.H2);
			schemaInitializer.setAutoCreateSchema(true);

			TenantRepository tenantRepository = new InMemoryTenantRepository();
			ApplicationEventPublisher noopPublisher = event -> {
			};

			DefaultTenantOnboardingService onboardingService =
					new DefaultTenantOnboardingService(tenantRepository, schemaInitializer, noopPublisher);

			new ApplicationContextRunner()
					.withConfiguration(AutoConfigurations.of(
							com.holonplatform.jpa.spring.boot.JpaAutoConfiguration.class,
							com.holonplatform.jpa.spring.boot.JpaDatastoreAutoConfiguration.class,
							org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
							org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration.class,
							org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class,
							com.holonplatform.multitenant.config.TenantCoreAutoConfiguration.class,
							com.holonplatform.multitenant.users.config.TenantUserAutoConfiguration.class,
							// The real wiring path crm-minimal uses: SignupAutoConfiguration
							// registers DefaultSignupService as an actual Spring bean, so any
							// @Transactional on signup(...) would genuinely be proxied here —
							// unlike DefaultSignupServiceTest's plain `new DefaultSignupService(...)`.
							SignupAutoConfiguration.class))
					.withUserConfiguration(EntityScanConfig.class, CredentialsStoreConfig.class)
					.withBean(DataSource.class, () -> dataSource)
					.withBean(TenantOnboardingService.class, () -> onboardingService)
					.withBean(TenantRepository.class, () -> tenantRepository)
					.withPropertyValues(
							"spring.jpa.hibernate.ddl-auto=none",
							"spring.jpa.open-in-view=false",
							"spring.jpa.properties.jakarta.persistence.validation.mode=none",
							"spring.jpa.properties.hibernate.integration.beanvalidation=false")
					.run(ctx -> {
						assertThat(ctx).hasNotFailed();

						SignupService signupService = ctx.getBean(SignupService.class);
						TenantUserService tenantUserService = ctx.getBean(TenantUserService.class);

						SignupRequest request = SignupRequest
								.builder(tenantId, "owner@" + tenantId + ".test", "s3cr3t!")
								.companyName("Acme Inc.")
								.plan(TenantPlan.STARTER)
								.build();

						// Before the fix (removing @Transactional from DefaultSignupService.signup),
						// this line throws SignupException/TenantOnboardingException wrapping
						// "Table TENANT_USERS not found", because the platform-schema read just
						// above (existsByEmail, inside signup()) would have pinned the whole
						// transaction's connection to the platform schema.
						TenantDetails tenant = signupService.signup(request);
						assertThat(tenant.tenantId()).isEqualTo(tenantId);

						TenantContext.set(tenant);
						try {
							assertThat(tenantUserService.findByTenant(tenantId, 0, 10))
									.hasSize(1)
									.first()
									.satisfies(user -> assertThat(user.getEmail())
											.isEqualTo("owner@" + tenantId + ".test"));
						} finally {
							TenantContext.clear();
						}
					});
		}
	}

	// -------------------------------------------------------------------------
	// Support
	// -------------------------------------------------------------------------

	@org.springframework.boot.persistence.autoconfigure.EntityScan({
			"com.holonplatform.multitenant.users",
			"com.holonplatform.multitenant.signup.internal"})
	static class EntityScanConfig {
	}

	@Configuration
	static class CredentialsStoreConfig {

		@Bean
		AccountCredentialsStore accountCredentialsStore(EntityManager entityManager) {
			return new JpaAccountCredentialsStore(entityManager);
		}

		@Bean
		PasswordEncoder passwordEncoder() {
			return new BCryptPasswordEncoder();
		}
	}

	/**
	 * A real, platform-schema-backed {@link AccountCredentialsStore} — the key difference
	 * from {@link DefaultSignupServiceTest}'s trivial in-memory fake. Its {@code @Transactional}
	 * methods each get their own independent transaction (Spring's default REQUIRED
	 * propagation, no ambient transaction from {@code signup()} anymore), so
	 * {@code existsByEmail}'s platform-schema read cannot pin a connection for anything else.
	 */
	static class JpaAccountCredentialsStore implements AccountCredentialsStore {

		private final EntityManager entityManager;

		JpaAccountCredentialsStore(EntityManager entityManager) {
			this.entityManager = entityManager;
		}

		@Override
		@Transactional(readOnly = true)
		public boolean existsByEmail(String tenantId, String email) {
			return entityManager.find(TestPlatformAccount.class, key(tenantId, email)) != null;
		}

		@Override
		@Transactional
		public void save(String tenantId, String email, String encodedPassword) {
			entityManager.persist(new TestPlatformAccount(key(tenantId, email), encodedPassword));
		}

		private static String key(String tenantId, String email) {
			return tenantId + "::" + email;
		}
	}

	@Entity(name = "testplatformaccount")
	@Table(name = "test_platform_account")
	public static class TestPlatformAccount {

		@Id
		@jakarta.persistence.Column(name = "id")
		private String id;

		@jakarta.persistence.Column(name = "encoded_password", nullable = false)
		private String encodedPassword;

		public TestPlatformAccount() {
		}

		public TestPlatformAccount(String id, String encodedPassword) {
			this.id = id;
			this.encodedPassword = encodedPassword;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getEncodedPassword() {
			return encodedPassword;
		}

		public void setEncodedPassword(String encodedPassword) {
			this.encodedPassword = encodedPassword;
		}
	}

	static class InMemoryTenantRepository implements TenantRepository {
		private final Map<String, TenantDetails> store = new ConcurrentHashMap<>();

		@Override
		public TenantDetails save(TenantDetails tenant) {
			store.put(tenant.tenantId(), tenant);
			return tenant;
		}

		@Override
		public Optional<TenantDetails> findById(String tenantId) {
			return Optional.ofNullable(store.get(tenantId));
		}

		@Override
		public boolean exists(String tenantId) {
			return store.containsKey(tenantId);
		}
	}
}
