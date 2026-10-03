package com.holonplatform.multitenant.demo.config;

import java.util.Objects;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;

/**
 * Performs a programmatic login from a Vaadin view.
 *
 * <p>Vaadin's stock login components submit only a username and a password, but this demo
 * authenticates against a {@code workspace/email} composite username, because the same email
 * may hold an account in several tenants — see {@link DemoUserDetailsService}. The login view
 * no longer asks for the workspace: it derives the candidates from the email and calls
 * {@link #verify} once per candidate, then {@link #establish}es the session for the one the
 * user is entering.
 *
 * <p>Splitting "check the credentials" from "start the session" is what makes that possible.
 * Probing several workspaces with a method that also wrote the session would otherwise leave
 * the {@link SecurityContextHolder} holding whichever attempt happened to run last.
 *
 * <p>Storing the resulting context in the {@link SecurityContextRepository} is what makes the
 * login survive beyond the current request; without it the user would appear authenticated for
 * exactly one round trip.
 */
@Component
public class DemoAuthenticator {

    private final AuthenticationManager    authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public DemoAuthenticator(AuthenticationManager authenticationManager,
                             SecurityContextRepository securityContextRepository) {
        this.authenticationManager     = Objects.requireNonNull(authenticationManager);
        this.securityContextRepository = Objects.requireNonNull(securityContextRepository);
    }

    /**
     * Authenticates and establishes the session.
     *
     * @throws org.springframework.security.core.AuthenticationException if the credentials or
     *         the workspace are not valid
     */
    public void login(String workspace, String email, String password) {
        establish(verify(workspace, email, password));
    }

    /**
     * Checks the credentials against one workspace <em>without</em> touching the session.
     *
     * @return the successful authentication, to be handed to {@link #establish}
     * @throws org.springframework.security.authentication.DisabledException if the workspace is
     *         suspended
     * @throws org.springframework.security.core.AuthenticationException if the credentials or
     *         the workspace are not valid
     */
    public Authentication verify(String workspace, String email, String password) {
        Authentication request = new UsernamePasswordAuthenticationToken(
                workspace.trim() + "/" + email.trim(), password);
        return authenticationManager.authenticate(request);
    }

    /**
     * Installs an authentication produced by {@link #verify} as the session's principal.
     *
     * @param authentication the successful authentication; must not be null
     */
    public void establish(Authentication authentication) {
        Objects.requireNonNull(authentication, "authentication must not be null");

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context,
                VaadinServletRequest.getCurrent().getHttpServletRequest(),
                VaadinServletResponse.getCurrent().getHttpServletResponse());
    }
}
