package com.holonplatform.multitenant.signup;

import com.holonplatform.multitenant.TenantDetails;

/**
 * Orchestrates self-service tenant sign-up so an implementing application does not
 * have to re-implement the same four-step flow (validate → provision tenant → create
 * owner membership → store credentials) for every new SaaS product:
 *
 * <ol>
 *   <li>Validates the requested workspace id and rejects duplicate accounts</li>
 *   <li>Provisions the tenant via {@code TenantOnboardingService}</li>
 *   <li>Creates and activates the owner's {@code TenantUser} membership
 *       (role {@code TenantRole.OWNER})</li>
 *   <li>Hashes the chosen password and delegates storage to the application's
 *       {@link AccountCredentialsStore} bean</li>
 * </ol>
 *
 * <p>Calling {@link #signup(SignupRequest)} again with the identical request after a
 * partial failure (tenant provisioned, but owner-user creation or credential storage
 * didn't complete) resumes from wherever the previous attempt left off, rather than
 * failing outright — see {@code DefaultSignupService}'s Javadoc for the exact guarantees.
 *
 * <p>Obtain the ready-to-use {@link #slugify(String)} helper to derive a workspace id
 * from a company name in any sign-up form, then simply inject this service into your
 * own UI layer (Vaadin, React, a REST controller, ...) to drive the sign-up flow.
 */
public interface SignupService {

    /**
     * Creates a new tenant and its owner account.
     *
     * @param request sign-up parameters; must not be null
     * @return the provisioned tenant's details
     * @throws SignupException if the workspace id is invalid or already taken
     */
    TenantDetails signup(SignupRequest request);

    /**
     * Derives a URL-safe workspace slug from a company name
     * (e.g. {@code "Acme Corp!"} → {@code "acme-corp"}).
     *
     * @param companyName free-text company/organisation name
     * @return a lowercase, hyphenated slug suitable as a tenant id
     */
    static String slugify(String companyName) {
        return com.holonplatform.multitenant.signup.internal.TenantSlugs.slugify(companyName);
    }
}

