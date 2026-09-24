package com.holonplatform.multitenant.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantContextTemplate;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantResolver;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.TenantSchemaStrategy;
import com.holonplatform.multitenant.data.datasource.TenantAwareDataSource;
import com.holonplatform.multitenant.demo.config.DemoTenantResolverConfig;
import com.holonplatform.multitenant.demo.service.CrossTenantAttemptLog;
import com.holonplatform.multitenant.demo.service.DataStrategyReport;
import com.holonplatform.multitenant.demo.service.DemoAsyncProbe;
import com.holonplatform.multitenant.demo.service.DemoTenantProvisioner;
import com.holonplatform.multitenant.demo.service.TenantScopedEventLog;
import com.holonplatform.multitenant.demo.support.TestWorkspaceIds;
import com.holonplatform.multitenant.resolver.CompositeTenantResolver;
import com.holonplatform.multitenant.security.event.CrossTenantAccessAttemptEvent;

/**
 * Batch four of the demo: how a request is attributed to a tenant, what stops one workspace
 * reaching another's data, and what survives a thread hop (F18–F22).
 *
 * <p>These features live in filters, aspects and thread-locals rather than in screens, so the
 * tests here are the primary evidence and the Internals view is the visible retelling. Two of
 * them — subdomain resolution and the two data strategies this deployment does not use — can
 * only be tested, because a single running application on {@code localhost} cannot exhibit
 * them at all.
 */
@SpringBootTest
class SaasDemoInternalsTest {

    @Autowired private DemoTenantProvisioner     provisioner;
    @Autowired private DemoAsyncProbe            asyncProbe;
    @Autowired private TenantScopedEventLog      eventLog;
    @Autowired private CrossTenantAttemptLog     attemptLog;
    @Autowired private DataStrategyReport        dataStrategy;
    @Autowired private ApplicationEventPublisher publisher;
    @Autowired private TenantResolver            resolver;

    private TenantDetails workspace;

    @BeforeEach
    void signUpAWorkspace() {
        String workspaceId = TestWorkspaceIds.uniqueWorkspaceId("b4");
        workspace = provisioner.signUpWorkspace(workspaceId, "Acme Ltd",
                TestWorkspaceIds.ownerEmail(workspaceId), "owner-secret");
    }

    // ── F18: tenant resolution strategies ──────────────────────────────────────────────

    @Test
    void eachStrategyReadsItsOwnSignalOffTheRequest() {
        assertThat(resolve(subdomainRequest("acme.holon.test"))).contains("acme");
        assertThat(resolve(headerRequest("acme"))).contains("acme");
        assertThat(resolve(pathRequest("/t/acme/api/whoami"))).contains("acme");
        assertThat(resolve(jwtRequest("acme"))).contains("acme");
    }

    @Test
    void aSingleLabelHostIsRefusedRatherThanTreatedAsATenant() {
        // Otherwise every request to localhost — or to a bare IP — would silently resolve to a
        // tenant named after the host, which is worse than resolving to nothing.
        assertThat(resolve(subdomainRequest("localhost"))).isEmpty();
        assertThat(resolve(subdomainRequest("203.0.113.9"))).isEmpty();
    }

    @Test
    void reservedSubdomainsAreNotTenants() {
        assertThat(resolve(subdomainRequest("www.holon.test"))).isEmpty();
        assertThat(resolve(subdomainRequest("api.holon.test"))).isEmpty();
    }

    @Test
    void theEarlierStrategyWinsWhenARequestCarriesTwoSignals() {
        // Both a header and a path prefix, disagreeing on purpose. The header orders at 20 and
        // the path at 30, so the header must win and the path must never be consulted — the
        // precedence is what decides which signal a request is trusted on.
        MockHttpServletRequest request = pathRequest("/t/from-path/api/whoami");
        request.addHeader("X-Tenant-ID", "from-header");

        assertThat(resolve(request)).contains("from-header");
    }

