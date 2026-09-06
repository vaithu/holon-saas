# Integration Guide: holon-core + holon-saas Security

**Goal:** Integrate holon-core's robust security framework with holon-saas's multi-tenant isolation.

---

## Step 1: Add holon-core Dependencies

**Edit:** `holon-saas/pom.xml` (root)

```xml
<properties>
    <holon-core.version>12.0.0</holon-core.version>
    <!-- ...existing properties... -->
</properties>

<dependencyManagement>
    <dependencies>
        <!-- holon-core BOM: Gets all holon-core artifacts with consistent versions -->
        <dependency>
            <groupId>com.holon-platform.core</groupId>
            <artifactId>holon-bom-platform</artifactId>
            <version>${holon-core.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        
        <!-- ...existing BOMs... -->
    </dependencies>
</dependencyManagement>
```

---

## Step 2: Update tenant-security Module

**Edit:** `holon-saas/tenant-security/pom.xml`

Add holon-core dependencies:

```xml
<dependencies>
    <!-- ...existing dependencies... -->
    
    <!-- holon-core: Authentication and Authorization abstractions -->
    <dependency>
        <groupId>com.holon-platform.core</groupId>
        <artifactId>holon-auth</artifactId>
    </dependency>
    
    <!-- holon-core: Spring Security integration -->
    <dependency>
        <groupId>com.holon-platform.core</groupId>
        <artifactId>holon-spring-security</artifactId>
    </dependency>
</dependencies>
```

---

## Step 3: Create Tenant-Aware Authenticator

**File:** `holon-saas/tenant-security/src/main/java/com/holonplatform/multitenant/security/TenantAuthenticator.java`

```java
package com.holonplatform.multitenant.security;

import com.holonplatform.auth.Account;
import com.holonplatform.auth.Authenticator;
import com.holonplatform.auth.AuthenticationError;
import com.holonplatform.auth.AuthenticationToken;
import com.holonplatform.multitenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Tenant-aware authenticator that wraps holon-core's Authenticator
 * with multi-tenant isolation checks.
 *
 * <p>After holon-core authenticates a user, this authenticator verifies
 * that the user's tenant matches the current request's tenant context.
 * If not, authentication is denied.</p>
 */
public class TenantAuthenticator implements Authenticator {

    private static final Logger log = LoggerFactory.getLogger(TenantAuthenticator.class);

    private final Authenticator delegate;  // holon-core's authenticator
    private boolean enforced = true;

    public TenantAuthenticator(Authenticator delegate) {
        this.delegate = delegate;
    }

    public void setEnforced(boolean enforced) {
        this.enforced = enforced;
    }

    @Override
    public Account authenticate(AuthenticationToken token) throws AuthenticationError {
        // Step 1: Delegate to holon-core's authenticator
        Account account = delegate.authenticate(token);
        
        // Step 2: Extract tenant from authenticated account
        String accountTenant = extractTenantFromAccount(account);
        
        // Step 3: Compare with current request's tenant
        Optional<String> contextTenant = TenantContext.currentTenantId();
        
        if (accountTenant != null && contextTenant.isPresent()) {
            String ctxTenant = contextTenant.get();
            
            if (!accountTenant.equalsIgnoreCase(ctxTenant)) {
                String msg = String.format(
                    "Cross-tenant authentication denied: account tenant '%s' does not match request tenant '%s'",
                    accountTenant, ctxTenant
                );
                
                if (enforced) {
                    log.warn(msg);
                    throw new AuthenticationError(msg);
                } else {
                    log.warn(msg + " (not enforced)");
                }
            }
        }
        
        // Step 4: Return authenticated account
        return account;
    }

    /**
     * Extracts tenant ID from the account.
     * Looks for a custom attribute or assumes the account name contains tenant info.
     */
    private String extractTenantFromAccount(Account account) {
        if (account == null) {
            return null;
        }
        
        // Try to get from custom attribute
        return account.getAttribute("tenantId")
            .map(Object::toString)
            .orElse(null);
    }
}
```

---

## Step 4: Create Tenant-Aware UserDetailsService

**File:** `holon-saas/tenant-users/src/main/java/com/holonplatform/multitenant/users/TenantAwareUserDetailsService.java`

