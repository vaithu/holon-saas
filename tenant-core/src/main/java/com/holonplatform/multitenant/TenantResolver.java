package com.holonplatform.multitenant;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Strategy interface for extracting a tenant identifier from an incoming HTTP request.
 *
 * <p>Multiple implementations are provided out of the box:
 * <ul>
 *   <li>{@link com.holonplatform.multitenant.resolver.SubdomainTenantResolver}
 *       — reads the first subdomain segment (e.g. {@code acme} from {@code acme.app.com})</li>
 *   <li>{@link com.holonplatform.multitenant.resolver.HeaderTenantResolver}
 *       — reads an HTTP request header (default: {@code X-Tenant-ID})</li>
 *   <li>{@link com.holonplatform.multitenant.resolver.PathPrefixTenantResolver}
 *       — reads a path segment (e.g. {@code acme} from {@code /t/acme/dashboard})</li>
 *   <li>{@link com.holonplatform.multitenant.resolver.JwtClaimTenantResolver}
 *       — reads a claim from the Bearer JWT (e.g. {@code tenant_id})</li>
 *   <li>{@link com.holonplatform.multitenant.resolver.CompositeTenantResolver}
 *       — tries a chain of resolvers in priority order</li>
 *   <li>{@link com.holonplatform.multitenant.resolver.SessionCachingTenantResolver}
 *       — decorates another resolver, caching its result in the {@code HttpSession} as a
 *       fallback for requests that can't carry the original resolution signal (e.g.
 *       Vaadin Flow's internal UIDL/heartbeat requests)</li>
 * </ul>
 *
 * <p>Register a custom implementation as a Spring bean to override the auto-configured default.
 *
 * <p>This is a {@link FunctionalInterface} so lambdas are accepted wherever a
 * {@code TenantResolver} is required.
 */
@FunctionalInterface
public interface TenantResolver {

	/**
	 * Attempts to extract a tenant identifier from the supplied HTTP request.
	 *
	 * @param request the current HTTP request; never null
	 * @return the resolved tenant ID, or {@link Optional#empty()} if this
	 *         resolver cannot determine the tenant from the given request
	 */
	Optional<String> resolveTenantId(HttpServletRequest request);

	/**
	 * Priority order for {@link com.holonplatform.multitenant.resolver.CompositeTenantResolver}.
	 * Lower values are tried first. Defaults to {@code 0}.
	 */
	default int getOrder() {
		return 0;
	}
}

