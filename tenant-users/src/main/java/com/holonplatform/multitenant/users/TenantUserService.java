package com.holonplatform.multitenant.users;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing users within a tenant.
 *
 * <h2>Lazy-load principle</h2>
 * <p>All listing methods accept {@code offset} and {@code limit}. Count is a
 * separate explicit call and is NEVER triggered as a side-effect of a fetch.
 *
 * <h2>Lifecycle</h2>
 * <pre>
 *   invite  →  INVITED
 *   activate → ACTIVE
 *   suspend  → SUSPENDED   (from ACTIVE)
 *   activate → ACTIVE      (from SUSPENDED)
 *   remove   → REMOVED
 * </pre>
 */
public interface TenantUserService {

    // ── Invite / activate / suspend / remove ─────────────────────────────────

    /**
     * Creates a new user in {@link TenantUserStatus#INVITED INVITED} state.
     *
     * @param tenantId tenant the user is being invited to; must not be null
     * @param userId   unique user identifier; must not be null
     * @param email    user email; must not be null
     * @param role     assigned role; must not be null
     * @return the created {@link TenantUser}
     * @throws TenantUserException if the user already belongs to the tenant
     */
    TenantUser invite(String tenantId, String userId, String email, TenantRole role);

    /**
     * Creates and immediately activates a user who has accepted a valid invitation.
     *
     * <h3>Why this is separate from {@link #invite} + {@link #activate}</h3>
     * <p>Invitation acceptance is performed by the <em>invitee</em>, who is by definition not
     * yet a member of the tenant and is typically not authenticated at all — they have only
     * proven possession of a valid, unexpired, single-use invitation token. Composing this
     * flow out of {@link #invite} and {@link #activate} therefore cannot work: both are
     * guarded by {@code @RequiresPermission({"USER_INVITE"/"USER_MANAGE", "ADMIN"})}, so the
     * permission aspect finds no privileged principal and throws
     * {@code PermissionDeniedException}, failing every acceptance.
     *
     * <p>This method is deliberately <strong>not</strong> permission-gated. Authorisation for
     * it comes from the invitation token itself, which the caller
     * ({@code TenantInvitationService#accept}) must validate — checking status, expiry and
     * single-use transition — <em>before</em> calling this.
     *
     * @param tenantId   tenant the user is joining; must not be null
     * @param userId     unique user identifier; must not be null
     * @param email      user email; must not be null
     * @param role       assigned role; must not be null
     * @param attributes extra metadata to store on the user (e.g. {@code job_role}); may be null
     * @return the created, {@link TenantUserStatus#ACTIVE ACTIVE} {@link TenantUser}
     * @throws TenantUserException if the user already belongs to the tenant
     */
    TenantUser acceptInvitation(String tenantId, String userId, String email, TenantRole role,
            java.util.Map<String, String> attributes);

    /**
     * Transitions a user to {@link TenantUserStatus#ACTIVE ACTIVE}.
     *
     * @param tenantId tenant; must not be null
     * @param userId   user; must not be null
     * @return updated {@link TenantUser}
     * @throws TenantUserException if the user is not found or already removed
     */
    TenantUser activate(String tenantId, String userId);

    /**
     * Transitions an active user to {@link TenantUserStatus#SUSPENDED SUSPENDED}.
     *
     * @param tenantId tenant; must not be null
     * @param userId   user; must not be null
     * @return updated {@link TenantUser}
     * @throws TenantUserException if the user is not found or cannot be suspended
     */
    TenantUser suspend(String tenantId, String userId);

    /**
     * Marks a user as {@link TenantUserStatus#REMOVED REMOVED}.
     * The record is retained for audit purposes.
     *
     * @param tenantId tenant; must not be null
     * @param userId   user; must not be null
     * @throws TenantUserException if the user is not found or is the sole owner
     */
    void remove(String tenantId, String userId);

    /**
     * Changes a user's role within the tenant.
     *
     * @param tenantId tenant; must not be null
     * @param userId   user; must not be null
     * @param newRole  new role; must not be null
     * @return updated {@link TenantUser}
     * @throws TenantUserException if the user is not found or the role change is invalid
     */
    TenantUser changeRole(String tenantId, String userId, TenantRole newRole);

    /**
     * Updates the last-login timestamp for a user.
     *
     * @param tenantId tenant; must not be null
     * @param userId   user; must not be null
     */
    void recordLogin(String tenantId, String userId);

    // ── Read ─────────────────────────────────────────────────────────────────

    /**
     * Finds a user by tenantId and userId.
     *
     * @param tenantId tenant; must not be null
     * @param userId   user; must not be null
     * @return the user, or {@link Optional#empty()} if not found or removed
     */
    Optional<TenantUser> findById(String tenantId, String userId);

    /**
     * Finds a user by tenantId and email.
     *
     * @param tenantId tenant; must not be null
     * @param email    email; must not be null
     * @return the user, or {@link Optional#empty()} if not found
     */
    Optional<TenantUser> findByEmail(String tenantId, String email);

    /**
     * Returns a page of users within the tenant (excludes {@link TenantUserStatus#REMOVED REMOVED}).
     *
     * @param tenantId tenant; must not be null
     * @param offset   zero-based start index
     * @param limit    maximum number of results
     * @return list of users, ordered by createdAt ascending; never null
     */
    List<TenantUser> findByTenant(String tenantId, int offset, int limit);

    /**
     * Returns all users with the given role (excludes REMOVED).
     *
     * @param tenantId tenant; must not be null
     * @param role     role to filter by; must not be null
     * @param offset   zero-based start index
     * @param limit    maximum number of results
     * @return matching users; never null
     */
    List<TenantUser> findByRole(String tenantId, TenantRole role, int offset, int limit);

    /**
     * Returns all users with the given status.
     *
     * @param tenantId tenant; must not be null
     * @param status   status to filter by; must not be null
     * @param offset   zero-based start index
     * @param limit    maximum number of results
     * @return matching users; never null
     */
    List<TenantUser> findByStatus(String tenantId, TenantUserStatus status, int offset, int limit);

    /**
     * Counts non-removed users in the tenant.
     *
     * @param tenantId tenant; must not be null
     * @return total user count
     */
    long count(String tenantId);

    /**
     * Counts users with the given status.
     *
     * @param tenantId tenant; must not be null
     * @param status   status to count; must not be null
     * @return count
     */
    long countByStatus(String tenantId, TenantUserStatus status);

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Hard-deletes all user records for a tenant. Use during tenant offboarding.
     *
     * @param tenantId tenant; must not be null
     */
    void deleteAll(String tenantId);
}

