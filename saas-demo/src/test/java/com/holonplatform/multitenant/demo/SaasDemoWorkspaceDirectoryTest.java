package com.holonplatform.multitenant.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.holonplatform.multitenant.demo.account.WorkspaceDirectory;
import com.holonplatform.multitenant.demo.account.WorkspaceMembership;
import com.holonplatform.multitenant.demo.service.DemoTenantProvisioner;
import com.holonplatform.multitenant.demo.support.TestWorkspaceIds;

/**
 * Covers the lookup that replaced the Workspace field on the login form: an address alone has
 * to be enough to find every workspace it can sign in to, together with the name and role that
 * let a person tell those workspaces apart.
 */
@SpringBootTest
class SaasDemoWorkspaceDirectoryTest {

    @Autowired private DemoTenantProvisioner provisioner;
    @Autowired private WorkspaceDirectory    directory;

    @Test
    void anAddressInTwoWorkspacesIsFoundInBoth() {
        // The same person, the same address, two unrelated workspaces — the case the login
        // form used to push back onto the user by asking them to type the workspace id.
        String first  = TestWorkspaceIds.uniqueWorkspaceId("acme");
        String second = TestWorkspaceIds.uniqueWorkspaceId("globex");
        String email  = "chair@" + first + ".test";

        provisioner.signUpWorkspace(first, "Acme Corp", email, "first-secret");
        provisioner.signUpWorkspace(second, "Globex Inc", email, "second-secret");

        assertThat(directory.membershipsOf(email))
                .extracting(WorkspaceMembership::workspaceId,
                            WorkspaceMembership::workspaceName,
                            WorkspaceMembership::role,
                            WorkspaceMembership::active)
                .containsExactlyInAnyOrder(
                        tuple(first, "Acme Corp", "OWNER", true),
                        tuple(second, "Globex Inc", "OWNER", true));
    }

    @Test
    void anAddressInOneWorkspaceYieldsOneMembership() {
        String workspace = TestWorkspaceIds.uniqueWorkspaceId("initech");
        String email     = "owner@" + workspace + ".test";

        provisioner.signUpWorkspace(workspace, "Initech", email, "owner-secret");

        assertThat(directory.membershipsOf(email))
                .singleElement()
                .satisfies(membership -> {
                    assertThat(membership.workspaceId()).isEqualTo(workspace);
                    assertThat(membership.userName()).isNotBlank();
                });
    }

    @Test
    void anUnknownAddressBelongsNowhere() {
        assertThat(directory.membershipsOf("nobody@nowhere.test")).isEmpty();
        assertThat(directory.membershipsOf("")).isEmpty();
        assertThat(directory.membershipsOf(null)).isEmpty();
    }
}
