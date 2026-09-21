package com.holonplatform.multitenant.demo.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.demo.config.DemoTenantResolverConfig;

import jakarta.servlet.http.HttpServletRequest;

/**
 * F18 — a tiny API surface whose whole purpose is to report how the request was resolved.
 *
 * <h2>Why an API and not a view</h2>
 * <p>Every resolution strategy reads something off the HTTP request: a host label, a header, a
 * path prefix, a bearer token. A Vaadin view cannot demonstrate them, because the browser sends
 * none of those things when it talks to Vaadin. An endpoint the page can {@code fetch} with
 * chosen headers and paths is the smallest honest way to show the strategies working — and it
 * goes through the real filter chain, so {@code TenantContextFilter} and
 * {@code TenantIsolationFilter} both get their say.
 *
 * <h2>Two mappings, one method</h2>
 * <p>{@code /t/&#123;tenant&#125;/api/whoami} exists so the path-prefix strategy has a path to
 * match. The controller never reads the tenant segment — {@code PathPrefixTenantResolver} does,
 * long before the request reaches Spring MVC. That is the point worth seeing: the tenant is
 * established by the filter chain, not by the handler.
 */
@RestController
public class TenantProbeController {

    @GetMapping({"/api/whoami", "/t/*/api/whoami"})
    public Map<String, Object> whoami(HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("boundTenant", TenantContext.currentTenantId().orElse(null));
        body.put("principal", currentPrincipal());
        body.put("requestUri", request.getRequestURI());

        // What each strategy makes of this same request, independently of which one the
        // composite happened to accept. Showing all four is what makes the ordering visible:
        // several can answer at once, and only the lowest order number is used.
        Map<String, String> verdicts = new LinkedHashMap<>();
        for (TenantResolver resolver : DemoTenantResolverConfig.strategies()) {
            verdicts.put(resolver.getClass().getSimpleName(),
                    resolver.resolveTenantId(request).orElse("-"));
        }
        body.put("resolvers", verdicts);

        return body;
    }

    private static String currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }
}
