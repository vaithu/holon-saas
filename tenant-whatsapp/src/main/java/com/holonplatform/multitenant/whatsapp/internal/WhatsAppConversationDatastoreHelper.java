package com.holonplatform.multitenant.whatsapp.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.whatsapp.WhatsAppConversation;
import com.holonplatform.multitenant.whatsapp.WhatsAppConversationProperties;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link WhatsAppConversation} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on the WhatsApp
 * 24-hour conversation window state, keyed by {@code tenantId::customerPhone}. This is the
 * durable source of truth for whether a tenant currently has an open free-reply window with a
 * given customer, replacing the previous in-memory {@code ConcurrentHashMap}-backed tracker.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.whatsapp.config.TenantWhatsAppAutoConfiguration}
 * rather than {@code @Component}-scanned — this class lives in an internal package outside
 * any consuming application's own base package, so component-scanning would never find it.
 */
public class WhatsAppConversationDatastoreHelper {

    private final BeanDatastoreHelper<WhatsAppConversation> helper;

    public WhatsAppConversationDatastoreHelper(Datastore datastore) {
        this.helper = BeanDatastoreHelper.of(datastore, WhatsAppConversation.class);
    }

    public WhatsAppConversation save(WhatsAppConversation conversation) {
        // Deliberately BeanDatastoreHelper#save (upsert), NOT #insert — ConversationWindowTracker
        // loads an existing WhatsAppConversation (real, already-persisted windowKey), copies it
        // via toBuilder(), mutates it (expiresAt/incrementMessages), and re-saves the rebuilt
        // instance with the SAME windowKey. #insert() on an already-persisted entity throws
        // "jakarta.persistence.EntityExistsException: Detached entity passed to persist"
        // (see the identical bug/fix in TenantUserDatastoreHelper#save for the full rationale).
        return helper.save(conversation).getResult().orElse(conversation);
    }

    public Optional<WhatsAppConversation> findByWindowKey(String windowKey) {
        return helper.findTop(1, QueryFilter.eq(WhatsAppConversationProperties.WINDOW_KEY, windowKey)).findFirst();
    }

    public Optional<WhatsAppConversation> findByTenantIdAndCustomerPhone(String tenantId, String customerPhone) {
        return helper.findTop(1,
                QueryFilter.eq(WhatsAppConversationProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(WhatsAppConversationProperties.CUSTOMER_PHONE, customerPhone)))
                .findFirst();
    }

    public List<WhatsAppConversation> findOpenWindowsForTenant(String tenantId, Instant now) {
        return helper.findAll(
                QueryFilter.eq(WhatsAppConversationProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.gt(WhatsAppConversationProperties.EXPIRES_AT, now)),
                WhatsAppConversationProperties.EXPIRES_AT.asc()
        ).toList();
    }

    public long countOpenWindowsForTenant(String tenantId, Instant now) {
        return helper.count(
                QueryFilter.eq(WhatsAppConversationProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.gt(WhatsAppConversationProperties.EXPIRES_AT, now))
        );
    }

    /** Counts currently-open windows across <em>all</em> tenants (observability). */
    public long countOpenWindows(Instant now) {
        return helper.count(QueryFilter.gt(WhatsAppConversationProperties.EXPIRES_AT, now));
    }

    /**
     * Bulk-deletes windows that have already expired (as of {@code now}), across all tenants.
     * Used by a scheduled housekeeping task to prevent unbounded table growth.
     */
    public long deleteExpiredWindows(Instant now) {
        List<WhatsAppConversation> expired = helper.findAll(
                QueryFilter.loe(WhatsAppConversationProperties.EXPIRES_AT, now)).toList();
        expired.forEach(helper::delete);
        return expired.size();
    }

    public long deleteByTenantId(String tenantId) {
        List<WhatsAppConversation> toDelete = helper.findAll(
                QueryFilter.eq(WhatsAppConversationProperties.TENANT_ID, tenantId)).toList();
        toDelete.forEach(helper::delete);
        return toDelete.size();
    }
}
