package com.holonplatform.multitenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantPlan} feature inclusion.
 */
class TenantPlanTest {

	@Test
	void free_includesNoFeaturesByDefault() {
		for (TenantFeature feature : TenantFeature.values()) {
			assertThat(TenantPlan.FREE.includesFeature(feature.key()))
					.as("FREE plan must not include " + feature)
					.isFalse();
		}
	}

	@Test
	void starter_includesApiAccess() {
		assertThat(TenantPlan.STARTER.includesFeature(TenantFeature.API_ACCESS.key())).isTrue();
	}

	@Test
	void professional_includesApiAccessReportsAudit() {
		assertThat(TenantPlan.PROFESSIONAL.includesFeature(TenantFeature.API_ACCESS.key())).isTrue();
		assertThat(TenantPlan.PROFESSIONAL.includesFeature(TenantFeature.ADVANCED_REPORTS.key())).isTrue();
		assertThat(TenantPlan.PROFESSIONAL.includesFeature(TenantFeature.AUDIT_LOG.key())).isTrue();
	}

	@Test
	void enterprise_includesAllFeatures() {
		for (TenantFeature feature : TenantFeature.values()) {
			assertThat(TenantPlan.ENTERPRISE.includesFeature(feature.key()))
					.as("ENTERPRISE plan must include " + feature)
					.isTrue();
		}
	}

	@Test
	void defaultFeatures_isImmutable() {
		assertThatThrownBy(() -> TenantPlan.STARTER.defaultFeatures().add("X"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void includesFeature_returnsFalseForUnknownKey() {
		assertThat(TenantPlan.ENTERPRISE.includesFeature("NONEXISTENT_FLAG")).isFalse();
	}
}