    @Test
    void theApplicationsResolverChainsAllFourStrategies() {
        // The composite the application actually runs, not a freshly built one: a demo that
        // asserted against its own local instance could pass while the app used something else.
        assertThat(resolver).isInstanceOf(CompositeTenantResolver.class);
        assertThat(((CompositeTenantResolver) resolver).getResolvers()).hasSize(4);
    }

    private Optional<String> resolve(MockHttpServletRequest request) {
        return new CompositeTenantResolver(DemoTenantResolverConfig.strategies())
                .resolveTenantId(request);
    }

    // ── F19: cross-tenant isolation ────────────────────────────────────────────────────

    @Test
    void aRefusedCrossTenantAttemptIsReportedAsAnEventAndNotOnlyAsAStatusCode() {
        int before = attemptLog.recent().size();

        publisher.publishEvent(new CrossTenantAccessAttemptEvent(this,
                workspace.tenantId(), "someone-elses-workspace",
                "/t/someone-elses-workspace/api/whoami", "alice@" + workspace.tenantId() + ".test"));

        List<CrossTenantAttemptLog.Attempt> recorded = attemptLog.recent();
        assertThat(recorded).hasSize(before + 1);

        CrossTenantAttemptLog.Attempt latest = recorded.getFirst();
        assertThat(latest.userTenant()).isEqualTo(workspace.tenantId());
        assertThat(latest.requestTenant()).isEqualTo("someone-elses-workspace");
        assertThat(latest.uri()).contains("/api/whoami");
    }

    // ── F20: context propagation across threads ────────────────────────────────────────

    @Test
    void aDecoratedExecutorCarriesTheTenantAndAPlainOneLosesIt() {
        DemoAsyncProbe.Observation observation =
                TenantContextTemplate.call(workspace, asyncProbe::observe);

        assertThat(observation.submittedBy()).isEqualTo(workspace.tenantId());
        assertThat(observation.seenByDecoratedWorker()).isEqualTo(workspace.tenantId());

        // The failure mode worth knowing: no exception, no warning, just the wrong schema.
        assertThat(observation.seenByUndecoratedWorker()).isEqualTo("(none)");
    }

    @Test
    void aVirtualThreadDoesNotInheritTheTenantEither() {
        String seen = TenantContextTemplate.call(workspace, asyncProbe::tenantSeenByAVirtualThread);
        assertThat(seen).isEqualTo("(none)");
    }

    @Test
    void theDecoratedPoolDoesNotLeakOneTenantIntoTheNextTask() {
        TenantContextTemplate.run(workspace, asyncProbe::observe);

        // The same single-threaded pool, now used with nothing bound. If the decorator failed
        // to clear on the way out, the previous tenant would still be there.
        DemoAsyncProbe.Observation clean = asyncProbe.observe();
        assertThat(clean.seenByDecoratedWorker()).isEqualTo("(none)");
    }

    // ── F21: tenant-scoped events ──────────────────────────────────────────────────────

    @Test
    void aScopedEventBindsItsTenantForTheListenerEvenWhenPublishedUnbound() {
        int before = eventLog.recent().size();

        // Published from a thread that never had a tenant, which is the only publication that
        // distinguishes the aspect working from the context merely never having been lost.
        TenantScopedEventLog.publishFromAnUnboundThread(publisher, workspace, "off-thread sale", 42);

        List<TenantScopedEventLog.Observed> observed = eventLog.recent();
        assertThat(observed).hasSize(before + 1);

        TenantScopedEventLog.Observed latest = observed.getFirst();
        assertThat(latest.tenantOnEvent()).isEqualTo(workspace.tenantId());
        assertThat(latest.tenantInContext()).isEqualTo(workspace.tenantId());
        assertThat(latest.bound()).isTrue();
    }

    @Test
    void theListenersBindingIsUndoneAfterwards() {
        TenantScopedEventLog.publishFromAnUnboundThread(publisher, workspace, "restores", 1);

        // The aspect restores whatever was bound before, so the publishing side is unaffected.
        assertThat(TenantContext.isSet()).isFalse();
    }

    // ── F22: data strategies ───────────────────────────────────────────────────────────

