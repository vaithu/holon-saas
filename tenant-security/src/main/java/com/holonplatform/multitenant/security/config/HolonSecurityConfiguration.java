package com.holonplatform.multitenant.security.config;

import com.holonplatform.multitenant.security.aop.HolonPermissionAspect;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Auto-configuration for Holon Spring Security integration.
 *
 * <p>Enables fine-grained permission-based method access control using
 * the @HolonPermission annotation via AOP aspects.
 *
 * <h2>Beans created</h2>
 * <ul>
 *   <li><strong>{@link HolonPermissionAspect}</strong> — AOP aspect for
 *       {@link com.holonplatform.multitenant.security.aop.HolonPermission}
 *       annotation-based permission checking</li>
 * </ul>
 */
@AutoConfiguration
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@EnableAspectJAutoProxy
public class HolonSecurityConfiguration {

    /**
     * Register the HolonPermissionAspect for AOP-based permission checking.
     *
     * @return HolonPermissionAspect instance
     */
    @Bean
    @ConditionalOnMissingBean(HolonPermissionAspect.class)
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnProperty(
            name = "holon.security.permission-aspect-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public HolonPermissionAspect holonPermissionAspect() {
        return new HolonPermissionAspect();
    }
}

