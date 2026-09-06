package com.holonplatform.multitenant.resolver;

import com.holonplatform.multitenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the tenant from a path prefix segment in the request URI.
 *
 * <p>Default prefix: {@code /t/}.
 * Given a request to {@code /t/acme/dashboard}, this resolver returns {@code "acme"}.
 *
 * <p>Usage:
 * <pre>{@code
 * // with default prefix "/t/"
 * TenantResolver resolver = new PathPrefixTenantResolver();
 *
 * // with custom prefix
 * TenantResolver resolver = new PathPrefixTenantResolver("/org/");
 * }</pre>
 */
public class PathPrefixTenantResolver implements TenantResolver {

	/** Default path prefix used when no custom prefix is supplied. */
	public static final String DEFAULT_PREFIX = "/t/";

	private final String prefix;

	/** Creates a resolver that reads from the {@value #DEFAULT_PREFIX} path prefix. */
	public PathPrefixTenantResolver() {
		this(DEFAULT_PREFIX);
	}

	/**
	 * Creates a resolver for a custom path prefix.
	 *
	 * @param prefix path segment prefix that precedes the tenant identifier;
	 *               must start with {@code /} and end with {@code /}
	 */
	public PathPrefixTenantResolver(String prefix) {
		Objects.requireNonNull(prefix, "prefix must not be null");
		if (!prefix.startsWith("/") || !prefix.endsWith("/")) {
			throw new IllegalArgumentException(
					"Path prefix must start and end with '/'. Got: " + prefix);
		}
		this.prefix = prefix;
	}

	/** Returns the configured path prefix. */
	public String getPrefix() {
		return prefix;
	}

	@Override
	public Optional<String> resolveTenantId(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (path == null || !path.startsWith(prefix)) {
			return Optional.empty();
		}

		// Extract the segment immediately after the prefix
		String remainder = path.substring(prefix.length());
		if (remainder.isBlank()) {
			return Optional.empty();
		}

		// The tenant ID is everything up to the next '/'
		int slashIndex = remainder.indexOf('/');
		String tenantId = (slashIndex == -1) ? remainder : remainder.substring(0, slashIndex);
		return tenantId.isBlank() ? Optional.empty() : Optional.of(tenantId.toLowerCase());
	}

	@Override
	public int getOrder() {
		return 30;
	}
}

