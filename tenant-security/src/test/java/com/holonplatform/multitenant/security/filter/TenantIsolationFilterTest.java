package com.holonplatform.multitenant.security.filter;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.security.TenantIdExtractor;
import com.holonplatform.multitenant.security.TenantUserDetails;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantIsolationFilter}.
 */
class TenantIsolationFilterTest {

	@BeforeEach
	void setUp() {
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static TenantDetails td(String id) {
		return TenantDetails.builder(id).build();
	}

	private static void authenticateAs(TenantDetails tenant) {
		TenantUserDetails ud = TenantUserDetails.builder("user@" + tenant.tenantId(), tenant).build();
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
	}

	// -------------------------------------------------------------------------
	// Positive cases
	// -------------------------------------------------------------------------

	@Test
	void filter_allowsRequest_whenTenantsMatch() throws Exception {
		TenantDetails td = td("acme");
		TenantContext.set(td);
		authenticateAs(td);

		TenantIsolationFilter filter = new TenantIsolationFilter(TenantIdExtractor.fromTenantAwareUserDetails());
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

		verify(chain, times(1)).doFilter(any(), any());
	}

	@Test
	void filter_allowsRequest_whenNoAuthentication() throws Exception {
		TenantContext.set(td("acme"));
		// No authentication in context

		TenantIsolationFilter filter = new TenantIsolationFilter(TenantIdExtractor.fromTenantAwareUserDetails());
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

		verify(chain, times(1)).doFilter(any(), any());
	}

	@Test
	void filter_allowsRequest_whenNoTenantContext() throws Exception {
		authenticateAs(td("acme"));
		// No TenantContext set

		TenantIsolationFilter filter = new TenantIsolationFilter(TenantIdExtractor.fromTenantAwareUserDetails());
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

		verify(chain, times(1)).doFilter(any(), any());
	}

	// -------------------------------------------------------------------------
	// Negative cases
	// -------------------------------------------------------------------------

	@Test
	void filter_returns403_whenTenantsMismatch_andEnforced() throws Exception {
		TenantContext.set(td("beta"));     // request targets beta
		authenticateAs(td("acme"));        // but user belongs to acme

		TenantIsolationFilter filter = new TenantIsolationFilter(TenantIdExtractor.fromTenantAwareUserDetails());
		filter.setEnforced(true);

		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(new MockHttpServletRequest(), response, chain);

		assertThat(response.getStatus()).isEqualTo(403);
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	void filter_allowsRequest_whenTenantsMismatch_andNotEnforced() throws Exception {
		TenantContext.set(td("beta"));
		authenticateAs(td("acme"));

		TenantIsolationFilter filter = new TenantIsolationFilter(TenantIdExtractor.fromTenantAwareUserDetails());
		filter.setEnforced(false);   // warn only

		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(new MockHttpServletRequest(), response, chain);

		// Should pass through (enforcement disabled)
		verify(chain, times(1)).doFilter(any(), any());
	}

	@Test
	void filter_publishesEvent_onMismatch() throws Exception {
		TenantContext.set(td("beta"));
		authenticateAs(td("acme"));

		var eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
		TenantIsolationFilter filter = new TenantIsolationFilter(TenantIdExtractor.fromTenantAwareUserDetails());
		filter.setEventPublisher(eventPublisher);
		filter.setEnforced(true);

		filter.doFilter(new MockHttpServletRequest(),
				new MockHttpServletResponse(),
				mock(FilterChain.class));

		verify(eventPublisher, times(1)).publishEvent(
				any(com.holonplatform.multitenant.security.event.CrossTenantAccessAttemptEvent.class));
	}

	@Test
	void constructor_throwsOnNullExtractor() {
		assertThatThrownBy(() -> new TenantIsolationFilter(null))
				.isInstanceOf(NullPointerException.class);
	}
}

