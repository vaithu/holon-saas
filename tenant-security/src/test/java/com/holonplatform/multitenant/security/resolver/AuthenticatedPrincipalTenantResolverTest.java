package com.holonplatform.multitenant.security.resolver;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.security.TenantIdExtractor;
import com.holonplatform.multitenant.security.TenantUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AuthenticatedPrincipalTenantResolver}.
 */
class AuthenticatedPrincipalTenantResolverTest {

	private final AuthenticatedPrincipalTenantResolver resolver =
			new AuthenticatedPrincipalTenantResolver(TenantIdExtractor.fromTenantAwareUserDetails());

	@BeforeEach
	void setUp() {
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	private static void authenticateAs(String tenantId) {
		TenantUserDetails ud = TenantUserDetails.builder("user@" + tenantId,
				TenantDetails.builder(tenantId).build()).build();
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
	}

	@Test
	void resolveTenantId_returnsTenant_fromAuthenticatedPrincipal() {
		authenticateAs("acme");
		// The request carries no tenant signal — resolution comes purely from the principal.
		assertThat(resolver.resolveTenantId(new MockHttpServletRequest("POST", "/"))).contains("acme");
	}

	@Test
	void resolveTenantId_returnsEmpty_whenNoAuthentication() {
		assertThat(resolver.resolveTenantId(new MockHttpServletRequest("POST", "/"))).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_whenAnonymous() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

		assertThat(resolver.resolveTenantId(new MockHttpServletRequest("POST", "/"))).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_whenPrincipalIsNotTenantAware() {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("plain-user", null,
						AuthorityUtils.createAuthorityList("ROLE_USER")));

		assertThat(resolver.resolveTenantId(new MockHttpServletRequest("POST", "/"))).isEmpty();
	}

	@Test
	void getOrder_isAfterAllBuiltInResolvers() {
		assertThat(resolver.getOrder()).isEqualTo(AuthenticatedPrincipalTenantResolver.DEFAULT_ORDER);
		assertThat(resolver.getOrder()).isGreaterThan(40); // JWT resolver, the highest built-in order
	}

	@Test
	void customOrder_isHonoured() {
		AuthenticatedPrincipalTenantResolver custom =
				new AuthenticatedPrincipalTenantResolver(TenantIdExtractor.fromTenantAwareUserDetails(), 5);
		assertThat(custom.getOrder()).isEqualTo(5);
	}

	@Test
	void constructor_rejectsNullExtractor() {
		assertThatThrownBy(() -> new AuthenticatedPrincipalTenantResolver(null))
				.isInstanceOf(NullPointerException.class);
	}
}
