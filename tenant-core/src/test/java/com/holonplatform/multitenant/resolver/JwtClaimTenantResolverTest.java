package com.holonplatform.multitenant.resolver;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link JwtClaimTenantResolver}.
 *
 * <p>Built-in Base64 extraction is tested without any JWT library dependency.
 */
class JwtClaimTenantResolverTest {

	private final JwtClaimTenantResolver resolver = new JwtClaimTenantResolver();

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Builds a minimal unsigned JWT (header.payload.signature) with the given payload JSON.
	 * NOTE: This produces an *unsigned* token, valid only for testing the claim extractor.
	 */
	private static String buildToken(String payloadJson) {
		String header  = Base64.getUrlEncoder().withoutPadding()
				.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
		String payload = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
		return header + "." + payload + ".";
	}

	private static MockHttpServletRequest bearerRequest(String token) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer " + token);
		return request;
	}

	// -------------------------------------------------------------------------
	// Positive cases
	// -------------------------------------------------------------------------

	@Test
	void resolveTenantId_extractsDefaultClaim() {
		String token   = buildToken("{\"sub\":\"user1\",\"tenant_id\":\"acme\",\"exp\":9999999999}");
		assertThat(resolver.resolveTenantId(bearerRequest(token))).contains("acme");
	}

	@Test
	void resolveTenantId_customClaimName() {
		JwtClaimTenantResolver custom = new JwtClaimTenantResolver("org_id");
		String token = buildToken("{\"org_id\":\"umbrella\"}");
		assertThat(custom.resolveTenantId(bearerRequest(token))).contains("umbrella");
	}

	// -------------------------------------------------------------------------
	// Negative / edge cases
	// -------------------------------------------------------------------------

	@Test
	void resolveTenantId_returnsEmpty_whenNoAuthorizationHeader() {
		assertThat(resolver.resolveTenantId(new MockHttpServletRequest())).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_whenNotBearerScheme() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

		assertThat(resolver.resolveTenantId(request)).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_whenClaimAbsent() {
		String token = buildToken("{\"sub\":\"user1\",\"exp\":9999999999}");
		assertThat(resolver.resolveTenantId(bearerRequest(token))).isEmpty();
	}

	@Test
	void resolveTenantId_returnsEmpty_whenTokenIsMalformed() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer not.a.valid.jwt.token.at.all");

		// Should not throw, just return empty
		assertThatCode(() -> resolver.resolveTenantId(request)).doesNotThrowAnyException();
	}

	// -------------------------------------------------------------------------
	// Static extractor unit tests
	// -------------------------------------------------------------------------

	@Test
	void extractStringClaim_extractsValue() {
		String payload = "{\"tenant_id\":\"acme\",\"sub\":\"user1\"}";
		String encoded = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
		String token   = "header." + encoded + ".sig";

		assertThat(JwtClaimTenantResolver.extractStringClaim(token, "tenant_id")).contains("acme");
	}

	@Test
	void extractStringClaim_returnsEmpty_forNullToken() {
		assertThat(JwtClaimTenantResolver.extractStringClaim(null, "tenant_id")).isEmpty();
	}

	@Test
	void extractStringClaim_returnsEmpty_forSinglePartToken() {
		assertThat(JwtClaimTenantResolver.extractStringClaim("onlyonepart", "tenant_id")).isEmpty();
	}
}


