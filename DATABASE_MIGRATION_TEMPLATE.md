# Database Migration Implementation Guide
## Template for Migrating Remaining In-Memory Services

This guide provides a step-by-step template for migrating each remaining in-memory service to a database-backed implementation.

---

## Template Steps

### Step 1: Create Spring Data Repository

**Location:** `<module>/src/main/java/com/holonplatform/multitenant/<module>/internal/<Entity>Repository.java`

```java
package com.holonplatform.multitenant.<module>.internal;

import com.holonplatform.multitenant.<module>.<Entity>;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface <Entity>Repository extends JpaRepository<<Entity>, Long> {
    
    // Add tenant-aware query methods here
    // Example patterns below
    
    // Tenant isolation
    List<<Entity>> findByTenantId(String tenantId);
    
    // Filtering
    List<<Entity>> findByTenantIdAndStatus(String tenantId, String status);
    
    // Counting
    long countByTenantId(String tenantId);
    
    // Deletion
    void deleteByTenantId(String tenantId);
}
```

### Step 2: Create Database-Backed Service Implementation

**Location:** `<module>/src/main/java/com/holonplatform/multitenant/<module>/internal/Database<Service>.java`

```java
package com.holonplatform.multitenant.<module>.internal;

import com.holonplatform.multitenant.<module>.<Service>;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class Database<Service> implements <Service> {
    
    private final <Entity>Repository repository;
    
    public Database<Service>(<Entity>Repository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }
    
    // Replace all ConcurrentHashMap operations with repository queries
    
    @Override
    @Transactional(readOnly = true)
    public <ReturnType> method(<ParameterType> param) {
        // Replace: this.map.get(param)
        // With: repository.find...(param)
        
        return repository.find...();
    }
}
```

### Step 3: Update Auto-Configuration

**Location:** `<module>/src/main/java/com/holonplatform/multitenant/<module>/config/<Module>AutoConfiguration.java`

```java
@AutoConfiguration
public class <Module>AutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(<Service>.class)
    @ConditionalOnClass(name = "com.holonplatform.multitenant.<module>.internal.<Entity>Repository")
    public <Service> database<Service>(<Entity>Repository repository) {
        return new Database<Service>(repository);
    }
    
    @Bean
    @ConditionalOnMissingBean(<Service>.class)
    public <Service> inMemory<Service>() {
        return new InMemory<Service>();  // Fallback
    }
}
```

### Step 4: Update Flyway Migration (if needed)

**Location:** `demo/src/main/resources/db/migration/V1__Initial_Schema.sql`

Ensure table schema exists:

```sql
CREATE TABLE <table_name> (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    -- ... other columns
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT fk_<table>_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_<table>_tenant ON <table_name>(tenant_id);
```

---

## Remaining Modules - Priority Order

### Priority 1: OnBoarding & Tenants

**Module:** `tenant-onboarding`
**Current Implementation:** `InMemoryTenantRepository`
**Entity:** `TenantDetails` (Record - needs mapping)
**Complexity:** MEDIUM - requires EntityMapper

```
Steps:
1. Create TenantEntity JPA entity
2. Create TenantRepository
3. Create EntityMapper for TenantDetails ↔ TenantEntity
4. Create DatabaseTenantRepository
5. Update TenantOnboardingAutoConfiguration
```

### Priority 2: WhatsApp Integration

**Module:** `tenant-whatsapp`
**Current Implementations:** 
- `InMemoryTenantWhatsAppService` (3 ConcurrentHashMaps)
- `ConversationWindowTracker` (nested ConcurrentHashMaps)
**Entities:** WhatsApp messages, conversations
**Complexity:** HIGH - complex nested structures

```
Steps:
1. Create WhatsAppMessageEntity JPA entity
2. Create WhatsAppConversationEntity JPA entity
3. Create WhatsAppMessageRepository
4. Create WhatsAppConversationRepository
5. Create DatabaseTenantWhatsAppService (replace InMemoryTenantWhatsAppService)
6. Create DatabaseConversationWindowTracker
7. Update auto-configuration
8. Migrate scheduled conversation expiration logic from memory to database
```

### Priority 3: Permissions & Security

