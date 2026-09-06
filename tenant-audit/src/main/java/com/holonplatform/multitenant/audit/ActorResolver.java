package com.holonplatform.multitenant.audit;

/**
 * SPI for resolving the current acting principal for audit log entries.
 *
 * <p>Register a custom implementation as a Spring bean to integrate with your
 * authentication system (e.g. Spring Security). The default
 * {@code SystemActorResolver} always returns {@code "system"}.
 *
 * <h2>Spring Security example</h2>
 * <pre>{@code
 * @Component
 * public class SecurityActorResolver implements ActorResolver {
 *     @Override
 *     public String resolveActor() {
 *         Authentication auth = SecurityContextHolder.getContext().getAuthentication();
 *         if (auth != null && auth.isAuthenticated()) {
 *             return auth.getName();
 *         }
 *         return ActorResolver.SYSTEM;
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface ActorResolver {

    /** Sentinel value used when no authenticated user is present. */
    String SYSTEM = "system";

    /**
     * Returns the identifier of the current acting principal.
     * Must never return null — return {@link #SYSTEM} as fallback.
     *
     * @return actor identifier; never null
     */
    String resolveActor();
}

