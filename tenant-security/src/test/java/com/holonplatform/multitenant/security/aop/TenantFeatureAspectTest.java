package com.holonplatform.multitenant.security.aop;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantFeature;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.security.TenantFeatureNotEnabledException;
import com.holonplatform.multitenant.security.TenantPlanRequiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantFeatureAspect} using direct proxy creation.
 * No Spring context required.
 */
class TenantFeatureAspectTest {

	@BeforeEach @AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	/** Wraps the target with the TenantFeatureAspect proxy. */
	private <T> T proxy(T target) {
		AspectJProxyFactory factory = new AspectJProxyFactory(target);
		factory.addAspect(new TenantFeatureAspect());
		return factory.getProxy();
	}

	// -------------------------------------------------------------------------
	// CheckTenantFeature
	// -------------------------------------------------------------------------

	@Test
	void checkFeature_allows_whenFeatureEnabled() {
		TenantContext.set(TenantDetails.builder("acme").plan(TenantPlan.ENTERPRISE).build());

		FeatureGatedService svc = proxy(new FeatureGatedService());
		assertThatCode(svc::doReport).doesNotThrowAnyException();
	}

	@Test
	void checkFeature_throws_whenFeatureNotEnabled() {
		TenantContext.set(TenantDetails.builder("acme").plan(TenantPlan.FREE).build());

		FeatureGatedService svc = proxy(new FeatureGatedService());
		assertThatThrownBy(svc::doReport)
				.isInstanceOf(TenantFeatureNotEnabledException.class)
				.hasMessageContaining("ADVANCED_REPORTS");
	}

	@Test
	void checkFeature_throws_whenNoTenantContext() {
		FeatureGatedService svc = proxy(new FeatureGatedService());
		assertThatThrownBy(svc::doReport)
				.isInstanceOf(TenantFeatureNotEnabledException.class);
	}

	// -------------------------------------------------------------------------
	// RequiresPlan
	// -------------------------------------------------------------------------

	@Test
	void requiresPlan_allows_whenPlanSufficient() {
		TenantContext.set(TenantDetails.builder("acme").plan(TenantPlan.ENTERPRISE).build());

		PlanGatedService svc = proxy(new PlanGatedService());
		assertThatCode(svc::doProfessionalWork).doesNotThrowAnyException();
	}

	@Test
	void requiresPlan_allows_whenExactlyMinimumPlan() {
		TenantContext.set(TenantDetails.builder("acme").plan(TenantPlan.PROFESSIONAL).build());

		PlanGatedService svc = proxy(new PlanGatedService());
		assertThatCode(svc::doProfessionalWork).doesNotThrowAnyException();
	}

	@Test
	void requiresPlan_throws_whenPlanTooLow() {
		TenantContext.set(TenantDetails.builder("acme").plan(TenantPlan.FREE).build());

		PlanGatedService svc = proxy(new PlanGatedService());
		assertThatThrownBy(svc::doProfessionalWork)
				.isInstanceOf(TenantPlanRequiredException.class)
				.satisfies(ex -> {
					TenantPlanRequiredException e = (TenantPlanRequiredException) ex;
					assertThat(e.getRequiredPlan()).isEqualTo(TenantPlan.PROFESSIONAL);
					assertThat(e.getActualPlan()).isEqualTo(TenantPlan.FREE);
				});
	}

	@Test
	void requiresPlan_throws_whenNoTenantContext() {
		PlanGatedService svc = proxy(new PlanGatedService());
		assertThatThrownBy(svc::doProfessionalWork)
				.isInstanceOf(TenantPlanRequiredException.class);
	}

	// -------------------------------------------------------------------------
	// Test services
	// -------------------------------------------------------------------------

	static class FeatureGatedService {
		@CheckTenantFeature(TenantFeature.ADVANCED_REPORTS)
		public String doReport() { return "report"; }
	}

	static class PlanGatedService {
		@RequiresPlan(minimum = TenantPlan.PROFESSIONAL)
		public String doProfessionalWork() { return "done"; }
	}
}