**Module:** `tenant-permissions`
**Current Implementation:** `InMemoryTenantPermissionService`
**Features:** 
- Per-request ThreadLocal cache (important for performance)
- User permission resolution
**Complexity:** MEDIUM - need to preserve per-request caching

```
Steps:
1. Create PermissionEntity JPA entity
2. Create PermissionRepository
3. Create DatabaseTenantPermissionService
4. Keep ThreadLocal per-request cache (query cache, not data cache)
5. Update auto-configuration
```

### Priority 4: Settings Management

**Module:** `tenant-settings`
**Current Implementation:** `InMemoryTenantSettingsService`
**Storage:** Nested ConcurrentHashMap<tenant, Map<key, value>>
**Complexity:** LOW - simple key-value storage

```
Steps:
1. Create SettingEntity JPA entity with columns: (id, tenant_id, setting_key, setting_value)
2. Create SettingRepository
3. Create DatabaseTenantSettingsService
4. Update auto-configuration
```

### Priority 5: Billing & Subscriptions

**Module:** `tenant-billing`
**Current Implementation:** `InMemoryTenantBillingService`
**Entities:** Subscription, Invoice
**Complexity:** MEDIUM - complex business logic

```
Steps:
1. Verify SubscriptionEntity, InvoiceEntity exist
2. Create SubscriptionRepository
3. Create InvoiceRepository
4. Create DatabaseTenantBillingService (replace InMemoryTenantBillingService)
5. Update auto-configuration
6. Verify payment status transitions work with persistent state
```

### Priority 6: Admin Panel

**Module:** `tenant-admin`
**Current Implementation:** `InMemoryTenantAdminService`
**Features:** Tenant CRUD, search, suspension
**Complexity:** MEDIUM - uses TenantDetails (record)

```
Steps:
1. Create/verify TenantAdminEntity (or reuse TenantEntity from onboarding)
2. Create TenantAdminRepository
3. Create EntityMapper if needed
4. Create DatabaseTenantAdminService
5. Update auto-configuration
```

### Priority 7: Quotas & Rate Limiting

**Module:** `tenant-quotas`
**Current Implementation:** Likely in-memory counter maps
**Features:** Usage limits, quota reset scheduling
**Complexity:** LOW to MEDIUM - counter tracking

```
Steps:
1. Create QuotaEntity JPA entity
2. Create QuotaRepository
3. Create DatabaseTenantQuotaService
4. Replace atomic counter logic with database increments
5. Implement quota reset via scheduled job or trigger
```

### Priority 8: Invitations

**Module:** `tenant-invitations`
**Current Implementation:** Likely in-memory list
**Features:** Invitation tokens, expiration
**Complexity:** LOW - simple CRUD + expiration

```
Steps:
1. Create InvitationEntity JPA entity
2. Create InvitationRepository
3. Create DatabaseTenantInvitationService
4. Handle invitation acceptance state transitions
5. Implement expiration cleanup via scheduled job
```

---

## Common Patterns

### Pattern 1: Simple CRUD Service

```java
@Override
@Transactional
public Entity create(String tenantId, Entity entity) {
    entity.setTenantId(tenantId);
    return repository.save(entity);
}

@Override
@Transactional(readOnly = true)
public Optional<Entity> findById(String tenantId, Long id) {
    return repository.findById(id)
        .filter(e -> e.getTenantId().equals(tenantId));
}

@Override
@Transactional
public void delete(String tenantId, Long id) {
    repository.deleteById(id);  // Rely on foreign key for tenant isolation
}
```

### Pattern 2: Pagination

```java
@Override
@Transactional(readOnly = true)
public List<Entity> findByTenant(String tenantId, int offset, int limit) {
    validatePagination(offset, limit);
    return repository.findByTenantId(tenantId).stream()
        .skip(offset)
        .limit(limit)
        .toList();
}

private void validatePagination(int offset, int limit) {
    if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
    if (limit <= 0) throw new IllegalArgumentException("limit must be greater than zero");
}
```

### Pattern 3: Counting & Status Filtering

