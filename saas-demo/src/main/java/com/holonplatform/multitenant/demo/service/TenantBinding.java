package com.holonplatform.multitenant.demo.service;

import java.util.Objects;
import java.util.function.Supplier;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;

/**
 * Runs a piece of work against a deliberately chosen schema.
 *
 * <h2>Why this is needed</h2>
 * <p>The tenant bound to {@link TenantContext} decides which schema every query reaches, and it
 * is normally bound once per request from the authenticated principal. Two kinds of work do not
 * fit that default and must state their intent explicitly:
 *
 * <ul>
 *   <li><strong>Control-plane work</strong> — the {@code tenant} registry lives in the default
 *       schema, so a signed-in member changing their own plan must temporarily <em>unbind</em>
 *       their tenant or the write is routed to {@code tenant_<id>}, where no such table
 *       exists.</li>
 *   <li><strong>Work on someone else's schema</strong> — the framework's billing, permission
 *       and settings tables are created <em>inside each tenant's schema</em>. The platform
 *       operator has no tenant bound at all, so to read a tenant's invoices it must
 *       temporarily <em>bind</em> that tenant.</li>
 * </ul>
 *
 * <p>Both directions restore whatever was bound before, so the remainder of the request is
 * unaffected — which matters because a Vaadin click handler runs inside a request that has
 * already bound a tenant and will keep using it after this call returns.
 */
public final class TenantBinding {

    private TenantBinding() {
    }

    /**
     * Runs the action with <em>no</em> tenant bound, so queries reach the control plane.
     *
     * @param action the work to perform; must not be null
     * @return whatever the action returns
     */
    public static <T> T onControlPlane(Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        TenantDetails previous = TenantContext.get().orElse(null);
        TenantContext.clear();
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    /**
     * Runs the action with the given tenant bound, so queries reach that tenant's schema.
     *
     * @param tenant the tenant to bind for the duration of the call; must not be null
     * @param action the work to perform; must not be null
     * @return whatever the action returns
     */
    public static <T> T asTenant(TenantDetails tenant, Supplier<T> action) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(action, "action must not be null");
        TenantDetails previous = TenantContext.get().orElse(null);
        TenantContext.set(tenant);
        try {
            return action.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(TenantDetails previous) {
        if (previous == null) {
            TenantContext.clear();
        } else {
            TenantContext.set(previous);
        }
    }
}
