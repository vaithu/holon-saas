package com.holonplatform.multitenant.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Resolves the current actor from Spring Security's {@link SecurityContextHolder} — the
 * "current authenticated username, or {@code system} if none" lookup every Spring-Security-based
 * {@code holon-saas} application otherwise needs to hand-write identically, for two unrelated
 * interfaces:
 * <ul>
 *   <li>{@link ActorResolver}, feeding this module's own {@code @Audited} AOP trail</li>
 *   <li>{@link AuditorAware AuditorAware&lt;String&gt;}, feeding Spring Data JPA's standard
 *       {@code @CreatedBy}/{@code @LastModifiedBy} entity auditing (see
 *       {@code TenantAuditAutoConfiguration}'s Javadoc for how to enable it)</li>
 * </ul>
 * This class implements both on a single instance specifically because that duplication is a
 * real, empirically-observed risk — a hand-written application ended up with the identical
 * ~10-line lookup logic copy-pasted into two separate classes, one per interface, before this
 * shared implementation existed.
 *
 * <h2>Automatic registration</h2>
 * <p>{@code TenantAuditAutoConfiguration} registers this as the default {@link ActorResolver}
 * bean (named {@code securityContextActorResolver}), unless the application already declares
 * its own. Spring Security's core library ({@code spring-security-core}) is always on the
 * classpath for any {@code tenant-audit} consumer — a required, non-optional transitive
 * dependency via {@code tenant-security} — so this is safe to use unconditionally:
 * {@link SecurityContextHolder#getContext()} simply returns no authentication (falling back to
 * {@link ActorResolver#SYSTEM} / {@link Optional#empty()}) in applications that never populate
 * it (e.g. those without Spring Security's filter chain configured at all).
 *
 * <h2>Enabling JPA {@code @CreatedBy}/{@code @LastModifiedBy} auditing</h2>
 * <p>This bean already satisfies {@code AuditorAware<String>} — an application only needs to
 * add the (unavoidably per-application) {@code @EnableJpaAuditing} activation annotation,
 * referencing this bean by name, without writing its own {@code AuditorAware} implementation:
 * <pre>{@code
 * @Configuration
 * @EnableJpaAuditing(auditorAwareRef = "securityContextActorResolver")
 * public class JpaAuditingConfig {
 * }
 * }</pre>
 */
public class SecurityContextActorResolver implements ActorResolver, AuditorAware<String> {

	@Override
	public String resolveActor() {
		return currentUsername().orElse(ActorResolver.SYSTEM);
	}

	@Override
	public Optional<String> getCurrentAuditor() {
		return currentUsername();
	}

	private Optional<String> currentUsername() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
			return Optional.of(authentication.getName());
		}
		return Optional.empty();
	}
}
