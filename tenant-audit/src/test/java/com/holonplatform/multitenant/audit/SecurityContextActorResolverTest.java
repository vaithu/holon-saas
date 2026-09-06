package com.holonplatform.multitenant.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SecurityContextActorResolver}.
 */
class SecurityContextActorResolverTest {

	private final SecurityContextActorResolver resolver = new SecurityContextActorResolver();

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void resolveActor_returnsSystem_whenNoAuthenticationPresent() {
		assertThat(resolver.resolveActor()).isEqualTo(ActorResolver.SYSTEM);
	}

	@Test
	void getCurrentAuditor_returnsEmpty_whenNoAuthenticationPresent() {
		assertThat(resolver.getCurrentAuditor()).isEmpty();
	}

	@Test
	void resolveActor_returnsUsername_whenAuthenticated() {
		authenticateAs("alice");

		assertThat(resolver.resolveActor()).isEqualTo("alice");
	}

	@Test
	void getCurrentAuditor_returnsUsername_whenAuthenticated() {
		authenticateAs("alice");

		assertThat(resolver.getCurrentAuditor()).contains("alice");
	}

	@Test
	void resolveActor_returnsSystem_whenAuthenticationIsNotAuthenticated() {
		UsernamePasswordAuthenticationToken unauthenticated =
				new UsernamePasswordAuthenticationToken("bob", "password");
		SecurityContextHolder.getContext().setAuthentication(unauthenticated);

		assertThat(unauthenticated.isAuthenticated()).isFalse(); // sanity check on the fixture itself
		assertThat(resolver.resolveActor()).isEqualTo(ActorResolver.SYSTEM);
	}

	private static void authenticateAs(String username) {
		UsernamePasswordAuthenticationToken authenticated =
				UsernamePasswordAuthenticationToken.authenticated(username, "password", java.util.List.of());
		SecurityContextHolder.getContext().setAuthentication(authenticated);
	}
}