```java
@Override
@Transactional(readOnly = true)
public long countByStatus(String tenantId, Status status) {
    return repository.countByTenantIdAndStatus(tenantId, status.name());
}

@Override
@Transactional(readOnly = true)
public List<Entity> findByStatus(String tenantId, Status status, int offset, int limit) {
    return repository.findByTenantIdAndStatus(tenantId, status.name()).stream()
        .skip(offset)
        .limit(limit)
        .toList();
}
```

### Pattern 4: Soft Delete (REMOVED status)

```java
@Override
@Transactional(readOnly = true)
public Optional<Entity> findById(String tenantId, String userId) {
    return repository.findByTenantIdAndUserId(tenantId, userId)
        .filter(e -> e.getStatus() != Status.REMOVED);  // Filter soft-deleted
}

@Override
@Transactional
public void remove(String tenantId, String userId) {
    findById(tenantId, userId).ifPresent(e -> {
        e.setStatus(Status.REMOVED);  // Soft delete
        repository.save(e);
    });
}
```

---

## Testing the Migration

### Unit Tests
```java
@SpringBootTest
public class DatabaseServiceTest {
    
    @Autowired
    private <Entity>Repository repository;
    
    @Autowired
    private <Service> service;
    
    @Test
    public void testPersistence() {
        Entity entity = service.create(...);
        Long id = entity.getId();
        
        // Data should persist
        assertThat(repository.findById(id)).isPresent();
    }
}
```

### Integration Tests
```java
@DataJpaTest  // Starts H2 in-memory for testing
public class RepositoryTest {
    
    @Autowired
    private <Entity>Repository repository;
    
    @Test
    public void testTenantIsolation() {
        Entity e1 = repository.save(new Entity("tenant-1", ...));
        Entity e2 = repository.save(new Entity("tenant-2", ...));
        
        List<Entity> tenant1Data = repository.findByTenantId("tenant-1");
        assertThat(tenant1Data).hasSize(1).contains(e1);
    }
}
```

---

## Flyway Migration Checklist

For each new table added:
- [ ] Table name matches JPA entity @Table(name = "...")
- [ ] Column names match @Column(name = "...") annotations
- [ ] Data types match field types (Instant → TIMESTAMP, String → VARCHAR, etc.)
- [ ] Primary key is auto-increment: `BIGINT AUTO_INCREMENT PRIMARY KEY`
- [ ] Foreign keys use `ON DELETE CASCADE` for tenant cleanup
- [ ] Indexes created for common query patterns
- [ ] Unique constraints for duplicate prevention
- [ ] Nullable columns match entity field nullability

---

## Performance Optimization Tips

1. **Lazy Load Collections** - Use LAZY fetch for @OneToMany relationships
2. **Batch Operations** - Use `repository.saveAll()` for bulk inserts
3. **Query Projections** - Select only needed columns with @Query
4. **Index Strategy** - Index on (tenant_id) and frequently filtered columns
5. **Connection Pooling** - Use HikariCP (Spring Boot default)

---

## Troubleshooting Guide

### Issue: "Table not found" on startup

**Solution:**
1. Verify Flyway migration exists: `demo/src/main/resources/db/migration/V*__*.sql`
2. Check `spring.flyway.enabled=true` in application.properties
3. Ensure file name follows pattern: `V<number>__<description>.sql`

### Issue: Foreign key constraint violation

**Solution:**
1. Delete parent record first (uses CASCADE DELETE)
2. Ensure tenant_id is set before saving child records
3. Check order of operations in tests

### Issue: @Transactional() lazy-loading exception

**Solution:**
1. Use `@Transactional(readOnly = true)` for queries
2. Eager load collections with `@Query(fetch = FetchType.EAGER)`
3. Or fetch within transaction bounds

### Issue: Duplicate key violation on unique constraint

**Solution:**
1. Check if entity with same (tenant_id, unique_key) exists
2. Query before insert: `repository.findByTenantIdAnd...().isPresent()`
3. Use `ifPresentOrElse()` to handle existing records

---

## Next Steps

1. **Pick one module** from Priority 1
2. **Follow the template** step by step
3. **Create tests** before implementation
4. **Test locally** with demo app
5. **Verify persistence** across restarts
6. **Move to next module**

Total estimated effort: **2-4 weeks** for all remaining modules

---

**Last Updated:** August 31, 2026
**Template Version:** 1.0

