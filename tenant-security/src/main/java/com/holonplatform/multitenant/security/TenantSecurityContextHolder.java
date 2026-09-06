package com.holonplatform.multitenant.security;

import com.holonplatform.auth.AuthContext;
import com.holonplatform.auth.Authentication;
import com.holonplatform.auth.Permission;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for reading the current tenant from either the
 * {@link TenantContext} thread-local or the Spring Security context,
 * plus permission and role checking using Holon AuthContext.
 *
 * <h2>Resolution order</h2>
 * <ol>
 *   <li>{@link TenantContext#get()} — populated by the HTTP request filter chain.</li>
 *   <li>Spring Security's {@link org.springframework.security.core.Authentication#getPrincipal()} if it is a
 *       {@link TenantAwareUserDetails} — useful in async/background tasks where
 *       the thread-local has been cleared.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * TenantDetails tenant = TenantSecurityContextHolder.requireTenant();
 * boolean hasReports   = TenantSecurityContextHolder.hasFeature(TenantFeature.ADVANCED_REPORTS);
 * boolean canViewAudit = TenantSecurityContextHolder.hasPermission("AUDIT_VIEW");
 * }</pre>
 */
public final class TenantSecurityContextHolder {

	private TenantSecurityContextHolder() {}

	// ─────────────────────────────────────────────────────────────────────────────
	// Tenant Access
	// ─────────────────────────────────────────────────────────────────────────────

