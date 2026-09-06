# Spring Boot Security - Quick Reference Checklist

## 📋 Pre-Development Review

### Dependencies ✅
- [x] Spring Security 7.1.0 is latest
- [x] No known CVEs in dependencies (validated: H2, Logback, SnakeYAML, Spring Security BOM)
- [x] All core dependencies up-to-date

### Architecture ✅
- [x] Multi-tenant isolation filter in place (TenantIsolationFilter)
- [x] Holon provides tenant-aware UserDetails
- [x] Role-based access control framework ready (@CheckTenantFeature, @RequiresPlan)
- [x] Audit logging infrastructure ready (@Audited, TenantAuditService)

---

## 🔴 GAPS: Must Implement Before Production

### Authentication
- [ ] Choose authentication method (form login, OAuth2, SAML, JWT)
- [ ] Implement UserDetailsService
- [ ] Implement AuthenticationProvider or configure with JDBC/LDAP
- [ ] Create login form / OAuth2 redirect
- [ ] Implement logout endpoint
- [ ] Add "forgot password" flow

### Password Security
- [ ] Add BCryptPasswordEncoder (strength 12+)
- [ ] Validate password strength (min 12 chars, mixed case, numbers, symbols)
- [ ] Hash existing passwords if migrating
- [ ] Configure password reset with time-limited tokens

### HTTP Security
- [ ] Enable HTTPS/TLS on all endpoints
- [ ] Configure CSRF protection (already partially done for Vaadin)
- [ ] Add HTTP security headers:
  - [ ] Content-Security-Policy
  - [ ] Strict-Transport-Security (HSTS)
  - [ ] X-Frame-Options (already done for Vaadin)
  - [ ] X-Content-Type-Options
  - [ ] X-XSS-Protection
  - [ ] Permissions-Policy
  - [ ] Referrer-Policy
- [ ] Configure CORS for legitimate cross-origin requests

### Session Management
- [ ] Enable session fixation protection
- [ ] Configure session timeout (recommend 30 minutes)
- [ ] Set HttpOnly and Secure on session cookies
- [ ] Configure SameSite cookie attribute (Strict recommended)
- [ ] Implement concurrent session control (max 1 per user)

### Rate Limiting & Brute-Force
- [ ] Add brute-force protection on login (lock after 5 attempts)
- [ ] Implement account lockout (15-30 minute duration)
- [ ] Add rate limiting to sensitive endpoints
- [ ] Alert on repeated failures

### Secrets Management
- [ ] Remove hard-coded secrets from code
- [ ] Externalize all configuration (environment variables, config server, secrets vault)
- [ ] Use Spring Cloud Config or HashiCorp Vault
- [ ] Rotate secrets regularly

### Monitoring & Alerting
- [ ] Monitor CrossTenantAccessAttemptEvent
- [ ] Log all authentication attempts
- [ ] Alert security team on:
  - [ ] Failed login attempts (>3)
  - [ ] Cross-tenant access attempts
  - [ ] Privilege escalation attempts
  - [ ] Admin actions

---

## 🟠 IMPORTANT Configurations

### Application Properties

```properties
# HTTPS
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=${SSL_KEYSTORE_PATH}
server.ssl.key-store-password=${SSL_KEYSTORE_PASSWORD}

# Session Security
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict
server.servlet.session.timeout=30m

# Disable development features
spring.h2.console.enabled=false
spring.devtools.restart.enabled=false
management.endpoints.web.exposure.include=health,metrics

# Logging
logging.level.org.springframework.security=INFO
logging.level.org.springframework.security.authentication=DEBUG
```

