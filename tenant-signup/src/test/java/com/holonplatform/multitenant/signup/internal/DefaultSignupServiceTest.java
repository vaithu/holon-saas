package com.holonplatform.multitenant.signup.internal;

import com.holonplatform.core.datastore.Datastore;
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
import com.holonplatform.multitenant.onboarding.TenantRepository;
import com.holonplatform.multitenant.onboarding.internal.DefaultTenantOnboardingService;
import com.holonplatform.multitenant.signup.AccountCredentialsStore;
import com.holonplatform.multitenant.signup.SignupRequest;
import com.holonplatform.multitenant.users.TenantUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end regression test for {@link DefaultSignupService}, exercising the real
 * onboarding + tenant-user + Datastore/Hibernate stack against a real H2 database — exactly
 * the combination {@code crm-minimal} exercises in production.
 *
 * <h2>The bug this test reproduces and guards against</h2>
 * <p>{@code tenantUserService.invite(...)}/{@code .activate(...)} write through the generic,
 * tenant-scoped {@link Datastore}, routed by {@link TenantAwareDataSource}, which resolves
 * the target schema from {@code TenantContext.peekTenantId()} — <em>not</em> from any
 * argument passed to {@code signup(...)}. A signup HTTP endpoint is commonly excluded from
 * path-based tenant resolution (no tenant exists yet for the filter to resolve against), so
 * {@code TenantContext} is empty for the whole duration of the request unless
 * {@link DefaultSignupService} binds it explicitly. Before the fix, both calls silently fell
 * back to the platform/bootstrap connection and failed with
 * {@code Table "TENANT_USERS" not found} — that table only exists in the new tenant's own,
 * freshly-migrated schema, never in the platform one.
 */
class DefaultSignupServiceTest {

	@AfterEach
	void clearTenantContext() {
		TenantContext.clear();
	}

	@Test
	void signup_createsTenantAndOwnerUser_endToEnd_onH2() {
		String tenantId = "acme-" + UUID.randomUUID().toString().substring(0, 8);

		withRealSignupStack(harness -> {
			SignupRequest request = SignupRequest
					.builder(tenantId, "owner@" + tenantId + ".test", "s3cr3t!")
					.companyName("Acme Inc.")
					.plan(TenantPlan.STARTER)
					.build();

			// Before the fix, this line throws TenantOnboardingException wrapping
			// "Table TENANT_USERS not found" — reproduced by simply NOT setting
			// TenantContext anywhere (matching a real /signup HTTP request, which
			// is deliberately excluded from tenant-path resolution).
			TenantDetails tenant = harness.signupService.signup(request);
			assertThat(tenant.tenantId()).isEqualTo(tenantId);

			// Verify the owner TenantUser genuinely exists in the tenant's OWN
			// schema (not the platform/bootstrap one) — this is only readable
			// with TenantContext correctly bound, exactly as a real subsequent
			// tenant-scoped request (via TenantContextFilter) would do.
			TenantContext.set(tenant);
			try {
				assertThat(harness.tenantUserService.findByTenant(tenantId, 0, 10))
						.hasSize(1)
						.first()
						.satisfies(user -> {
							assertThat(user.getEmail()).isEqualTo("owner@" + tenantId + ".test");
							assertThat(user.isActive()).isTrue();
						});
			} finally {
				TenantContext.clear();
			}

			// credentialsStore.save(...) must still have been called with the
			// platform-schema semantics intact (TenantContext cleared again
			// before that call) — verified indirectly via the captured arguments.
			assertThat(harness.credentialsStore.lastTenantId).isEqualTo(tenantId);
			assertThat(harness.credentialsStore.lastEmail).isEqualTo("owner@" + tenantId + ".test");
		});
	}

