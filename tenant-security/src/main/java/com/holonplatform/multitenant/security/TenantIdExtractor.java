package com.holonplatform.multitenant.security;

import org.springframework.security.core.Authentication;

import java.util.Optional;

/**
 * SPI for extracting a tenant identifier from a Spring Security
 * {@link Authentication} object.
 *
 * <p>The default implementation ({@link #fromTenantAwareUserDetails()}) reads
 * the tenant ID from principals that implement {@link TenantAwareUserDetails}.
 *
 * <p>Register a custom bean implementing this interface to support other
 * authentication schemes (JWT claims, LDAP attributes, etc.).
 *
 * <p>This is a {@link FunctionalInterface} — lambdas are accepted in tests and
 * auto-configuration.
 */
@FunctionalInterface
public interface TenantIdExtractor {

	/**
	 * Attempts to extract a tenant ID from the supplied authentication.
	 *
	 * @param authentication the current security authentication; never null
	 * @return the tenant ID, or {@link Optional#empty()} if this extractor
	 *         cannot determine the tenant from the given authentication
	 */
	Optional<String> extract(Authentication authentication);

	// -------------------------------------------------------------------------
	// Factory methods
	// -------------------------------------------------------------------------

	/**
	 * Creates an extractor that reads the tenant ID from principals implementing
	 * {@link TenantAwareUserDetails}.
	 *
	 * <p>This is the default extractor used by the auto-configuration.
	 */
	static TenantIdExtractor fromTenantAwareUserDetails() {
		return auth -> {
			if (auth.getPrincipal() instanceof TenantAwareUserDetails tud) {
				return Optional.of(tud.getTenantId());
			}
			return Optional.empty();
		};
	}

	/**
	 * Creates an extractor that reads the tenant ID from the authentication's
	 * credential, treating it as a String tenant ID.
	 *
	 * <p>Useful only in specific authentication schemes where the credential
	 * carries the tenant ID.
	 */
	static TenantIdExtractor fromCredentials() {
		return auth -> {
			if (auth.getCredentials() instanceof String s && !s.isBlank()) {
				return Optional.of(s);
			}
			return Optional.empty();
		};
	}

	/**
	 * Chains this extractor with a fallback. Returns the first non-empty result.
	 *
	 * @param fallback the extractor to try if this one returns empty
	 */
	default TenantIdExtractor orElse(TenantIdExtractor fallback) {
		return auth -> {
			Optional<String> result = this.extract(auth);
			return result.isPresent() ? result : fallback.extract(auth);
		};
	}
}

