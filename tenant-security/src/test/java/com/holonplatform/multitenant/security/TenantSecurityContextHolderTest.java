package com.holonplatform.multitenant.security;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantFeature;
import com.holonplatform.multitenant.TenantPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantSecurityContextHolder}.
 */
class TenantSecurityContextHolderTest {

	@BeforeEach
	void setUpSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	// -------------------------------------------------------------------------
	// getTenant
	// -------------------------------------------------------------------------

	@Test
	void getTenant_returnsFromTenantContext_first() {
		TenantDetails td = TenantDetails.builder("acme").build();
		TenantContext.set(td);

		assertThat(TenantSecurityContextHolder.getTenant()).contains(td);
	}

	@Test
	void getTenant_fallsBackToSecurityContext_whenNoTenantContext() {
		TenantDetails td = TenantDetails.builder("beta").plan(TenantPlan.PROFESSIONAL).build();
		TenantUserDetails ud = TenantUserDetails.builder("user@beta.com", td).build();

		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));

		assertThat(TenantSecurityContextHolder.getTenant()).contains(td);
	}

	@Test
	void getTenant_returnsEmpty_whenNoContextNorAuthentication() {
		assertThat(TenantSecurityContextHolder.getTenant()).isEmpty();
	}

	@Test
	void getTenant_returnsEmpty_whenPrincipalIsNotTenantAware() {
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("plainUsername", null));

		assertThat(TenantSecurityContextHolder.getTenant()).isEmpty();
	}

	// -------------------------------------------------------------------------
	// requireTenant
	// -------------------------------------------------------------------------

	@Test
	void requireTenant_throwsWhenNoContext() {
		assertThatThrownBy(TenantSecurityContextHolder::requireTenant)
				.isInstanceOf(TenantSecurityException.class);
	}

	@Test
	void requireTenant_returnsWhenContextSet() {
		TenantContext.set(TenantDetails.builder("acme").build());
		assertThat(TenantSecurityContextHolder.requireTenant().tenantId()).isEqualTo("acme");
	}

	// -------------------------------------------------------------------------
	// hasFeature / hasPlan
	// -------------------------------------------------------------------------

	@Test
	void hasFeature_trueWhenTenantHasFeature() {
		TenantContext.set(TenantDetails.builder("acme").plan(TenantPlan.ENTERPRISE).build());
		assertThat(TenantSecurityContextHolder.hasFeature(TenantFeature.SSO)).isTrue();
	}

	@Test
	void hasFeature_falseWhenNoContext() {
		assertThat(TenantSecurityContextHolder.hasFeature(TenantFeature.SSO)).isFalse();
	}

	@Test
	void hasPlan_trueWhenSufficientPlan() {
		TenantContext.set(TenantDetails.builder("acme").plan(TenantPlan.ENTERPRISE).build());
		assertThat(TenantSecurityContextHolder.hasPlan(TenantPlan.PROFESSIONAL)).isTrue();
	}

	@Test
	void hasPlan_falseWhenPlanTooLow() {
		TenantContext.set(TenantDetails.builder("acme").plan(TenantPlan.FREE).build());
		assertThat(TenantSecurityContextHolder.hasPlan(TenantPlan.STARTER)).isFalse();
	}

	@Test
	void hasPlan_falseWhenNoContext() {
		assertThat(TenantSecurityContextHolder.hasPlan(TenantPlan.FREE)).isFalse();
	}
}

