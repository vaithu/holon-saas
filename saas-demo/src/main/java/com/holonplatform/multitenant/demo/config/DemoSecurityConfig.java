package com.holonplatform.multitenant.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import com.holonplatform.multitenant.demo.ui.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

/**
 * Spring Security setup for the demo.
 *
 * <p>Only ordinary Spring Security and Vaadin configuration appears here — the tenant-specific
 * filters are contributed automatically by {@code tenant-core} and {@code tenant-security}:
 * <ul>
 *   <li>{@code TenantContextFilter} (order -100) resolves a tenant from the request. With
 *       {@code holon.tenant.strategy=HEADER} a browser never supplies one, so it is a no-op
 *       here, and {@code holon.tenant.required=false} lets the request continue.</li>
 *   <li>{@code AuthenticatedPrincipalTenantContextFilter} (order -60) runs after Spring
 *       Security has authenticated the request and binds the {@code TenantContext} from the
 *       {@link com.holonplatform.multitenant.security.TenantUserDetails} principal. This is
 *       what actually establishes the tenant for a logged-in user.</li>
 *   <li>{@code TenantIsolationFilter} (order -50) rejects any request whose resolved tenant
 *       differs from the authenticated user's tenant.</li>
 * </ul>
 *
 * <p>The {@link PasswordEncoder} bean below is also one of the two beans that
 * {@code SignupAutoConfiguration} is conditional on (the other being
 * {@link com.holonplatform.multitenant.signup.AccountCredentialsStore}); without it the
 * {@code SignupService} would silently not be created.
 */
@Configuration(proxyBeanMethods = false)
public class DemoSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // The probe endpoints exist to show the tenant filters working, so they must be
        // authenticated — an anonymous request has no user tenant for TenantIsolationFilter to
        // compare against, and the cross-tenant demonstration would prove nothing.
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/api/**", "/t/*/api/**").authenticated());

        // Vaadin's configurer wires the login view, CSRF, the request cache and the
        // request matchers that keep Vaadin's internal endpoints reachable. Per-view access
        // is then driven by the jakarta.annotation security annotations on each @Route.
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> configurer.loginView(LoginView.class));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Built explicitly rather than borrowed from Spring Boot's defaults, because the demo
     * authenticates programmatically from a Vaadin view (see {@code DemoAuthenticator}) and
     * therefore needs a directly injectable {@link AuthenticationManager}.
     */
    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** Persists the authenticated context into the HTTP session after a programmatic login. */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
}
