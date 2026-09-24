package com.holonplatform.multitenant.demo.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.holonplatform.multitenant.demo.support.TestWorkspaceIds;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Drives the demo's whole journey through a real browser, in order, starting from sign-up:
 * create a workspace, invite two colleagues with different job roles, let each accept, prove
 * that what they can see and do differs in both directions, and then walk the platform features
 * that depend on the workspace's plan rather than on any person's role — settings, quotas, rate
 * limits, the audit trail, and plan gating.
 *
 * <h2>Why the steps share one class and run in order</h2>
 * <p>Each step genuinely depends on the state the previous one created — Bob cannot sign in
 * before he has accepted an invitation that did not exist before the owner signed up, and the
 * seat quota cannot be shown as full until both colleagues have joined. Splitting them into
 * independent tests would mean re-running sign-up and both invitations as setup for every
 * assertion, making the run slower and its failure output harder to read without testing
 * anything extra.</p>
 *
 * <h2>Running it</h2>
 * <pre>
 *   mvn -pl saas-demo spring-boot:run                           # terminal 1
 *   mvn -pl saas-demo test -Pe2e "-De2e.headed=true"            # terminal 2, watch the browser
 * </pre>
 *
 * <p>The argument must be quoted in PowerShell, which otherwise splits it at the dot and hands
 * Maven {@code .headed=true} as a lifecycle phase.
 *
 * <p>Nothing is cleaned up afterwards: the workspace id is unique per run, the application
 * keeps its data in memory, and the demo exposes no delete operation through the UI — which is
 * the only surface a blackbox test may use.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Sign-up to per-role permissions, end to end")
class SignupToPermissionsE2ETest {

    private static final String BASE_URL = System.getenv().getOrDefault("APP_URL", "http://localhost:8080");

    private static final Path   SCREENSHOT_DIR = Paths.get("target", "e2e-screenshots");

    // A workspace id unique to this run, so the journey also passes against a server that has
    // already been used — for instance when re-running without restarting the application.
    private static final String WORKSPACE = TestWorkspaceIds.uniqueWorkspaceId("acme");

    // A second workspace, so that "every workspace on the platform" and "suspension stops at
    // the tenant boundary" have something to actually be demonstrated against.
    private static final String SECOND_WORKSPACE = TestWorkspaceIds.uniqueWorkspaceId("globex");

    private static final String SECOND_OWNER_EMAIL = "owner@globex.test";

    private static final String OWNER_EMAIL = "owner@acme.test";
    private static final String ALICE_EMAIL = "alice@acme.test";
    private static final String BOB_EMAIL   = "bob@acme.test";
    private static final String CAROL_EMAIL = "carol@acme.test";
    private static final String PASSWORD    = "demo-password";

    private static Playwright     playwright;
    private static Browser        browser;
    private static BrowserContext context;
    private static Page           page;

    /** Invitation links captured from the Team view and consumed by the acceptance step. */
    private static String aliceInviteLink;
    private static String bobInviteLink;

    @BeforeAll
    static void launchBrowser() {
        boolean headed = Boolean.parseBoolean(System.getProperty("e2e.headed", "false"));
        double  slowMo = Double.parseDouble(System.getProperty("e2e.slowMo", "0"));

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(!headed)
                .setSlowMo(slowMo));

