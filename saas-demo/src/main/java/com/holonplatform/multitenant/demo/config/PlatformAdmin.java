package com.holonplatform.multitenant.demo.config;

/**
 * Identity of the platform operator — the one account in the demo that is deliberately
 * <em>not</em> scoped to a tenant.
 *
 * <h2>Why an unscoped account is needed at all</h2>
 * <p>The tenant registry lives in the control plane (the default schema). Every signed-in
 * tenant member has their tenant bound to the {@code TenantContext} for the whole request, so
 * every query they make is routed to {@code tenant_<id>} — where no {@code tenant} table
 * exists. A member therefore cannot list, search or suspend tenants, and no amount of
 * permission granting would change that; it is a data-routing constraint, not an authorization
 * one.
 *
 * <p>The operator sidesteps it by having no tenant to bind:
 * {@code AuthenticatedPrincipalTenantContextFilter} asks the principal for a tenant id, the
 * plain {@code User} returned for this account has none, and so the context stays empty and
 * queries land on the control plane. That is exactly the position
 * {@code TenantAdminService} and {@code TenantBillingService} are designed to be called from.
 *
 * <h2>The privilege this implies</h2>
 * <p>An unbound context means the tenant isolation machinery is simply not in play, so this
 * account is only as safe as the guard on the routes that use it. The admin routes are
 * therefore gated with {@code @RolesAllowed(PLATFORM_ADMIN_ROLE)} on the layout <em>and</em>
 * on each view, rather than by hiding menu entries.
 */
public final class PlatformAdmin {

    private PlatformAdmin() {
    }

    /**
     * The pseudo-workspace typed into the login form's Workspace field to reach the operator
     * console.
     *
     * <p>It is a reserved word rather than a real tenant: {@code DemoUserDetailsService}
     * short-circuits on it before any tenant lookup happens, and {@code SignupView} refuses it
     * as a workspace id so a tenant can never shadow it.
     */
    public static final String WORKSPACE = "platform";

    /** The seeded operator address. */
    public static final String EMAIL = "admin@holon.test";

    /** The seeded operator password. Fixed, because this is a demo. */
    public static final String PASSWORD = "platform-admin";

    /**
     * Spring Security role held only by the operator. Guards every route under {@code /admin}.
     *
     * <p>Declared without the {@code ROLE_} prefix because that is the form
     * {@code @RolesAllowed} and {@code TenantUserDetails.role(..)} both expect; Spring adds the
     * prefix itself.
     */
    public static final String ROLE = "PLATFORM_ADMIN";
}
