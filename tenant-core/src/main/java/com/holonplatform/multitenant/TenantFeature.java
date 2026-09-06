package com.holonplatform.multitenant;

/**
 * Well-known feature flags that can be enabled per tenant or included in a
 * {@link TenantPlan}.
 *
 * <p>Application-specific flags are supported as plain strings via
 * {@link TenantDetails#hasFeature(String)}. Use this enum for first-class
 * platform-level features.
 *
 * <p>Usage from view / service code:
 * <pre>{@code
 * if (TenantFeature.ADVANCED_REPORTS.isEnabled()) {
 *     add(reportsSection);
 * }
 * }</pre>
 */
public enum TenantFeature {

	/** Programmatic REST/API access on behalf of the tenant. */
	API_ACCESS,

	/** Advanced analytics and report builder. */
	ADVANCED_REPORTS,

	/** Full per-action audit trail stored per tenant. */
	AUDIT_LOG,

	/** Custom domain / subdomain support. */
	CUSTOM_DOMAIN,

	/** White-labelling: custom logo, colours, CSS. */
	WHITE_LABEL,

	/** Custom third-party integrations (webhooks, Zapier, etc.). */
	CUSTOM_INTEGRATIONS,

	/** Dedicated priority support channel. */
	PRIORITY_SUPPORT,

	/** Single Sign-On (SAML / OIDC) for the tenant. */
	SSO;

	/**
	 * Returns the string key used in {@link TenantDetails#features()} and
	 * {@link TenantDetails#hasFeature(String)}.
	 * The key is the enum constant's {@link #name()} value.
	 */
	public String key() {
		return name();
	}

	/**
	 * Convenience method: returns {@code true} if this feature is enabled for the
	 * tenant currently stored in {@link TenantContext}.
	 *
	 * <p>Returns {@code false} when no tenant is present in the current context.
	 */
	public boolean isEnabled() {
		return TenantContext.get()
				.map(t -> t.hasFeature(this))
				.orElse(false);
	}
}

