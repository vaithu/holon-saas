package com.holonplatform.multitenant.security;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantIdExtractor}.
 */
class TenantIdExtractorTest {

    private static TenantDetails tenant(String id) {
        return TenantDetails.builder(id).plan(TenantPlan.FREE).build();
    }

    // ── fromTenantAwareUserDetails ────────────────────────────────────────────

    @Test
    @DisplayName("fromTenantAwareUserDetails extracts tenantId when principal implements TenantAwareUserDetails")
    void fromTenantAwareUserDetails_extractsTenantId() {
        TenantAwareUserDetails ud = mock(TenantAwareUserDetails.class);
        when(ud.getTenantId()).thenReturn("acme");
        when(ud.getTenantDetails()).thenReturn(tenant("acme"));

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(ud);

        assertThat(TenantIdExtractor.fromTenantAwareUserDetails().extract(auth))
                .contains("acme");
    }

    @Test
    @DisplayName("fromTenantAwareUserDetails returns empty when principal is a plain String")
    void fromTenantAwareUserDetails_returnsEmpty_whenPrincipalIsPlainString() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("plain-string-principal");

        assertThat(TenantIdExtractor.fromTenantAwareUserDetails().extract(auth))
                .isEmpty();
    }

    @Test
    @DisplayName("fromTenantAwareUserDetails returns empty when principal is null")
    void fromTenantAwareUserDetails_returnsEmpty_whenPrincipalIsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(null);

        assertThat(TenantIdExtractor.fromTenantAwareUserDetails().extract(auth))
                .isEmpty();
    }

    @Test
    @DisplayName("fromTenantAwareUserDetails returns empty when principal is a non-tenant object")
    void fromTenantAwareUserDetails_returnsEmpty_whenPrincipalIsAnonymousObject() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(new Object());

        assertThat(TenantIdExtractor.fromTenantAwareUserDetails().extract(auth))
                .isEmpty();
    }

    // ── fromCredentials ───────────────────────────────────────────────────────

    @Test
    @DisplayName("fromCredentials extracts a non-blank String credential as tenant ID")
    void fromCredentials_extractsStringCredential() {
        Authentication auth = mock(Authentication.class);
        when(auth.getCredentials()).thenReturn("my-tenant");

        assertThat(TenantIdExtractor.fromCredentials().extract(auth))
                .contains("my-tenant");
    }

    @Test
    @DisplayName("fromCredentials returns empty when credential is blank whitespace")
    void fromCredentials_returnsEmpty_whenCredentialIsBlank() {
        Authentication auth = mock(Authentication.class);
        when(auth.getCredentials()).thenReturn("   ");

        assertThat(TenantIdExtractor.fromCredentials().extract(auth))
                .isEmpty();
    }

    @Test
    @DisplayName("fromCredentials returns empty when credential is an empty string")
    void fromCredentials_returnsEmpty_whenCredentialIsEmptyString() {
        Authentication auth = mock(Authentication.class);
        when(auth.getCredentials()).thenReturn("");

        assertThat(TenantIdExtractor.fromCredentials().extract(auth))
                .isEmpty();
    }

    @Test
    @DisplayName("fromCredentials returns empty when credential is not a String")
    void fromCredentials_returnsEmpty_whenCredentialIsNotString() {
        Authentication auth = mock(Authentication.class);
        when(auth.getCredentials()).thenReturn(12345); // Integer, not String

        assertThat(TenantIdExtractor.fromCredentials().extract(auth))
                .isEmpty();
    }

    @Test
    @DisplayName("fromCredentials returns empty when credential is null")
    void fromCredentials_returnsEmpty_whenCredentialIsNull() {
        Authentication auth = mock(Authentication.class);
        when(auth.getCredentials()).thenReturn(null);

        assertThat(TenantIdExtractor.fromCredentials().extract(auth))
                .isEmpty();
    }

    // ── orElse ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("orElse uses fallback extractor when primary returns empty")
    void orElse_usesFallback_whenPrimaryReturnsEmpty() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-aware");        // primary → empty
        when(auth.getCredentials()).thenReturn("fallback-tenant"); // fallback → present

        TenantIdExtractor chain = TenantIdExtractor.fromTenantAwareUserDetails()
                .orElse(TenantIdExtractor.fromCredentials());

        assertThat(chain.extract(auth)).contains("fallback-tenant");
    }

    @Test
    @DisplayName("orElse does NOT use fallback when primary already returns a value")
    void orElse_doesNotUseFallback_whenPrimarySucceeds() {
        TenantAwareUserDetails ud = mock(TenantAwareUserDetails.class);
        when(ud.getTenantId()).thenReturn("primary-tenant");
        when(ud.getTenantDetails()).thenReturn(tenant("primary-tenant"));

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(ud);
        when(auth.getCredentials()).thenReturn("fallback-tenant"); // should be ignored

        TenantIdExtractor chain = TenantIdExtractor.fromTenantAwareUserDetails()
                .orElse(TenantIdExtractor.fromCredentials());

        assertThat(chain.extract(auth)).contains("primary-tenant");
    }

    @Test
    @DisplayName("orElse chain of three extractors: uses the first one that returns a value")
    void orElse_chainOfThree_usesFirstMatch() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-aware"); // primary → empty
        when(auth.getCredentials()).thenReturn("   ");      // second → empty (blank)

        // Third extractor always returns a fixed value
        TenantIdExtractor always = a -> Optional.of("third-extractor-tenant");

        TenantIdExtractor chain = TenantIdExtractor.fromTenantAwareUserDetails()
                .orElse(TenantIdExtractor.fromCredentials())
                .orElse(always);

        assertThat(chain.extract(auth)).contains("third-extractor-tenant");
    }

    @Test
    @DisplayName("orElse returns empty when both primary and fallback return empty")
    void orElse_returnsEmpty_whenBothReturnEmpty() {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("not-aware");
        when(auth.getCredentials()).thenReturn("   "); // blank → empty

        TenantIdExtractor chain = TenantIdExtractor.fromTenantAwareUserDetails()
                .orElse(TenantIdExtractor.fromCredentials());

        assertThat(chain.extract(auth)).isEmpty();
    }

    // ── Functional interface (lambda) ─────────────────────────────────────────

    @Test
    @DisplayName("TenantIdExtractor can be used as a lambda (functional interface)")
    void lambda_extractor_works() {
        Authentication auth = mock(Authentication.class);
        TenantIdExtractor extractor = a -> Optional.of("lambda-tenant");

        assertThat(extractor.extract(auth)).contains("lambda-tenant");
    }

    @Test
    @DisplayName("Lambda extractor composed with orElse works correctly")
    void lambda_orElse_composition() {
        Authentication auth = mock(Authentication.class);
        TenantIdExtractor primary  = a -> Optional.empty();
        TenantIdExtractor fallback = a -> Optional.of("composed-tenant");

        assertThat(primary.orElse(fallback).extract(auth)).contains("composed-tenant");
    }
}

