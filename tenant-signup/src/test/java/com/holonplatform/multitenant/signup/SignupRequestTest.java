package com.holonplatform.multitenant.signup;

import com.holonplatform.multitenant.TenantPlan;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SignupRequest}.
 */
class SignupRequestTest {

	@Test
	void builder_buildsWithAllFields() {
		SignupRequest request = SignupRequest.builder("acme", "owner@acme.com", "secret")
				.companyName("Acme Inc.")
				.plan(TenantPlan.STARTER)
				.attribute("locale", "en-US")
				.build();

		assertThat(request.tenantId()).isEqualTo("acme");
		assertThat(request.companyName()).isEqualTo("Acme Inc.");
		assertThat(request.ownerEmail()).isEqualTo("owner@acme.com");
		assertThat(request.rawPassword()).isEqualTo("secret");
		assertThat(request.plan()).isEqualTo(TenantPlan.STARTER);
		assertThat(request.attributes()).containsEntry("locale", "en-US");
	}

	@Test
	void builder_defaultsPlanToFree_whenNotSet() {
		SignupRequest request = SignupRequest.builder("acme", "owner@acme.com", "secret").build();
		assertThat(request.plan()).isEqualTo(TenantPlan.FREE);
	}

	@Test
	void builder_attribute_coercesNullValueToEmptyString() {
		// Reproduces a blank optional form field (e.g. logo URL left empty) whose
		// getValue() returns null — must not throw, unlike a raw Map.copyOf(...).
		SignupRequest request = SignupRequest.builder("acme", "owner@acme.com", "secret")
				.attribute("logoUrl", null)
				.build();

		assertThat(request.attributes()).containsEntry("logoUrl", "");
	}

	@Test
	void builder_attribute_rejectsNullKey() {
		assertThatThrownBy(() -> SignupRequest.builder("acme", "owner@acme.com", "secret").attribute(null, "x"))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void canonicalConstructor_coercesNullAttributeValuesToEmptyString() {
		Map<String, String> attributes = new HashMap<>();
		attributes.put("font", null);

		SignupRequest request = new SignupRequest(
				"acme", "Acme Inc.", "owner@acme.com", "secret", TenantPlan.FREE, attributes);

		assertThat(request.attributes()).containsEntry("font", "");
	}

	@Test
	void canonicalConstructor_rejectsNullAttributeKey() {
		Map<String, String> attributes = new HashMap<>();
		attributes.put(null, "x");

		assertThatThrownBy(() -> new SignupRequest(
				"acme", "Acme Inc.", "owner@acme.com", "secret", TenantPlan.FREE, attributes))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void canonicalConstructor_treatsNullAttributesMapAsEmpty() {
		SignupRequest request = new SignupRequest(
				"acme", "Acme Inc.", "owner@acme.com", "secret", TenantPlan.FREE, null);

		assertThat(request.attributes()).isEmpty();
	}

	@Test
	void canonicalConstructor_rejectsNullTenantId() {
		assertThatThrownBy(() -> new SignupRequest(null, "Acme Inc.", "owner@acme.com", "secret",
				TenantPlan.FREE, Map.of()))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void canonicalConstructor_rejectsNullOwnerEmail() {
		assertThatThrownBy(() -> new SignupRequest("acme", "Acme Inc.", null, "secret",
				TenantPlan.FREE, Map.of()))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void canonicalConstructor_rejectsNullRawPassword() {
		assertThatThrownBy(() -> new SignupRequest("acme", "Acme Inc.", "owner@acme.com", null,
				TenantPlan.FREE, Map.of()))
				.isInstanceOf(NullPointerException.class);
	}
}