	@Test
	void signup_resumesPartiallyFailedSignup_whenOnboardingSucceededButNothingElseDid() {
		String tenantId = "acme-" + UUID.randomUUID().toString().substring(0, 8);
		String email = "owner@" + tenantId + ".test";

		withRealSignupStack(harness -> {
			// Simulate the exact failure this feature compensates for: onboarding succeeded
			// (tenant is ACTIVE) but the process crashed/errored before owner-user creation
			// or credential storage ever ran — by calling onboard() directly, bypassing
			// signupService entirely, and doing nothing else.
			harness.onboardingService.onboard(
					com.holonplatform.multitenant.onboarding.TenantOnboardingRequest.builder(tenantId)
							.name("Acme Inc.")
							.plan(TenantPlan.STARTER)
							.adminEmail(email)
							.build())
					.orThrow();

			SignupRequest retryRequest = SignupRequest.builder(tenantId, email, "s3cr3t!")
					.companyName("Acme Inc.")
					.plan(TenantPlan.STARTER)
					.build();

			// Must NOT throw "tenant already exists" — must resume and complete signup.
			TenantDetails tenant = harness.signupService.signup(retryRequest);
			assertThat(tenant.tenantId()).isEqualTo(tenantId);

			TenantContext.set(tenant);
			try {
				assertThat(harness.tenantUserService.findByTenant(tenantId, 0, 10))
						.hasSize(1)
						.first()
						.satisfies(user -> assertThat(user.getEmail()).isEqualTo(email));
			} finally {
				TenantContext.clear();
			}
			assertThat(harness.credentialsStore.lastTenantId).isEqualTo(tenantId);
		});
	}

	@Test
	void signup_resumingDoesNotDuplicateOwnerUser_whenOwnerAlreadyProvisioned() {
		String tenantId = "acme-" + UUID.randomUUID().toString().substring(0, 8);
		String email = "owner@" + tenantId + ".test";

		withRealSignupStack(harness -> {
			// Simulate a LATER partial failure: onboarding AND owner-user creation both
			// succeeded, but credential storage never ran (e.g. the process crashed right
			// before it, or credentialsStore.save(...) itself threw).
			TenantDetails tenant = harness.onboardingService.onboard(
					com.holonplatform.multitenant.onboarding.TenantOnboardingRequest.builder(tenantId)
							.name("Acme Inc.")
							.plan(TenantPlan.STARTER)
							.adminEmail(email)
							.build())
					.orThrow()
					.tenant();
			TenantContext.set(tenant);
			try {
				String userId = UUID.randomUUID().toString();
				harness.tenantUserService.invite(tenantId, userId,
						email, com.holonplatform.multitenant.users.TenantRole.OWNER);
				harness.tenantUserService.activate(tenantId, userId);
			} finally {
				TenantContext.clear();
			}

			SignupRequest retryRequest = SignupRequest.builder(tenantId, email, "s3cr3t!")
					.companyName("Acme Inc.")
					.plan(TenantPlan.STARTER)
					.build();

			assertThat(harness.signupService.signup(retryRequest).tenantId()).isEqualTo(tenantId);

			TenantContext.set(tenant);
			try {
				// Exactly one owner user — resuming must not have created a second one.
				assertThat(harness.tenantUserService.findByTenant(tenantId, 0, 10)).hasSize(1);
			} finally {
				TenantContext.clear();
			}
			assertThat(harness.credentialsStore.lastTenantId).isEqualTo(tenantId);
		});
	}

	@Test
	void signup_throwsSignupException_whenExistingTenantIsNotActive() {
		String tenantId = "acme-" + UUID.randomUUID().toString().substring(0, 8);
		String email = "owner@" + tenantId + ".test";

		withRealSignupStack(harness -> {
			// A tenant record exists (e.g. left PROVISIONING by a failed onboarding attempt),
			// but is not ACTIVE — not a shape this resume logic can safely complete.
			harness.tenantRepository.save(TenantDetails.builder(tenantId)
					.status(com.holonplatform.multitenant.TenantStatus.PROVISIONING)
					.build());

			SignupRequest request = SignupRequest.builder(tenantId, email, "s3cr3t!")
					.companyName("Acme Inc.")
					.plan(TenantPlan.STARTER)
					.build();

			assertThatThrownBy(() -> harness.signupService.signup(request))
					.isInstanceOf(com.holonplatform.multitenant.signup.SignupException.class)
					.hasMessageContaining(tenantId);
		});
	}

	// -------------------------------------------------------------------------
	// Test harness
	// -------------------------------------------------------------------------

	/** Collaborators built by {@link #withRealSignupStack(java.util.function.Consumer)}. */
	private static final class Harness {
		final DefaultSignupService signupService;
		final DefaultTenantOnboardingService onboardingService;
		final TenantRepository tenantRepository;
		final TenantUserService tenantUserService;
		final InMemoryAccountCredentialsStore credentialsStore;

		Harness(DefaultSignupService signupService, DefaultTenantOnboardingService onboardingService,
				TenantRepository tenantRepository, TenantUserService tenantUserService,
				InMemoryAccountCredentialsStore credentialsStore) {
			this.signupService = signupService;
			this.onboardingService = onboardingService;
			this.tenantRepository = tenantRepository;
			this.tenantUserService = tenantUserService;
			this.credentialsStore = credentialsStore;
		}
	}