### SecurityFilterChain Bean

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .requiresChannel(channel -> channel.anyRequest().requiresSecure())
        .csrf(csrf -> csrf.ignoringRequestMatchers("/VAADIN/**", "/webhook/**"))
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
            .httpStrictTransportSecurity()
            .frameOptions().sameOrigin())
        .sessionManagement(session -> session
            .sessionFixationProtection(SessionFixationProtectionStrategy.MIGRATE_SESSION)
            .sessionConcurrency(concurrency -> concurrency.maximumSessions(1)))
        .formLogin(form -> form.loginPage("/login").permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/public/**").permitAll()
            .anyRequest().authenticated());
    return http.build();
}
```

---

## 🟢 Already Good

### Multi-Tenant Isolation
- ✅ TenantIsolationFilter prevents cross-tenant access
- ✅ TenantAwareUserDetails carries tenant context
- ✅ TenantContext auto-propagates to async threads
- ✅ CVE-related dependencies are all patched

### Authorization
- ✅ @CheckTenantFeature for feature flags
- ✅ @RequiresPlan for plan-based access
- ✅ Role management framework (OWNER, ADMIN, MEMBER, VIEWER)
- ✅ Audit trail with @Audited

### Rate Limiting
- ✅ @RateLimit AOP decorator
- ✅ Tenant-level limits in tenant-limits module
- ✅ Can be combined with HTTP-level rate limiting

---

## 🔍 Testing Checklist

Before going to production, test:

- [ ] Authentication flow (login, logout, session)
- [ ] Password hashing (verify BCrypt, not plain text)
- [ ] CSRF protection (POST without token should fail)
- [ ] CORS (verify only allowed origins can call API)
- [ ] Session fixation (new session ID after login)
- [ ] Concurrent sessions (2nd login kills 1st session)
- [ ] Cross-tenant isolation (user A cannot access tenant B's data)
- [ ] Rate limiting (N requests over time limit blocked)
- [ ] Brute-force (account locked after 5 failed login attempts)
- [ ] HTTP headers present in response (HSTS, CSP, X-Frame-Options)
- [ ] HTTPS enforcement (HTTP redirects to HTTPS)
- [ ] Logout clears session (cannot access protected resources)

---

## 📊 Security Maturity Matrix

| Level | Status | Gap |
|-------|--------|-----|
| **1. Authentication** | ❌ NOT STARTED | Must implement user auth |
| **2. Password Encoding** | ❌ NOT STARTED | Must add BCrypt |
| **3. HTTPS** | ⚠️ PARTIALLY (H2 demo) | Enable in production |
| **4. CSRF** | ✅ IMPLEMENTED | Good for Vaadin |
| **5. Session Management** | ❌ NOT STARTED | Must implement |
| **6. CORS** | ❌ NOT STARTED | Add if API needed |
| **7. HTTP Headers** | ⚠️ PARTIALLY | Add CSP, HSTS, etc. |
| **8. Rate Limiting** | ✅ IMPLEMENTED | Via @RateLimit AOP |
| **9. Audit Logging** | ✅ IMPLEMENTED | Via @Audited AOP |
| **10. Tenant Isolation** | ✅ IMPLEMENTED | TenantIsolationFilter |
| **11. Secrets Management** | ❌ NOT STARTED | Externalize all config |
| **12. Monitoring** | ⚠️ PARTIAL | Need alerts on security events |

---

## 🚀 Deployment Readiness Scorecard

```
Current Score: 4/12 = 33% READY

Required for MVP (Tier 1):
✅ Multi-tenant isolation      [DONE]
✅ Role-based access control   [DONE]
✅ Audit logging               [DONE]
❌ User authentication         [TODO]
❌ Password encoding           [TODO]
❌ HTTPS                        [TODO]
❌ Session management          [TODO]

After Tier 1 (Score will be ~60%):
❌ HTTP security headers       [TODO]
❌ CSRF hardening             [TODO]
❌ Rate limiting on auth      [TODO]
❌ Secrets management         [TODO]

After Tier 2 (Score will be ~90%):
❌ Security monitoring        [TODO]
❌ Incident response plan     [TODO]
❌ Penetration testing        [TODO]
```

---

## 📞 Quick Reference Links

- [Spring Security 7.1.0 Docs](https://docs.spring.io/spring-security/reference/index.html)
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE Top 25](https://cwe.mitre.org/top25/)
- [Spring Boot Security Best Practices](https://spring.io/guides/gs/securing-web/)
- [Holon Platform Docs](https://docs.holon-platform.com/)

---

## ✋ STOP Before Production If

- [ ] No UserDetailsService implemented
- [ ] HTTP Basic is in use (demo only!)
- [ ] Passwords are not BCrypt encoded
- [ ] No HTTPS certificate installed
- [ ] Hard-coded secrets in application.properties
- [ ] H2 console enabled
- [ ] Demo data seeding enabled
- [ ] Spring Security debug logging enabled
- [ ] No session management configured
- [ ] No CSRF protection on form endpoints

---

## 📝 Template: SecurityFilterChain Override

If using Holon's multi-tenant starter, override the demo config:

```java
// In your app, create your own @Configuration
// Spring will use this instead of DemoSecurityConfig

@Configuration
@EnableWebSecurity
public class AppSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Your production config here
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

The `@ConditionalOnMissingBean` in Holon auto-configuration will not override your bean.

---

**Generated:** 2026-08-28  
**For:** Holon SaaS Framework 1.0.0 on Spring Boot 4.1.0

