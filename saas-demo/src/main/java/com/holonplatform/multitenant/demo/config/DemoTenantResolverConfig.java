package com.holonplatform.multitenant.demo.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.resolver.CompositeTenantResolver;
import com.holonplatform.multitenant.resolver.HeaderTenantResolver;
import com.holonplatform.multitenant.resolver.JwtClaimTenantResolver;
import com.holonplatform.multitenant.resolver.PathPrefixTenantResolver;
import com.holonplatform.multitenant.resolver.SubdomainTenantResolver;

/**
 * F18 — all four request-based tenant resolution strategies active at once.
 *
 * <h2>Why a bean rather than a property</h2>
 * <p>{@code holon.tenant.strategy} picks exactly one strategy, and the framework's
 * {@code COMPOSITE} value chains only subdomain, header and path — leaving the JWT claim out.
 * Declaring the {@link TenantResolver} bean here replaces that choice wholesale, because the
 * auto-configuration's own resolver is {@code @ConditionalOnMissingBean(TenantResolver.class)}.
 * One running application can then demonstrate every strategy, which is the only way to show
 * what the ordering between them actually does.
 *
 * <h2>The order is the interesting part</h2>
 * <p>{@link CompositeTenantResolver} sorts its delegates by {@code getOrder()} and takes the
 * first non-empty answer: subdomain (10), header (20), path prefix (30), JWT claim (40). So a
 * request carrying both a header and a path prefix resolves from the header, and the path is
 * never consulted. That precedence is a security decision, not a detail — it decides which of
 * two conflicting signals a request is trusted on.
 *
 * <h2>What this does not change</h2>
 * <p>None of these resolvers can see a logged-in browser user: a Vaadin request carries no
 * header, no path prefix and no bearer token, and {@code localhost} is a single-label host that
 * {@link SubdomainTenantResolver} deliberately refuses. The tenant for the UI therefore still
 * comes from {@code AuthenticatedPrincipalTenantContextFilter}, which runs afterwards and only
 * binds when nothing else has. The resolvers matter for the API surface, where they are the
 * only signal there is.
 */
@Configuration(proxyBeanMethods = false)
public class DemoTenantResolverConfig {

    /**
     * The four strategies, in one place so the probe endpoint reports on exactly the resolvers
     * the composite is built from.
     *
     * <p>Deliberately a static factory rather than a {@code List<TenantResolver>} bean: exposing
     * the resolvers as beans would make them candidates for injection into the composite itself,
     * and the composite is a {@code TenantResolver} too.
     */
    public static List<TenantResolver> strategies() {
        return List.of(
                new SubdomainTenantResolver(),
                new HeaderTenantResolver(),
                new PathPrefixTenantResolver(),
                new JwtClaimTenantResolver());
    }

    @Bean
    @Primary
    public TenantResolver tenantResolver() {
        return new CompositeTenantResolver(strategies());
    }
}
