package com.holonplatform.multitenant.users.internal;

import com.holonplatform.multitenant.permissions.annotation.RequiresPermission;
import com.holonplatform.multitenant.users.TenantRole;
import com.holonplatform.multitenant.users.TenantUser;
import com.holonplatform.multitenant.users.TenantUserException;
import com.holonplatform.multitenant.users.TenantUserService;
import com.holonplatform.multitenant.users.TenantUserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Database-backed {@link TenantUserService} implementation using Holon Datastore.
 *
 * <p>Persists all tenant user data to the relational database via Holon's Datastore.
 * Supports role-based access control, user status management, and soft deletion.
 *
 * <p>This is the sole {@link TenantUserService} implementation shipped by the framework;
 * a Holon {@code Datastore} bean must be configured (e.g. via {@code holon-datastore-jpa}).
 */
@Service
public class DatabaseTenantUserService implements TenantUserService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTenantUserService.class);

    private final TenantUserDatastoreHelper datastoreHelper;

    public DatabaseTenantUserService(TenantUserDatastoreHelper datastoreHelper) {
        this.datastoreHelper = Objects.requireNonNull(datastoreHelper, "datastoreHelper must not be null");
    }

    // -- Write -----------------------------------------------------------------

    @Override
    @Transactional
    @RequiresPermission({"USER_INVITE", "ADMIN"})
    public TenantUser invite(String tenantId, String userId, String email, TenantRole role) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(role, "role must not be null");

        Optional<TenantUser> existing = datastoreHelper.findById(userId, tenantId);
        if (existing.isPresent()) {
            throw new TenantUserException(
                    "User '" + userId + "' already belongs to tenant '" + tenantId + "'",
                    tenantId, userId);
        }

        TenantUser user = TenantUser.builder(userId, tenantId, email.toLowerCase(Locale.ROOT))
                .role(role)
                .status(TenantUserStatus.INVITED)
                .build();

        datastoreHelper.save(user);
        log.info("Invited user '{}' ({}) to tenant '{}' as {}", userId, email, tenantId, role);
        return user;
    }

    @Override
    @Transactional
    @RequiresPermission({"USER_MANAGE", "ADMIN"})
    public TenantUser activate(String tenantId, String userId) {
        TenantUser user = requireUser(tenantId, userId);
        if (user.getStatus() == TenantUserStatus.REMOVED) {
            throw new TenantUserException("Cannot activate removed user", tenantId, userId);
        }

        user.setStatus(TenantUserStatus.ACTIVE);
        datastoreHelper.save(user);
        log.info("Activated user '{}' in tenant '{}'", userId, tenantId);
        return user;
    }

    @Override
    @Transactional
    @RequiresPermission({"USER_MANAGE", "ADMIN"})
    public TenantUser suspend(String tenantId, String userId) {
        TenantUser user = requireUser(tenantId, userId);
        if (user.getStatus() != TenantUserStatus.ACTIVE) {
            throw new TenantUserException(
                    "Only ACTIVE users can be suspended; current status: " + user.getStatus(),
                    tenantId, userId);
        }

        user.setStatus(TenantUserStatus.SUSPENDED);
        datastoreHelper.save(user);
        log.info("Suspended user '{}' in tenant '{}'", userId, tenantId);
        return user;
    }

    @Override
    @Transactional
    @RequiresPermission("ADMIN")
    public void remove(String tenantId, String userId) {
        TenantUser user = requireUser(tenantId, userId);

        if (user.getRole() == TenantRole.OWNER && countByRole(tenantId, TenantRole.OWNER) <= 1) {
            throw new TenantUserException(
                    "Cannot remove the sole OWNER of tenant '" + tenantId + "'",
                    tenantId, userId);
        }

        user.setStatus(TenantUserStatus.REMOVED);
        datastoreHelper.save(user);
        log.info("Removed user '{}' from tenant '{}'", userId, tenantId);
    }

    @Override
    @Transactional
    @RequiresPermission("ADMIN")
    public TenantUser changeRole(String tenantId, String userId, TenantRole newRole) {
        Objects.requireNonNull(newRole, "newRole must not be null");
        TenantUser user = requireUser(tenantId, userId);

        if (user.getRole() == TenantRole.OWNER && newRole != TenantRole.OWNER) {
            if (countByRole(tenantId, TenantRole.OWNER) <= 1) {
                throw new TenantUserException(
                        "Cannot demote the sole OWNER of tenant '" + tenantId + "'",
                        tenantId, userId);
            }
        }

        user.setRole(newRole);
        datastoreHelper.save(user);
        log.info("Changed role of user '{}' in tenant '{}' to {}", userId, tenantId, newRole);
        return user;
    }

    @Override
    @Transactional
    public void recordLogin(String tenantId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        findById(tenantId, userId).ifPresent(user -> {
            user.setLastLoginAt(Instant.now());
            datastoreHelper.save(user);
            log.debug("Recorded login for user '{}' in tenant '{}'", userId, tenantId);
        });
    }

    // -- Read ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission({"USER_VIEW", "ADMIN"})
    public Optional<TenantUser> findById(String tenantId, String userId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");

        return datastoreHelper.findById(userId, tenantId)
                .filter(u -> u.getStatus() != TenantUserStatus.REMOVED);
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission({"USER_VIEW", "ADMIN"})
    public Optional<TenantUser> findByEmail(String tenantId, String email) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(email, "email must not be null");

        return datastoreHelper.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                .filter(u -> u.getStatus() != TenantUserStatus.REMOVED);
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission({"USER_VIEW", "ADMIN"})
    public List<TenantUser> findByTenant(String tenantId, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        validatePagination(offset, limit);

        return datastoreHelper.findByTenantId(tenantId).stream()
                .filter(u -> u.getStatus() != TenantUserStatus.REMOVED)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission({"USER_VIEW", "ADMIN"})
    public List<TenantUser> findByRole(String tenantId, TenantRole role, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        validatePagination(offset, limit);

        return datastoreHelper.findByTenantIdAndRole(tenantId, role.name()).stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission({"USER_VIEW", "ADMIN"})
    public List<TenantUser> findByStatus(String tenantId, TenantUserStatus status, int offset, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        validatePagination(offset, limit);

        return datastoreHelper.findByTenantIdAndStatus(tenantId, status.name()).stream()
                .skip(offset)
                .limit(limit)
                .toList();
    }


    @Override
    @Transactional(readOnly = true)
    @RequiresPermission({"USER_VIEW", "ADMIN"})
    public long count(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return datastoreHelper.countByTenantId(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    @RequiresPermission({"USER_VIEW", "ADMIN"})
    public long countByStatus(String tenantId, TenantUserStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        return datastoreHelper.countByTenantIdAndStatus(tenantId, status.name());
    }

    // -- Lifecycle ---------------------------------------------------------------

    @Override
    @Transactional
    @RequiresPermission("ADMIN")
    public void deleteAll(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        List<TenantUser> toDelete = datastoreHelper.findByTenantId(tenantId);
        long count = toDelete.size();
        datastoreHelper.deleteByTenantId(tenantId);
        log.info("Hard-deleted {} user records for tenant '{}'", count, tenantId);
    }

    // -- Helpers ---------------------------------------------------------------

    private TenantUser requireUser(String tenantId, String userId) {
        return findById(tenantId, userId)
                .orElseThrow(() -> new TenantUserException(
                        "User '" + userId + "' not found in tenant '" + tenantId + "'",
                        tenantId, userId));
    }

    private long countByRole(String tenantId, TenantRole role) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        return datastoreHelper.countByTenantIdAndRole(tenantId, role.name());
    }

    private void validatePagination(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be greater than zero");
        }
    }
}


