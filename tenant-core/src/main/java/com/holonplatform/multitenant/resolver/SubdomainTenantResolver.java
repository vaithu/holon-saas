package com.holonplatform.multitenant.resolver;

import com.holonplatform.multitenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves the tenant by reading the first subdomain segment of the request
 * host name.
 *
 * <p>Example: a request to {@code acme.app.com} resolves to tenant {@code "acme"}.
 *
 * <h2>Edge cases</h2>
 * <ul>
 *   <li>Single-label hosts (e.g. {@code localhost}, IP addresses) always return
 *       {@link Optional#empty()} — no subdomain can be extracted.</li>
 *   <li>The host is normalised to lower-case before extraction.</li>
 *   <li>{@code www} and {@code app} are treated as reserved prefixes and skipped,
 *       returning {@link Optional#empty()}.</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>Register it as the primary {@link TenantResolver} bean or use it inside a
 * {@link CompositeTenantResolver}.
 */
public class SubdomainTenantResolver implements TenantResolver {

	/** Subdomain segments treated as non-tenant infrastructure prefixes. */
	private static final java.util.Set<String> RESERVED = java.util.Set.of("www", "app", "api", "mail", "smtp");

	/**
	 * Pre-compiled IPv4 pattern.
	 * Using {@code String.matches()} compiles a new {@link Pattern} on every call;
	 * a static pre-compiled field reduces this to a single allocation at class load.
	 */
	private static final Pattern IPV4_PATTERN = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");

	@Override
	public Optional<String> resolveTenantId(HttpServletRequest request) {
		String host = request.getServerName();
		if (host == null || host.isBlank()) {
			return Optional.empty();
		}
		host = host.toLowerCase();

		// Strip port if present (e.g. "acme.app.com:8080")
		int colonIndex = host.indexOf(':');
		if (colonIndex != -1) {
			host = host.substring(0, colonIndex);
		}

		// Reject IPv4 addresses (all dot-separated segments are digits)
		if (IPV4_PATTERN.matcher(host).matches()) {
			return Optional.empty();
		}

		// Reject IPv6 addresses
		if (host.contains(":") || host.startsWith("[")) {
			return Optional.empty();
		}

		String[] parts = host.split("\\.");
		if (parts.length < 2) {
			// Single label (localhost) — no subdomain
			return Optional.empty();
		}

		String candidate = parts[0];
		if (RESERVED.contains(candidate) || candidate.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(candidate);
	}

	@Override
	public int getOrder() {
		return 10;
	}
}