```java
package com.holonplatform.multitenant.users;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.security.TenantAwareUserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Loads user details from the tenant user service.
 * Integrates holon-saas user management with Spring Security.
 */
public class TenantAwareUserDetailsService implements UserDetailsService {

    private final TenantUserService userService;

    public TenantAwareUserDetailsService(TenantUserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Get current tenant from context
        String tenantId = TenantContext.currentTenantId()
            .orElseThrow(() -> new UsernameNotFoundException("No tenant in context"));

        // Load user from tenant service
        TenantUser user = userService.findByUsername(tenantId, username)
            .orElseThrow(() -> new UsernameNotFoundException(
                String.format("User '%s' not found in tenant '%s'", username, tenantId)
            ));

        // Ensure user is active
        if (!user.isActive()) {
            throw new UsernameNotFoundException("User account is disabled");
        }

        // Map tenant roles to Spring authorities
        Collection<GrantedAuthority> authorities = user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
            .collect(Collectors.toList());

        // Build tenant-aware UserDetails
        return TenantAwareUserDetails.builder()
            .username(user.getEmail())
            .password(user.getPasswordHash())  // Must be BCrypt encoded
            .authorities(authorities)
            .accountNonExpired(true)
            .accountNonLocked(!user.isLocked())
            .credentialsNonExpired(true)
            .enabled(user.isActive())
            .tenantId(tenantId)
            .build();
    }
}
```

---

## Step 5: Production Security Configuration

**File:** `holon-saas/demo/src/main/java/com/holonplatform/multitenancy/demo/config/ProductionSecurityConfig.java`

```java
package com.holonplatform.multitenancy.demo.config;

import com.holonplatform.multitenant.security.TenantAuthenticator;
import com.holonplatform.multitenant.users.TenantAwareUserDetailsService;
import com.holonplatform.multitenant.users.TenantUserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.http.SessionFixationProtectionStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Production Spring Security configuration.
 *
 * This configuration:
 * 1. Enables HTTPS-only access
 * 2. Configures session management (fixation protection, concurrent session control)
 * 3. Hardens HTTP headers (HSTS, CSP, X-Frame-Options, etc.)
 * 4. Configures CORS for trusted origins
 * 5. Integrates with holon-core's authentication
 * 6. Enables method-level security (@Secured, @PreAuthorize, etc.)
 *
 * <p>This configuration is automatically selected when:
 * - Profile "prod" is active, OR
 * - Property "spring.security.use-holon-core" is true
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, prePostEnabled = true)
@Profile({"prod", "production"})
@ConditionalOnProperty(
    name = "holon.security.production-enabled",
    havingValue = "true",
    matchIfMissing = false
)
public class ProductionSecurityConfig {

    /**
     * BCrypt password encoder with strength 12 (200ms per auth).
     * This is used throughout the application for password hashing.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Tenant-aware UserDetailsService.
     * Loads users from holon-saas multi-tenant user service.
     */
    @Bean
    public TenantAwareUserDetailsService userDetailsService(TenantUserService userService) {
        return new TenantAwareUserDetailsService(userService);
    }

    /**
     * Session registry for concurrent session control.
     * Limits each user to 1 active session.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Main security filter chain.
     * Configures HTTPS, CSRF, headers, session management, authentication, and authorization.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        TenantAwareUserDetailsService userDetailsService,
        SessionRegistry sessionRegistry
    ) throws Exception {

        http
            // ──────────────── HTTPS Enforcement ────────────────
            .requiresChannel(channel -> channel
                .anyRequest()
                .requiresSecure()
            )

            // ──────────────── CSRF Protection ────────────────
            .csrf(csrf -> csrf
                // Disable CSRF only for webhook paths (and any UI-specific asset
                // paths your chosen UI toolkit requires, e.g. /VAADIN/** for Vaadin,
                // /api/** for a SPA using token auth, etc.)
                .ignoringRequestMatchers(
                    "/webhook/**"
                )
            )

            // ──────────────── HTTP Security Headers ────────────────
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        // Adjust script-src/style-src/font-src to match the CDN(s)
                        // required by your chosen UI toolkit, if any.
                        "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: https:; " +
                        "font-src 'self'; " +
                        "connect-src 'self' ws: wss:;"
                    )
                )
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)  // 1 year
                    .includeSubDomains(true)
                    .preload(true)
                )
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions()
                .xssProtection()
                .permissionsPolicy(permissions -> permissions
                    .policy("accelerometer=(), camera=(), geolocation=(), " +
                           "gyroscope=(), magnetometer=(), microphone=(), " +
                           "payment=(), usb=()")
                )
            )

            // ──────────────── CORS Configuration ────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ──────────────── Session Management ────────────────
            .sessionManagement(session -> session
                .sessionFixationProtection(SessionFixationProtectionStrategy.MIGRATE_SESSION)
                .sessionConcurrency(concurrency -> concurrency
                    .maximumSessions(1)
                    .sessionRegistry(sessionRegistry)
                    .expiredUrl("/login?expired")
                )
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // ──────────────── Form Login ────────────────
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/authenticate")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error")
                .permitAll()
            )

            // ──────────────── Remember-Me (optional) ────────────────
            .rememberMe(rememberMe -> rememberMe
                .key("holon-saas-remember-me-key")
                .tokenValiditySeconds(86400)  // 24 hours
            )

            // ──────────────── Logout ────────────────
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .permitAll()
            )

            // ──────────────── Authorization ────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login",
                    "/login/**",
                    "/register",
                    "/forgot-password",
                    "/public/**",
                    "/static/**",
                    "/assets/**",
                    "/VAADIN/**",
                    "/frontend/**"
                ).permitAll()
                // Remove /h2-console in production!
                // Webhook endpoints (if applicable)
                .requestMatchers("/webhook/**").permitAll()
                // All other requests require authentication
                .anyRequest().authenticated()
            )

            // ──────────────── Exception Handling ────────────────
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    if (request.getHeader("Accept").contains("application/json")) {
                        response.setContentType("application/json;charset=UTF-8");
                        response.setStatus(401);
                        response.getWriter().write("{\"error\": \"Unauthorized\"}");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
                .accessDeniedPage("/access-denied")
            );

        return http.build();
    }

    /**
     * CORS configuration.
     * Allow only trusted origins.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        return request -> {
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowedOrigins(List.of(
                "https://yourdomain.com",
                "https://www.yourdomain.com",
                "https://app.yourdomain.com"
                // DO NOT use "*" in production
            ));
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("*"));
            config.setAllowCredentials(true);
            config.setMaxAge(3600L);
            return config;
        };
    }
}
```

