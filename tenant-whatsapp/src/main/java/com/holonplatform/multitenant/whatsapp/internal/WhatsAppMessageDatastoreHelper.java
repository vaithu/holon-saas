package com.holonplatform.multitenant.whatsapp.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;
import com.holonplatform.multitenant.whatsapp.WhatsAppMessage;
import com.holonplatform.multitenant.whatsapp.WhatsAppMessageProperties;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link WhatsAppMessage} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on the WhatsApp message
 * log, used both for message history/audit lookups and as the source of truth for computing
 * {@code WhatsAppUsageStats} (usage statistics are aggregated in the service layer from the
 * message rows returned here, rather than via a dedicated persisted aggregate).
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.whatsapp.config.TenantWhatsAppAutoConfiguration}
 * rather than {@code @Component}-scanned — this class lives in an internal package outside
 * any consuming application's own base package, so component-scanning would never find it.
 */
public class WhatsAppMessageDatastoreHelper {

    private final BeanDatastoreHelper<WhatsAppMessage> helper;

    public WhatsAppMessageDatastoreHelper(Datastore datastore) {
        // Register the exclusion rule before Holon caches this entity's PropertySet, so
        // computed getters (e.g. WhatsAppMessage#isDelivered()) and synthetic Map "empty"
        // sub-properties never leak into generated HQL.
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.helper = BeanDatastoreHelper.of(datastore, WhatsAppMessage.class);
    }

    public WhatsAppMessage save(WhatsAppMessage message) {
        // Deliberately BeanDatastoreHelper#save (upsert), NOT #insert — updateDeliveryStatus()
        // loads an existing WhatsAppMessage (real, already-persisted localId), copies it via
        // toBuilder(), mutates the status/deliveredAt/readAt fields, and re-saves the rebuilt
        // instance with the SAME localId. #insert() on an already-persisted entity throws
        // "jakarta.persistence.EntityExistsException: Detached entity passed to persist"
        // (see the identical bug/fix in TenantUserDatastoreHelper#save for the full rationale).
        return helper.save(message).getResult().orElse(message);
    }

    public Optional<WhatsAppMessage> findByLocalId(String localId) {
        return helper.findTop(1, QueryFilter.eq(WhatsAppMessageProperties.LOCAL_ID, localId)).findFirst();
    }

    public Optional<WhatsAppMessage> findByMessageId(String tenantId, String messageId) {
        return helper.findTop(1,
                QueryFilter.eq(WhatsAppMessageProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(WhatsAppMessageProperties.MESSAGE_ID, messageId)))
                .findFirst();
    }

    public List<WhatsAppMessage> findByTenantId(String tenantId) {
        return helper.findAll(QueryFilter.eq(WhatsAppMessageProperties.TENANT_ID, tenantId),
                WhatsAppMessageProperties.CREATED_AT.desc()).toList();
    }

    public List<WhatsAppMessage> findByTenantIdAndPhone(String tenantId, String phone) {
        QueryFilter phoneFilter = QueryFilter.anyOf(
                QueryFilter.eq(WhatsAppMessageProperties.TO, phone),
                QueryFilter.eq(WhatsAppMessageProperties.FROM, phone))
                .orElseThrow(() -> new IllegalStateException("Unable to build phone filter"));
        return helper.findAll(
                QueryFilter.eq(WhatsAppMessageProperties.TENANT_ID, tenantId).and(phoneFilter),
                WhatsAppMessageProperties.CREATED_AT.desc()
        ).toList();
    }

    public List<WhatsAppMessage> findByTenantIdAndCreatedAtBetween(String tenantId, Instant from, Instant to) {
        return helper.findAll(
                QueryFilter.eq(WhatsAppMessageProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.goe(WhatsAppMessageProperties.CREATED_AT, from))
                        .and(QueryFilter.lt(WhatsAppMessageProperties.CREATED_AT, to)),
                WhatsAppMessageProperties.CREATED_AT.asc()
        ).toList();
    }

    public long countByTenantId(String tenantId) {
        return helper.count(QueryFilter.eq(WhatsAppMessageProperties.TENANT_ID, tenantId));
    }

    public long deleteByTenantId(String tenantId) {
        List<WhatsAppMessage> toDelete = helper.findAll(
                QueryFilter.eq(WhatsAppMessageProperties.TENANT_ID, tenantId)).toList();
        toDelete.forEach(helper::delete);
        return toDelete.size();
    }
}
