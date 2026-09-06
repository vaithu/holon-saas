package com.holonplatform.multitenant.security.filter;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.security.TenantIdExtractor;
import com.holonplatform.multitenant.security.event.CrossTenantAccessAttemptEvent;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

/**
 * Servlet filter that validates cross-tenant isolation after Spring Security
 * has authenticated the user.
 *
 * <h2>What it does</h2>
 * <p>For every request where BOTH of the following are true:
 * <ul>
 *   <li>An authenticated {@link Authentication} exists in the Security context</li>
 *   <li>A tenant ID is present in {@link TenantContext}</li>
 * </ul>
 * the filter verifies that the user's tenant (extracted via {@link TenantIdExtractor})
 * matches the request's resolved tenant. If they differ, the request is rejected with
 * HTTP {@code 403 Forbidden} and a {@link CrossTenantAccessAttemptEvent} is published.
 *
 * <h2>Why this matters</h2>
 * <p>Without this filter, a user could authenticate as {@code acme} but craft a request
 * targeting {@code beta} (e.g. via a subdomain override in a header). This filter closes
 * that attack surface.
 *
 * <h2>Filter ordering</h2>
 * <p>Must run <em>after</em> Spring Security's authentication filters and <em>after</em>
 * {@link com.holonplatform.multitenant.filter.TenantContextFilter}. The default
 * order is {@code -50} (after tenant-core's default of {@code -100}).
 */
public class TenantIsolationFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(TenantIsolationFilter.class);

	private final TenantIdExtractor tenantIdExtractor;

	/** Controls whether mismatches cause a 403 (true) or just a warning log (false). */
	private boolean enforced = true;

	private ApplicationEventPublisher eventPublisher;

	/**
	 * Creates the filter.
	 *
	 * @param tenantIdExtractor extracts the authenticated user's tenant ID; must not be null
	 */
	public TenantIsolationFilter(TenantIdExtractor tenantIdExtractor) {
		this.tenantIdExtractor = Objects.requireNonNull(tenantIdExtractor, "tenantIdExtractor must not be null");
	}

	public void setEnforced(boolean enforced)                         { this.enforced = enforced; }
	public void setEventPublisher(ApplicationEventPublisher publisher) { this.eventPublisher = publisher; }

	// -------------------------------------------------------------------------
	// Filter logic
	// -------------------------------------------------------------------------

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth == null || !auth.isAuthenticated()) {
			// No authenticated user — nothing to check
			filterChain.doFilter(request, response);
			return;
		}

		Optional<String> userTenantOpt = tenantIdExtractor.extract(auth);
		Optional<String> ctxTenantOpt  = TenantContext.currentTenantId();

		if (userTenantOpt.isEmpty() || ctxTenantOpt.isEmpty()) {
			// Either the extractor doesn't handle this auth type, or no tenant in context
			filterChain.doFilter(request, response);
			return;
		}

		String userTenant    = userTenantOpt.get();
		String contextTenant = ctxTenantOpt.get();

		if (!userTenant.equalsIgnoreCase(contextTenant)) {
			logAndPublish(auth, userTenant, contextTenant, request);

			if (enforced) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN,
						"Cross-tenant access denied: your account does not belong to this tenant.");
				return;
			}
		}

		filterChain.doFilter(request, response);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void logAndPublish(Authentication auth, String userTenant, String contextTenant,
			HttpServletRequest request) {

		String username = auth.getName();
		log.warn("Cross-tenant access attempt: user '{}' (tenant '{}') tried to access tenant '{}' at '{}'",
				username, userTenant, contextTenant, request.getRequestURI());

		if (eventPublisher != null) {
			try {
				eventPublisher.publishEvent(new CrossTenantAccessAttemptEvent(
						this, userTenant, contextTenant, request.getRequestURI(), username));
			} catch (Exception e) {
				log.debug("Failed to publish CrossTenantAccessAttemptEvent", e);
			}
		}
	}
}

