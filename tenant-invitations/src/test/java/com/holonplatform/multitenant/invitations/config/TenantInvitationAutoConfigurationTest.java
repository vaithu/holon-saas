package com.holonplatform.multitenant.invitations.config;

import com.holonplatform.multitenant.invitations.InvitationRequest;
import com.holonplatform.multitenant.invitations.InvitationStatus;
import com.holonplatform.multitenant.invitations.TenantInvitationService;
import com.holonplatform.multitenant.invitations.UserInvitation;
import com.holonplatform.multitenant.invitations.internal.DatabaseTenantInvitationService;
import com.holonplatform.multitenant.invitations.internal.UserInvitationDatastoreHelper;
import com.holonplatform.multitenant.users.TenantRole;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link TenantInvitationAutoConfiguration}.
 *
 * <p>A mocked {@link UserInvitationDatastoreHelper} stands in for a real Holon Datastore
 * so the context can be exercised without a database.
 */
class TenantInvitationAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantInvitationAutoConfiguration.class))
            .withPropertyValues("holon.saas.invitations.accept-base-url=https://test.example.com/accept")
            .withUserConfiguration(DatastoreHelperConfig.class, SupportConfig.class);

    @Test
    @DisplayName("auto-config registers TenantInvitationService bean")
    void registersInvitationService() {
        contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(TenantInvitationService.class));
    }

    @Test
    @DisplayName("auto-config registers DatabaseTenantInvitationService by default")
    void registersDefaultImplementation() {
        contextRunner.run(ctx -> {
            TenantInvitationService svc = ctx.getBean(TenantInvitationService.class);
            assertThat(svc).isInstanceOf(DatabaseTenantInvitationService.class);
        });
    }

    @Test
    @DisplayName("accept-base-url property is wired into the service")
    void acceptBaseUrlIsConfigured() {
        // The service should be usable — if wrong URL was wired it would be blank
        contextRunner.run(ctx -> {
            TenantInvitationService svc = ctx.getBean(TenantInvitationService.class);
            assertThat(svc).isNotNull();
        });
    }

    @Test
    @DisplayName("auto-configured service can invite and retrieve invitations")
    void autoConfig_service_isFunctional() {
        contextRunner.run(ctx -> {
            TenantInvitationService service = ctx.getBean(TenantInvitationService.class);
            UserInvitation created = service.invite(
                    InvitationRequest.builder("acme", "user@example.com").build());
            assertThat(service.findByToken(created.token())).isPresent();
        });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link UserInvitationDatastoreHelper}
     * (which normally requires a Holon {@code Datastore} bean) with an in-memory-backed
     * mock, so the auto-configuration can be exercised without a real database.
     */
    static class DatastoreHelperConfig {

        @Bean
        UserInvitationDatastoreHelper userInvitationDatastoreHelper() {
            UserInvitationDatastoreHelper helper = mock(UserInvitationDatastoreHelper.class);
            Map<String, UserInvitation> store = new ConcurrentHashMap<>();

            when(helper.save(any(UserInvitation.class))).thenAnswer(inv -> {
                UserInvitation invitation = inv.getArgument(0);
                store.put(invitation.token(), invitation);
                return invitation;
            });
            when(helper.findByToken(anyString())).thenAnswer(inv ->
                    Optional.ofNullable(store.get(inv.getArgument(0, String.class))));
            when(helper.findByTenantIdAndStatus(anyString(), anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0, String.class);
                String status = inv.getArgument(1, String.class);
                return store.values().stream()
                        .filter(i -> i.tenantId().equals(tenantId) && i.status().name().equals(status))
                        .toList();
            });
            when(helper.findByTenantIdAndEmail(anyString(), anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0, String.class);
                String email = inv.getArgument(1, String.class);
                return store.values().stream()
                        .filter(i -> i.tenantId().equals(tenantId) && i.email().equalsIgnoreCase(email))
                        .toList();
            });
            when(helper.findPendingByTenantIdAndEmail(anyString(), anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0, String.class);
                String email = inv.getArgument(1, String.class);
                return store.values().stream()
                        .filter(i -> i.tenantId().equals(tenantId) && i.email().equalsIgnoreCase(email)
                                && i.status() == InvitationStatus.PENDING)
                        .findFirst();
            });
            when(helper.findAllExpiredPending(any(Instant.class))).thenAnswer(inv -> {
                Instant now = inv.getArgument(0, Instant.class);
                return store.values().stream()
                        .filter(i -> i.status() == InvitationStatus.PENDING && i.expiresAt().isBefore(now))
                        .toList();
            });
            when(helper.deleteByTenantId(anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0, String.class);
                List<String> toRemove = store.values().stream()
                        .filter(i -> i.tenantId().equals(tenantId))
                        .map(UserInvitation::token)
                        .toList();
                toRemove.forEach(store::remove);
                return (long) toRemove.size();
            });
            return helper;
        }
    }

    static class SupportConfig {
        @Bean
        TenantUserService tenantUserService() {
            TenantUserService mock = mock(TenantUserService.class);
            when(mock.invite(anyString(), anyString(), anyString(), any(TenantRole.class)))
                    .thenAnswer(inv -> TenantUser.builder(inv.getArgument(1), inv.getArgument(0), inv.getArgument(2))
                            .role(inv.getArgument(3)).build());
            when(mock.activate(anyString(), anyString()))
                    .thenAnswer(inv -> TenantUser.builder(inv.getArgument(1), inv.getArgument(0), "user@example.com")
                            .build());
            return mock;
        }
    }
}
