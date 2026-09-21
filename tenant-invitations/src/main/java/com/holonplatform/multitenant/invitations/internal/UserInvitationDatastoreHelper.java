package com.holonplatform.multitenant.invitations.internal;

import com.holonplatform.core.datastore.Datastore;
import com.holonplatform.core.datastore.beans.BeanDatastoreHelper;
import com.holonplatform.core.query.QueryFilter;
import com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix;
import com.holonplatform.multitenant.invitations.InvitationStatus;
import com.holonplatform.multitenant.invitations.UserInvitation;
import com.holonplatform.multitenant.invitations.UserInvitationProperties;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Holon Datastore-based helper for {@link UserInvitation} persistence.
 *
 * <p>Wraps Holon's BeanDatastoreHelper for type-safe query operations on user invitations,
 * replicating the tenant/email/status lookups previously served by the in-memory
 * {@code DefaultTenantInvitationService} indexes.
 *
 * <p>Email comparisons ({@link #findByTenantIdAndEmail(String, String)} and
 * {@link #findPendingByTenantIdAndEmail(String, String)}) are performed case-insensitively
 * in application code (after narrowing the query to the relevant tenant/status) to
 * match the original in-memory implementation's semantics regardless of the
 * configured database's collation.
 *
 * <p>Registered explicitly as a {@code @Bean} by
 * {@link com.holonplatform.multitenant.invitations.config.TenantInvitationAutoConfiguration}
 * rather than {@code @Component}-scanned — this class lives in an internal package outside
 * any consuming application's own base package, so component-scanning would never find it.
 */
public class UserInvitationDatastoreHelper {

    private final BeanDatastoreHelper<UserInvitation> helper;

    public UserInvitationDatastoreHelper(Datastore datastore) {
        // Register the exclusion rule before Holon caches this entity's PropertySet, so
        // computed getters (e.g. UserInvitation#isExpired()/isPending()) and synthetic Map
        // "attributes.empty" sub-properties never leak into generated HQL.
        TenantCoreBeanPropertyFix.ensureRegistered();
        this.helper = BeanDatastoreHelper.of(datastore, UserInvitation.class);
    }

    public UserInvitation save(UserInvitation invitation) {
        return helper.save(invitation).getResult().orElse(invitation);
    }

    public Optional<UserInvitation> findByToken(String token) {
        return helper.findTop(1, QueryFilter.eq(UserInvitationProperties.TOKEN, token)).findFirst();
    }

    /** Returns all invitations for a tenant in a given status, newest first. */
    public List<UserInvitation> findByTenantIdAndStatus(String tenantId, InvitationStatus status) {
        return helper.findAll(
                QueryFilter.eq(UserInvitationProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(UserInvitationProperties.STATUS, status)),
                UserInvitationProperties.CREATED_AT.desc()
        ).toList();
    }

    /** Returns all invitations (any status) for a tenant+email (case-insensitive), newest first. */
    public List<UserInvitation> findByTenantIdAndEmail(String tenantId, String email) {
        String emailLower = email.toLowerCase(Locale.ROOT);
        return helper.findAll(
                QueryFilter.eq(UserInvitationProperties.TENANT_ID, tenantId),
                UserInvitationProperties.CREATED_AT.desc()
        ).filter(inv -> inv.getEmail() != null && inv.getEmail().toLowerCase(Locale.ROOT).equals(emailLower))
                .toList();
    }

    /** Returns the PENDING invitation for a tenant+email (case-insensitive), if any. */
    public Optional<UserInvitation> findPendingByTenantIdAndEmail(String tenantId, String email) {
        String emailLower = email.toLowerCase(Locale.ROOT);
        return helper.findAll(
                QueryFilter.eq(UserInvitationProperties.TENANT_ID, tenantId)
                        .and(QueryFilter.eq(UserInvitationProperties.STATUS, InvitationStatus.PENDING))
        ).filter(inv -> inv.getEmail() != null && inv.getEmail().toLowerCase(Locale.ROOT).equals(emailLower))
                .findFirst();
    }

    /** Returns all PENDING invitations whose expiry has already passed. */
    public List<UserInvitation> findAllExpiredPending(Instant now) {
        return helper.findAll(
                QueryFilter.eq(UserInvitationProperties.STATUS, InvitationStatus.PENDING)
                        .and(QueryFilter.lt(UserInvitationProperties.EXPIRES_AT, now))
        ).toList();
    }

    /** Deletes all invitation records for a tenant; returns the number of deleted rows. */
    public long deleteByTenantId(String tenantId) {
        return helper.bulkDelete(QueryFilter.eq(UserInvitationProperties.TENANT_ID, tenantId)).getAffectedCount();
    }
}
