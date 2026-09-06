package com.holonplatform.multitenant.quotas.config;

import com.holonplatform.multitenant.quotas.TenantQuotaService;
import com.holonplatform.multitenant.quotas.internal.DefaultTenantQuotaService;
import com.holonplatform.multitenant.quotas.internal.QuotaAspect;
import com.holonplatform.multitenant.quotas.internal.UserCountQuotaProvider;
import com.holonplatform.multitenant.users.TenantUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link TenantQuotaAutoConfiguration} registers expected beans.
 */
class TenantQuotaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantQuotaAutoConfiguration.class))
            .withBean(TenantUserService.class, () -> mock(TenantUserService.class));

    @Test
    @DisplayName("auto-config registers TenantQuotaService bean")
    void registersQuotaService() {
        contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(TenantQuotaService.class));
    }

    @Test
    @DisplayName("auto-config registers DefaultTenantQuotaService by default")
    void registersDefaultImplementation() {
        contextRunner.run(ctx -> {
            TenantQuotaService svc = ctx.getBean(TenantQuotaService.class);
            assertThat(svc).isInstanceOf(DefaultTenantQuotaService.class);
        });
    }

    @Test
    @DisplayName("auto-config registers UserCountQuotaProvider for built-in max_users quota")
    void registersUserCountProvider() {
        contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(UserCountQuotaProvider.class));
    }

    @Test
    @DisplayName("auto-config registers QuotaAspect bean")
    void registersQuotaAspect() {
        contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(QuotaAspect.class));
    }
}

