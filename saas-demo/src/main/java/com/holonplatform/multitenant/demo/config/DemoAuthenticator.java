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
 * <p>Vaadin's stock login components submit only a username and a password, but this demo needs
 * a third field — the workspace — because the same email may exist in several tenants. The
 * login view therefore collects all three and calls this component, which folds the workspace
 * and email into the composite {@code workspace/email} username that
 * {@link DemoUserDetailsService} understands.
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
        Authentication request = new UsernamePasswordAuthenticationToken(
                workspace.trim() + "/" + email.trim(), password);

        Authentication authentication = authenticationManager.authenticate(request);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        securityContextRepository.saveContext(context,
                VaadinServletRequest.getCurrent().getHttpServletRequest(),
                VaadinServletResponse.getCurrent().getHttpServletResponse());
    }
}
