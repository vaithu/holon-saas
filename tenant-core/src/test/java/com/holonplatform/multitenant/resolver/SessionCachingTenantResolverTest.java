package com.holonplatform.multitenant.resolver;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SessionCachingTenantResolver}.
 */
class SessionCachingTenantResolverTest {

	private final SessionCachingTenantResolver resolver =
			new SessionCachingTenantResolver(new PathPrefixTenantResolver());

	@Test
	void resolveTenantId_delegatesAndCachesInSession_whenDelegateResolves() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/t/acme/dashboard");

		assertThat(resolver.resolveTenantId(request)).contains("acme");
		assertThat(request.getSession(false).getAttribute(SessionCachingTenantResolver.SESSION_ATTRIBUTE))
				.isEqualTo("acme");
	}

	@Test
	void resolveTenantId_fallsBackToCachedSessionValue_whenDelegateCannotResolve() {
		MockHttpSession session = new MockHttpSession();

		MockHttpServletRequest initialRequest = new MockHttpServletRequest("GET", "/t/acme/dashboard");
		initialRequest.setSession(session);
		assertThat(resolver.resolveTenantId(initialRequest)).contains("acme");

		// Simulates a Vaadin UIDL/heartbeat request: same session, but the request path is
		// the servlet root and no longer carries the tenant path segment.
		MockHttpServletRequest uidlRequest = new MockHttpServletRequest("POST", "/");
		uidlRequest.setSession(session);

		assertThat(resolver.resolveTenantId(uidlRequest)).contains("acme");
	}

	@Test
	void resolveTenantId_returnsEmpty_whenDelegateFailsAndNoSessionExists() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_whenDelegateFailsAndSessionHasNoCachedValue() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
		request.getSession(true);

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void resolveTenantId_switchesCachedTenant_whenDelegateResolvesADifferentOne() {
		MockHttpSession session = new MockHttpSession();

		MockHttpServletRequest first = new MockHttpServletRequest("GET", "/t/acme/dashboard");
		first.setSession(session);
		assertThat(resolver.resolveTenantId(first)).contains("acme");

		MockHttpServletRequest second = new MockHttpServletRequest("GET", "/t/umbrella/dashboard");
		second.setSession(session);
		assertThat(resolver.resolveTenantId(second)).contains("umbrella");

		MockHttpServletRequest uidlRequest = new MockHttpServletRequest("POST", "/");
		uidlRequest.setSession(session);
		assertThat(resolver.resolveTenantId(uidlRequest)).contains("umbrella");
	}

	@Test
	void getOrder_delegatesToWrappedResolver() {
		assertThat(resolver.getOrder()).isEqualTo(new PathPrefixTenantResolver().getOrder());
	}

	@Test
	void getDelegate_returnsWrappedResolver() {
		PathPrefixTenantResolver delegate = new PathPrefixTenantResolver();
		assertThat(new SessionCachingTenantResolver(delegate).getDelegate()).isSameAs(delegate);
	}

	@Test
	void constructor_rejectsNullDelegate() {
		assertThatThrownBy(() -> new SessionCachingTenantResolver(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void cacheTenant_seedsSession_soLaterUnresolvableRequestsFallBack() {
		MockHttpSession session = new MockHttpSession();

		SessionCachingTenantResolver.cacheTenant(session, "acme");

		assertThat(session.getAttribute(SessionCachingTenantResolver.SESSION_ATTRIBUTE))
				.isEqualTo("acme");

		// A Vaadin UIDL/heartbeat request that never carried the tenant now resolves from the cache.
		MockHttpServletRequest uidlRequest = new MockHttpServletRequest("POST", "/");
		uidlRequest.setSession(session);
		assertThat(resolver.resolveTenantId(uidlRequest)).contains("acme");
	}

	@Test
	void cacheTenant_rejectsNullSession() {
		assertThatThrownBy(() -> SessionCachingTenantResolver.cacheTenant(null, "acme"))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void cacheTenant_rejectsBlankTenantId() {
		MockHttpSession session = new MockHttpSession();
		assertThatThrownBy(() -> SessionCachingTenantResolver.cacheTenant(session, "  "))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SessionCachingTenantResolver.cacheTenant(session, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void clearCachedTenant_removesCachedValue() {
		MockHttpSession session = new MockHttpSession();
		SessionCachingTenantResolver.cacheTenant(session, "acme");

		SessionCachingTenantResolver.clearCachedTenant(session);

		assertThat(session.getAttribute(SessionCachingTenantResolver.SESSION_ATTRIBUTE)).isNull();

		MockHttpServletRequest uidlRequest = new MockHttpServletRequest("POST", "/");
		uidlRequest.setSession(session);
		assertThat(resolver.resolveTenantId(uidlRequest)).isEmpty();
	}

	@Test
	void clearCachedTenant_rejectsNullSession() {
		assertThatThrownBy(() -> SessionCachingTenantResolver.clearCachedTenant(null))
				.isInstanceOf(NullPointerException.class);
	}
}
