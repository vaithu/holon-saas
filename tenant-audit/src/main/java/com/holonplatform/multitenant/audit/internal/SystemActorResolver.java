package com.holonplatform.multitenant.audit.internal;

import com.holonplatform.multitenant.audit.ActorResolver;

/**
 * {@link ActorResolver} that always returns {@link ActorResolver#SYSTEM} — no lookup of any
 * kind, useful for tests or applications that never track a real acting principal.
 *
 * <p><strong>Not the default anymore</strong> — {@code TenantAuditAutoConfiguration} registers
 * {@link com.holonplatform.multitenant.audit.SecurityContextActorResolver} by default instead
 * (a Spring-Security-aware resolver that already degrades to {@link ActorResolver#SYSTEM} when
 * nothing is authenticated, making this class largely redundant for real applications). Use
 * this class explicitly only if you want to guarantee no Spring Security lookup ever happens.
 */
public class SystemActorResolver implements ActorResolver {

    @Override
    public String resolveActor() {
        return SYSTEM;
    }
}

