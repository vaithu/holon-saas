package com.holonplatform.multitenant.demo.ui;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.demo.service.CrossTenantAttemptLog;
import com.holonplatform.multitenant.demo.service.DataStrategyReport;
import com.holonplatform.multitenant.demo.service.DemoAsyncProbe;
import com.holonplatform.multitenant.demo.service.OnboardingEventLog;
import com.holonplatform.multitenant.demo.service.OnboardingFailureProbe;
import com.holonplatform.multitenant.demo.service.PlatformConsoleService;
import com.holonplatform.multitenant.demo.service.PoolRegistryReport;
import com.holonplatform.multitenant.demo.service.RedisFeatureReport;
import com.holonplatform.multitenant.demo.service.TenantScopedEventLog;
import com.holonplatform.multitenant.permissions.view.annotation.SecuredView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import jakarta.annotation.security.PermitAll;

/**
 * Batch 4 — the mechanics underneath the application: how a request is attributed to a tenant,
 * what stops one workspace reaching another's data, and what survives a thread hop.
 *
 * <h2>Why the resolution and isolation sections call a real endpoint</h2>
 * <p>Both features live in servlet filters, so they only do anything on a real HTTP request with
 * real headers. The buttons below therefore {@code fetch} {@code /api/whoami} from the browser
 * rather than calling a service: that way the request passes through {@code TenantContextFilter}
 * and {@code TenantIsolationFilter} exactly as any client's would, and what is displayed is the
 * filter chain's own answer rather than a re-enactment of it.
 */
@Route(value = "internals", layout = MainLayout.class)
@PageTitle("Internals")
@SecuredView(id = "internals", displayName = "Internals", category = "Administration",
        actions = {"READ"})
@PermitAll
public class InternalsView extends VerticalLayout {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final transient CrossTenantAttemptLog    attempts;
    private final transient TenantScopedEventLog     eventLog;
    private final transient ApplicationEventPublisher publisher;

    private final Pre                              probeOutput   = new Pre("(nothing probed yet)");
    private final Pre                              asyncOutput   = new Pre("(not run yet)");
    private final Grid<CrossTenantAttemptLog.Attempt>      attemptGrid = new Grid<>(CrossTenantAttemptLog.Attempt.class, false);
    private final Grid<TenantScopedEventLog.Observed>      eventGrid   = new Grid<>(TenantScopedEventLog.Observed.class, false);
    private final Grid<OnboardingEventLog.Observed>        onboardingGrid = new Grid<>(OnboardingEventLog.Observed.class, false);
    private final OnboardingEventLog                       onboardingEvents;

    public InternalsView(PlatformConsoleService console,
                         CrossTenantAttemptLog attempts,
                         DemoAsyncProbe asyncProbe,
                         TenantScopedEventLog eventLog,
                         DataStrategyReport dataStrategy,
                         RedisFeatureReport redis,
                         PoolRegistryReport pools,
                         OnboardingEventLog onboardingEvents,
                         OnboardingFailureProbe onboardingProbe,
                         ApplicationEventPublisher publisher) {
        this.attempts  = attempts;
        this.eventLog  = eventLog;
        this.publisher = publisher;
        this.onboardingEvents = onboardingEvents;

        setPadding(true);
        setSpacing(true);

        // The probe prints raw JSON, which is one very long line. Left unwrapped it pushes the
        // whole page into a horizontal scroll and hides everything below it.
        wrap(probeOutput);
        wrap(asyncOutput);

        add(new H2("Internals"),
                new Paragraph("The tenant plumbing, shown working rather than described."));

        add(resolutionSection());
        add(isolationSection(console));
        add(propagationSection(asyncProbe));
        add(scopedEventSection());
        add(dataStrategySection(dataStrategy));
        add(redisSection(redis));
        add(poolSection(pools, console));
        add(onboardingSection(onboardingProbe));

        refreshAttempts();
        refreshEvents();
        refreshOnboardingEvents();
    }

    // ── F18: tenant resolution strategies ───────────────────────────────────────