    @Test
    void thisDeploymentPutsEachWorkspaceInItsOwnSchema() {
        assertThat(dataStrategy.strategy()).isEqualTo(TenantSchemaStrategy.SCHEMA_PER_TENANT);

        String schema = TenantContextTemplate.call(workspace, dataStrategy::schemaForCurrentTenant);
        assertThat(schema).isEqualTo("tenant_" + workspace.tenantId());
    }

    @Test
    void theSharedStrategyAsksTheRegistryForNoTenantAtAll() throws Exception {
        // SHARED means one pool for everyone and a tenant_id column for isolation. The routing
        // proves it by never passing a tenant id to the registry — and the framework adds no
        // query predicate of its own, so with SHARED the isolation is the application's alone.
        RecordingRegistry registry = new RecordingRegistry();
        DataSource routed = new TenantAwareDataSource(TenantSchemaStrategy.SHARED, registry);

        TenantContextTemplate.run(workspace, () -> getConnectionQuietly(routed));

        assertThat(registry.lastRequested.get()).isNull();
    }

    @Test
    void theDatabasePerTenantStrategyRoutesByTenantExactlyAsSchemaPerTenantDoes() throws Exception {
        // The routing code is identical for the two isolated strategies; only the provider that
        // builds the pool differs. Asserting that here is what makes the distinction honest.
        for (TenantSchemaStrategy strategy :
                List.of(TenantSchemaStrategy.DATABASE_PER_TENANT, TenantSchemaStrategy.SCHEMA_PER_TENANT)) {

            RecordingRegistry registry = new RecordingRegistry();
            DataSource routed = new TenantAwareDataSource(strategy, registry);

            TenantContextTemplate.run(workspace, () -> getConnectionQuietly(routed));

            assertThat(registry.lastRequested.get())
                    .as("%s must route by tenant", strategy)
                    .isEqualTo(workspace.tenantId());
        }
    }

    private static void getConnectionQuietly(DataSource dataSource) {
        try (Connection ignored = dataSource.getConnection()) {
            // The connection itself is a mock; only the routing decision is under test.
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A registry that answers with a mock and remembers which tenant it was asked about. */
    private static final class RecordingRegistry implements TenantDataSourceRegistry {

        private final AtomicReference<String> lastRequested = new AtomicReference<>();

        @Override
        public DataSource getDataSource(String tenantId) {
            lastRequested.set(tenantId);
            DataSource dataSource = Mockito.mock(DataSource.class);
            try {
                Mockito.when(dataSource.getConnection()).thenReturn(Mockito.mock(Connection.class));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return dataSource;
        }

        @Override
        public void evict(String tenantId) {
            // Nothing is cached, so nothing can be evicted.
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public java.util.Set<String> activeTenantIds() {
            return java.util.Set.of();
        }

        @Override
        public void close() {
            // Nothing to release.
        }
    }

    // ── Request builders ───────────────────────────────────────────────────────────────

    private static MockHttpServletRequest subdomainRequest(String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/whoami");
        request.setServerName(host);
        return request;
    }

    private static MockHttpServletRequest headerRequest(String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/whoami");
        request.setServerName("localhost");
        request.addHeader("X-Tenant-ID", tenantId);
        return request;
    }

    private static MockHttpServletRequest pathRequest(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setServerName("localhost");
        request.setRequestURI(uri);
        return request;
    }

    private static MockHttpServletRequest jwtRequest(String tenantId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/whoami");
        request.setServerName("localhost");
        request.addHeader("Authorization", "Bearer " + unsignedToken(tenantId));
        return request;
    }

    /**
     * An unsigned token. {@code JwtClaimTenantResolver} decodes the payload without verifying
     * the signature, trusting something upstream to have done so — which is exactly why the
     * demo's probe can forge one, and exactly what an application must not forget.
     */
    private static String unsignedToken(String tenantId) {
        return encode("{\"alg\":\"none\"}") + "." + encode("{\"tenant_id\":\"" + tenantId + "\"}") + ".";
    }

    private static String encode(String json) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
