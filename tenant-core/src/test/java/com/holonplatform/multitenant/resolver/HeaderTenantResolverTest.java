package com.holonplatform.multitenant.resolver;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link HeaderTenantResolver}.
 */
class HeaderTenantResolverTest {

	private final HeaderTenantResolver resolver = new HeaderTenantResolver();

	@Test
	void resolveTenantId_extractsDefaultHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Tenant-ID", "acme");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
	}

	@Test
	void resolveTenantId_normalisesToLowerCase() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Tenant-ID", "ACME");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
	}

	@Test
	void resolveTenantId_trimsWhitespace() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Tenant-ID", "  acme  ");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
	}

	@Test
	void resolveTenantId_returnsEmpty_whenHeaderAbsent() {
		assertThat(resolver.resolveTenantId(new MockHttpServletRequest())).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_forBlankHeaderValue() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Tenant-ID", "   ");

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void customHeaderName_isUsed() {
		HeaderTenantResolver custom = new HeaderTenantResolver("X-Org-ID");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Org-ID", "org-42");

		assertThat(custom.resolveTenantId(request)).contains("org-42");
	}

	@Test
	void constructor_throwsOnBlankHeaderName() {
		assertThatThrownBy(() -> new HeaderTenantResolver(""))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void constructor_throwsOnNullHeaderName() {
		assertThatThrownBy(() -> new HeaderTenantResolver(null))
				.isInstanceOf(NullPointerException.class);
	}
}

