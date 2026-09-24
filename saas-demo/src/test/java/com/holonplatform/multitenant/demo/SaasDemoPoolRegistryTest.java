package com.holonplatform.multitenant.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.demo.service.DemoTenantProvisioner;
import com.holonplatform.multitenant.demo.service.PoolRegistryReport;
import com.holonplatform.multitenant.demo.support.TestWorkspaceIds;

/**
 * F24 — the bounded connection-pool registry, exercised against the running application's real
 * registry rather than a stub.
 *
 * <p>The demo configures {@code holon.tenant.data.max-pools: 3}, which is what makes these
 * assertions possible: a cap of 200 could not be reached by a test without provisioning two
 * hundred schemas. The cap is small, but the behaviour under it is the behaviour a deployment
 * gets at any size.
 *
 * <p>The eviction policy is the part worth testing. Capping pools is only safe if the pool that
 * gets closed is one nobody is using; if the busiest tenant is the one evicted, the cap turns
 * into a pool-thrashing loop that is worse than no cap at all.
 */
@SpringBootTest
class SaasDemoPoolRegistryTest {

    @Autowired private DemoTenantProvisioner provisioner;
    @Autowired private PoolRegistryReport    pools;

    private final List<String> workspaces = new ArrayList<>();

    @BeforeEach
    void signUpMoreWorkspacesThanThereArePoolSlots() {
        workspaces.clear();
        // One more workspace than the cap, so that keeping them all open is impossible.
        int count = pools.maxPools() + 1;
        for (int i = 0; i < count; i++) {
            String workspaceId = TestWorkspaceIds.uniqueWorkspaceId("pool" + i);
            TenantDetails workspace = provisioner.signUpWorkspace(
                    workspaceId, "Pool Test " + i,
                    TestWorkspaceIds.ownerEmail(workspaceId), "owner-secret");
            workspaces.add(workspace.tenantId());
        }
    }

    @Test
    @DisplayName("the configured ceiling is the one the registry enforces")
    void ceilingIsConfigured() {
        assertThat(pools.maxPools()).isEqualTo(3);
    }

    @Test
    @DisplayName("using a workspace opens a pool that can actually connect")
    void touchOpensAUsablePool() {
        String what = pools.touch(workspaces.getFirst());

        assertThat(what).doesNotContain("FAILED");
        assertThat(what).contains("connected to schema");
        assertThat(pools.pooledTenantIds()).contains(workspaces.getFirst());
    }

    @Test
    @DisplayName("the number of open pools never exceeds the ceiling, however many workspaces are used")
    void poolCountStaysUnderTheCeiling() {
        workspaces.forEach(pools::touch);

        assertThat(pools.pooledCount()).isLessThanOrEqualTo(pools.maxPools());
    }

    @Test
    @DisplayName("a workspace still in use is not the one that gets closed")
    void theWorkspaceInUseSurvivesThePressure() {
        String busy = workspaces.getFirst();
        pools.touch(busy);

        // Every other workspace competes for the remaining slots, but the busy one keeps being
        // used in between — under last-use eviction that is enough to protect it.
        for (String other : workspaces.subList(1, workspaces.size())) {
            pools.touch(busy);
            pools.touch(other);
        }
        pools.touch(busy);

        assertThat(pools.pooledTenantIds()).contains(busy);
    }

    @Test
    @DisplayName("the bootstrap pool counts against the ceiling but is not reported as a workspace")
    void bootstrapPoolIsCountedButNotNamed() {
        workspaces.forEach(pools::touch);

        // pooledCount() includes the null-tenant bootstrap pool; pooledTenantIds() does not, so
        // the two legitimately disagree and nothing should try to reconcile them.
        assertThat(pools.pooledCount()).isGreaterThanOrEqualTo(pools.pooledTenantIds().size());
        assertThat(pools.pooledTenantIds()).doesNotContainNull();
    }

    @Test
    @DisplayName("the metrics gauge reports the same number of pools as the registry")
    void gaugeAgreesWithTheRegistry() {
        pools.touch(workspaces.getFirst());

        assertThat(pools.activeGauge()).isEqualTo(String.valueOf(pools.pooledCount()));
    }
}
