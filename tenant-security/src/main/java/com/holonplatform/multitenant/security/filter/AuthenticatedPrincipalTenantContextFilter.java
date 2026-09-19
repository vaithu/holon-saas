package com.holonplatform.multitenant.security.filter;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.exception.TenantNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Servlet filter that binds a {@link TenantContext} derived from the <em>authenticated
 * principal</em>, for requests that could not otherwise resolve a tenant.
 *
 * <h2>What it does</h2>
 * <p>When {@link TenantContext} is <strong>not already set</strong> for the current request
 * (i.e. the very-early {@code tenant-core} {@code TenantContextFilter} could not resolve a
 * tenant from the request — typically a Vaadin Flow UIDL/heartbeat POST to the servlet root),
 * this filter asks a principal-based {@link TenantResolver}
 * ({@link com.holonplatform.multitenant.security.resolver.AuthenticatedPrincipalTenantResolver})
 * for the logged-in user's tenant, loads the full {@link TenantDetails} via
 * {@link TenantDetailsLoader}, and binds it to the thread for the remainder of the request.
 *
 * <p>It never overrides a tenant that some earlier layer already bound, and it never rejects a
 * request: it is a best-effort fallback binding, not an enforcement point. Enforcement remains
 * the job of {@link TenantIsolationFilter}, which must run <em>after</em> this filter so it can
 * validate the binding this filter produced.
 *
 * <h2>Filter ordering</h2>
 * <p>Must run <em>after</em> Spring Security's authentication filters (so the
 * {@code SecurityContextHolder} is populated) and <em>before</em> {@link TenantIsolationFilter}.
 * The default order is {@code -60}: after tenant-core's {@code -100} and Spring Security's
 * {@code -100}, and before the isolation filter's {@code -50}.
 */
public class AuthenticatedPrincipalTenantContextFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(AuthenticatedPrincipalTenantContextFilter.class);

	private final TenantResolver      principalTenantResolver;
	private final TenantDetailsLoader tenantDetailsLoader;

	/**
	 * Creates the filter.
	 *
	 * @param principalTenantResolver resolves the tenant ID from the authenticated principal;
	 *                                must not be null
	 * @param tenantDetailsLoader     loads the full tenant details for that ID; must not be null
	 */
	public AuthenticatedPrincipalTenantContextFilter(TenantResolver principalTenantResolver,
			TenantDetailsLoader tenantDetailsLoader) {
		this.principalTenantResolver = Objects.requireNonNull(principalTenantResolver,
				"principalTenantResolver must not be null");
		this.tenantDetailsLoader = Objects.requireNonNull(tenantDetailsLoader,
				"tenantDetailsLoader must not be null");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		// Never override a tenant an earlier layer already bound.
		if (TenantContext.isSet()) {
			filterChain.doFilter(request, response);
			return;
		}

		boolean bound = bindTenantFromPrincipal(request);
		try {
			filterChain.doFilter(request, response);
		} finally {
			if (bound) {
				TenantContext.clear();
			}
		}
	}

	/**
	 * Attempts to bind the tenant from the authenticated principal.
	 *
	 * @return {@code true} if this filter bound a tenant (and must therefore clear it), else
	 *         {@code false}
	 */
	private boolean bindTenantFromPrincipal(HttpServletRequest request) {
		Optional<String> tenantIdOpt = principalTenantResolver.resolveTenantId(request);
		if (tenantIdOpt.isEmpty()) {
			return false;
		}
		String tenantId = tenantIdOpt.get();
		TenantDetails tenant;
		try {
			tenant = tenantDetailsLoader.load(tenantId);
		} catch (TenantNotFoundException e) {
			log.warn("Authenticated user's tenant '{}' not found — leaving tenant context unbound", tenantId);
			return false;
		} catch (RuntimeException e) {
			log.warn("Failed to load authenticated user's tenant '{}' — leaving tenant context unbound",
					tenantId, e);
			return false;
		}
		if (!tenant.isActive()) {
			log.warn("Authenticated user's tenant '{}' is not active (status: {}) — leaving tenant context unbound",
					tenantId, tenant.status());
			return false;
		}
		TenantContext.set(tenant);
		log.debug("Tenant context bound from authenticated principal: id='{}'", tenantId);
		return true;
	}
}
