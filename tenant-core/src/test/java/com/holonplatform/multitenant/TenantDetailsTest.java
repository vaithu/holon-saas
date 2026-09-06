package com.holonplatform.multitenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantDetails}.
 */
class TenantDetailsTest {

	// -------------------------------------------------------------------------
	// Construction
	// -------------------------------------------------------------------------

	@Test
	void builder_createsDetailsWithDefaults() {
		TenantDetails td = TenantDetails.builder("acme").build();

		assertThat(td.tenantId()).isEqualTo("acme");
		assertThat(td.plan()).isEqualTo(TenantPlan.FREE);
		assertThat(td.status()).isEqualTo(TenantStatus.ACTIVE);
		assertThat(td.features()).isEmpty();
		assertThat(td.attributes()).isEmpty();
		assertThat(td.name()).isNull();
		assertThat(td.locale()).isNull();
		assertThat(td.timezone()).isNull();
		assertThat(td.theme()).isNull();
	}

	@Test
	void builder_allFields() {
		TenantDetails td = TenantDetails.builder("acme")
				.name("Acme Corp")
				.plan(TenantPlan.PROFESSIONAL)
				.status(TenantStatus.ACTIVE)
				.locale("en-AU")
				.timezone("Australia/Sydney")
				.theme("dark")
				.feature(TenantFeature.ADVANCED_REPORTS)
				.attribute("billing_ref", "BILL-001")
				.build();

		assertThat(td.tenantId()).isEqualTo("acme");
		assertThat(td.name()).isEqualTo("Acme Corp");
		assertThat(td.plan()).isEqualTo(TenantPlan.PROFESSIONAL);
		assertThat(td.locale()).isEqualTo("en-AU");
		assertThat(td.timezone()).isEqualTo("Australia/Sydney");
		assertThat(td.theme()).isEqualTo("dark");
		assertThat(td.features()).contains(TenantFeature.ADVANCED_REPORTS.key());
		assertThat(td.attributes()).containsEntry("billing_ref", "BILL-001");
	}

	@Test
	void constructor_throwsOnNullTenantId() {
		assertThatThrownBy(() -> TenantDetails.builder(null).build())
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void constructor_throwsOnNullPlan() {
		assertThatThrownBy(() -> TenantDetails.builder("acme").plan(null).build())
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void constructor_throwsOnNullStatus() {
		assertThatThrownBy(() -> TenantDetails.builder("acme").status(null).build())
				.isInstanceOf(NullPointerException.class);
	}

	// -------------------------------------------------------------------------
	// Immutability
	// -------------------------------------------------------------------------

	@Test
	void features_collectionIsUnmodifiable() {
		TenantDetails td = TenantDetails.builder("acme")
				.feature("CUSTOM_FEATURE")
				.build();

		assertThatThrownBy(() -> td.features().add("X"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void attributes_collectionIsUnmodifiable() {
		TenantDetails td = TenantDetails.builder("acme")
				.attribute("k", "v")
				.build();

		assertThatThrownBy(() -> td.attributes().put("x", "y"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	// -------------------------------------------------------------------------
	// isActive / hasFeature
	// -------------------------------------------------------------------------

	@Test
	void isActive_trueForActiveStatus() {
		assertThat(TenantDetails.builder("a").status(TenantStatus.ACTIVE).build().isActive()).isTrue();
	}

	@Test
	void isActive_falseForSuspended() {
		assertThat(TenantDetails.builder("a").status(TenantStatus.SUSPENDED).build().isActive()).isFalse();
	}

	@Test
	void hasFeature_trueForExplicitFeature() {
		TenantDetails td = TenantDetails.builder("acme")
				.plan(TenantPlan.FREE)
				.feature(TenantFeature.CUSTOM_DOMAIN.key())
				.build();

		assertThat(td.hasFeature(TenantFeature.CUSTOM_DOMAIN)).isTrue();
	}

	@Test
	void hasFeature_trueViaPlan() {
		// PROFESSIONAL includes API_ACCESS and ADVANCED_REPORTS by default
		TenantDetails td = TenantDetails.builder("acme")
				.plan(TenantPlan.PROFESSIONAL)
				.build();

		assertThat(td.hasFeature(TenantFeature.ADVANCED_REPORTS)).isTrue();
		assertThat(td.hasFeature(TenantFeature.API_ACCESS)).isTrue();
	}

	@Test
	void hasFeature_falseWhenNotInPlanOrExplicitSet() {
		TenantDetails td = TenantDetails.builder("acme")
				.plan(TenantPlan.FREE)
				.build();

		assertThat(td.hasFeature(TenantFeature.SSO)).isFalse();
	}

	@Test
	void hasFeature_enterprisePlanIncludesAllFeatures() {
		TenantDetails td = TenantDetails.builder("acme")
				.plan(TenantPlan.ENTERPRISE)
				.build();

		for (TenantFeature feature : TenantFeature.values()) {
			assertThat(td.hasFeature(feature))
					.as("ENTERPRISE plan must include feature " + feature)
					.isTrue();
		}
	}

	// -------------------------------------------------------------------------
	// toBuilder
	// -------------------------------------------------------------------------

	@Test
	void toBuilder_producesEqualCopy() {
		TenantDetails original = TenantDetails.builder("acme")
				.name("Acme")
				.plan(TenantPlan.STARTER)
				.attribute("k", "v")
				.build();

		TenantDetails copy = original.toBuilder().build();

		assertThat(copy.tenantId()).isEqualTo(original.tenantId());
		assertThat(copy.name()).isEqualTo(original.name());
		assertThat(copy.plan()).isEqualTo(original.plan());
		assertThat(copy.attributes()).isEqualTo(original.attributes());
	}

	@Test
	void toBuilder_allowsSelectiveOverride() {
		TenantDetails original = TenantDetails.builder("acme")
				.plan(TenantPlan.FREE)
				.build();

		TenantDetails upgraded = original.toBuilder()
				.plan(TenantPlan.ENTERPRISE)
				.build();

		assertThat(upgraded.plan()).isEqualTo(TenantPlan.ENTERPRISE);
		assertThat(upgraded.tenantId()).isEqualTo("acme");
	}
}

