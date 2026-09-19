package com.holonplatform.multitenant.security.context;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.holonplatform.auth.AuthContext;
import com.holonplatform.core.Context;
import com.holonplatform.core.ContextScope;
import com.holonplatform.spring.security.SpringSecurity;

/**
 * Publishes a global Holon {@link AuthContext}, backed by Spring Security's
 * {@code SecurityContextHolder}, into the Holon {@link Context} classloader scope.
 *
 * <p>Once registered, {@link AuthContext#getCurrent()} and {@link AuthContext#require()} resolve on
 * any thread that carries an authenticated Spring Security context — including the request threads
 * that serve a Holon Vaadin Flow UI, whose navigator authorization guards call
 * {@code AuthContext.require()}. This removes the need for applications to hand-bind an
 * {@code AuthContext} at login just to satisfy those guards.
 *
 * <p>The {@link AuthContext} produced by {@link SpringSecurity#authContext()} is stateless: it reads
 * the current thread's {@code SecurityContextHolder} on each call, so a single shared instance
 * correctly reflects the authenticated principal of whichever thread asks for it.
 *
 * <p>Registration is non-destructive. It uses {@link ContextScope#putIfAbsent(String, Object)}, so
 * an application that already publishes its own {@code AuthContext} — in the classloader scope, or in
 * any higher-priority scope such as a Vaadin session scope — keeps precedence. This instance only
 * removes the resource on shutdown if it was the one that registered it.
 */
public final class SpringSecurityAuthContextRegistrar implements InitializingBean, DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(SpringSecurityAuthContextRegistrar.class);

	private boolean registeredByThisInstance = false;

	@Override
	public void afterPropertiesSet() {
		ContextScope scope = Context.get().classLoaderScope().orElse(null);
		if (scope == null) {
			log.warn("No Holon classloader ContextScope is available; the Spring Security-backed "
					+ "AuthContext will not be published. AuthContext.require() may fail for Holon UIs.");
			return;
		}
		AuthContext authContext = SpringSecurity.authContext();
		Optional<AuthContext> previous = scope.putIfAbsent(AuthContext.CONTEXT_KEY, authContext);
		if (previous.isEmpty()) {
			registeredByThisInstance = true;
			log.debug("Published a Spring Security-backed AuthContext into the Holon classloader Context scope.");
		} else {
			log.debug("An AuthContext is already bound in the Holon classloader Context scope; "
					+ "leaving the existing one in place.");
		}
	}

	@Override
	public void destroy() {
		if (!registeredByThisInstance) {
			return;
		}
		Context.get().classLoaderScope().ifPresent(scope -> {
			scope.remove(AuthContext.CONTEXT_KEY);
			log.debug("Removed the Spring Security-backed AuthContext from the Holon classloader Context scope.");
		});
		registeredByThisInstance = false;
	}
}
