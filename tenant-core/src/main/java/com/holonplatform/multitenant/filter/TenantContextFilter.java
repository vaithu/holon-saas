package com.holonplatform.multitenant.filter;

import com.holonplatform.multitenant.*;
import com.holonplatform.multitenant.event.TenantResolvedEvent;
import com.holonplatform.multitenant.exception.TenantNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Servlet filter that resolves, loads, and binds a {@link TenantDetails} to the
 * current request thread via {@link TenantContext}.
 *
 * <h2>Processing order</h2>
 * <ol>
 *   <li>If the request path matches any {@link #ignoredPaths}, the filter skips
 *       tenant resolution and calls the next filter in the chain.</li>
 *   <li>{@link TenantResolver} extracts a raw tenant ID from the request.</li>
 *   <li>If no ID is found and {@link #required} is {@code true}, the filter sends
 *       HTTP {@code 400 Bad Request} — unless {@code deferMissingTenantToChain} is set,
 *       in which case the request proceeds down the chain for a downstream layer
 *       (e.g. Spring Security) to handle.</li>
 *   <li>{@link TenantDetailsLoader} loads the full {@link TenantDetails}.</li>
 *   <li>If the tenant is not {@link TenantStatus#ACTIVE} and {@code required} is
 *       {@code true}, the filter sends HTTP {@code 403 Forbidden}.</li>
 *   <li>{@link TenantContext#set(TenantDetails)} binds the details to the thread.</li>
 *   <li>An optional {@link ApplicationEventPublisher} publishes a
 *       {@link TenantResolvedEvent}.</li>
 *   <li>The {@code finally} block always calls {@link TenantContext#clear()}.</li>
 * </ol>
 *
 * <p>Register this filter via the auto-configuration (see
 * {@link com.holonplatform.multitenant.config.TenantCoreAutoConfiguration}) or
 * manually as a Spring bean / {@code FilterRegistrationBean}.
 */
public class TenantContextFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(TenantContextFilter.class);

	private final TenantResolver      tenantResolver;
	private final TenantDetailsLoader tenantDetailsLoader;

	/** Whether a missing or inactive tenant causes the request to be rejected. */
	private boolean required = true;

	/**
	 * When {@link #required} is {@code true} and no tenant identifier can be resolved, whether the
	 * request proceeds down the filter chain (so a downstream layer such as Spring Security can
	 * redirect to login) instead of being rejected with HTTP {@code 400}. Default: {@code false}.
	 */
	private boolean deferMissingTenantToChain = false;

	/** Ant-style path patterns that bypass tenant resolution (actuator, public pages, etc.). */
	private List<String> ignoredPaths = List.of("/actuator/**", "/public/**", "/error");

	/** Optional Spring event publisher — may be null. */
	private ApplicationEventPublisher eventPublisher;

	/**
	 * Pre-compiled {@link PathPattern} objects derived from {@link #ignoredPaths}.
	 *
	 * <p>{@link PathPattern} (Spring 5+) is significantly faster than
	 * {@link org.springframework.util.AntPathMatcher} because patterns are compiled
	 * once at configuration time and evaluated against a pre-parsed
	 * {@link PathContainer} at match time — no regex compilation on each request.
	 * At 10k concurrent users this eliminates per-request pattern compilation overhead.
	 */
	private List<PathPattern> compiledPatterns = compilePatterns(ignoredPaths);

	private static final PathPatternParser PATTERN_PARSER = new PathPatternParser();

	/**
	 * Creates a new filter.
	 *
	 * @param tenantResolver      strategy for extracting the raw tenant ID; must not be null
	 * @param tenantDetailsLoader loader for the full tenant details; must not be null
	 */
	public TenantContextFilter(TenantResolver tenantResolver, TenantDetailsLoader tenantDetailsLoader) {
		this.tenantResolver      = Objects.requireNonNull(tenantResolver, "tenantResolver must not be null");
		this.tenantDetailsLoader = Objects.requireNonNull(tenantDetailsLoader, "tenantDetailsLoader must not be null");
	}

	// -------------------------------------------------------------------------
	// Configuration setters (called by auto-configuration)
	// -------------------------------------------------------------------------

	/**
	 * Controls whether an unresolvable tenant causes HTTP {@code 400/403}.
	 * Default: {@code true}.
	 */
	public void setRequired(boolean required) {
		this.required = required;
	}

	/**
	 * Controls what happens when {@link #setRequired(boolean) required} is {@code true} but no
	 * tenant identifier can be resolved from the request: {@code false} (the default) rejects with
	 * HTTP {@code 400}; {@code true} lets the request proceed down the filter chain so a downstream
	 * layer (typically Spring Security) can handle it — for example by redirecting an
	 * unauthenticated browser to the login page.
	 *
	 * <p>Applies only to the "no tenant identifier at all" case; a request carrying an unknown or
	 * inactive tenant identifier still yields {@code 404}/{@code 403}.
	 */
	public void setDeferMissingTenantToChain(boolean deferMissingTenantToChain) {
		this.deferMissingTenantToChain = deferMissingTenantToChain;
	}

	/**
	 * Sets Ant-style path patterns that bypass tenant resolution.
	 * Patterns are compiled immediately so that each request pays only a
	 * pre-compiled {@link PathPattern#matches} cost, not a regex-compilation cost.
	 *
	 * @param ignoredPaths list of Ant patterns; null or empty disables all exclusions
	 */
	public void setIgnoredPaths(List<String> ignoredPaths) {
		this.ignoredPaths      = (ignoredPaths != null) ? List.copyOf(ignoredPaths) : List.of();
		this.compiledPatterns  = compilePatterns(this.ignoredPaths);
	}

	/**
	 * Wires the optional Spring event publisher for {@link TenantResolvedEvent}.
	 *
	 * @param eventPublisher may be null — events are simply not published when absent
	 */
	public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
		this.eventPublisher = eventPublisher;
	}

	// -------------------------------------------------------------------------
	// Filter logic
	// -------------------------------------------------------------------------

	@Override
	protected void doFilterInternal(HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		try {
			if (isIgnoredPath(request)) {
				filterChain.doFilter(request, response);
				return;
			}

			Optional<String> tenantIdOpt = tenantResolver.resolveTenantId(request);

			if (tenantIdOpt.isEmpty()) {
				if (required && !deferMissingTenantToChain) {
					log.warn("Tenant resolution failed for request '{}' — no tenant identifier found",
							request.getRequestURI());
					response.sendError(HttpServletResponse.SC_BAD_REQUEST,
							"Tenant identifier could not be resolved from the request.");
					return;
				}
				if (required) {
					log.debug("No tenant identifier for request '{}' — deferring to the filter chain",
							request.getRequestURI());
				}
				// Tenant not required, or deferred to the chain — proceed without context
				filterChain.doFilter(request, response);
				return;
			}

			String tenantId = tenantIdOpt.get();
			TenantDetails tenant;

			try {
				tenant = tenantDetailsLoader.load(tenantId);
			} catch (TenantNotFoundException e) {
				log.warn("Tenant '{}' not found", tenantId);
				if (required) {
					response.sendError(HttpServletResponse.SC_NOT_FOUND,
							"Tenant not found: " + tenantId);
					return;
				}
				filterChain.doFilter(request, response);
				return;
			}

			if (!tenant.isActive()) {
				log.warn("Tenant '{}' access denied — status: {}", tenantId, tenant.status());
				if (required) {
					response.sendError(HttpServletResponse.SC_FORBIDDEN,
							"Tenant '" + tenantId + "' is not accessible (status: " + tenant.status() + ").");
					return;
				}
				filterChain.doFilter(request, response);
				return;
			}

			TenantContext.set(tenant);
			publishResolvedEvent(tenant, request);

			log.debug("Tenant context set: id='{}', plan={}", tenantId, tenant.plan());
			filterChain.doFilter(request, response);

		} finally {
			TenantContext.clear();
		}
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private boolean isIgnoredPath(HttpServletRequest request) {
		if (compiledPatterns.isEmpty()) {
			return false;
		}
		PathContainer path = PathContainer.parsePath(request.getRequestURI());
		for (PathPattern pattern : compiledPatterns) {
			if (pattern.matches(path)) {
				return true;
			}
		}
		return false;
	}

	private static List<PathPattern> compilePatterns(List<String> patterns) {
		return patterns.stream()
				.map(PATTERN_PARSER::parse)
				.toList();
	}

	private void publishResolvedEvent(TenantDetails tenant, HttpServletRequest request) {
		if (eventPublisher != null) {
			try {
				eventPublisher.publishEvent(new TenantResolvedEvent(this, tenant, request.getRequestURI()));
			} catch (Exception e) {
				log.debug("Failed to publish TenantResolvedEvent", e);
			}
		}
	}
}