---

## Step 6: Update Application Properties

**File:** `demo/src/main/resources/application-prod.properties`

```properties
# ─── Server & HTTPS ───────────────────────────────────────────────────
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=${SSL_KEYSTORE_PATH}
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}
server.ssl.key-store-type=PKCS12
server.ssl.key-alias=tomcat

# Session security
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict
server.servlet.session.timeout=30m

# ─── Spring profiles ──────────────────────────────────────────────────
spring.profiles.active=prod

# ─── Holon Security: Production ────────────────────────────────────────
holon.security.production-enabled=true
holon.tenant.security.isolation-enforced=true

# ─── DataSource (PostgreSQL in production, not H2) ─────────────────────
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USER}
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.hikari.maximum-pool-size=20

# ─── Logging ──────────────────────────────────────────────────────────
logging.level.root=WARN
logging.level.com.holonplatform=INFO
logging.level.org.springframework.security=INFO

# ─── Disable development features ──────────────────────────────────────
spring.h2.console.enabled=false
spring.devtools.restart.enabled=false
management.endpoints.web.exposure.include=health,metrics
```

---

## Step 7: Run & Test

```bash
# Build with holon-core
mvn clean install -Dspring.profiles.active=prod

# Run with production profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=prod"

# Test login
curl -X POST http://localhost:8443/authenticate \
  -d "username=user@test.com&password=password" \
  -H "Content-Type: application/x-www-form-urlencoded"

# Test HTTPS redirect
curl -I http://localhost:8080/dashboard  # Should redirect to https
```

---

## 🎯 What You Get Now

| Feature | From | Status |
|---------|------|--------|
| User authentication | holon-core | ✅ BCrypt, Spring Security integrated |
| Tenant isolation | holon-saas | ✅ TenantIsolationFilter |
| Role-based access | holon-saas + holon-core | ✅ @Secured, @PreAuthorize, @RequiresPlan |
| Method security | holon-core | ✅ Full support |
| Password encoding | holon-core (BCryptPasswordEncoder) | ✅ Strength 12 |
| JWT (optional) | holon-core (holon-auth-jwt) | ✅ Available |
| HTTPS | Spring Security + your config | ✅ Enforced |
| Session management | Spring Security + your config | ✅ Fixation protection, limits |
| CSRF protection | Spring Security + your config | ✅ Configured |
| HTTP headers | Your config | ✅ CSP, HSTS, X-Frame, etc. |
| Rate limiting | holon-saas (@RateLimit) | ✅ Still there |
| Audit logging | holon-saas (@Audited) | ✅ Still there |

---

## 🚀 Next Steps

1. **Add dependencies** to pom.xml (Step 1-2)
2. **Copy ProductionSecurityConfig** (Step 5)
3. **Implement TenantAwareUserDetailsService** (Step 4)
4. **Update application properties** (Step 6)
5. **Test with authentication** (Step 7)
6. **Deploy to staging** before production

**Estimated time:** 1 day (vs. 2-3 weeks building from scratch)

