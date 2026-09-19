package com.holonplatform.multitenant.security.filter;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.exception.TenantNotFoundException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthenticatedPrincipalTenantContextFilter}.
 */
class AuthenticatedPrincipalTenantContextFilterTest {

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	private static TenantDetails tenant(String id, TenantStatus status) {
		return TenantDetails.builder(id).status(status).build();
	}

	@Test
	void filter_bindsTenantFromPrincipal_whenContextUnset_andClearsAfter() throws Exception {
		TenantResolver principal = req -> Optional.of("acme");
		TenantDetailsLoader loader = id -> tenant(id, TenantStatus.ACTIVE);

		AuthenticatedPrincipalTenantContextFilter filter =
				new AuthenticatedPrincipalTenantContextFilter(principal, loader);
		FilterChain chain = (req, res) ->
				assertThat(TenantContext.require().tenantId()).isEqualTo("acme");

		filter.doFilter(new MockHttpServletRequest("POST", "/"), new MockHttpServletResponse(), chain);

		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void filter_doesNotOverride_whenContextAlreadySet() throws Exception {
		TenantContext.set(tenant("existing", TenantStatus.ACTIVE));
		TenantResolver principal = req -> { throw new AssertionError("resolver must not be called"); };
		TenantDetailsLoader loader = id -> { throw new AssertionError("loader must not be called"); };

		AuthenticatedPrincipalTenantContextFilter filter =
				new AuthenticatedPrincipalTenantContextFilter(principal, loader);
		FilterChain chain = (req, res) ->
				assertThat(TenantContext.require().tenantId()).isEqualTo("existing");

		filter.doFilter(new MockHttpServletRequest("POST", "/"), new MockHttpServletResponse(), chain);

		// The pre-existing context is left untouched (this filter did not bind it, so it must not clear it).
		assertThat(TenantContext.require().tenantId()).isEqualTo("existing");
	}

	@Test
	void filter_doesNotBind_whenPrincipalResolvesNoTenant() throws Exception {
		TenantResolver principal = req -> Optional.empty();
		TenantDetailsLoader loader = id -> { throw new AssertionError("loader must not be called"); };

		AuthenticatedPrincipalTenantContextFilter filter =
				new AuthenticatedPrincipalTenantContextFilter(principal, loader);
		FilterChain chain = mock(FilterChain.class);

		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
		filter.doFilter(request, new MockHttpServletResponse(), chain);

		verify(chain).doFilter(eq(request), any());
		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void filter_doesNotBind_whenTenantNotFound() throws Exception {
		TenantResolver principal = req -> Optional.of("ghost");
		TenantDetailsLoader loader = id -> { throw new TenantNotFoundException(id); };

		AuthenticatedPrincipalTenantContextFilter filter =
				new AuthenticatedPrincipalTenantContextFilter(principal, loader);
		FilterChain chain = (req, res) -> assertThat(TenantContext.isSet()).isFalse();

		filter.doFilter(new MockHttpServletRequest("POST", "/"), new MockHttpServletResponse(), chain);

		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void filter_doesNotBind_whenTenantInactive() throws Exception {
		TenantResolver principal = req -> Optional.of("suspended");
		TenantDetailsLoader loader = id -> tenant(id, TenantStatus.SUSPENDED);

		AuthenticatedPrincipalTenantContextFilter filter =
				new AuthenticatedPrincipalTenantContextFilter(principal, loader);
		FilterChain chain = (req, res) -> assertThat(TenantContext.isSet()).isFalse();

		filter.doFilter(new MockHttpServletRequest("POST", "/"), new MockHttpServletResponse(), chain);

		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void constructor_rejectsNullArguments() {
		TenantResolver principal = req -> Optional.empty();
		TenantDetailsLoader loader = id -> tenant(id, TenantStatus.ACTIVE);

		assertThatThrownBy(() -> new AuthenticatedPrincipalTenantContextFilter(null, loader))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new AuthenticatedPrincipalTenantContextFilter(principal, null))
				.isInstanceOf(NullPointerException.class);
	}
}