	/**
	 * Returns the current {@link TenantDetails}: first from {@link TenantContext},
	 * then from Spring Security's authentication principal.
	 *
	 * @return the current tenant, or empty if neither source has one
	 */
	public static Optional<TenantDetails> getTenant() {
		Optional<TenantDetails> direct = TenantContext.get();
		if (direct.isPresent()) {
			return direct;
		}
		org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof TenantAwareUserDetails tud) {
			return Optional.of(tud.getTenantDetails());
		}
		return Optional.empty();
	}

	/**
	 * Returns the current tenant ID string.
	 *
	 * @return tenant ID, or empty if no authentication or tenant context is present
	 */
	public static Optional<String> getCurrentTenantId() {
		return getTenant().map(TenantDetails::tenantId);
	}

	/**
	 * Returns the current {@link TenantDetails}.
	 *
	 * @throws TenantSecurityException if no tenant can be resolved
	 */
	public static TenantDetails requireTenant() {
		return getTenant().orElseThrow(() ->
				new TenantSecurityException("No TenantDetails available in TenantContext or SecurityContext"));
	}

	/**
	 * Returns {@code true} if the current tenant has the given feature enabled
	 * (checking both plan defaults and per-tenant grants).
	 *
	 * <p>Returns {@code false} when no tenant is currently available.
	 */
	public static boolean hasFeature(com.holonplatform.multitenant.TenantFeature feature) {
		return getTenant().map(t -> t.hasFeature(feature)).orElse(false);
	}

	/**
	 * Returns {@code true} if the current tenant's plan is at least the given
	 * minimum plan.
	 *
	 * <p>Returns {@code false} when no tenant is currently available.
	 */
	public static boolean hasPlan(com.holonplatform.multitenant.TenantPlan minimumPlan) {
		return getTenant()
				.map(t -> t.plan().ordinal() >= minimumPlan.ordinal())
				.orElse(false);
	}

	// ─────────────────────────────────────────────────────────────────────────────
	// Permission & Role Access (Holon Integration)
	// ─────────────────────────────────────────────────────────────────────────────

	/**
	 * Returns the current Holon Authentication (from AuthContext).
	 *
	 * @return Holon Authentication, or empty if not available
	 */
	public static Optional<Authentication> getHolonAuthentication() {
		try {
			return AuthContext.getCurrent().flatMap(AuthContext::getAuthentication);
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	/**
	 * Returns the authenticated principal name (user ID / username).
	 *
	 * @return principal name, or empty if not authenticated
	 */
	public static Optional<String> getPrincipalName() {
		Optional<Authentication> auth = getHolonAuthentication();
		if (auth.isPresent()) {
			return auth.map(Authentication::getName);
		}

		org.springframework.security.core.Authentication springAuth =
				SecurityContextHolder.getContext().getAuthentication();
		if (springAuth != null && springAuth.isAuthenticated()) {
			return Optional.ofNullable((String) springAuth.getPrincipal());
		}

		return Optional.empty();
	}

	/**
	 * Returns all permissions for the current authenticated user.
	 *
	 * @return set of permission names, never null
	 */
	public static Set<String> getPermissions() {
		Optional<Authentication> auth = getHolonAuthentication();
		if (auth.isPresent()) {
			return auth.get()
					.getPermissions()
					.stream()
					.map(Permission::getPermission)
					.filter(Optional::isPresent)
					.map(Optional::get)
					.collect(Collectors.toSet());
		}
		return Set.of();
	}

	/**
	 * Returns all roles for the current authenticated user.
	 *
	 * <p>Holon's Authentication has no separate "roles" concept (only
	 * {@link Permission}), so roles are always resolved from Spring Security's
	 * granted authorities (stripping the {@code ROLE_} prefix).
	 *
	 * @return set of role names, never null
	 */
	public static Set<String> getRoles() {
		org.springframework.security.core.Authentication springAuth =
				SecurityContextHolder.getContext().getAuthentication();
		if (springAuth != null && springAuth.isAuthenticated()) {
			return springAuth.getAuthorities()
					.stream()
					.map(a -> a.getAuthority())
					.filter(java.util.Objects::nonNull)
					.map(a -> a.replace("ROLE_", ""))
					.collect(Collectors.toSet());
		}

		return Set.of();
	}

	/**
	 * Checks if the current authenticated user has the specified permission.
	 *
	 * @param permission the permission to check
	 * @return {@code true} if user has the permission, {@code false} otherwise
	 */
	public static boolean hasPermission(String permission) {
		return getPermissions().stream()
				.anyMatch(p -> p.equalsIgnoreCase(permission));
	}

	/**
	 * Checks if the current authenticated user has any of the specified permissions.
	 *
	 * @param permissions the permissions to check (OR logic)
	 * @return {@code true} if user has any of the permissions
	 */
	public static boolean hasAnyPermission(String... permissions) {
		Set<String> userPermissions = getPermissions();
		return java.util.Arrays.stream(permissions)
				.anyMatch(p -> userPermissions.stream()
						.anyMatch(up -> up.equalsIgnoreCase(p)));
	}

	/**
	 * Checks if the current authenticated user has all of the specified permissions.
	 *
	 * @param permissions the permissions to check (AND logic)
	 * @return {@code true} if user has all permissions
	 */
	public static boolean hasAllPermissions(String... permissions) {
		Set<String> userPermissions = getPermissions();
		return java.util.Arrays.stream(permissions)
				.allMatch(p -> userPermissions.stream()
						.anyMatch(up -> up.equalsIgnoreCase(p)));
	}

	/**
	 * Checks if the current authenticated user has the specified role.
	 *
	 * @param role the role to check
	 * @return {@code true} if user has the role
	 */
	public static boolean hasRole(String role) {
		return getRoles().stream()
				.anyMatch(r -> r.equalsIgnoreCase(role));
	}

	/**
	 * Throws an exception if the current user does NOT have the required permission.
	 *
	 * @param permission the required permission
	 * @throws TenantSecurityException if permission is not granted
	 */
	public static void requirePermission(String permission) {
		if (!hasPermission(permission)) {
			throw new TenantSecurityException(
					"Access denied: required permission '" + permission + "'");
		}
	}

	/**
	 * Throws an exception if the current user does NOT have any of the required permissions.
	 *
	 * @param permissions the required permissions (OR logic)
	 * @throws TenantSecurityException if none of the permissions are granted
	 */
	public static void requireAnyPermission(String... permissions) {
		if (!hasAnyPermission(permissions)) {
			throw new TenantSecurityException(
					"Access denied: required permission(s) [" + String.join(", ", permissions) + "]");
		}
	}

	/**
	 * Throws an exception if the current user does NOT have all of the required permissions.
	 *
	 * @param permissions the required permissions (AND logic)
	 * @throws TenantSecurityException if not all permissions are granted
	 */
	public static void requireAllPermissions(String... permissions) {
		if (!hasAllPermissions(permissions)) {
			throw new TenantSecurityException(
					"Access denied: all of the following permissions required [" + String.join(", ", permissions) + "]");
		}
	}
}

