package com.holonplatform.multitenant.security;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantUserDetails}.
 */
class TenantUserDetailsTest {

	private static TenantDetails tenant(String id) {
		return TenantDetails.builder(id).plan(TenantPlan.PROFESSIONAL).build();
	}

	@Test
	void builder_createsWithDefaults() {
		TenantUserDetails ud = TenantUserDetails.builder("jane", tenant("acme")).build();

		assertThat(ud.getUsername()).isEqualTo("jane");
		assertThat(ud.getTenantId()).isEqualTo("acme");
		assertThat(ud.isEnabled()).isTrue();
		assertThat(ud.isAccountNonExpired()).isTrue();
		assertThat(ud.isAccountNonLocked()).isTrue();
		assertThat(ud.isCredentialsNonExpired()).isTrue();
		assertThat(ud.getAuthorities()).isEmpty();
	}

	@Test
	void builder_withRolesPrefixedCorrectly() {
		TenantUserDetails ud = TenantUserDetails.builder("jane", tenant("acme"))
				.role("ADMIN")
				.role("ROLE_USER")
				.build();

		List<String> authorities = ud.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority).toList();

		assertThat(authorities).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER");
	}

	@Test
	void builder_withBareAuthority() {
		TenantUserDetails ud = TenantUserDetails.builder("jane", tenant("acme"))
				.authority("SCOPE_read")
				.build();

		assertThat(ud.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
				.containsExactly("SCOPE_read");
	}

	@Test
	void builder_disabledAccount() {
		TenantUserDetails ud = TenantUserDetails.builder("jane", tenant("acme"))
				.enabled(false)
				.accountNonLocked(false)
				.build();

		assertThat(ud.isEnabled()).isFalse();
		assertThat(ud.isAccountNonLocked()).isFalse();
	}

	@Test
	void getTenantDetails_returnsSameTenant() {
		TenantDetails td = tenant("acme");
		TenantUserDetails ud = TenantUserDetails.builder("jane", td).build();

		assertThat(ud.getTenantDetails()).isSameAs(td);
	}

	@Test
	void authorities_isUnmodifiable() {
		TenantUserDetails ud = TenantUserDetails.builder("jane", tenant("acme"))
				.role("USER").build();

		assertThatThrownBy(() -> ((java.util.List<?>) ud.getAuthorities()).clear())
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void builder_throwsOnNullUsername() {
		assertThatThrownBy(() -> TenantUserDetails.builder(null, tenant("acme")).build())
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void builder_throwsOnNullTenant() {
		assertThatThrownBy(() -> TenantUserDetails.builder("jane", null).build())
				.isInstanceOf(NullPointerException.class);
	}
}

