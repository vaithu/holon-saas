package com.holonplatform.multitenant.resolver;

import com.holonplatform.multitenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the tenant by reading a claim from a Bearer JWT in the
 * {@code Authorization} header.
 *
 * <p><strong>Important:</strong> This resolver does <em>not</em> validate the JWT
 * signature. Signature validation is the responsibility of Spring Security
 * (OAuth2 Resource Server / JWT filter), which runs before this class executes.
 * This resolver only extracts the claim value from the already-trusted payload.
 *
 * <p>Default claim name: {@code tenant_id}.
 *
 * <p>Only simple {@code String}-valued claims are supported. For complex claim
 * types, use the {@link #JwtClaimTenantResolver(String, JwtClaimExtractor)} constructor
 * to supply a custom extractor.
 *
 * <p>Usage:
 * <pre>{@code
 * // default claim name "tenant_id"
 * TenantResolver resolver = new JwtClaimTenantResolver();
 *
 * // custom claim name
 * TenantResolver resolver = new JwtClaimTenantResolver("org_id");
 * }</pre>
 */
public class JwtClaimTenantResolver implements TenantResolver {

	/** Default JWT claim name used when no custom name is supplied. */
	public static final String DEFAULT_CLAIM_NAME = "tenant_id";

	private static final String BEARER_PREFIX = "Bearer ";

	private final String            claimName;
	private final JwtClaimExtractor claimExtractor;

	/** Creates a resolver for the {@value #DEFAULT_CLAIM_NAME} claim using the built-in Base64 extractor. */
	public JwtClaimTenantResolver() {
		this(DEFAULT_CLAIM_NAME, JwtClaimTenantResolver::extractStringClaim);
	}

	/**
	 * Creates a resolver for the given claim name using the built-in Base64 extractor.
	 *
	 * @param claimName JWT payload claim name; must not be null
	 */
	public JwtClaimTenantResolver(String claimName) {
		this(claimName, JwtClaimTenantResolver::extractStringClaim);
	}

	/**
	 * Creates a resolver with a custom {@link JwtClaimExtractor}.
	 * Use this constructor to integrate with a full JWT library (e.g. Nimbus JOSE).
	 *
	 * @param claimName      JWT payload claim name; must not be null
	 * @param claimExtractor custom extractor; must not be null
	 */
	public JwtClaimTenantResolver(String claimName, JwtClaimExtractor claimExtractor) {
		this.claimName      = Objects.requireNonNull(claimName, "claimName must not be null");
		this.claimExtractor = Objects.requireNonNull(claimExtractor, "claimExtractor must not be null");
	}

	@Override
	public Optional<String> resolveTenantId(HttpServletRequest request) {
		String authHeader = request.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
			return Optional.empty();
		}
		String token = authHeader.substring(BEARER_PREFIX.length()).trim();
		return claimExtractor.extract(token, claimName);
	}

	@Override
	public int getOrder() {
		return 40;
	}

	// -------------------------------------------------------------------------
	// Default claim extractor — Base64 decode only, no signature verification
	// -------------------------------------------------------------------------

	/**
	 * Extracts a simple string claim from a JWT payload using Base64URL decoding.
	 * No signature verification is performed.
	 *
	 * @param token     the raw JWT (three dot-separated Base64URL-encoded parts)
	 * @param claimName the claim name to extract
	 * @return the claim value, or empty if not found or extraction fails
	 */
	static Optional<String> extractStringClaim(String token, String claimName) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		String[] parts = token.split("\\.");
		if (parts.length < 2) {
			return Optional.empty();
		}
		try {
			byte[] decodedBytes = Base64.getUrlDecoder().decode(addPadding(parts[1]));
			String payload      = new String(decodedBytes, StandardCharsets.UTF_8);

			// Simple JSON string-value extraction without a JSON parser dependency.
			// Works correctly for string-valued claims. Nested objects are not supported.
			String searchKey = "\"" + claimName + "\":\"";
			int start = payload.indexOf(searchKey);
			if (start == -1) {
				return Optional.empty();
			}
			start += searchKey.length();
			int end = payload.indexOf('"', start);
			if (end == -1) {
				return Optional.empty();
			}
			String value = payload.substring(start, end);
			return value.isBlank() ? Optional.empty() : Optional.of(value);
		} catch (Exception e) {
			// Any decoding or string-manipulation error → cannot resolve
			return Optional.empty();
		}
	}

	/** Adds Base64 padding characters if needed. */
	private static String addPadding(String base64) {
		int mod = base64.length() % 4;
		if (mod == 0) return base64;
		return base64 + "=".repeat(4 - mod);
	}

	// -------------------------------------------------------------------------
	// SPI
	// -------------------------------------------------------------------------

	/**
	 * Extension point for custom JWT payload parsing.
	 * Implement this to integrate a full JWT library such as Nimbus JOSE+JWT.
	 */
	@FunctionalInterface
	public interface JwtClaimExtractor {

		/**
		 * Extracts a string-valued claim from the supplied token.
		 *
		 * @param token     the raw JWT string; never null
		 * @param claimName the claim key to look up; never null
		 * @return the claim value, or empty if absent or unreadable
		 */
		Optional<String> extract(String token, String claimName);
	}
}

