package com.holonplatform.multitenant.signup.config;

import com.holonplatform.multitenant.onboarding.TenantOnboardingService;
import com.holonplatform.multitenant.onboarding.TenantRepository;
import com.holonplatform.multitenant.signup.AccountCredentialsStore;
import com.holonplatform.multitenant.signup.SignupService;
import com.holonplatform.multitenant.signup.internal.DefaultSignupService;
import com.holonplatform.multitenant.users.TenantUserService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring Boot auto-configuration for the reusable self-service sign-up flow.
 *
 * <p>Registers a {@link SignupService} bean automatically as soon as the
 * implementing application provides:
 * <ul>
 *   <li>an {@link AccountCredentialsStore} bean — the only piece of application-specific
 *       code required (typically a ~15-line adapter around the app's own login table)</li>
 *   <li>a {@link PasswordEncoder} bean — normally already required for Spring Security</li>
 * </ul>
 * No other wiring is necessary: {@link TenantOnboardingService}, {@link TenantRepository}
 * and {@link TenantUserService} are supplied by their own auto-configurations.
 */
@AutoConfiguration
@ConditionalOnBean({AccountCredentialsStore.class, PasswordEncoder.class})
public class SignupAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SignupService.class)
    public SignupService signupService(TenantOnboardingService onboardingService,
                                        TenantRepository tenantRepository,
                                        TenantUserService tenantUserService,
                                        AccountCredentialsStore credentialsStore,
                                        PasswordEncoder passwordEncoder) {
        return new DefaultSignupService(onboardingService, tenantRepository, tenantUserService,
                credentialsStore, passwordEncoder);
    }
}

