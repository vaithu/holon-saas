package com.holonplatform.multitenant.security.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import com.holonplatform.auth.AuthContext;
import com.holonplatform.core.Context;

/**
 * Unit tests for {@link SpringSecurityAuthContextRegistrar}.
 *
 * <p>These tests manipulate the process-global Holon classloader {@code Context} scope, so each test
 * removes the {@code AuthContext} resource afterwards to avoid leaking state into other tests.
 */
class SpringSecurityAuthContextRegistrarTest {

	@AfterEach
	void cleanUp() {
		Context.get().classLoaderScope().ifPresent(scope -> scope.remove(AuthContext.CONTEXT_KEY));
		SecurityContextHolder.clearContext();
	}

	@Test
	void afterPropertiesSet_publishesAuthContext_soGetCurrentResolves() {
		assertThat(AuthContext.getCurrent()).isEmpty();

		SpringSecurityAuthContextRegistrar registrar = new SpringSecurityAuthContextRegistrar();
		registrar.afterPropertiesSet();

		assertThat(AuthContext.getCurrent()).isPresent();
		// AuthContext.require() must not throw once published.
		assertThat(AuthContext.require()).isNotNull();
	}

	@Test
	void publishedAuthContext_reflectsSecurityContextHolder() {
		SpringSecurityAuthContextRegistrar registrar = new SpringSecurityAuthContextRegistrar();
		registrar.afterPropertiesSet();

		AuthContext authContext = AuthContext.require();
		// No Spring authentication yet -> not authenticated.
		assertThat(authContext.isAuthenticated()).isFalse();
	}

	@Test
	void afterPropertiesSet_isNonDestructive_whenAuthContextAlreadyBound() {
		AuthContext existing = AuthContext.create(com.holonplatform.auth.Realm.builder().build());
		Context.get().classLoaderScope()
				.orElseThrow()
				.put(AuthContext.CONTEXT_KEY, existing);

		SpringSecurityAuthContextRegistrar registrar = new SpringSecurityAuthContextRegistrar();
		registrar.afterPropertiesSet();

		assertThat(AuthContext.getCurrent()).containsSame(existing);
	}

	@Test
	void destroy_removesAuthContext_whenRegisteredByThisInstance() {
		SpringSecurityAuthContextRegistrar registrar = new SpringSecurityAuthContextRegistrar();
		registrar.afterPropertiesSet();
		assertThat(AuthContext.getCurrent()).isPresent();

		registrar.destroy();

		assertThat(AuthContext.getCurrent()).isEmpty();
	}

	@Test
	void destroy_leavesForeignAuthContextInPlace() {
		AuthContext existing = AuthContext.create(com.holonplatform.auth.Realm.builder().build());
		Context.get().classLoaderScope()
				.orElseThrow()
				.put(AuthContext.CONTEXT_KEY, existing);

		SpringSecurityAuthContextRegistrar registrar = new SpringSecurityAuthContextRegistrar();
		registrar.afterPropertiesSet();
		registrar.destroy();

		// The foreign AuthContext must survive: this instance never registered it.
		assertThat(AuthContext.getCurrent()).containsSame(existing);
	}
}
