package com.holonplatform.multitenant.resolver;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SubdomainTenantResolver}.
 */
class SubdomainTenantResolverTest {

	private final SubdomainTenantResolver resolver = new SubdomainTenantResolver();

	// -------------------------------------------------------------------------
	// Positive cases
	// -------------------------------------------------------------------------

	@Test
	void resolveTenantId_extractsFirstSubdomain() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("acme.app.com");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
	}

	@Test
	void resolveTenantId_extractsDeepSubdomain() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("umbrella-corp.staging.app.com");

		assertThat(resolver.resolveTenantId(request)).contains("umbrella-corp");
	}

	@Test
	void resolveTenantId_normalisesToLowerCase() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("ACME.app.com");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
	}

	// -------------------------------------------------------------------------
	// Negative / edge cases
	// -------------------------------------------------------------------------

	@Test
	void resolveTenantId_returnsEmpty_forLocalhost() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("localhost");

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_forIpAddress() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("127.0.0.1");

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_forReservedWwwSubdomain() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("www.app.com");

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_forReservedAppSubdomain() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName("app.com");

		// "app.com" has only 2 parts but first is "app" which is reserved
		// AND it has only 2 labels (no real subdomain above a 2-part host)
		// — single label detection: parts.length is 2, so candidate = "app", which is reserved
		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_forNullServerName() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServerName(null);

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}
}