    private VerticalLayout resolutionSection() {
        VerticalLayout section = section("F18 — how a request is attributed to a tenant",
                "Four strategies are active at once, chained by CompositeTenantResolver and "
                        + "consulted in order: subdomain (10), header (20), path prefix (30), JWT "
                        + "claim (40). The first non-empty answer wins, so a request carrying two "
                        + "signals resolves on the earlier one and the later one is never read.");

        Button plain  = probeButton("Probe with no tenant signal", "/api/whoami", null);
        Button header = probeButton("Probe with X-Tenant-ID header", "/api/whoami", headerScript());
        Button path   = probeButton("Probe with /t/<workspace>/ path prefix",
                "/t/" + currentTenantId() + "/api/whoami", null);
        Button jwt    = probeButton("Probe with a JWT tenant_id claim", "/api/whoami", jwtScript());
        Button both   = probeButton("Probe with header AND path together",
                "/t/" + currentTenantId() + "/api/whoami", headerScript());

        HorizontalLayout buttons = new HorizontalLayout(plain, header, path, jwt, both);
        // Five buttons do not fit 1280px side by side, and an unwrapped row scrolls the whole
        // page sideways rather than only itself.
        buttons.getStyle().set("flex-wrap", "wrap");
        section.add(buttons);
        section.add(new Paragraph("Note: SubdomainTenantResolver always reports nothing here. "
                + "'localhost' is a single-label host, which it deliberately refuses rather than "
                + "treating the whole host as a tenant id — the same rule that stops a bare IP "
                + "address being read as one."));
        section.add(probeOutput);
        return section;
    }

    /**
     * A button that performs the probe from the browser, so the request really does carry the
     * headers and path being demonstrated.
     */
    private Button probeButton(String label, String url, String headerJs) {
        Button button = new Button(label, event -> {
            String script = """
                    const r = await fetch($0, { headers: %s, credentials: 'same-origin' });
                    const body = await r.text();
                    return r.status + ' ' + r.statusText + '\\n' + body;
                    """.formatted(headerJs == null ? "{}" : headerJs);
            getElement().executeJs("return (async () => {" + script + "})()", url)
                    .then(String.class, probeOutput::setText);
        });
        button.getElement().setAttribute("aria-label", label);
        return button;
    }

    private String headerScript() {
        return "{ 'X-Tenant-ID': '" + currentTenantId() + "' }";
    }

    /**
     * An unsigned token carrying only a {@code tenant_id} claim.
     *
     * <p>{@code JwtClaimTenantResolver} decodes the payload without verifying the signature, on
     * the assumption that something upstream already validated the token. That is a reasonable
     * division of labour and a dangerous default to forget about: on its own, this resolver
     * will believe any tenant id a client cares to Base64-encode.
     */
    private String jwtScript() {
        String payload = "{\"sub\":\"demo\",\"tenant_id\":\"" + currentTenantId() + "\"}";
        String token = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}")
                + "." + base64Url(payload) + ".";
        return "{ 'Authorization': 'Bearer " + token + "' }";
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    // ── F19: cross-tenant isolation ─────────────────────────────────────────────

    private VerticalLayout isolationSection(PlatformConsoleService console) {
        VerticalLayout section = section("F19 — reaching for another workspace",
                "TenantIsolationFilter compares the tenant the request resolved to against the "
                        + "tenant the signed-in user belongs to. A mismatch is refused with 403 and "
                        + "published as a CrossTenantAccessAttemptEvent, so the attempt is visible "
                        + "to the operator and not only to whoever made it.");

        Select<String> target = new Select<>();
        target.setLabel("Workspace to aim at");
        List<String> others = console.list(0, 100, "").stream()
                .map(TenantDetails::tenantId)
                .filter(id -> !id.equals(currentTenantId()))
                .toList();
        target.setItems(others);
        if (!others.isEmpty()) {
            target.setValue(others.getFirst());
        }

        Button attempt = new Button("Try to read that workspace", event -> {
            if (target.getValue() == null) {
                return;
            }
            String url = "/t/" + target.getValue() + "/api/whoami";
            getElement().executeJs("""
                    return (async () => {
                      const r = await fetch($0, { credentials: 'same-origin' });
                      return r.status + ' ' + r.statusText;
                    })()
                    """, url)
                    .then(String.class, result -> {
                        probeOutput.setText(url + "\n" + result);
                        refreshAttempts();
                    });
        });
        attempt.getElement().setAttribute("aria-label", "Try to read that workspace");

        attemptGrid.addColumn(a -> TIMESTAMP.format(a.at())).setHeader("At").setAutoWidth(true);
        attemptGrid.addColumn(CrossTenantAttemptLog.Attempt::username).setHeader("User").setAutoWidth(true);
        attemptGrid.addColumn(CrossTenantAttemptLog.Attempt::userTenant).setHeader("Belongs to").setAutoWidth(true);
        attemptGrid.addColumn(CrossTenantAttemptLog.Attempt::requestTenant).setHeader("Aimed at").setAutoWidth(true);
        attemptGrid.addColumn(CrossTenantAttemptLog.Attempt::uri).setHeader("Request").setAutoWidth(true);
        attemptGrid.setAllRowsVisible(true);

        section.add(new HorizontalLayout(target, attempt), new H4("Refused attempts"), attemptGrid);
        return section;
    }

    private void refreshAttempts() {
        attemptGrid.setItems(attempts.recent());
    }

    // ── F20: context propagation ────────────────────────────────────────────────

