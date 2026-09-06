package com.holonplatform.multitenant.resolver;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PathPrefixTenantResolver}.
 */
class PathPrefixTenantResolverTest {

	private final PathPrefixTenantResolver resolver = new PathPrefixTenantResolver();

	@Test
	void resolveTenantId_extractsTenantFromPath() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/t/acme/dashboard");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
	}

	@Test
	void resolveTenantId_normalisesToLowerCase() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/t/ACME/settings");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
	}

	@Test
	void resolveTenantId_handlesNoTrailingPath() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/t/acme");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
	}

	@Test
	void resolveTenantId_returnsEmpty_whenPathDoesNotMatchPrefix() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dashboard/home");

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_whenTenantSegmentIsEmpty() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/t/");

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void customPrefix_isUsed() {
		PathPrefixTenantResolver custom = new PathPrefixTenantResolver("/org/");
		MockHttpServletRequest request  = new MockHttpServletRequest("GET", "/org/umbrella/home");

		assertThat(custom.resolveTenantId(request)).contains("umbrella");
	}

	@Test
	void constructor_throwsOnPrefixWithoutLeadingSlash() {
		assertThatThrownBy(() -> new PathPrefixTenantResolver("t/"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void constructor_throwsOnPrefixWithoutTrailingSlash() {
		assertThatThrownBy(() -> new PathPrefixTenantResolver("/t"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}

