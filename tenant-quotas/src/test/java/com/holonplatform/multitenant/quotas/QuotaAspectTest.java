package com.holonplatform.multitenant.quotas;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.TenantPlan;
import com.holonplatform.multitenant.TenantStatus;
import com.holonplatform.multitenant.quotas.annotation.EnforceQuota;
import com.holonplatform.multitenant.quotas.exception.QuotaExceededException;
import com.holonplatform.multitenant.quotas.internal.DefaultTenantQuotaService;
import com.holonplatform.multitenant.quotas.internal.QuotaAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests verifying {@link QuotaAspect} enforces {@link EnforceQuota}
 * annotations correctly with a minimal Spring context.
 */
@SpringBootTest(classes = QuotaAspectTest.TestConfig.class)
class QuotaAspectTest {

    static long contactCount = 0;

    @Autowired DefaultTenantQuotaService quotaService;
    @Autowired ContactService            contactService;

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        quotaService.invalidateUsageCache("acme", QuotaKeys.MAX_CONTACTS);
        quotaService.invalidateUsageCache("acme", QuotaKeys.MAX_ACTIVE_DEALS);
        contactCount = 0;
    }

    // ── allows ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("@EnforceQuota allows call when usage is below plan limit")
    void allowsCallWhenBelowLimit() {
        setTenant(TenantPlan.FREE); // FREE = 500
        contactCount = 499;

        assertThatCode(() -> contactService.createContact("Alice"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("@EnforceQuota allows call when plan is unlimited (-1)")
    void allowsCallForUnlimitedPlan() {
        setTenant(TenantPlan.ENTERPRISE); // ENTERPRISE = unlimited
        contactCount = 1_000_000;

        assertThatCode(() -> contactService.createContact("Bob"))
                .doesNotThrowAnyException();
    }

    // ── blocks ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("@EnforceQuota throws QuotaExceededException when limit is reached")
    void throwsWhenLimitReached() {
        setTenant(TenantPlan.FREE);
        contactCount = 500; // at limit

        assertThatExceptionOfType(QuotaExceededException.class)
                .isThrownBy(() -> contactService.createContact("Charlie"))
                .satisfies(ex -> {
                    assertThat(ex.getLimit()).isEqualTo(500L);
                    assertThat(ex.getCurrentUsage()).isEqualTo(500L);
                    assertThat(ex.getQuotaKey().key()).isEqualTo("max_contacts");
                });
    }

    @Test
    @DisplayName("@EnforceQuota custom message is included in exception")
    void customMessageIncludedInException() {
        setTenant(TenantPlan.FREE);
        contactCount = 500;

        assertThatExceptionOfType(QuotaExceededException.class)
                .isThrownBy(() -> contactService.createContact("Dave"))
                .withMessageContaining("Contact limit reached");
    }

    // ── bypass ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("@EnforceQuota is bypassed when no tenant in context")
    void bypassedWithNoTenantContext() {
        // No TenantContext.set() — system/admin call, no enforcement
        contactCount = 999_999;

        assertThatCode(() -> contactService.createContact("System"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("method return value is preserved when quota is not exceeded")
    void methodReturnValuePreserved() {
        setTenant(TenantPlan.PROFESSIONAL);
        contactCount = 100;

        assertThat(contactService.getContactName()).isEqualTo("Contact#1");
    }

    @Test
    @DisplayName("business exception from method propagates through the aspect")
    void businessExceptionPropagatesThrough() {
        setTenant(TenantPlan.FREE);
        contactCount = 0;

        assertThatThrownBy(() -> contactService.throwingMethod())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad input");
    }

    // ── different quotas ──────────────────────────────────────────────────────

    @Test
    @DisplayName("@EnforceQuota enforces the correct quota key per method")
    void enforcesDifferentQuotaKeys() {
        setTenant(TenantPlan.FREE);
        contactCount = 500; // MAX_CONTACTS exceeded

        // createContact exceeds MAX_CONTACTS
        assertThatExceptionOfType(QuotaExceededException.class)
                .isThrownBy(() -> contactService.createContact("X"))
                .satisfies(ex -> assertThat(ex.getQuotaKey().key()).isEqualTo("max_contacts"));

        // openDeal checks MAX_ACTIVE_DEALS, provider returns 0 (no provider registered) → allowed
        assertThatCode(() -> contactService.openDeal("deal1"))
                .doesNotThrowAnyException();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static void setTenant(TenantPlan plan) {
        TenantContext.set(TenantDetails.builder("acme")
                .plan(plan).status(TenantStatus.ACTIVE).build());
    }

    // ── inner service under test ──────────────────────────────────────────────

    interface ContactService {
        void createContact(String name);
        String getContactName();
        void throwingMethod();
        void openDeal(String dealName);
    }

    static class ContactServiceImpl implements ContactService {

        @Override
        @EnforceQuota(value = "max_contacts", message = "Contact limit reached. Please upgrade.")
        public void createContact(String name) { /* no-op */ }

        @Override
        @EnforceQuota("max_contacts")
        public String getContactName() { return "Contact#1"; }

        @Override
        @EnforceQuota("max_contacts")
        public void throwingMethod() { throw new IllegalArgumentException("bad input"); }

        @Override
        @EnforceQuota("max_active_deals")
        public void openDeal(String dealName) { /* no-op */ }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfig {

        @Bean
        QuotaUsageProvider contactCountProvider() {
            return new QuotaUsageProvider() {
                @Override public boolean supports(QuotaKey key) {
                    return "max_contacts".equals(key.key());
                }
                @Override public long getCurrentUsage(String tenantId, QuotaKey key) {
                    return QuotaAspectTest.contactCount;
                }
            };
        }

        @Bean
        DefaultTenantQuotaService tenantQuotaService(List<QuotaUsageProvider> providers) {
            DefaultTenantQuotaService svc = new DefaultTenantQuotaService(providers);
            svc.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_CONTACTS)
                    .limit(TenantPlan.FREE,         500L)
                    .limit(TenantPlan.STARTER,      5_000L)
                    .limit(TenantPlan.PROFESSIONAL, 50_000L)
                    .limit(TenantPlan.ENTERPRISE,   -1L)
                    .build());
            // MAX_ACTIVE_DEALS: no usage provider → always 0
            svc.registerQuota(QuotaDefinition.builder(QuotaKeys.MAX_ACTIVE_DEALS)
                    .limit(TenantPlan.FREE, 50L)
                    .limit(TenantPlan.ENTERPRISE, -1L)
                    .build());
            return svc;
        }

        @Bean
        QuotaAspect quotaAspect(TenantQuotaService quotaService) {
            return new QuotaAspect(quotaService);
        }

        @Bean
        ContactService contactService() {
            return new ContactServiceImpl();
        }
    }
}

