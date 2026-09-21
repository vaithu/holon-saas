package com.holonplatform.multitenant;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Central validation rule for tenant identifiers.
 *
 * <h2>Why this exists</h2>
 * <p>A tenant identifier is not just a lookup key: with the {@code SCHEMA_PER_TENANT} strategy
 * (see {@code tenant-data}) it is concatenated into a database schema name, which is in turn interpolated into DDL and
 * {@code SET search_path}/{@code SET SCHEMA} statements that are executed verbatim on every
 * connection checkout. Those statements cannot use JDBC bind parameters — a schema name is an
 * identifier, not a value — so the only defence against an identifier that "breaks out" of its
 * surrounding quotes is to reject it before it ever reaches SQL construction.
 *
 * <p>Tenant identifiers originate from untrusted input: {@code SubdomainTenantResolver} takes
 * whatever label the client put in the {@code Host} header, {@code PathPrefixTenantResolver}
 * takes a URL path segment, {@code HeaderTenantResolver} takes a raw header value. Requiring a
 * conservative character set here means no downstream layer has to reason about quoting at all.
 *
 * <h2>The rule</h2>
 * <p>3-64 characters, lowercase letters/digits/hyphen, starting and ending with a letter or
 * digit. This is deliberately stricter than what most databases accept as an identifier: it is
 * also a valid DNS label (so a tenant can always be addressed as a subdomain) and a URL-safe
 * path segment, and it contains no quote, whitespace, backslash, semicolon or comment character
 * of any SQL dialect.
 */
public final class TenantIdentifiers {

	/** The canonical tenant-identifier pattern — see this class's Javadoc. */
	public static final Pattern PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");

	/** Human-readable description of {@link #PATTERN}, for error messages. */
	public static final String RULE_DESCRIPTION =
			"3-64 characters, lowercase letters, digits or hyphens, starting and ending with a letter or digit";

	private TenantIdentifiers() {
	}

	/**
	 * Returns {@code true} if {@code tenantId} is a well-formed tenant identifier.
	 *
	 * @param tenantId the identifier to test; may be null (returns {@code false})
	 */
	public static boolean isValid(String tenantId) {
		return tenantId != null && PATTERN.matcher(tenantId).matches();
	}

	/**
	 * Validates {@code tenantId}, returning it unchanged when well-formed.
	 *
	 * @param tenantId the identifier to validate; must not be null
	 * @return {@code tenantId}, unchanged
	 * @throws NullPointerException     if {@code tenantId} is null
	 * @throws IllegalArgumentException if {@code tenantId} does not match {@link #PATTERN}
	 */
	public static String requireValid(String tenantId) {
		Objects.requireNonNull(tenantId, "tenantId must not be null");
		if (!PATTERN.matcher(tenantId).matches()) {
			throw new IllegalArgumentException(
					"Invalid tenant identifier '" + tenantId + "' — must be " + RULE_DESCRIPTION
							+ ". Tenant identifiers are interpolated into database schema names and "
							+ "cannot be parameterised, so anything outside this character set is rejected.");
		}
		return tenantId;
	}
}
