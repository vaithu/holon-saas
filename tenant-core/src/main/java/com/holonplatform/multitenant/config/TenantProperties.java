package com.holonplatform.multitenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the multi-tenant core framework.
 *
 * <p>All properties are prefixed with {@code holon.tenant} in
 * {@code application.yml} / {@code application.properties}.
 *
 * <h2>Minimal YAML example</h2>
 * <pre>{@code
 * holon:
 *   tenant:
 *     strategy: SUBDOMAIN
 *     required: true
 *     ignored-paths:
 *       - /actuator/**
 *       - /public/**
 *       - /error
 * }</pre>
 *
 * <h2>Header-based resolution</h2>
 * <pre>{@code
 * holon:
 *   tenant:
 *     strategy: HEADER
 *     header-name: X-Org-ID
 * }</pre>
 *
 * <h2>Composite (try subdomain first, fall back to header)</h2>
 * <pre>{@code
 * holon:
 *   tenant:
 *     strategy: COMPOSITE
 * }</pre>
 */
@ConfigurationProperties(prefix = "holon.tenant")
public class TenantProperties {

	/** Resolution strategy. Default: {@link ResolutionStrategy#SUBDOMAIN}. */
	private ResolutionStrategy strategy = ResolutionStrategy.SUBDOMAIN;

	/** HTTP header name for {@link ResolutionStrategy#HEADER} strategy. Default: {@code X-Tenant-ID}. */
	private String headerName = "X-Tenant-ID";

	/** Path prefix for {@link ResolutionStrategy#PATH} strategy. Default: {@code /t/}. */
	private String pathPrefix = "/t/";

	/** JWT claim name for {@link ResolutionStrategy#JWT} strategy. Default: {@code tenant_id}. */
	private String jwtClaim = "tenant_id";

	/**
	 * Whether a missing or inactive tenant causes HTTP 400/403.
	 * Set to {@code false} in development or for mixed public/tenant routes. Default: {@code true}.
	 */
	private boolean required = true;

	/**
	 * Ant-style path patterns that bypass tenant resolution entirely.
	 * Default: {@code [/actuator/**, /public/**, /error]}.
	 */
	private List<String> ignoredPaths = new ArrayList<>(List.of("/actuator/**", "/public/**", "/error"));

	/** Order of the {@code FilterRegistrationBean}. Default: {@code -100} (very early). */
	private int filterOrder = -100;

	/**
	 * Whether the resolved tenant ID should also be cached in the {@code HttpSession} via
	 * {@link com.holonplatform.multitenant.resolver.SessionCachingTenantResolver}, so that
	 * requests which cannot carry the resolution signal (e.g. Vaadin Flow's internal
	 * UIDL/heartbeat requests, always sent to the servlet root regardless of the browser's
	 * visible route) still resolve correctly by falling back to the cached value.
	 *
	 * <p>{@code null} (the default) means "auto": enabled automatically for
	 * {@link ResolutionStrategy#PATH} — the only built-in strategy whose signal (a path
	 * segment) is not resent on such requests — and disabled for every other strategy.
	 * Set explicitly to {@code true} or {@code false} to override the automatic choice for
	 * any strategy.
	 */
	private Boolean sessionCache;

	// -------------------------------------------------------------------------
	// Getters / setters
	// -------------------------------------------------------------------------

	public ResolutionStrategy getStrategy() {
		return strategy;
	}

	public void setStrategy(ResolutionStrategy strategy) {
		this.strategy = strategy;
	}

	public String getHeaderName() {
		return headerName;
	}

	public void setHeaderName(String headerName) {
		this.headerName = headerName;
	}

	public String getPathPrefix() {
		return pathPrefix;
	}

	public void setPathPrefix(String pathPrefix) {
		this.pathPrefix = pathPrefix;
	}

	public String getJwtClaim() {
		return jwtClaim;
	}

	public void setJwtClaim(String jwtClaim) {
		this.jwtClaim = jwtClaim;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public List<String> getIgnoredPaths() {
		return ignoredPaths;
	}

	public void setIgnoredPaths(List<String> ignoredPaths) {
		this.ignoredPaths = ignoredPaths;
	}

	public int getFilterOrder() {
		return filterOrder;
	}

	public void setFilterOrder(int filterOrder) {
		this.filterOrder = filterOrder;
	}

	public Boolean getSessionCache() {
		return sessionCache;
	}

	public void setSessionCache(Boolean sessionCache) {
		this.sessionCache = sessionCache;
	}

	// -------------------------------------------------------------------------
	// Nested enum
	// -------------------------------------------------------------------------

	/**
	 * Describes how the tenant ID is extracted from an HTTP request.
	 */
	public enum ResolutionStrategy {

		/**
		 * Extract the first subdomain segment, e.g. {@code acme} from {@code acme.app.com}.
		 * Best for dedicated per-tenant subdomains with wildcard DNS.
		 */
		SUBDOMAIN,

		/**
		 * Read a request header (default: {@code X-Tenant-ID}).
		 * Best for API gateway / reverse-proxy architectures.
		 */
		HEADER,

		/**
		 * Read a path prefix segment (default: {@code /t/<tenantId>/}).
		 * Best for path-based routing without wildcard DNS.
		 */
		PATH,

		/**
		 * Extract a claim from the Bearer JWT in the {@code Authorization} header.
		 * Requires Spring Security / OAuth2 Resource Server to validate the token first.
		 */
		JWT,

		/**
		 * Try subdomain → header → path in priority order.
		 * Use when multiple entry points coexist (browser, API, mobile).
		 */
		COMPOSITE
	}
}

