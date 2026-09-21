package com.holonplatform.multitenant.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holonplatform.multitenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;

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

	/** Parses the decoded JWT payload. Shared and thread-safe; reads only, never serializes. */
	private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper();

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
	 * Extracts a simple string claim from a JWT payload using Base64URL decoding plus a real
	 * JSON parse. No signature verification is performed.
	 *
	 * <h2>Why a real JSON parser is required here</h2>
	 * <p>An earlier version searched the decoded payload for the literal text
	 * {@code "<claimName>":"} and took the first match. That is a genuine tenant-impersonation
	 * vector, not merely sloppy parsing: JSON string values may contain escaped quotes, so an
	 * attacker who controls <em>any</em> string claim appearing earlier in the payload (a
	 * display name, an email, a scope) can embed the escaped text
	 * {@code \",\"tenant_id\":\"victim} inside their own legitimately-signed claim. The naive
	 * scan finds that injected occurrence first and resolves the request as the victim's
	 * tenant — signature validation upstream does not help, because the token genuinely is
	 * validly signed. Parsing the payload properly makes the escaped text a literal part of
	 * the attacker's own claim value, exactly as intended.
	 *
	 * <p>The substring scan also silently failed on perfectly ordinary payloads formatted with
	 * a space after the colon ({@code "tenant_id": "acme"}), and on any non-string claim.
	 *
	 * @param token     the raw JWT (three dot-separated Base64URL-encoded parts)
	 * @param claimName the claim name to extract
	 * @return the claim value, or empty if not found, not a string, or extraction fails
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

			JsonNode payload = PAYLOAD_MAPPER.readTree(decodedBytes);
			JsonNode claim = payload.get(claimName);
			// Only scalar textual claims are supported; an object/array/number claim is not a
			// tenant identifier, and isTextual() keeps a JSON null from becoming the string "null".
			if (claim == null || !claim.isTextual()) {
				return Optional.empty();
			}
			String value = claim.textValue();
			return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
		} catch (Exception e) {
			// Any decoding or parsing error → cannot resolve
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

