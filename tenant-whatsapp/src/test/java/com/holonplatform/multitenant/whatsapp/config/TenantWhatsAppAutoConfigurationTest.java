package com.holonplatform.multitenant.whatsapp.config;

import com.holonplatform.multitenant.whatsapp.TenantWhatsAppService;
import com.holonplatform.multitenant.whatsapp.WhatsAppConversation;
import com.holonplatform.multitenant.whatsapp.WhatsAppMessage;
import com.holonplatform.multitenant.whatsapp.internal.DatabaseTenantWhatsAppService;
import com.holonplatform.multitenant.whatsapp.internal.WhatsAppConversationDatastoreHelper;
import com.holonplatform.multitenant.whatsapp.internal.WhatsAppMessageDatastoreHelper;
import com.holonplatform.multitenant.whatsapp.service.ConversationWindowTracker;
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
 * Integration tests for {@link TenantWhatsAppAutoConfiguration}.
 *
 * <p>Mocked {@link WhatsAppMessageDatastoreHelper} and {@link WhatsAppConversationDatastoreHelper}
 * beans stand in for a real Holon Datastore so the context can be exercised without a database.
 */
class TenantWhatsAppAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TenantWhatsAppAutoConfiguration.class))
            .withUserConfiguration(DatastoreHelperConfig.class);

    // ── Default bean registration ─────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers DatabaseTenantWhatsAppService when no custom bean present")
    void autoConfig_registersDefaultService() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(TenantWhatsAppService.class);
            assertThat(ctx.getBean(TenantWhatsAppService.class))
                    .isInstanceOf(DatabaseTenantWhatsAppService.class);
        });
    }

    @Test
    @DisplayName("auto-config registers a Datastore-backed ConversationWindowTracker")
    void autoConfig_registersConversationWindowTracker() {
        contextRunner.run(ctx -> assertThat(ctx).hasSingleBean(ConversationWindowTracker.class));
    }

    // ── Back-off when custom bean present ─────────────────────────────────────

    @Test
    @DisplayName("auto-config backs off when a custom TenantWhatsAppService bean is declared")
    void autoConfig_backsOff_whenCustomServicePresent() {
        contextRunner
                .withUserConfiguration(CustomWhatsAppServiceConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TenantWhatsAppService.class);
                    assertThat(ctx.getBean(TenantWhatsAppService.class))
                            .isInstanceOf(CustomWhatsAppServiceConfig.CustomWhatsAppService.class);
                });
    }

    // ── Only one bean registered ──────────────────────────────────────────────

    @Test
    @DisplayName("auto-config registers exactly one TenantWhatsAppService bean")
    void autoConfig_registersSingleBean() {
        contextRunner.run(ctx ->
                assertThat(ctx.getBeansOfType(TenantWhatsAppService.class)).hasSize(1));
    }

    // ── Registered service is functional ─────────────────────────────────────

    @Test
    @DisplayName("auto-configured service can send a text message and persist it")
    void autoConfig_service_isFunctional() {
        contextRunner.run(ctx -> {
            TenantWhatsAppService service = ctx.getBean(TenantWhatsAppService.class);
            WhatsAppMessage sent = service.sendTextMessage("acme", "+15551234567", "hello there");
            assertThat(sent).isNotNull();
            assertThat(sent.getTenantId()).isEqualTo("acme");
            assertThat(service.listMessages("acme", 0, 10)).contains(sent);
            assertThat(service.countMessages("acme")).isEqualTo(1L);
        });
    }

    // ── Support ───────────────────────────────────────────────────────────────

    /**
     * Stands in for the real {@code @Component}-scanned {@link WhatsAppMessageDatastoreHelper}
     * and {@link WhatsAppConversationDatastoreHelper} (which normally require a Holon
     * {@code Datastore} bean) with in-memory-backed mocks, so the auto-configuration can be
     * exercised without a real database.
     */
    static class DatastoreHelperConfig {

        @Bean
        WhatsAppMessageDatastoreHelper whatsAppMessageDatastoreHelper() {
            WhatsAppMessageDatastoreHelper helper = mock(WhatsAppMessageDatastoreHelper.class);
            Map<String, WhatsAppMessage> store = new ConcurrentHashMap<>();

            when(helper.save(any(WhatsAppMessage.class))).thenAnswer(inv -> {
                WhatsAppMessage message = inv.getArgument(0);
                store.put(message.getLocalId(), message);
                return message;
            });
            when(helper.findByLocalId(anyString())).thenAnswer(inv ->
                    Optional.ofNullable(store.get((String) inv.getArgument(0))));
            when(helper.findByTenantId(anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0);
                return store.values().stream()
                        .filter(m -> tenantId.equals(m.getTenantId()))
                        .toList();
            });
            when(helper.countByTenantId(anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0);
                return store.values().stream().filter(m -> tenantId.equals(m.getTenantId())).count();
            });
            when(helper.findByTenantIdAndCreatedAtBetween(anyString(), any(Instant.class), any(Instant.class)))
                    .thenAnswer(inv -> {
                        String tenantId = inv.getArgument(0);
                        return store.values().stream()
                                .filter(m -> tenantId.equals(m.getTenantId()))
                                .toList();
                    });
            return helper;
        }

        @Bean
        WhatsAppConversationDatastoreHelper whatsAppConversationDatastoreHelper() {
            WhatsAppConversationDatastoreHelper helper = mock(WhatsAppConversationDatastoreHelper.class);
            Map<String, WhatsAppConversation> store = new ConcurrentHashMap<>();

            when(helper.save(any(WhatsAppConversation.class))).thenAnswer(inv -> {
                WhatsAppConversation conversation = inv.getArgument(0);
                store.put(conversation.getWindowKey(), conversation);
                return conversation;
            });
            when(helper.findByTenantIdAndCustomerPhone(anyString(), anyString())).thenAnswer(inv -> {
                String tenantId = inv.getArgument(0);
                String phone = inv.getArgument(1);
                return store.values().stream()
                        .filter(c -> tenantId.equals(c.getTenantId()) && phone.equals(c.getCustomerPhone()))
                        .findFirst();
            });
            when(helper.countOpenWindows(any(Instant.class))).thenReturn(0L);
            return helper;
        }
    }

    static class CustomWhatsAppServiceConfig {

        @Bean
        TenantWhatsAppService tenantWhatsAppService() {
            return new CustomWhatsAppService();
        }

        static class CustomWhatsAppService implements TenantWhatsAppService {

            @Override
            public WhatsAppMessage sendTextMessage(String tenantId, String to, String text) {
                return null;
            }

            @Override
            public WhatsAppMessage sendTemplateMessage(String tenantId, String to,
                    String templateName, String language, Map<String, String> params) {
                return null;
            }

            @Override
            public WhatsAppMessage sendMediaMessage(String tenantId, String to,
                    com.holonplatform.multitenant.whatsapp.WhatsAppMessageType mediaType,
                    String mediaUrl, String caption) {
                return null;
            }

            @Override
            public com.holonplatform.multitenant.whatsapp.WhatsAppBusinessSendResult sendBusinessMessage(
                    String tenantId, com.holonplatform.multitenant.whatsapp.WhatsAppBusinessMessage message) {
                return null;
            }

            @Override
            public boolean hasOpenServiceWindow(String tenantId, String customerPhone) {
                return false;
            }

            @Override
            public Optional<WhatsAppConversation> getConversationWindow(String tenantId, String customerPhone) {
                return Optional.empty();
            }

            @Override
            public Optional<WhatsAppMessage> getMessageStatus(String tenantId, String localId) {
                return Optional.empty();
            }

            @Override
            public List<WhatsAppMessage> listMessages(String tenantId, int offset, int limit) {
                return List.of();
            }

            @Override
            public long countMessages(String tenantId) {
                return 0;
            }

            @Override
            public List<WhatsAppMessage> listMessagesByPhone(String tenantId, String customerPhone) {
                return List.of();
            }

            @Override
            public List<com.holonplatform.multitenant.whatsapp.WhatsAppTemplate> listTemplates(String tenantId) {
                return List.of();
            }

            @Override
            public com.holonplatform.multitenant.whatsapp.WhatsAppUsageStats getUsageStats(
                    String tenantId, java.time.YearMonth yearMonth) {
                return null;
            }

            @Override
            public void processWebhookPayload(String tenantId, String payload) {
            }
        }
    }
}
