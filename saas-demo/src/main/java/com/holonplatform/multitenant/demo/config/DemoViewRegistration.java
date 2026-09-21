package com.holonplatform.multitenant.demo.config;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.holonplatform.multitenant.demo.service.DemoRoles;
import com.holonplatform.multitenant.demo.ui.AccountingView;
import com.holonplatform.multitenant.demo.ui.AuditView;
import com.holonplatform.multitenant.demo.ui.DashboardView;
import com.holonplatform.multitenant.demo.ui.InternalsView;
import com.holonplatform.multitenant.demo.ui.PlanView;
import com.holonplatform.multitenant.demo.ui.RolesView;
import com.holonplatform.multitenant.demo.ui.SalesView;
import com.holonplatform.multitenant.demo.ui.SettingsView;
import com.holonplatform.multitenant.demo.ui.TeamView;
import com.holonplatform.multitenant.permissions.view.SystemDefaultPermissionRegistry;
import com.holonplatform.multitenant.permissions.view.ViewAction;
import com.holonplatform.multitenant.permissions.view.ViewRegistry;
import com.holonplatform.multitenant.permissions.view.ViewResource;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;

/**
 * F17 — puts the demo's screens into the {@link ViewRegistry}, and states the baseline
 * permissions each system role holds on them.
 *
 * <h2>Why the views are registered by hand</h2>
 * <p>{@code DefaultViewRegistry} discovers {@link SecuredView} automatically, but it does so via
 * {@code ApplicationContext.getBeansWithAnnotation(..)} — and Vaadin Flow's {@code @Route}
 * classes are <strong>not</strong> Spring bean definitions. Vaadin instantiates them per
 * navigation through {@code SpringInstantiator}, which autowires a fresh instance rather than
 * fetching a registered bean, so nothing annotated ends up in the context for the registry to
 * find. Auto-discovery therefore works for {@code @Component}-style screens and silently finds
 * nothing for a Vaadin application.
 *
 * <p>The annotations are still declared on the views, because they are the honest place for a
 * screen to describe its own id and actions. This class reads them straight off the classes —
 * the same metadata, sourced without needing the classes to be beans. If the framework's
 * discovery is later taught to scan classpath candidates instead of beans, deleting this class
 * is all that would be required.
 *
 * <h2>Why the registry matters</h2>
 * <p>{@code getRoleMatrix} iterates the registered views and their declared actions — an
 * unregistered view simply has no rows, so the owner cannot grant anything on it. The registry
 * is what defines the vocabulary the permission matrix is expressed in.
 */
@Component
public class DemoViewRegistration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoViewRegistration.class);

    /** Every screen that should appear in the permission matrix. */
    private static final List<Class<?>> SECURED_VIEWS = List.of(
            DashboardView.class,
            TeamView.class,
            SalesView.class,
            AccountingView.class,
            SettingsView.class,
            AuditView.class,
            PlanView.class,
            RolesView.class,
            InternalsView.class);

    private final ViewRegistry                    viewRegistry;
    private final SystemDefaultPermissionRegistry defaultPermissions;

    public DemoViewRegistration(ViewRegistry viewRegistry,
                                SystemDefaultPermissionRegistry defaultPermissions) {
        this.viewRegistry       = viewRegistry;
        this.defaultPermissions = defaultPermissions;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Class<?> viewClass : SECURED_VIEWS) {
            SecuredView annotation = viewClass.getAnnotation(SecuredView.class);
            if (annotation == null) {
                throw new IllegalStateException(viewClass.getName() + " is listed as a secured view "
                        + "but carries no @SecuredView annotation");
            }
            viewRegistry.register(new ViewResource(
                    annotation.id(),
                    annotation.displayName().isBlank() ? annotation.id() : annotation.displayName(),
                    annotation.category(),
                    annotation.description(),
                    Set.of(annotation.actions())));
        }

        registerBaselineDefaults();

        log.info("Registered {} demo views into the ViewRegistry", viewRegistry.getAllViews().size());
    }

    /**
     * The out-of-the-box answer to "what may a SALESMAN do?", before any tenant customises it.
     *
     * <p>The framework's own baseline names generic views ({@code leads}, {@code invoices}) that
     * this demo does not have, so the demo states its own. {@code OWNER} and {@code ADMIN} need
     * no entry — {@code isActionPermitted} short-circuits both to full access.
     *
     * <p>Note what is deliberately <em>absent</em>: a SALESMAN gets {@code READ} and
     * {@code CREATE} on the sales view but not {@code EXPORT}. That gap is what the Roles view
     * closes with a per-tenant override, which is the whole point of F16 — the same role means
     * different things in different workspaces.
     */
    private void registerBaselineDefaults() {
        defaultPermissions.registerDefault(DemoRoles.SALESMAN, "dashboard", ViewAction.READ);
        defaultPermissions.registerDefault(DemoRoles.SALESMAN, "sales",
                ViewAction.READ, ViewAction.CREATE);

        defaultPermissions.registerDefault(DemoRoles.ACCOUNTANT, "dashboard", ViewAction.READ);
        defaultPermissions.registerDefault(DemoRoles.ACCOUNTANT, "accounting",
                ViewAction.READ, ViewAction.CREATE);
    }
}
