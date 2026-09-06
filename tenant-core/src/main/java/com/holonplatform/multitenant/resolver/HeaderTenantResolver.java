package com.holonplatform.multitenant.resolver;

import com.holonplatform.multitenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the tenant from an HTTP request header.
 *
 * <p>Default header name: {@code X-Tenant-ID}.
 * The value is trimmed and lower-cased before being returned.
 *
 * <p>Usage:
 * <pre>{@code
 * // with default header name
 * TenantResolver resolver = new HeaderTenantResolver();
 *
 * // with custom header name
 * TenantResolver resolver = new HeaderTenantResolver("X-Organisation-ID");
 * }</pre>
 */
public class HeaderTenantResolver implements TenantResolver {

	/** Default header name used when no custom name is supplied. */
	public static final String DEFAULT_HEADER_NAME = "X-Tenant-ID";

	private final String headerName;

	/** Creates a resolver that reads the {@value #DEFAULT_HEADER_NAME} header. */
	public HeaderTenantResolver() {
		this(DEFAULT_HEADER_NAME);
	}

	/**
	 * Creates a resolver that reads the named header.
	 *
	 * @param headerName the HTTP header name; must not be null or blank
	 */
	public HeaderTenantResolver(String headerName) {
		Objects.requireNonNull(headerName, "headerName must not be null");
		if (headerName.isBlank()) {
			throw new IllegalArgumentException("headerName must not be blank");
		}
		this.headerName = headerName;
	}

	/** Returns the configured header name. */
	public String getHeaderName() {
		return headerName;
	}

	@Override
	public Optional<String> resolveTenantId(HttpServletRequest request) {
		String value = request.getHeader(headerName);
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(value.trim().toLowerCase());
	}

	@Override
	public int getOrder() {
		return 20;
	}
}

