package com.holonplatform.multitenant.filter;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.exception.TenantNotFoundException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantContextFilter}.
 */
class TenantContextFilterTest {

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static TenantDetails activeTenant(String id) {
		return TenantDetails.builder(id).plan(TenantPlan.FREE).status(TenantStatus.ACTIVE).build();
	}

	// -------------------------------------------------------------------------
	// Positive cases
	// -------------------------------------------------------------------------

	@Test
	void doFilter_setsTenantContextAndClearsAfter() throws Exception {
		TenantDetails tenant = activeTenant("acme");
		TenantResolver resolver = req -> Optional.of("acme");
		TenantDetailsLoader loader = id -> tenant;

		TenantContextFilter filter = new TenantContextFilter(resolver, loader);
		FilterChain chain = (req, res) -> {
			// Inside the filter chain the context must be set
			assertThat(TenantContext.require().tenantId()).isEqualTo("acme");
		};

		filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

		// After the filter the context must be cleared
		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void doFilter_ignoredPath_skipsResolution() throws Exception {
		TenantResolver resolver = req -> { throw new AssertionError("Should not be called"); };
		TenantDetailsLoader loader = id -> { throw new AssertionError("Should not be called"); };

		TenantContextFilter filter = new TenantContextFilter(resolver, loader);
		filter.setIgnoredPaths(List.of("/actuator/**"));

		MockHttpServletRequest request  = new MockHttpServletRequest("GET", "/actuator/health");
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(response.getStatus()).isEqualTo(200);
	}

	// -------------------------------------------------------------------------
	// Negative cases
	// -------------------------------------------------------------------------

	@Test
	void doFilter_returns400_whenTenantNotResolved_andRequired() throws Exception {
		TenantResolver resolver = req -> Optional.empty();
		TenantDetailsLoader loader = id -> activeTenant(id);

		TenantContextFilter filter = new TenantContextFilter(resolver, loader);
		filter.setRequired(true);

		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(new MockHttpServletRequest(), response, chain);

		assertThat(response.getStatus()).isEqualTo(400);
		verify(chain, never()).doFilter(any(), any());
	}

	@Test
	void doFilter_proceedsWithoutContext_whenTenantNotResolved_andNotRequired() throws Exception {
		TenantResolver resolver = req -> Optional.empty();
		TenantDetailsLoader loader = id -> activeTenant(id);

		TenantContextFilter filter = new TenantContextFilter(resolver, loader);
		filter.setRequired(false);

		MockHttpServletRequest request   = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		FilterChain chain = mock(FilterChain.class);

		filter.doFilter(request, response, chain);

		verify(chain).doFilter(request, response);
		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void doFilter_returns404_whenTenantNotFound_andRequired() throws Exception {
		TenantResolver resolver = req -> Optional.of("unknown-tenant");
		TenantDetailsLoader loader = id -> { throw new TenantNotFoundException(id); };

		TenantContextFilter filter = new TenantContextFilter(resolver, loader);
		filter.setRequired(true);

		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest(), response, mock(FilterChain.class));

		assertThat(response.getStatus()).isEqualTo(404);
	}

	@Test
	void doFilter_returns403_whenTenantSuspended_andRequired() throws Exception {
		TenantDetails suspended = TenantDetails.builder("acme").status(TenantStatus.SUSPENDED).build();
		TenantResolver resolver = req -> Optional.of("acme");
		TenantDetailsLoader loader = id -> suspended;

		TenantContextFilter filter = new TenantContextFilter(resolver, loader);
		filter.setRequired(true);

		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(new MockHttpServletRequest(), response, mock(FilterChain.class));

		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	void doFilter_alwaysClearsContext_evenWhenChainThrows() throws Exception {
		TenantResolver resolver = req -> Optional.of("acme");
		TenantDetailsLoader loader = id -> activeTenant(id);

		TenantContextFilter filter = new TenantContextFilter(resolver, loader);
		FilterChain chain = (req, res) -> { throw new RuntimeException("chain exploded"); };

		assertThatThrownBy(() ->
				filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
				.isInstanceOf(RuntimeException.class);

		// Context must be clean even after chain exception
		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void constructor_throwsOnNullResolver() {
		assertThatThrownBy(() -> new TenantContextFilter(null, id -> activeTenant(id)))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void constructor_throwsOnNullLoader() {
		assertThatThrownBy(() -> new TenantContextFilter(req -> Optional.empty(), null))
				.isInstanceOf(NullPointerException.class);
	}
}