        // A fixed viewport keeps screenshots comparable between machines.
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 900));
        page = context.newPage();
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    // ── 1. Tenant sign-up ───────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("1 - a new workspace can be created from the sign-up page")
    void ownerSignsUpForANewWorkspace() {
        page.navigate(BASE_URL + "/signup");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Create your workspace"))
                .waitFor();

        page.getByLabel("Company name").fill("Acme Corp");
        page.getByLabel("Your email").fill(OWNER_EMAIL);
        page.getByLabel("Choose a password").fill(PASSWORD);

        // The view suggests a workspace id derived from the company name, and does so on a
        // server round trip. Waiting for that suggestion to land before typing the real id
        // avoids racing it — otherwise the suggestion arrives last and wins.
        Locator workspaceField = page.getByLabel("Workspace id");
        assertThat(workspaceField).hasValue(Pattern.compile(".+"));
        workspaceField.fill(WORKSPACE);
        assertThat(workspaceField).hasValue(WORKSPACE);

        capture("01-signup-filled");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create workspace")).click();

        // Sign-up redirects to the login page once the tenant schema has been provisioned.
        page.waitForURL("**/login");
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Sign in")))
                .isVisible();

        capture("02-after-signup");
    }

    // ── 2. Tenant login ─────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("2 - the owner can sign in and holds every permission")
    void ownerSignsIn() {
        signIn(OWNER_EMAIL);

        // The dashboard reports the resolved tenant and the permissions actually granted, so
        // asserting on it covers tenant binding and permission resolution in one step.
        assertThat(page.getByText("Active workspace: " + WORKSPACE)).isVisible();
        assertThat(page.getByText("GRANTED  USER_INVITE")).isVisible();
        assertThat(page.getByText("GRANTED  SALES_RECORD")).isVisible();

        capture("03-owner-dashboard");
    }

    // ── 3. Inviting colleagues ──────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("3 - the owner invites a salesman and an accountant")
    void ownerInvitesTwoColleagues() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Team")).click();
        page.waitForURL("**/team");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Team")).waitFor();

        invite(ALICE_EMAIL, "SALESMAN");
        invite(BOB_EMAIL, "ACCOUNTANT");

        aliceInviteLink = inviteLinkFor(ALICE_EMAIL);
        bobInviteLink   = inviteLinkFor(BOB_EMAIL);

        assertTrue(aliceInviteLink.contains("/accept-invite/" + WORKSPACE + "/"),
                "Invitation link should carry the workspace id: " + aliceInviteLink);

        capture("04-team-with-invitations");
    }

    // ── 4. Invited users accept ─────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("4 - both invitees accept and choose their own password")
    void inviteesAcceptTheirInvitations() {
        signOutIfSignedIn();

        acceptInvitation(aliceInviteLink);
        capture("05-alice-accepted");

        acceptInvitation(bobInviteLink);
        capture("06-bob-accepted");
    }

    // ── 5, 6, 7. Alice: Sales only ──────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("5-7 - the salesman sees Sales, never Accounting, and may only record a sale")
    void salesmanSeesAndDoesOnlySales() {
        signOutIfSignedIn();
        signIn(ALICE_EMAIL);

        assertThat(page.getByText("GRANTED  SALES_VIEW")).isVisible();
        assertThat(page.getByText("DENIED   ACCOUNTING_VIEW")).isVisible();

        // View-level: the Accounting entry is absent from the navigation altogether.
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sales")))
                .isVisible();
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Accounting")))
                .isHidden();

        capture("07-alice-dashboard");

        // Method-level: recording a sale is allowed.
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sales")).click();
        page.waitForURL("**/sales");
        page.getByLabel("Customer").fill("Globex");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record sale")).click();

        assertThat(page.getByText("Recorded:")).isVisible();
        capture("08-alice-records-sale");

        // ...and Accounting stays unreachable even when its URL is typed directly.
        page.navigate(BASE_URL + "/accounting");
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Access denied")))
                .isVisible();

        capture("09-alice-denied-accounting");
    }

    // ── The mirror image: Bob ───────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("5-7 - the accountant sees Accounting, never Sales, and may only post a ledger entry")
    void accountantSeesAndDoesOnlyAccounting() {
        signOutIfSignedIn();
        signIn(BOB_EMAIL);

        assertThat(page.getByText("GRANTED  ACCOUNTING_VIEW")).isVisible();
        assertThat(page.getByText("DENIED   SALES_VIEW")).isVisible();

        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Accounting")))
                .isVisible();
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sales")))
                .isHidden();

        capture("10-bob-dashboard");

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Accounting")).click();
        page.waitForURL("**/accounting");
        page.getByLabel("Account").fill("4000");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Post ledger entry")).click();

        assertThat(page.getByText("Posted:")).isVisible();
        capture("11-bob-posts-entry");

        page.navigate(BASE_URL + "/sales");
        assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Access denied")))
                .isVisible();

        capture("12-bob-denied-sales");
    }

    // ── 7. Per-workspace settings (F8) ──────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("F8 - a setting falls back to its global default until the workspace overrides it")
    void ownerOverridesAndClearsASetting() {
        signOutIfSignedIn();
        signIn(OWNER_EMAIL);

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Settings")).click();
        page.waitForURL("**/settings");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Settings")).waitFor();

        // With nothing overridden the row resolves to the default declared in DemoSettings.
        assertThat(gridCell("USD").first()).isVisible();
        capture("13-settings-default");

        page.getByLabel("Workspace value").fill("EUR");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Save override")).click();
        page.getByText("Saved sales.currency = EUR").waitFor();

        // Two cells now read EUR: the workspace override, and the value it resolved to.
        assertThat(gridCell("EUR")).hasCount(2);
        capture("14-settings-overridden");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Clear override")).click();
        page.getByText("Cleared override for sales.currency").waitFor();

        // Clearing must fall back to the default rather than leave an empty value behind.
        assertThat(gridCell("EUR")).hasCount(0);
        assertThat(gridCell("USD").first()).isVisible();
        capture("15-settings-cleared");
    }

    // ── 8. Plan-derived quota (F9) ──────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("F9 - a FREE workspace runs out of seats once its three members have joined")
    void freeWorkspaceRefusesAFourthMember() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Team")).click();
        page.waitForURL("**/team");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Team")).waitFor();
        capture("16-team-quota-full");

        // The owner plus the two colleagues who accepted in step 4 fill the FREE plan exactly.
        assertThat(page.getByText(Pattern.compile("Members: \\d+ of 3 allowed on the FREE plan")))
                .isVisible();
        assertThat(page.getByText("Members: 3 of 3 allowed on the FREE plan")).isVisible();

        invite(CAROL_EMAIL, "SALESMAN");

        assertThat(page.getByText(Pattern.compile("Quota reached"))).isVisible();
        capture("17-invite-refused-by-quota");
    }

    // ── 9. Tenant-scoped rate limiting (F10) ────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("F10 - recording sales too quickly is refused once the minute budget is spent")
    void salesRecordingIsRateLimited() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sales")).click();
        page.waitForURL("**/sales");

        assertThat(page.getByText(Pattern.compile("Remaining this minute:"))).isVisible();
        capture("18-sales-budget");

        Locator refusal = page.getByText(Pattern.compile("Slow down"));

        // How many permits are left depends on what earlier steps spent and on how much the
        // bucket has refilled meanwhile, so the loop records until it is refused rather than
        // assuming a fixed count. One attempt more than the budget is enough either way.
        boolean refused = false;
        for (int attempt = 1; attempt <= 5 && !refused; attempt++) {
            page.getByLabel("Customer").fill("Globex " + attempt);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record sale")).click();
            page.getByText(Pattern.compile("Recorded:|Slow down")).first().waitFor();
            refused = refusal.count() > 0;
        }

        assertTrue(refused, "Recording sales repeatedly should eventually hit the rate limit");
        capture("19-sales-rate-limited");
    }

    // ── 10. What a FREE workspace has not bought (F11, F12) ─────────────────────

    @Test
    @Order(10)
    @DisplayName("F11/F12 - on FREE the audit log is unavailable and the report export is refused")
    void freeWorkspaceIsRefusedAuditLogAndExport() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Audit log")).click();
        page.waitForURL("**/audit");

        // The permission to reach the page is held; the workspace simply lacks the feature.
        assertThat(page.getByText(Pattern.compile("Audit log unavailable"))).isVisible();
        capture("20-audit-not-on-free");

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Plan")).click();
        page.waitForURL("**/plan");
        assertThat(page.getByText("Current plan: FREE")).isVisible();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export sales report")).click();
        assertThat(page.getByText(Pattern.compile("Denied:"))).isVisible();
        capture("21-export-denied-on-free");
    }

    // ── 11. Upgrading the plan (F12) ────────────────────────────────────────────

    @Test
    @Order(11)
    @DisplayName("F12 - moving to PROFESSIONAL lets the same user export the same report")
    void upgradingThePlanUnlocksTheExport() {
        selectOption("Plan", "PROFESSIONAL");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change plan")).click();

        // The view reloads itself, because the plan is bound to the request that is already in
        // flight; the new one takes effect on the next request.
        page.getByText("Current plan: PROFESSIONAL").waitFor();
        assertThat(page.getByText(Pattern.compile("AUDIT_LOG"))).isVisible();
        capture("22-plan-upgraded");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export sales report")).click();
        assertThat(page.getByText(Pattern.compile("exported"))).isVisible();
        capture("23-export-allowed-on-professional");
    }

    // ── 12. The audit trail (F11) ───────────────────────────────────────────────

    @Test
    @Order(12)
    @DisplayName("F11 - the audit log lists what the @Audited aspect recorded, filterable by action")
    void auditLogListsWhatTheAspectRecorded() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Audit log")).click();
        page.waitForURL("**/audit");

        // Every one of these rows was written by the framework aspect, not by any view.
        assertThat(gridCell("PLAN_CHANGED").first()).isVisible();
        assertThat(gridCell("USER_INVITED").first()).isVisible();
        assertThat(gridCell("CREATE").first()).isVisible();
        capture("24-audit-log");

        // Filtering runs as a typed query against the enum column — the path that the
        // AuditEntryProperties retyping fixed.
        selectOption("Action", "USER_INVITED");
        assertThat(gridCell("USER_INVITED").first()).isVisible();
        assertThat(gridCell("PLAN_CHANGED")).hasCount(0);

        // The Id column comes from @Audited's SpEL resourceIdExpression over the method's
        // arguments, which only resolves when the build records parameter names.
        assertThat(gridCell(ALICE_EMAIL).first()).isVisible();
        capture("25-audit-filtered");
    }

    // ── 13. The quota follows the plan (F9) ─────────────────────────────────────

    @Test
    @Order(13)
    @DisplayName("F9 - the seat the FREE plan refused is available on PROFESSIONAL")
    void upgradedWorkspaceAcceptsTheFourthMember() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Team")).click();
        page.waitForURL("**/team");

        assertThat(page.getByText("Members: 3 of 50 allowed on the PROFESSIONAL plan")).isVisible();

        invite(CAROL_EMAIL, "SALESMAN");
        assertThat(page.getByText("Invitation created for " + CAROL_EMAIL)).isVisible();
        capture("26-invite-allowed-after-upgrade");
    }

    // ── 14. A second workspace, so "cross-tenant" means something (F13) ─────────

    @Test
    @Order(14)
    @DisplayName("F13 - a second workspace is created, giving the platform console something to compare")
    void aSecondWorkspaceSignsUp() {
        signOutIfSignedIn();
        signUp(SECOND_WORKSPACE, "Globex Ltd", SECOND_OWNER_EMAIL);

        // It must be reachable in its own right — the isolation claim in the next steps is only
        // interesting if this workspace is genuinely live.
        signIn(SECOND_WORKSPACE, SECOND_OWNER_EMAIL);
        assertThat(page.getByText("Active workspace: " + SECOND_WORKSPACE)).isVisible();
        capture("27-second-workspace-dashboard");
    }

    // ── 15. The platform console (F13) ──────────────────────────────────────────

    @Test
    @Order(15)
    @DisplayName("F13 - the operator sees every workspace, counted and searchable, with no tenant bound")
    void operatorListsEveryWorkspace() {
        signOutIfSignedIn();
        signInAsOperator();

        // Both workspaces in one grid: a query no tenant-bound user could ever issue.
        assertThat(gridCell(WORKSPACE).first()).isVisible();
        assertThat(gridCell(SECOND_WORKSPACE).first()).isVisible();
        assertThat(page.getByText(Pattern.compile("ACTIVE: \\d+"))).isVisible();
        capture("28-admin-workspaces");

        // The search term is pushed into the query, not filtered in the browser — so the row
        // that drops out was never fetched.
        page.getByLabel("Search workspaces").fill(SECOND_WORKSPACE);
        assertThat(gridCell(SECOND_WORKSPACE).first()).isVisible();
        assertThat(gridCell(WORKSPACE)).hasCount(0);
        capture("29-admin-search");

        page.getByLabel("Search workspaces").fill("");
        assertThat(gridCell(WORKSPACE).first()).isVisible();
    }

    // ── 16. Suspension, and that it stops at the workspace boundary (F14) ───────

    @Test
    @Order(16)
    @DisplayName("F14 - suspending one workspace locks its members out and leaves the other untouched")
    void suspendingAWorkspaceLocksOutOnlyItsMembers() {
        page.getByLabel("Suspend " + WORKSPACE).click();
        assertThat(page.getByText("Workspace " + WORKSPACE + " suspended")).isVisible();
        assertThat(page.getByText(Pattern.compile("SUSPENDED: [1-9]"))).isVisible();
        capture("30-workspace-suspended");

        // Alice is refused — the tenant registry says the workspace is out of service, so the
        // demo's UserDetailsService hands Spring a disabled account.
        signOutIfSignedIn();
        attemptSignIn(WORKSPACE, ALICE_EMAIL);
        assertThat(page.getByText(Pattern.compile("This workspace is suspended"))).isVisible();
        capture("31-suspended-login-refused");

        // The other workspace carries on, which is the whole point of the boundary.
        signIn(SECOND_WORKSPACE, SECOND_OWNER_EMAIL);
        assertThat(page.getByText("Active workspace: " + SECOND_WORKSPACE)).isVisible();
        capture("32-other-workspace-unaffected");
    }

    @Test
    @Order(17)
    @DisplayName("F14 - reactivating restores access, with nothing having been deleted")
    void reactivatingRestoresAccess() {
        signOutIfSignedIn();
        signInAsOperator();

        page.getByLabel("Activate " + WORKSPACE).click();
        assertThat(page.getByText("Workspace " + WORKSPACE + " reactivated")).isVisible();
        capture("33-workspace-reactivated");

        signOutIfSignedIn();
        signIn(WORKSPACE, ALICE_EMAIL);
        assertThat(page.getByText("Active workspace: " + WORKSPACE)).isVisible();
        capture("34-alice-back-in");
    }

    // ── 18. Subscriptions and invoices (F15) ────────────────────────────────────

    @Test
    @Order(18)
    @DisplayName("F15 - changing a plan from the console raises an invoice in that workspace's schema")
    void changingThePlanRaisesAnInvoice() {
        signOutIfSignedIn();
        signInAsOperator();

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Billing")).click();
        page.waitForURL("**/admin/billing");

        selectOption("Workspace", WORKSPACE);
        assertThat(page.getByText(Pattern.compile("Subscription: PROFESSIONAL — ACTIVE"))).isVisible();
        capture("35-admin-billing");

        selectOption("Move to plan", "ENTERPRISE");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Change plan and invoice"))
                .click();
        assertThat(page.getByText("Plan changed and invoice raised")).isVisible();

        // The invoice is real data read back out of the tenant's own schema, and the tenant
        // registry moved with it — billing and entitlement stay in step.
        assertThat(page.getByText(Pattern.compile("Subscription: ENTERPRISE — ACTIVE"))).isVisible();
        assertThat(gridCell("plan change to ENTERPRISE").first()).isVisible();
        capture("36-invoice-raised");
    }

    // ── 19. The permission matrix (F16, F17) ────────────────────────────────────

    @Test
    @Order(19)
    @DisplayName("F16 - Alice is refused an export her role has no EXPORT action for")
    void aliceIsRefusedTheExportAction() {
        signOutIfSignedIn();
        signIn(WORKSPACE, ALICE_EMAIL);

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sales")).click();
        page.waitForURL("**/sales");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export sales")).click();
        assertThat(page.getByText("Denied: your role has no EXPORT permission on the sales view"))
                .isVisible();
        capture("37-export-denied");
    }

    @Test
    @Order(20)
    @DisplayName("F16/F17 - the owner grants sales EXPORT to SALESMAN, and Alice's button starts working")
    void ownerGrantsTheExportActionAndAliceCanExport() {
        signOutIfSignedIn();
        signIn(WORKSPACE, OWNER_EMAIL);

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Roles")).click();
        page.waitForURL("**/roles");

        // The matrix rows exist only because each view registered itself and declared its
        // actions (F17) — an unregistered view cannot be granted anything here.
        selectOption("Role to edit", "SALESMAN");
        assertThat(gridCell("EXPORT").first()).isVisible();
        capture("38-role-matrix");

        // Clicked rather than checked: the accessible name sits on the <vaadin-checkbox> host
        // element, which Playwright's check() refuses because it is not a native input.
        page.getByLabel("sales EXPORT").click();
        assertThat(page.getByText("Granted sales:EXPORT for SALESMAN")).isVisible();
        capture("39-export-granted");

        // Nothing about Alice changed — only what her workspace grants her role.
        signOutIfSignedIn();
        signIn(WORKSPACE, ALICE_EMAIL);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sales")).click();
        page.waitForURL("**/sales");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export sales")).click();
        assertThat(page.getByText("Sales exported")).isVisible();
        capture("40-export-allowed");
    }

    @Test
    @Order(21)
    @DisplayName("F16 - resetting the role to defaults takes the grant away again")
    void resettingTheRoleRestoresTheDenial() {
        signOutIfSignedIn();
        signIn(WORKSPACE, OWNER_EMAIL);

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Roles")).click();
        page.waitForURL("**/roles");
        selectOption("Role to edit", "SALESMAN");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reset role to defaults"))
                .click();
        assertThat(page.getByText("SALESMAN reset to baseline defaults")).isVisible();
        capture("41-role-reset");

        signOutIfSignedIn();
        signIn(WORKSPACE, ALICE_EMAIL);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sales")).click();
        page.waitForURL("**/sales");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Export sales")).click();
        assertThat(page.getByText("Denied: your role has no EXPORT permission on the sales view"))
                .isVisible();
        capture("42-export-denied-again");
    }

    // ── 22. Tenant resolution strategies (F18) ──────────────────────────────────

    @Test
    @Order(22)
    @DisplayName("F18 - each strategy reads its own signal, and the earlier one wins a tie")
    void resolutionStrategiesReadTheirSignals() {
        signOutIfSignedIn();
        signIn(WORKSPACE, OWNER_EMAIL);

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Internals")).click();
        page.waitForURL("**/internals");
        capture("43-internals");

        // A header the browser chooses to send. The probe goes through the real filter chain,
        // so what comes back is TenantContextFilter's own answer.
        page.getByLabel("Probe with X-Tenant-ID header").click();
        assertThat(probeOutput(Pattern.compile("HeaderTenantResolver\": ?\"" + WORKSPACE))).isVisible();
        capture("44-probe-header");

        page.getByLabel("Probe with /t/<workspace>/ path prefix").click();
        assertThat(probeOutput(Pattern.compile("PathPrefixTenantResolver\": ?\"" + WORKSPACE))).isVisible();
        capture("45-probe-path");

        page.getByLabel("Probe with a JWT tenant_id claim").click();
        assertThat(probeOutput(Pattern.compile("JwtClaimTenantResolver\": ?\"" + WORKSPACE))).isVisible();
        capture("46-probe-jwt");

        // Two signals at once: both resolvers answer, and the composite takes the header
        // because it orders before the path. The precedence decides which signal is trusted.
        page.getByLabel("Probe with header AND path together").click();
        assertThat(probeOutput(Pattern.compile("HeaderTenantResolver\": ?\"" + WORKSPACE))).isVisible();
        assertThat(probeOutput(Pattern.compile("PathPrefixTenantResolver\": ?\"" + WORKSPACE))).isVisible();
        capture("47-probe-header-and-path");
    }

    // ── 23. Cross-tenant isolation (F19) ────────────────────────────────────────

    @Test
    @Order(23)
    @DisplayName("F19 - aiming at another workspace is refused with 403 and reported as an event")
    void crossTenantAccessIsRefusedAndReported() {
        selectOption("Workspace to aim at", SECOND_WORKSPACE);
        page.getByLabel("Try to read that workspace").click();

        assertThat(probeOutput(Pattern.compile("403"))).isVisible();

        // The 403 alone is only visible to whoever made the request; the event is what makes
        // the attempt visible to the operator.
        assertThat(gridCell(SECOND_WORKSPACE).first()).isVisible();
        assertThat(gridCell(OWNER_EMAIL).first()).isVisible();
        capture("48-cross-tenant-refused");
    }

    // ── 24. Context propagation (F20) ───────────────────────────────────────────

    @Test
    @Order(24)
    @DisplayName("F20 - the decorated pool carries the tenant and the plain one loses it silently")
    void tenantContextSurvivesOnlyTheDecoratedExecutor() {
        page.getByLabel("Run the same task on both pools").click();

        assertThat(probeOutput(Pattern.compile("decorated pool worker saw\\s+: " + WORKSPACE))).isVisible();
        assertThat(probeOutput(Pattern.compile("undecorated pool worker saw\\s+: \\(none\\)"))).isVisible();
        assertThat(probeOutput(Pattern.compile("virtual thread saw\\s+: \\(none\\)"))).isVisible();
        capture("49-thread-propagation");
    }

    // ── 25. Tenant-scoped events (F21) ──────────────────────────────────────────

    @Test
    @Order(25)
    @DisplayName("F21 - a scoped event binds its workspace for a listener that knows nothing about tenants")
    void scopedEventBindsItsTenantForTheListener() {
        page.getByLabel("Publish from an unbound thread").click();

        // Published where no tenant was bound, yet the listener saw one — which only the
        // aspect can account for.
        assertThat(gridCell("published off-thread (1)").first()).isVisible();
        assertThat(gridCell("bound").first()).isVisible();
        capture("50-scoped-event");
    }

    // ── 26. The data strategy (F22) ─────────────────────────────────────────────

    @Test
    @Order(26)
    @DisplayName("F22 - the workspace's rows live in a schema of its own")
    void theDataStrategyIsSchemaPerTenant() {
        assertThat(probeOutput(Pattern.compile("strategy\\s+: SCHEMA_PER_TENANT"))).isVisible();
        assertThat(probeOutput(Pattern.compile("schema for this workspace\\s+: tenant_" + WORKSPACE)))
                .isVisible();
        capture("51-data-strategy");
    }

    @Test
    @Order(27)
    @DisplayName("F23 - the cache and the rate-limit counters live in Redis, not in this JVM")
    void cacheAndRateLimitStateLiveInRedis() {
        // Record a sale first. The rate-limit window is a minute wide, so the counters from the
        // earlier steps have long since expired by now — and doing it here shows the counter
        // appearing in Redis as the direct consequence of a business action rather than as
        // leftover state.
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sales")).click();
        page.waitForURL("**/sales");
        page.getByLabel("Customer").fill("Initech");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Record sale")).click();
        assertThat(page.getByText("Recorded:")).isVisible();

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Internals")).click();
        page.waitForURL("**/internals");
        page.getByLabel("Re-read from Redis").click();

        // A real redis-server answered, rather than a connection factory bean merely existing.
        assertThat(probeOutput(Pattern.compile("redis server\\s+: reachable, version 5\\."))).isVisible();

        // Nothing in the application asked for either of these. Putting the Redis module on the
        // classpath replaced both implementations underneath unchanged calling code.
        assertThat(probeOutput(Pattern.compile("rate limiter\\s+: RedisTenantRateLimiter"))).isVisible();
        assertThat(probeOutput(Pattern.compile("tenant details cache\\s+: RedisCache.*shared, in Redis")))
                .isVisible();

        // The counter read straight back out of Redis under a key naming this workspace — the
        // evidence that a second application instance would be counting against the same total.
        assertThat(probeOutput(Pattern.compile("holon:ratelimit:" + WORKSPACE + ":sales\\.records")))
                .isVisible();
        // Let the "Recorded:" toast expire before capturing — it is bottom-left, which is
        // exactly where the Redis key line renders, and it obscures the one detail this
        // screenshot exists to show.
        page.getByText("Recorded:").waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN));
        capture("52-redis-shared-state");
    }

    @Test
    @Order(28)
    @DisplayName("F24 - only a bounded number of connection pools stay open, and the busy one survives")
    void connectionPoolsAreBoundedAndEvictTheIdleOne() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Internals")).click();
        page.waitForURL("**/internals");
        page.getByLabel("Re-read the registry").click();

        // The cap this demo runs under. Production leaves it at 200; three is what makes the
        // behaviour reachable by clicking.
        assertThat(probeOutput(Pattern.compile("pool ceiling\\s+: 3"))).isVisible();
        probeOutput(Pattern.compile("pool ceiling")).scrollIntoViewIfNeeded();
        capture("53-pools-before");

        // Use every workspace in turn. There are more workspaces than slots once the bootstrap
        // pool is counted, so this necessarily forces an eviction.
        page.getByLabel("Use every workspace in turn").click();
        assertThat(probeOutput(Pattern.compile("connected to schema"))).isVisible();

        // The cap is the point: however many workspaces get used, the pool count cannot pass it.
        assertThat(probeOutput(Pattern.compile("pools open right now\\s+: [0-3]\\b"))).isVisible();

        // Now use one workspace again, making it the most recently used. Under eviction by
        // creation order this is precisely the pool that would be closed next — the busiest
        // tenant is usually the oldest — which is the failure this policy exists to avoid.
        selectOption("Workspace to use", WORKSPACE);
        page.getByLabel("Use that workspace").click();

        assertThat(probeOutput(Pattern.compile("workspaces holding a pool\\s+:.*" + WORKSPACE))).isVisible();

        // The metrics gauge reports the same number the registry does, so an operator watching
        // dashboards sees the real pool count rather than one that silently stopped updating.
        assertThat(probeOutput(Pattern.compile("pools\\.active gauge\\s+: [0-3]\\b"))).isVisible();
        // The app layout scrolls internally, so a full-page screenshot stops at the viewport
        // rather than growing to fit — the output block has to be brought into view itself.
        probeOutput(Pattern.compile("pools\\.active gauge")).scrollIntoViewIfNeeded();
        capture("54-pools-after-eviction");
    }

    @Test
    @Order(29)
    @DisplayName("F26 - a failed onboarding announces itself and does not burn the workspace name")
    void onboardingFailureIsAnnouncedAndRetryable() {
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Internals")).click();
        page.waitForURL("**/internals");

        page.getByLabel("Force an onboarding failure, then retry").scrollIntoViewIfNeeded();
        page.getByLabel("Force an onboarding failure, then retry").click();

        // Step 2: the rig has to actually break onboarding. If it did not, everything after it
        // would pass for the wrong reason, so this is asserted before anything else.
        // This probe does two real onboardings, each creating a schema and running Flyway, so
        // it takes appreciably longer than the default assertion timeout allows for.
        assertThat(probeOutput(Pattern.compile("2\\. onboarding failed as intended")))
                .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(60_000));

        // Step 3: the point of the whole feature. The name the customer typed must be free
        // again, because the failure was the platform's fault and not theirs.
        assertThat(probeOutput(Pattern.compile("3\\. is the workspace name still taken\\? no"))).isVisible();

        // Step 5: "free" has to mean usable, not merely absent from a lookup.
        assertThat(probeOutput(Pattern.compile("5\\. retried the same workspace name: succeeded"))).isVisible();

        // And onboarding told the rest of the application about both outcomes, which is how
        // billing, provisioning and mail hear about a new workspace without onboarding
        // knowing they exist.
        assertThat(gridCell("FAILED").first()).isVisible();
        assertThat(gridCell("COMPLETED").first()).isVisible();

        probeOutput(Pattern.compile("workspace used for this run")).scrollIntoViewIfNeeded();
        capture("55-onboarding-failure-then-retry");
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * The output block of one of the Internals sections, matched by its content. The sections
     * render into {@code <pre>} elements, which carry no accessible name of their own, so the
     * text is the only thing there is to address them by.
     */
    private static Locator probeOutput(Pattern content) {
        return page.getByText(content).first();
    }

    /**
     * A Vaadin grid cell addressed by its accessible name.
     *
     * <p>Rows cannot be used for this. Vaadin renders the {@code <tr role="row">} elements in
     * the grid's shadow DOM while the cell text stays in the light DOM, slotted in — so
     * {@code getByRole(ROW).filter(hasText(...))} matches nothing, because {@code textContent}
     * does not flatten slots. The accessible name of a {@code gridcell} is computed from the
     * flattened tree and therefore does see the text.
     */
    private static Locator gridCell(String text) {
        return page.getByRole(AriaRole.GRIDCELL,
                new Page.GetByRoleOptions().setName(text).setExact(true));
    }

    /**
     * Picks an option from a Vaadin {@code Select} by its accessible name, keeping the
     * interaction at the accessibility layer rather than reaching into the shadow DOM.
     */
    private static void selectOption(String label, String option) {
        page.getByLabel(label).click();
        page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(option).setExact(true))
                .click();
    }

    private static void signIn(String email) {
        signIn(WORKSPACE, email);
    }

    private static void signIn(String workspace, String email) {
        attemptSignIn(workspace, email);

        try {
            page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Signed in as " + email))
                    .waitFor();
        } catch (RuntimeException failure) {
            // A screenshot of whatever the page settled on makes a failed sign-in far quicker
            // to diagnose than a bare timeout.
            capture("failed-signin-" + email.substring(0, email.indexOf('@')));
            throw failure;
        }
    }

    /**
     * Submits the sign-in form without asserting that it worked — the suspension step needs a
     * sign-in that is expected to be refused.
     */
    private static void attemptSignIn(String workspace, String email) {
        page.navigate(BASE_URL + "/login");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Sign in")).waitFor();

        page.getByLabel("Workspace").fill(workspace);
        page.getByLabel("Email").fill(email);
        // Addressed by role rather than by label: a Vaadin password field also exposes a
        // "Show password" reveal button, which a label lookup for "Password" would match too.
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password").setExact(true))
                .fill(PASSWORD);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
    }

    /**
     * Signs in the platform operator, who belongs to no tenant at all. That is precisely what
     * lets the console query across workspaces — so it lands on the admin console rather than
     * on a tenant dashboard.
     */
    private static void signInAsOperator() {
        page.navigate(BASE_URL + "/login");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Sign in")).waitFor();

        page.getByLabel("Workspace").fill("platform");
        page.getByLabel("Email").fill("admin@holon.test");
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Password").setExact(true))
                .fill("platform-admin");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();

        page.waitForURL("**/admin/tenants");
    }

    /** Creates a workspace through the sign-up form, exactly as the first step does. */
    private static void signUp(String workspace, String companyName, String email) {
        page.navigate(BASE_URL + "/signup");
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Create your workspace"))
                .waitFor();

        page.getByLabel("Company name").fill(companyName);
        page.getByLabel("Your email").fill(email);
        page.getByLabel("Choose a password").fill(PASSWORD);

        // The suggested workspace id arrives on a server round trip; waiting for it before
        // typing the real one avoids the suggestion landing last and overwriting it.
        Locator workspaceField = page.getByLabel("Workspace id");
        assertThat(workspaceField).hasValue(Pattern.compile(".+"));
        workspaceField.fill(workspace);
        assertThat(workspaceField).hasValue(workspace);

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Create workspace")).click();
        page.waitForURL("**/login");
    }

    private static void signOutIfSignedIn() {
        Locator signOut = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign out"));
        if (signOut.count() > 0 && signOut.isVisible()) {
            signOut.click();
            page.waitForURL("**/login**");
        }
    }

    private static void invite(String email, String jobRole) {
        page.getByLabel("Email to invite").fill(email);

        // The job role is a Vaadin Select: opening it and picking the option by its accessible
        // name keeps the interaction at the accessibility layer rather than reaching into the
        // shadow DOM.
        selectOption("Job role", jobRole);

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send invitation")).click();

        // An invitation may legitimately be refused — the quota step depends on exactly that —
        // so wait for whichever outcome the server reports rather than assuming success.
        page.getByText(Pattern.compile("Invitation created for|Quota reached|Could not invite"))
                .first().waitFor();
    }

    /** Reads the invitation link the Team view renders in place of sending an email. */
    private static String inviteLinkFor(String email) {
        Locator row = page.locator("vaadin-horizontal-layout")
                .filter(new Locator.FilterOptions().setHasText(email));

        return row.getByRole(AriaRole.LINK).first().textContent().trim();
    }

    private static void acceptInvitation(String relativeLink) {
        page.navigate(BASE_URL + relativeLink);

        Locator join = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Join workspace"));
        join.waitFor();

        page.getByLabel("Choose a password").fill(PASSWORD);
        join.click();

        page.waitForURL("**/login");
    }

    /**
     * Writes a full-page screenshot of the current state. These are a record of the run for a
     * human to look at, deliberately separate from the committed visual baselines.
     */
    private static void capture(String name) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(SCREENSHOT_DIR.resolve(name + ".png"))
                .setFullPage(true));
    }

}
