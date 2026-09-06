package com.holonplatform.multitenant.whatsapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.multitenant.whatsapp.TenantWhatsAppService;
import com.holonplatform.multitenant.whatsapp.internal.DatabaseTenantWhatsAppService;
import com.holonplatform.multitenant.whatsapp.internal.WhatsAppConversationDatastoreHelper;
import com.holonplatform.multitenant.whatsapp.internal.WhatsAppMessageDatastoreHelper;
import com.holonplatform.multitenant.whatsapp.service.ConversationWindowTracker;
import com.holonplatform.multitenant.whatsapp.webhook.WhatsAppWebhookController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot auto-configuration for the WhatsApp integration module.
 *
 * <h3>What this configures</h3>
 * <ul>
 *   <li>{@link ConversationWindowTracker} – 24-hour conversation-window tracker, backed by
 *       {@link WhatsAppConversationDatastoreHelper} (Holon Datastore)</li>
 *   <li>{@link DatabaseTenantWhatsAppService} – default service, backed by
 *       {@link WhatsAppMessageDatastoreHelper} (Holon Datastore); replace with your own bean</li>
 *   <li>{@link WhatsAppWebhookController} – REST endpoint for Meta webhooks</li>
 * </ul>
 *
 * <p>A {@code Datastore} bean must be configured (e.g. via {@code holon-datastore-jpa}) for
 * this module to start: there is no in-memory fallback, matching the tenant-users module's
 * established pattern.</p>
 *
 * <h3>Replacing the default implementation</h3>
 * <pre>
 * {@code @Service}
 * public class MyWhatsAppService implements TenantWhatsAppService { ... }
 * </pre>
 * The auto-configuration uses {@code @ConditionalOnMissingBean} so your custom bean takes precedence.
 *
 * <p>This module is UI-agnostic: it exposes only the {@link TenantWhatsAppService} backend
 * API and the webhook REST endpoint. Any UI (Vaadin, React, mobile, ...) can be built on top
 * by the consuming application.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "holon.tenant.whatsapp.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TenantWhatsAppProperties.class)
@EnableScheduling
@Import(TenantWhatsAppEntityScanRegistrar.class)
public class TenantWhatsAppAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenantWhatsAppAutoConfiguration.class);

    /**
     * Registers {@link WhatsAppConversationDatastoreHelper} and
     * {@link WhatsAppMessageDatastoreHelper} explicitly — neither is
     * {@code @Component}-scanned (they live in an internal package outside any consuming
     * application's own base package), so this module must construct them itself.
     */
    @Bean
    @ConditionalOnMissingBean(WhatsAppConversationDatastoreHelper.class)
    public WhatsAppConversationDatastoreHelper whatsAppConversationDatastoreHelper(Datastore datastore) {
        return new WhatsAppConversationDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean(WhatsAppMessageDatastoreHelper.class)
    public WhatsAppMessageDatastoreHelper whatsAppMessageDatastoreHelper(Datastore datastore) {
        return new WhatsAppMessageDatastoreHelper(datastore);
    }

    @Bean
    @ConditionalOnMissingBean
    public ConversationWindowTracker conversationWindowTracker(
            WhatsAppConversationDatastoreHelper conversationDatastoreHelper) {
        log.debug("Registering WhatsApp ConversationWindowTracker (Datastore-backed)");
        return new ConversationWindowTracker(conversationDatastoreHelper);
    }

    @Bean
    @ConditionalOnMissingBean(TenantWhatsAppService.class)
    public TenantWhatsAppService tenantWhatsAppService(
            ConversationWindowTracker tracker,
            WhatsAppMessageDatastoreHelper messageDatastoreHelper,
            TenantWhatsAppProperties properties,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        log.info("Registering WhatsApp service (DatabaseTenantWhatsAppService)");
        // Use the application's ObjectMapper if available, otherwise fall back to a default one.
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new DatabaseTenantWhatsAppService(tracker, messageDatastoreHelper, properties, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "holon.tenant.whatsapp.webhook.enabled",
            havingValue = "true", matchIfMissing = true)
    public WhatsAppWebhookController whatsAppWebhookController(
            TenantWhatsAppService service,
            TenantWhatsAppProperties properties) {
        log.debug("Registering WhatsApp webhook controller at /webhook/whatsapp");
        return new WhatsAppWebhookController(service, properties);
    }
}