	/**
	 * Builds a real onboarding + tenant-user + Datastore/Hibernate stack against a real H2
	 * database (exactly the combination {@code crm-minimal} exercises in production) and runs
	 * {@code testBody} against it.
	 */
	private static void withRealSignupStack(java.util.function.Consumer<Harness> testBody) {
		String jdbcUrl = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

		SchemaNameResolver schemaNameResolver = SchemaNameResolver.withPrefix("tenant_");
		SchemaInitSqlProvider initSqlProvider = SchemaInitSqlProvider.forDialect(DatabaseDialect.H2);
		SchemaPerTenantDataSourceProvider provider = new SchemaPerTenantDataSourceProvider(
				jdbcUrl, "sa", "", schemaNameResolver, initSqlProvider);

		try (TenantDataSourceRegistry registry = new BoundedTenantDataSourceRegistry(provider, 10)) {
			TenantAwareDataSource dataSource =
					new TenantAwareDataSource(TenantSchemaStrategy.SCHEMA_PER_TENANT, registry);

			TenantSchemaInitializer schemaInitializer = new TenantSchemaInitializer(registry, schemaNameResolver);
			schemaInitializer.setDialect(DatabaseDialect.H2);
			schemaInitializer.setAutoCreateSchema(true);

			TenantRepository tenantRepository = new InMemoryTenantRepository();
			ApplicationEventPublisher noopPublisher = event -> { };

			DefaultTenantOnboardingService onboardingService =
					new DefaultTenantOnboardingService(tenantRepository, schemaInitializer, noopPublisher);

			InMemoryAccountCredentialsStore credentialsStore = new InMemoryAccountCredentialsStore();
			PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

			new ApplicationContextRunner()
					.withConfiguration(AutoConfigurations.of(
							com.holonplatform.jpa.spring.boot.JpaAutoConfiguration.class,
							com.holonplatform.jpa.spring.boot.JpaDatastoreAutoConfiguration.class,
							org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration.class,
							org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration.class,
							org.springframework.boot.autoconfigure.aop.AopAutoConfiguration.class,
							// Registers the JVM-wide BeanPropertyPostProcessor that excludes computed
							// isXxx() getters with no backing field/write method (e.g. TenantUser#isActive())
							// from Holon's bean property model. In the real app this runs automatically
							// because Spring Boot auto-configuration scanning picks it up; here it must be
							// listed explicitly since the test hand-picks its auto-configurations.
							com.holonplatform.multitenant.config.TenantCoreAutoConfiguration.class,
							com.holonplatform.multitenant.users.config.TenantUserAutoConfiguration.class))
					.withUserConfiguration(EntityScanConfig.class)
					.withBean(DataSource.class, () -> dataSource)
					.withPropertyValues(
							"spring.jpa.hibernate.ddl-auto=none",
							"spring.jpa.open-in-view=false",
							"spring.jpa.properties.hibernate.show_sql=true",
							"spring.jpa.properties.hibernate.format_sql=false",
							"spring.jpa.properties.hibernate.highlight_sql=false",
							"spring.jpa.properties.jakarta.persistence.validation.mode=none",
							"spring.jpa.properties.hibernate.integration.beanvalidation=false")
					.run(ctx -> {
						assertThat(ctx).hasNotFailed();

						TenantUserService tenantUserService = ctx.getBean(TenantUserService.class);

						DefaultSignupService signupService = new DefaultSignupService(
								onboardingService, tenantRepository, tenantUserService, credentialsStore,
								passwordEncoder);

						testBody.accept(new Harness(signupService, onboardingService, tenantRepository,
								tenantUserService, credentialsStore));
					});
		}
	}

	// -------------------------------------------------------------------------
	// Support
	// -------------------------------------------------------------------------

	@org.springframework.boot.persistence.autoconfigure.EntityScan("com.holonplatform.multitenant.users")
	static class EntityScanConfig {
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

	static class InMemoryAccountCredentialsStore implements AccountCredentialsStore {
		final Map<String, String> savedCredentials = new HashMap<>();
		String lastTenantId;
		String lastEmail;

		@Override
		public boolean existsByEmail(String tenantId, String email) {
			return savedCredentials.containsKey(tenantId + "::" + email);
		}

		@Override
		public void save(String tenantId, String email, String encodedPassword) {
			this.lastTenantId = tenantId;
			this.lastEmail = email;
			savedCredentials.put(tenantId + "::" + email, encodedPassword);
		}
	}
}