    private VerticalLayout propagationSection(DemoAsyncProbe probe) {
        VerticalLayout section = section("F20 — what a worker thread knows",
                "TenantContext is a plain ThreadLocal, so handing work to an executor loses the "
                        + "tenant silently unless the executor is decorated. Both pools below are "
                        + "identical apart from TenantAwareTaskDecorator.");

        Button run = new Button("Run the same task on both pools", event -> {
            DemoAsyncProbe.Observation observation = probe.observe();
            asyncOutput.setText("""
                    submitted by this request thread : %s
                    decorated pool worker saw        : %s
                    undecorated pool worker saw      : %s
                    a fresh virtual thread saw       : %s"""
                    .formatted(observation.submittedBy(),
                            observation.seenByDecoratedWorker(),
                            observation.seenByUndecoratedWorker(),
                            probe.tenantSeenByAVirtualThread()));
        });
        run.getElement().setAttribute("aria-label", "Run the same task on both pools");

        section.add(run, asyncOutput);
        return section;
    }

    // ── F21: tenant-scoped events ───────────────────────────────────────────────

    private VerticalLayout scopedEventSection() {
        VerticalLayout section = section("F21 — an event that carries its workspace",
                "The event is published from a thread with no tenant bound at all. The listener "
                        + "does nothing tenant-aware, yet still runs with the right workspace bound, "
                        + "because TenantScopedEventListenerAspect binds what the event declared.");

        Button publish = new Button("Publish from an unbound thread", event -> {
            TenantContext.get().ifPresent(tenant -> {
                TenantScopedEventLog.publishFromAnUnboundThread(publisher, tenant,
                        "published off-thread", 1);
                refreshEvents();
            });
        });
        publish.getElement().setAttribute("aria-label", "Publish from an unbound thread");

        eventGrid.addColumn(o -> TIMESTAMP.format(o.at())).setHeader("At").setAutoWidth(true);
        eventGrid.addColumn(TenantScopedEventLog.Observed::description).setHeader("Event").setAutoWidth(true);
        eventGrid.addColumn(TenantScopedEventLog.Observed::tenantOnEvent).setHeader("Declared").setAutoWidth(true);
        eventGrid.addColumn(TenantScopedEventLog.Observed::tenantInContext).setHeader("Bound in listener").setAutoWidth(true);
        eventGrid.addColumn(o -> o.bound() ? "bound" : "LOST").setHeader("Result").setAutoWidth(true);
        eventGrid.setAllRowsVisible(true);

        section.add(publish, eventGrid);
        return section;
    }

    private void refreshEvents() {
        eventGrid.setItems(eventLog.recent());
    }

    // ── F22: data strategy ──────────────────────────────────────────────────────

    private VerticalLayout dataStrategySection(DataStrategyReport report) {
        VerticalLayout section = section("F22 — where this workspace's rows actually live",
                "The strategy is fixed when the DataSource bean is built, so it is reported here "
                        + "rather than switched. SHARED and DATABASE_PER_TENANT are covered by tests, "
                        + "where the registry can be substituted.");

        Pre output = new Pre("""
                strategy                  : %s
                schema for this workspace : %s
                tenants holding a pool    : %d %s"""
                .formatted(report.strategy(), report.schemaForCurrentTenant(),
                        report.pooledTenantCount(), report.pooledTenantIds()));
        wrap(output);

        section.add(output);
        return section;
    }

    // ── F23: Redis-backed cache and distributed rate limiting ───────────────────

    private VerticalLayout redisSection(RedisFeatureReport redis) {
        VerticalLayout section = section("F23 — shared cache and rate-limit state in Redis",
                "Nothing in this application asked for Redis. Putting holon-multi-tenant-cache-redis "
                        + "and a connection factory on the classpath replaced the in-memory rate limiter "
                        + "and the per-JVM tenant details cache underneath the unchanged calling code. "
                        + "The point is where the state lives: the counters below are read straight back "
                        + "out of Redis, which is what a second application instance would also see.");

        Pre output = new Pre();
        wrap(output);

        Runnable refresh = () -> output.setText("""
                redis server              : %s
                rate limiter              : %s
                tenant details cache      : %s
                details cache key for you : %s
                sales permits left        : %s

                rate-limit keys in Redis for this workspace:
                %s"""
                .formatted(
                        redis.redisAvailable() ? "reachable, version " + redis.serverVersion()
                                               : redis.serverVersion(),
                        redis.rateLimiterImplementation(),
                        redis.detailsCacheImplementation(),
                        redis.detailsCacheKeyFor(currentTenantId()),
                        redis.remainingSalesPermits(),
                        redis.rateLimitKeysForCurrentTenant().isEmpty()
                                ? "  (none yet — record a sale on the Sales view first)"
                                : "  " + String.join("\n  ", redis.rateLimitKeysForCurrentTenant())));
        refresh.run();

        Button reload = new Button("Re-read from Redis", event -> refresh.run());
        reload.getElement().setAttribute("aria-label", "Re-read from Redis");

        section.add(reload, output);
        return section;
    }

