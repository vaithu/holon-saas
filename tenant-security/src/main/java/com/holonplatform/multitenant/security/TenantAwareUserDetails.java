package com.holonplatform.multitenant.security;

import com.holonplatform.multitenant.TenantDetails;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Extension of Spring Security's {@link UserDetails} that carries the tenant
 * identity of the authenticated user.
 *
 * <p>Implementations are stored as the {@code principal} inside a Spring Security
 * {@link org.springframework.security.core.Authentication} and are also used by
 * {@link com.holonplatform.multitenant.security.filter.TenantIsolationFilter}
 * to verify that the user belongs to the tenant resolved for the current HTTP request.
 *
 * <p>The standard implementation is {@link TenantUserDetails}. Applications may
 * extend it or provide their own by annotating a bean with
 * {@link org.springframework.security.core.userdetails.UserDetailsService}.
 */
public interface TenantAwareUserDetails extends UserDetails {

	/**
	 * Returns the tenant identifier this user belongs to.
	 *
	 * @return the tenant ID; never null
	 */
	String getTenantId();

	/**
	 * Returns the full {@link TenantDetails} for this user's tenant.
	 *
	 * <p>This is the same object that would be stored in
	 * {@link com.holonplatform.multitenant.TenantContext} for the request.
	 *
	 * @return tenant details; never null
	 */
	TenantDetails getTenantDetails();
}

