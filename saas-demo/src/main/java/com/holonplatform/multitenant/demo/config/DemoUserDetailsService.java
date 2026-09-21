package com.holonplatform.multitenant.demo.config;

import java.util.Objects;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantDetailsLoader;
import com.holonplatform.multitenant.demo.account.PlatformAccountStore;
import com.holonplatform.multitenant.security.TenantUserDetails;

/**
 * Resolves a login to a {@link TenantUserDetails}, which is what ties Spring Security to the
 * tenant framework.
 *
 * <h2>The "workspace/email" username</h2>
 * <p>The same email address may hold an account in several workspaces, so the email alone does
 * not identify an account. The login view therefore submits a composite username of the form
 * {@code workspace/email}, which this service splits apart.
 *
 * <p>Crucially, the {@link UserDetails} returned is built with the <em>bare email</em> as its
 * username, not the composite string. Two framework mechanisms depend on that:
 * <ul>
 *   <li>{@code TenantIdExtractor.fromTenantAwareUserDetails()} reads the tenant id from the
 *       principal, which is how {@code AuthenticatedPrincipalTenantContextFilter} binds the
 *       {@code TenantContext} on every subsequent request; and</li>
 *   <li>the permission layer resolves the current user's roles via
 *       {@code Authentication.getName()}, which it expects to be the tenant user's email —
 *       if the composite string leaked through, every permission check would silently fail to
 *       find a matching tenant user and deny access.</li>
 * </ul>
 */
@Service
public class DemoUserDetailsService implements UserDetailsService {

    private final PlatformAccountStore accountStore;
    private final TenantDetailsLoader  tenantDetailsLoader;

    public DemoUserDetailsService(PlatformAccountStore accountStore, TenantDetailsLoader tenantDetailsLoader) {
        this.accountStore        = Objects.requireNonNull(accountStore, "accountStore must not be null");
        this.tenantDetailsLoader = Objects.requireNonNull(tenantDetailsLoader, "tenantDetailsLoader must not be null");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.indexOf('/') < 0) {
            throw new UsernameNotFoundException("Expected a 'workspace/email' username");
        }
        int separator = username.indexOf('/');
        String tenantId = username.substring(0, separator).trim();
        String email    = username.substring(separator + 1).trim();

        if (tenantId.isEmpty() || email.isEmpty()) {
            throw new UsernameNotFoundException("Both workspace and email are required");
        }

        if (PlatformAdmin.WORKSPACE.equals(tenantId)) {
            return loadPlatformOperator(email);
        }

        TenantDetails tenant;
        try {
            tenant = tenantDetailsLoader.load(tenantId);
        } catch (RuntimeException e) {
            // Deliberately does not distinguish "unknown workspace" from "wrong password",
            // so the login form cannot be used to enumerate existing workspaces.
            throw new UsernameNotFoundException("Unknown workspace or account");
        }
        if (tenant == null) {
            throw new UsernameNotFoundException("Unknown workspace or account");
        }

        String passwordHash = accountStore.findPasswordHash(tenantId, email)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown workspace or account"));

        return TenantUserDetails.builder(email, tenant)
                .password(passwordHash)
                .role("USER")
                // A suspended workspace yields a disabled account rather than a "not found",
                // so the login view can say plainly that the workspace is suspended (F14)
                // instead of the misleading "bad credentials". The trade-off is accepted
                // knowingly: it confirms the workspace exists. That is not a leak worth
                // guarding here, because the workspace id already travels in invitation URLs
                // and is typed into this very form by every member who signs in.
                .enabled(tenant.isActive())
                .build();
    }

    /**
     * Builds the operator principal.
     *
     * <p>Returns a plain {@link User}, <em>not</em> a {@link TenantUserDetails}: carrying no
     * tenant is precisely what leaves the {@code TenantContext} unbound and routes this
     * account's queries to the control plane. See {@link PlatformAdmin} for the reasoning.
     */
    private UserDetails loadPlatformOperator(String email) {
        String passwordHash = accountStore.findPasswordHash(PlatformAdmin.WORKSPACE, email)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown workspace or account"));
        return User.withUsername(email)
                .password(passwordHash)
                .roles(PlatformAdmin.ROLE)
                .build();
    }
}
