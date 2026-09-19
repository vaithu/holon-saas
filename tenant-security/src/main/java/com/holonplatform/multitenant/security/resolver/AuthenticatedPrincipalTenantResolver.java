package com.holonplatform.multitenant.security.resolver;

import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.security.TenantIdExtractor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@link TenantResolver} that derives the tenant identifier from the <em>authenticated
 * user</em> rather than from anything in the HTTP request itself.
 *
 * <p>It reads the current Spring Security {@link Authentication} from the
 * {@link SecurityContextHolder} and delegates to a {@link TenantIdExtractor} (by default one
 * that reads {@link com.holonplatform.multitenant.security.TenantAwareUserDetails}). The
 * {@link HttpServletRequest} argument is deliberately ignored.
 *
 * <h2>Why this is useful</h2>
 * <p>Single-page UIs — Vaadin Flow in particular — only carry the original tenant-resolution
 * signal (a {@code /t/{id}/} path segment, a subdomain, a header, ...) on the very first full
 * page load. Every later interaction is an internal UIDL/heartbeat POST to the servlet root,
 * which carries none of those signals, so a request-based {@link TenantResolver} returns empty
 * for them. Once the user has logged in, however, the tenant is unambiguously known from the
 * authenticated principal — which is exactly what this resolver reads.
 *
 * <h2>Ordering constraint</h2>
 * <p>This resolver can only see the authenticated principal <strong>after</strong> Spring
 * Security has populated the {@link SecurityContextHolder} for the current request. It is
 * therefore intended to be driven by a filter that runs after the Spring Security filter
 * chain — see
 * {@link com.holonplatform.multitenant.security.filter.AuthenticatedPrincipalTenantContextFilter}
 * — not by the very-early {@code TenantContextFilter} (order {@code -100}) from
 * {@code tenant-core}, which generally runs before authentication.
 *
 * <p>Its {@link #getOrder()} defaults to {@value #DEFAULT_ORDER}, higher than every built-in
 * request-based resolver, so that when it is composed into a
 * {@link com.holonplatform.multitenant.resolver.CompositeTenantResolver} it is always tried
 * last, as a fallback.
 */
public class AuthenticatedPrincipalTenantResolver implements TenantResolver {

	/**
	 * Default {@link #getOrder()} value — higher than every built-in request-based resolver
	 * (subdomain {@code 10}, header {@code 20}, path {@code 30}, JWT {@code 40}), so this
	 * principal-based resolver is always tried last.
	 */
	public static final int DEFAULT_ORDER = 100;

	private final TenantIdExtractor tenantIdExtractor;
	private final int order;

	/**
	 * Creates a resolver with the {@linkplain #DEFAULT_ORDER default order}.
	 *
	 * @param tenantIdExtractor extracts the tenant ID from the authentication; must not be null
	 */
	public AuthenticatedPrincipalTenantResolver(TenantIdExtractor tenantIdExtractor) {
		this(tenantIdExtractor, DEFAULT_ORDER);
	}

	/**
	 * Creates a resolver with a custom order.
	 *
	 * @param tenantIdExtractor extracts the tenant ID from the authentication; must not be null
	 * @param order             the {@link #getOrder()} value
	 */
	public AuthenticatedPrincipalTenantResolver(TenantIdExtractor tenantIdExtractor, int order) {
		this.tenantIdExtractor = Objects.requireNonNull(tenantIdExtractor, "tenantIdExtractor must not be null");
		this.order = order;
	}

	@Override
	public Optional<String> resolveTenantId(HttpServletRequest request) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
			return Optional.empty();
		}
		return tenantIdExtractor.extract(auth).filter(id -> !id.isBlank());
	}

	@Override
	public int getOrder() {
		return order;
	}
}
