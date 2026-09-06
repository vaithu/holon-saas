package com.holonplatform.multitenant.signup;

/**
 * SPI implemented by the consuming application to persist login credentials
 * for a newly self-registered tenant owner.
 *
 * <p>The {@code holon-saas} framework deliberately does <strong>not</strong> ship a
 * concrete login-credentials table: every application has different requirements
 * (shared vs. per-tenant login, SSO, external identity providers, ...). Instead,
 * {@link SignupService} delegates storage of the (already-hashed) password to this
 * single-method SPI, so an implementing application only has to provide a thin
 * adapter around its own user/account table — typically a one-line wrapper around a
 * Spring Data repository:
 *
 * <pre>{@code
 * @Component
 * public class PlatformAccountCredentialsStore implements AccountCredentialsStore {
 *
 *     private final PlatformAccountRepository repository;
 *
 *     public PlatformAccountCredentialsStore(PlatformAccountRepository repository) {
 *         this.repository = repository;
 *     }
 *
 *     @Override
 *     public boolean existsByEmail(String tenantId, String email) {
 *         return repository.existsByTenantIdAndEmailIgnoreCase(tenantId, email);
 *     }
 *
 *     @Override
 *     public void save(String tenantId, String email, String encodedPassword) {
 *         repository.save(new PlatformAccount(tenantId, email, encodedPassword));
 *     }
 * }
 * }</pre>
 *
 * <p>Once this bean (and a {@code PasswordEncoder} bean, typically already required
 * for Spring Security) are present, {@link SignupService} auto-configures itself —
 * no further wiring is required to enable the sign-up flow.
 */
public interface AccountCredentialsStore {

    /**
     * Returns {@code true} if login credentials already exist for the given
     * tenant/email pair.
     *
     * @param tenantId tenant (workspace) identifier; must not be null
     * @param email    account email; must not be null
     * @return {@code true} if an account already exists
     */
    boolean existsByEmail(String tenantId, String email);

    /**
     * Persists the (already BCrypt/PasswordEncoder-hashed) password for a new account.
     * Implementations must never receive or log the raw password — {@link SignupService}
     * hashes it before calling this method.
     *
     * @param tenantId        tenant (workspace) identifier; must not be null
     * @param email           account email; must not be null
     * @param encodedPassword hashed password, ready to persist; must not be null
     */
    void save(String tenantId, String email, String encodedPassword);
}

