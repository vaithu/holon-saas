package com.holonplatform.multitenant.security;

import com.holonplatform.multitenant.TenantFeature;

/**
 * Thrown by the AOP aspect when a method annotated with
 * {@link com.holonplatform.multitenant.security.aop.CheckTenantFeature}
 * is invoked by a tenant that does not have the required feature enabled.
 */
public class TenantFeatureNotEnabledException extends TenantSecurityException {

	private static final long serialVersionUID = 1L;

	private final TenantFeature feature;

	public TenantFeatureNotEnabledException(TenantFeature feature) {
		super("Feature '" + feature.key() + "' is not enabled for the current tenant");
		this.feature = feature;
	}

	/** The feature that was required but not available. */
	public TenantFeature getFeature() { return feature; }
}

