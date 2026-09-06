package com.holonplatform.multitenant.quotas;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.quotas.internal.DefaultTenantQuotaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DefaultTenantQuotaService}.
 */
class DefaultTenantQuotaServiceTest {

    private static final String TENANT = "acme";

    /** Tracks usage for MAX_CONTACTS quota */
    private long contactCount = 0;

    private DefaultTenantQuotaService service;

    @BeforeEach
    void setUp() {
        // Register a usage provider that reads our contactCount field
        QuotaUsageProvider contactProvider = new QuotaUsageProvider() {
            @Override public boolean supports(QuotaKey key) {
                return QuotaKeys.MAX_CONTACTS.key().equals(key.key());
            }
            @Override public long getCurrentUsage(String tenantId, QuotaKey key) {
                return contactCount;
            }
        };

        service = new DefaultTenantQuotaService(List.of(contactProvider));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        contactCount = 0;
    }

    // ── registerQuota ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("registerQuota stores definition and it appears in listDefinitions")
    void registerQuota_storesDefinition() {
        QuotaDefinition def = QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
                .limit(TenantPlan.FREE, 100L)
                .build();
        service.registerQuota(def);

        List<QuotaDefinition> defs = service.listDefinitions();
        assertThat(defs).hasSize(1);
        assertThat(defs.get(0).key()).isEqualTo(QuotaKeys.MAX_CONTACTS);
    }

