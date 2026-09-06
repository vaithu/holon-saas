package com.holonplatform.multitenant.limits.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as subject to tenant-scoped rate limiting.
 *
 * <p>When Spring AOP is present, the {@code RateLimitAspect} intercepts
 * every call to an annotated method and invokes
 * {@link com.holonplatform.multitenant.limits.TenantRateLimiter#acquireOrThrow}
 * before the method body executes. The tenant ID is resolved from
 * {@link com.holonplatform.multitenant.TenantContext#peekTenantId()}.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * @RateLimit(key = TenantLimitKeys.API_REQUESTS)
 * public ResponseDto callApi(RequestDto request) { ... }
 * }</pre>
 *
 * <p>If no tenant is active the guard is skipped (system/admin calls are
 * never rate-limited by default).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * The rate-limit key that identifies which bucket to consume from.
     * Should be one of the constants in
     * {@link com.holonplatform.multitenant.limits.TenantLimitKeys}.
     */
    String key();
}

