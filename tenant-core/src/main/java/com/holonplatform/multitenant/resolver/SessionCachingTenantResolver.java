package com.holonplatform.multitenant.resolver;

import com.holonplatform.multitenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.Objects;
import java.util.Optional;

/**
 * Decorates another {@link TenantResolver}, caching the last successfully resolved tenant
 * identifier in the {@link HttpSession} and falling back to that cached value whenever the
 * delegate cannot resolve a tenant from the current request.
 *
 * <h2>Why this is needed</h2>
 * <p>Client-rendered SPA frameworks (Vaadin Flow among them) only issue a request whose
 * {@code request.getRequestURI()} matches the browser's visible route on the very first,
 * full page load. Every later client/server round-trip within the same UI session (button
 * clicks, programmatic navigation, polling) is instead sent to a fixed internal endpoint
 * (e.g. Vaadin's UIDL/heartbeat channel), whose request path is the servlet root regardless
 * of what route the browser currently shows. A {@link TenantResolver} that depends on a
 * path segment present only in the visible route — most notably
 * {@link PathPrefixTenantResolver} — would therefore resolve the tenant correctly only on
 * that first request and silently fail (or, with {@code holon.tenant.required=true}, reject
 * the request) on every request after it.
 *
 * <p>This decorator closes that gap: whenever the delegate resolves a tenant ID, it is
 * cached on the current {@link HttpSession}; whenever the delegate cannot resolve one, the
 * last cached value for that session (if any) is returned instead.
 *
 * <p>Auto-enabled by {@code TenantCoreAutoConfiguration} for
 * {@link com.holonplatform.multitenant.config.TenantProperties.ResolutionStrategy#PATH}
 * (subdomain-derived and header/JWT-derived strategies are not affected by this problem,
 * since the browser resends the same host/header on every request regardless of the
 * visible route). Can be forced on or off for any strategy via
 * {@code holon.tenant.session-cache} — see
 * {@link com.holonplatform.multitenant.config.TenantProperties#setSessionCache(Boolean)}.
 */
public class SessionCachingTenantResolver implements TenantResolver {

	/** Session attribute name used to cache the last resolved tenant ID. */
	public static final String SESSION_ATTRIBUTE = SessionCachingTenantResolver.class.getName() + ".TENANT_ID";

	/**
	 * Seeds the session tenant cache with the supplied tenant identifier.
	 *
	 * <p>Plain-route applications (those that never carry the original resolution signal in
	 * every request — e.g. a Vaadin Flow UI with plain {@code @Route}s and no
	 * {@code /t/{id}/} path prefix) can call this once the authenticated tenant is known,
	 * typically right after login, so that later requests whose delegate resolver returns
	 * empty (Vaadin's UIDL/heartbeat POSTs to the servlet root among them) fall back to this
	 * cached value.
	 *
	 * <p>Prefer this over writing {@link #SESSION_ATTRIBUTE} directly: the attribute name is
	 * an implementation detail this helper encapsulates.
	 *
	 * @param session  the HTTP session to seed; must not be null
	 * @param tenantId the tenant identifier to cache; must not be null or blank
	 * @throws NullPointerException     if {@code session} is null
	 * @throws IllegalArgumentException if {@code tenantId} is null or blank
	 */
	public static void cacheTenant(HttpSession session, String tenantId) {
		Objects.requireNonNull(session, "session must not be null");
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("tenantId must not be null or blank");
		}
		session.setAttribute(SESSION_ATTRIBUTE, tenantId);
	}

	/**
	 * Removes any cached tenant identifier from the supplied session.
	 *
	 * <p>Call this on logout, so a subsequently reused session does not leak the previous
	 * user's tenant into requests that cannot otherwise resolve one.
	 *
	 * @param session the HTTP session to clear; must not be null
	 * @throws NullPointerException if {@code session} is null
	 */
	public static void clearCachedTenant(HttpSession session) {
		Objects.requireNonNull(session, "session must not be null");
		session.removeAttribute(SESSION_ATTRIBUTE);
	}

	private final TenantResolver delegate;

	/**
	 * Creates a new session-caching decorator.
	 *
	 * @param delegate the resolver to delegate to; must not be null
	 */
	public SessionCachingTenantResolver(TenantResolver delegate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
	}

	/** Returns the wrapped resolver. */
	public TenantResolver getDelegate() {
		return delegate;
	}

	@Override
	public Optional<String> resolveTenantId(HttpServletRequest request) {
		Optional<String> resolved = delegate.resolveTenantId(request);
		if (resolved.isPresent()) {
			request.getSession(true).setAttribute(SESSION_ATTRIBUTE, resolved.get());
			return resolved;
		}

		HttpSession session = request.getSession(false);
		if (session != null) {
			Object cached = session.getAttribute(SESSION_ATTRIBUTE);
			if (cached instanceof String cachedTenantId) {
				return Optional.of(cachedTenantId);
			}
		}
		return Optional.empty();
	}

	@Override
	public int getOrder() {
		return delegate.getOrder();
	}
}