    @Test
    @DisplayName("registerQuota replaces existing definition for same key")
    void registerQuota_replacesExisting() {
        service.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
                .limit(TenantPlan.FREE, 100L).build());
        service.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
                .limit(TenantPlan.FREE, 200L).build());

        assertThat(service.listDefinitions()).hasSize(1);
        assertThat(service.getLimit(TENANT, QuotaKeys.MAX_CONTACTS)).isEqualTo(-1L); // no tenant context
    }

    @Test
    @DisplayName("registerQuota throws NPE on null definition")
    void registerQuota_nullDef_throws() {
        assertThatNullPointerException().isThrownBy(() -> service.registerQuota(null));
    }

    // ── checkQuota ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("checkQuota returns unlimited result when no definition registered")
    void checkQuota_noDefinition_returnsUnlimited() {
        setTenantContext(TenantPlan.FREE);
        QuotaCheckResult result = service.checkQuota(TENANT, QuotaKeys.MAX_CONTACTS);
        assertThat(result.unlimited()).isTrue();
        assertThat(result.exceeded()).isFalse();
    }

    @Test
    @DisplayName("checkQuota with usage below limit: not exceeded")
    void checkQuota_belowLimit_notExceeded() {
        registerContactQuota();
        setTenantContext(TenantPlan.FREE); // FREE = 500
        contactCount = 499;

        QuotaCheckResult result = service.checkQuota(TENANT, QuotaKeys.MAX_CONTACTS);
        assertThat(result.exceeded()).isFalse();
        assertThat(result.currentUsage()).isEqualTo(499);
        assertThat(result.limit()).isEqualTo(500);
        assertThat(result.remaining()).isEqualTo(1);
        assertThat(result.percentUsed()).isEqualTo(99);
    }

    @Test
    @DisplayName("checkQuota with usage at limit: exceeded")
    void checkQuota_atLimit_exceeded() {
        registerContactQuota();
        setTenantContext(TenantPlan.FREE);
        contactCount = 500;

        QuotaCheckResult result = service.checkQuota(TENANT, QuotaKeys.MAX_CONTACTS);
        assertThat(result.exceeded()).isTrue();
        assertThat(result.remaining()).isZero();
        assertThat(result.percentUsed()).isEqualTo(100);
    }

    @Test
    @DisplayName("checkQuota with usage above limit: exceeded")
    void checkQuota_aboveLimit_exceeded() {
        registerContactQuota();
        setTenantContext(TenantPlan.FREE);
        contactCount = 600;

        QuotaCheckResult result = service.checkQuota(TENANT, QuotaKeys.MAX_CONTACTS);
        assertThat(result.exceeded()).isTrue();
        assertThat(result.remaining()).isZero();
    }

    @Test
    @DisplayName("checkQuota returns unlimited result when plan limit is -1")
    void checkQuota_unlimitedPlan_returnsUnlimited() {
        registerContactQuota();
        setTenantContext(TenantPlan.ENTERPRISE); // -1 = unlimited
        contactCount = 999_999;

        QuotaCheckResult result = service.checkQuota(TENANT, QuotaKeys.MAX_CONTACTS);
        assertThat(result.unlimited()).isTrue();
        assertThat(result.exceeded()).isFalse();
        assertThat(result.remaining()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("checkQuota respects different plan limits")
    void checkQuota_respectsPlanLimits() {
        registerContactQuota();
        contactCount = 600;

        // FREE plan: limit 500 → exceeded
        setTenantContext(TenantPlan.FREE);
        assertThat(service.checkQuota(TENANT, QuotaKeys.MAX_CONTACTS).exceeded()).isTrue();

        // STARTER plan: limit 5000 → not exceeded
        setTenantContext(TenantPlan.STARTER);
        assertThat(service.checkQuota(TENANT, QuotaKeys.MAX_CONTACTS).exceeded()).isFalse();
    }

    @Test
    @DisplayName("checkQuota assumes usage=0 when no usage provider registered for key")
    void checkQuota_noUsageProvider_assumesZero() {
        service.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_ACTIVE_DEALS)
                .limit(TenantPlan.FREE, 10L).build());
        setTenantContext(TenantPlan.FREE);

        // No provider for MAX_ACTIVE_DEALS → usage = 0 → not exceeded
        QuotaCheckResult result = service.checkQuota(TENANT, QuotaKeys.MAX_ACTIVE_DEALS);
        assertThat(result.exceeded()).isFalse();
        assertThat(result.currentUsage()).isZero();
        assertThat(result.remaining()).isEqualTo(10);
    }

    @Test
    @DisplayName("checkQuota returns unlimited when tenant not in TenantContext")
    void checkQuota_noTenantContext_returnsUnlimited() {
        registerContactQuota();
        // No TenantContext.set()

        QuotaCheckResult result = service.checkQuota(TENANT, QuotaKeys.MAX_CONTACTS);
        assertThat(result.unlimited()).isTrue();
    }

    // ── checkQuota by string key ──────────────────────────────────────────────

    @Test
    @DisplayName("checkQuota(tenantId, String) resolves correctly")
    void checkQuota_byStringKey_works() {
        registerContactQuota();
        setTenantContext(TenantPlan.FREE);
        contactCount = 600;

        QuotaCheckResult result = service.checkQuota(TENANT, "max_contacts");
        assertThat(result.exceeded()).isTrue();
    }

    @Test
    @DisplayName("checkQuota(tenantId, unknown string) returns unlimited")
    void checkQuota_unknownStringKey_returnsUnlimited() {
        setTenantContext(TenantPlan.FREE);
        QuotaCheckResult result = service.checkQuota(TENANT, "completely_unknown_key");
        assertThat(result.unlimited()).isTrue();
    }

    // ── getLimit / getRemaining ───────────────────────────────────────────────

    @Test
    @DisplayName("getLimit returns the plan-specific limit")
    void getLimit_returnsPlanLimit() {
        registerContactQuota();
        setTenantContext(TenantPlan.STARTER);
        assertThat(service.getLimit(TENANT, QuotaKeys.MAX_CONTACTS)).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("getLimit returns -1 when no definition or no tenant context")
    void getLimit_noDefinition_returnsMinusOne() {
        assertThat(service.getLimit(TENANT, QuotaKeys.MAX_CONTACTS)).isEqualTo(-1L);
    }

    @Test
    @DisplayName("getRemaining returns MAX_VALUE for unlimited quota")
    void getRemaining_unlimited_returnsMaxValue() {
        registerContactQuota();
        setTenantContext(TenantPlan.ENTERPRISE);
        assertThat(service.getRemaining(TENANT, QuotaKeys.MAX_CONTACTS)).isEqualTo(Long.MAX_VALUE);
    }

    // ── getAllQuotas ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllQuotas returns results for all registered keys")
    void getAllQuotas_returnsAllKeys() {
        registerContactQuota();
        service.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_USERS)
                .limit(TenantPlan.FREE, 3L).build());
        setTenantContext(TenantPlan.FREE);

        Map<String, QuotaCheckResult> all = service.getAllQuotas(TENANT);
        assertThat(all).containsKeys("max_contacts", "max_users");
    }

    // ── QuotaDefinition builder ───────────────────────────────────────────────

    @Test
    @DisplayName("QuotaDefinition.allPlans sets the same limit for every plan")
    void quotaDefinition_allPlans() {
        QuotaDefinition def = QuotaDefinition.builder(QuotaKeys.MAX_API_KEYS)
                .allPlans(5L)
                .build();
        for (TenantPlan plan : TenantPlan.values()) {
            assertThat(def.getLimit(plan)).isEqualTo(5L);
        }
        assertThat(def.isUnlimited(TenantPlan.FREE)).isFalse();
    }

    @Test
    @DisplayName("QuotaDefinition.isUnlimited returns true for -1 limit")
    void quotaDefinition_isUnlimited() {
        QuotaDefinition def = QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
                .limit(TenantPlan.ENTERPRISE, -1L)
                .build();
        assertThat(def.isUnlimited(TenantPlan.ENTERPRISE)).isTrue();
        assertThat(def.isUnlimited(TenantPlan.FREE)).isTrue(); // default -1 for unset plans
    }

    // ── QuotaCheckResult helpers ──────────────────────────────────────────────

    @Test
    @DisplayName("QuotaCheckResult.percentUsed calculates percentage correctly")
    void quotaCheckResult_percentUsed() {
        QuotaCheckResult result = QuotaCheckResult.of(QuotaKeys.MAX_CONTACTS, 1000, 250);
        assertThat(result.percentUsed()).isEqualTo(25);
    }

    @Test
    @DisplayName("QuotaCheckResult.percentUsed returns 0 for unlimited quota")
    void quotaCheckResult_percentUsed_unlimited() {
        QuotaCheckResult result = QuotaCheckResult.unlimited(QuotaKeys.MAX_CONTACTS, 999);
        assertThat(result.percentUsed()).isZero();
    }

    @Test
    @DisplayName("QuotaCheckResult.remaining never goes negative when exceeded")
    void quotaCheckResult_remainingNeverNegative() {
        QuotaCheckResult result = QuotaCheckResult.of(QuotaKeys.MAX_CONTACTS, 500, 600);
        assertThat(result.remaining()).isZero();
        assertThat(result.exceeded()).isTrue();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void setTenantContext(TenantPlan plan) {
        TenantContext.set(TenantDetails.builder(TENANT)
                .plan(plan).status(TenantStatus.ACTIVE).build());
    }

    private void registerContactQuota() {
        service.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
                .limit(TenantPlan.FREE,         500L)
                .limit(TenantPlan.STARTER,      5_000L)
                .limit(TenantPlan.PROFESSIONAL, 50_000L)
                .limit(TenantPlan.ENTERPRISE,   -1L)
                .build());
    }
}