    // ── F24: bounded connection-pool registry ──────────────────────────────────

    private VerticalLayout poolSection(PoolRegistryReport pools, PlatformConsoleService console) {
        VerticalLayout section = section("F24 — a bounded number of connection pools",
                "Schema-per-tenant gives every workspace its own connection pool, so memory grows "
                        + "with the customer list rather than with traffic. The registry caps how many "
                        + "pools stay open and closes the rest. This demo is capped at "
                        + pools.maxPools() + " so the cap is reachable by clicking. Use a workspace and "
                        + "it stays; leave one unused longest and it is the one that gets closed.");

        Pre output = new Pre();
        wrap(output);

        Runnable refresh = () -> output.setText("""
                pool ceiling              : %d
                pools open right now      : %d  (the bootstrap pool for platform work counts too)
                workspaces holding a pool : %s
                pools.active gauge        : %s"""
                .formatted(
                        pools.maxPools(),
                        pools.pooledCount(),
                        pools.pooledTenantIds().isEmpty() ? "(none)"
                                                          : String.join(", ", pools.pooledTenantIds()),
                        pools.activeGauge()));
        refresh.run();

        Select<String> target = new Select<>();
        target.setLabel("Workspace to use");
        List<String> all = console.list(0, 100, "").stream().map(TenantDetails::tenantId).sorted().toList();
        target.setItems(all);
        if (!all.isEmpty()) {
            target.setValue(all.getFirst());
        }

        Button use = new Button("Use that workspace", event -> {
            if (target.getValue() != null) {
                String what = pools.touch(target.getValue());
                refresh.run();
                output.setText(what + "\n\n" + output.getText());
            }
        });
        use.getElement().setAttribute("aria-label", "Use that workspace");

        // Touching every workspace in turn is the quickest way past the ceiling: with more
        // workspaces than pool slots, the last ones used survive and the rest are closed.
        Button useAll = new Button("Use every workspace in turn", event -> {
            String log = all.stream().map(pools::touch).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
            refresh.run();
            output.setText(log + "\n\n" + output.getText());
        });
        useAll.getElement().setAttribute("aria-label", "Use every workspace in turn");

        Button reload = new Button("Re-read the registry", event -> refresh.run());
        reload.getElement().setAttribute("aria-label", "Re-read the registry");

        section.add(new HorizontalLayout(target, use), new HorizontalLayout(useAll, reload), output);
        return section;
    }

    // ── F26: onboarding events and failure handling ────────────────────────────

    private VerticalLayout onboardingSection(OnboardingFailureProbe probe) {
        VerticalLayout section = section("F26 — onboarding announces what happened, and a failure is not final",
                "Every onboarding publishes an event, so billing, mail and provisioning can react "
                        + "without onboarding knowing they exist. The interesting case is the failure: "
                        + "the button below rigs a real Flyway failure for a brand-new workspace, lets "
                        + "onboarding fail, then immediately retries the same name. The retry succeeds, "
                        + "because a signup that failed for a transient reason must not permanently burn "
                        + "the name the customer chose.");

        Pre output = new Pre("(not run yet)");
        wrap(output);

        Button run = new Button("Force an onboarding failure, then retry", event -> {
            output.setText(probe.runFailureThenRetry());
            refreshOnboardingEvents();
        });
        run.getElement().setAttribute("aria-label", "Force an onboarding failure, then retry");

        Button reload = new Button("Re-read the onboarding events", event -> refreshOnboardingEvents());
        reload.getElement().setAttribute("aria-label", "Re-read the onboarding events");

        onboardingGrid.addColumn(OnboardingEventLog.Observed::tenantId).setHeader("Workspace");
        onboardingGrid.addColumn(OnboardingEventLog.Observed::outcome).setHeader("Outcome");
        onboardingGrid.addColumn(OnboardingEventLog.Observed::detail).setHeader("Detail").setFlexGrow(2);
        onboardingGrid.setAllRowsVisible(true);

        section.add(new HorizontalLayout(run, reload), output,
                new H4("Events onboarding published"), onboardingGrid);
        return section;
    }

    private void refreshOnboardingEvents() {
        onboardingGrid.setItems(onboardingEvents.recent());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private static void wrap(Pre output) {
        output.getStyle().set("white-space", "pre-wrap").set("word-break", "break-all");
        output.setWidthFull();
    }

    private static VerticalLayout section(String title, String explanation) {        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.add(new H4(title), new Paragraph(explanation));
        return layout;
    }

    private static String currentTenantId() {
        return TenantContext.currentTenantId().orElse("-");
    }
}
