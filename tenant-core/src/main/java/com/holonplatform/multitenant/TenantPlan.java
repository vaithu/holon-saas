package com.holonplatform.multitenant;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Subscription plan tiers.
 *
 * <p>Each plan defines a default set of {@link TenantFeature} keys that are
 * automatically available to tenants on that plan, without requiring an explicit
 * per-tenant feature grant.
 *
 * <p>Plans are cumulative — {@code ENTERPRISE} includes all features.
 */
public enum TenantPlan {

	/**
	 * Free tier — no paid features enabled by default.
	 */
	FREE(Set.of()),

	/**
	 * Starter tier — basic API access.
	 */
	STARTER(Set.of(
			TenantFeature.API_ACCESS.key()
	)),

	/**
	 * Professional tier — API + advanced reports + audit log.
	 */
	PROFESSIONAL(Set.of(
			TenantFeature.API_ACCESS.key(),
			TenantFeature.ADVANCED_REPORTS.key(),
			TenantFeature.AUDIT_LOG.key()
	)),

	/**
	 * Enterprise tier — all features enabled.
	 */
	ENTERPRISE(allFeatureKeys());

	private final Set<String> defaultFeatures;

	TenantPlan(Set<String> defaultFeatures) {
		this.defaultFeatures = Collections.unmodifiableSet(defaultFeatures);
	}

	/**
	 * Returns {@code true} if this plan includes the named feature by default.
	 *
	 * @param featureKey the feature key (typically {@link TenantFeature#key()})
	 */
	public boolean includesFeature(String featureKey) {
		return defaultFeatures.contains(featureKey);
	}

	/**
	 * Returns the immutable set of feature keys included in this plan by default.
	 */
	public Set<String> defaultFeatures() {
		return defaultFeatures;
	}

	// -------------------------------------------------------------------------

	private static Set<String> allFeatureKeys() {
		return Arrays.stream(TenantFeature.values())
				.map(TenantFeature::key)
				.collect(Collectors.toUnmodifiableSet());
	}
}

