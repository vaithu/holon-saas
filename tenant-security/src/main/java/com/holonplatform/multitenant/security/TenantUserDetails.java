package com.holonplatform.multitenant.security;

import com.holonplatform.multitenant.TenantDetails;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Default immutable implementation of {@link TenantAwareUserDetails}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // In a UserDetailsService implementation:
 * UserDetails user = TenantUserDetails.builder("jane@acme.com", tenantDetails)
 *         .password("{bcrypt}$2a$...")
 *         .authority("ROLE_USER")
 *         .build();
 * }</pre>
 */
public final class TenantUserDetails implements TenantAwareUserDetails {

	private static final long serialVersionUID = 1L;

	private final String                                    username;
	private final String                                    password;
	private final TenantDetails                             tenantDetails;
	private final Collection<? extends GrantedAuthority>   authorities;
	private final boolean                                   enabled;
	private final boolean                                   accountNonExpired;
	private final boolean                                   accountNonLocked;
	private final boolean                                   credentialsNonExpired;

	private TenantUserDetails(Builder builder) {
		this.username              = builder.username;
		this.password              = builder.password;
		this.tenantDetails         = builder.tenantDetails;
		this.authorities           = Collections.unmodifiableList(List.copyOf(builder.authorities));
		this.enabled               = builder.enabled;
		this.accountNonExpired     = builder.accountNonExpired;
		this.accountNonLocked      = builder.accountNonLocked;
		this.credentialsNonExpired = builder.credentialsNonExpired;
	}

	@Override public String  getUsername()            { return username; }
	@Override public String  getPassword()            { return password; }
	@Override public String  getTenantId()            { return tenantDetails.tenantId(); }
	@Override public TenantDetails getTenantDetails() { return tenantDetails; }

	@Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
	@Override public boolean isEnabled()                  { return enabled; }
	@Override public boolean isAccountNonExpired()        { return accountNonExpired; }
	@Override public boolean isAccountNonLocked()         { return accountNonLocked; }
	@Override public boolean isCredentialsNonExpired()    { return credentialsNonExpired; }

	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	/**
	 * Starts a fluent builder.
	 *
	 * @param username      the username (email, login name, etc.); must not be null
	 * @param tenantDetails the tenant this user belongs to; must not be null
	 */
	public static Builder builder(String username, TenantDetails tenantDetails) {
		return new Builder(username, tenantDetails);
	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	public static final class Builder {

		private final String        username;
		private       String        password       = "";
		private final TenantDetails tenantDetails;
		private final List<GrantedAuthority> authorities = new java.util.ArrayList<>();
		private boolean enabled               = true;
		private boolean accountNonExpired     = true;
		private boolean accountNonLocked      = true;
		private boolean credentialsNonExpired = true;

		private Builder(String username, TenantDetails tenantDetails) {
			this.username      = Objects.requireNonNull(username, "username must not be null");
			this.tenantDetails = Objects.requireNonNull(tenantDetails, "tenantDetails must not be null");
		}

		public Builder password(String password)                        { this.password = password; return this; }
		public Builder enabled(boolean enabled)                         { this.enabled  = enabled;  return this; }
		public Builder accountNonExpired(boolean accountNonExpired)     { this.accountNonExpired = accountNonExpired; return this; }
		public Builder accountNonLocked(boolean accountNonLocked)       { this.accountNonLocked  = accountNonLocked; return this; }
		public Builder credentialsNonExpired(boolean credentialsNonExpired) { this.credentialsNonExpired = credentialsNonExpired; return this; }

		/** Adds a role authority (prefixes with {@code ROLE_} if not already prefixed). */
		public Builder role(String role) {
			String normalized = role.startsWith("ROLE_") ? role : "ROLE_" + role;
			this.authorities.add(() -> normalized);
			return this;
		}

		/** Adds a bare authority (no prefix added). */
		public Builder authority(String authority) {
			Objects.requireNonNull(authority, "authority must not be null");
			this.authorities.add(() -> authority);
			return this;
		}

		/** Adds multiple bare authorities. */
		public Builder authorities(String... auths) {
			for (String a : auths) authority(a);
			return this;
		}

		/** Adds multiple {@link GrantedAuthority} instances. */
		public Builder grantedAuthorities(Collection<? extends GrantedAuthority> auths) {
			if (auths != null) this.authorities.addAll(auths);
			return this;
		}

		public TenantUserDetails build() {
			return new TenantUserDetails(this);
		}
	}
}

