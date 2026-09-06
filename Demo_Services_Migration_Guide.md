# Demo Services Migration Guide - Quick Reference

## Overview
This guide helps update the remaining 6 demo services from JPA Repository pattern to Holon BeanDatastoreHelper pattern.

## Service-by-Service Migration Template

### 1. JpaTenantAdminService

**Current File Path:**
```
demo/src/main/java/com/holonplatform/multitenancy/demo/db/JpaTenantAdminService.java
```

**Dependency Replacement:**
```java
// BEFORE
private final TenantJpaRepository repo;

public JpaTenantAdminService(TenantJpaRepository repo) {
    this.repo = repo;
}

// AFTER
private final TenantDatastoreHelper repo;

public JpaTenantAdminService(TenantDatastoreHelper repo) {
    this.repo = repo;
}
```

**Method Call Replacements:**
| JPA Repository Method | Datastore Helper Equivalent | Notes |
|---|---|---|
| `repo.findAll(Sort.by("name").ascending())` | `repo.findAll()` | Already sorted by name |
| `repo.search(term)` | `repo.search(term)` | No change - same signature |
| `repo.findById(id)` | `repo.findById(id)` | Same signature |
| `repo.save(entity)` | `repo.save(entity)` | Same signature |
| `repo.deleteById(id)` | `repo.deleteById(id)` | Same signature |
| `repo.count()` | `repo.count()` | Same signature |
| `repo.countByStatus(status)` | `repo.countByStatus(status)` | Same signature |
| `repo.countByPlan(plan)` | `repo.countByPlan(plan)` | Same signature |

**Migration Complexity:** ⭐ LOW

---

### 2. JpaTenantAuditService

**Current File Path:**
```
demo/src/main/java/com/holonplatform/multitenancy/demo/db/JpaTenantAuditService.java
```

**Dependency Replacement:**
```java
// BEFORE
private final AuditEntryJpaRepository repo;

public JpaTenantAuditService(AuditEntryJpaRepository repo) {
    this.repo = repo;
}

// AFTER
private final AuditEntryDatastoreHelper repo;

public JpaTenantAuditService(AuditEntryDatastoreHelper repo) {
    this.repo = repo;
}
```

**Method Call Replacements:**
| JPA Repository Method | Datastore Helper Equivalent | Notes |
|---|---|---|
| `repo.findByTenant(tid, pageable)` | `repo.findByTenant(tid, pageable)` | Same signature - pagination handled |
| `repo.findByTenantAndAction(tid, action, pageable)` | `repo.findByTenantAndAction(tid, action, pageable)` | Same signature |
| `repo.findByTenantAndSeverity(tid, severity, pageable)` | `repo.findByTenantAndSeverity(tid, severity, pageable)` | Same signature |
| `repo.findTopByTenant(tid, pageable)` | `repo.findTopByTenant(tid, pageable)` | Same signature |
| `repo.countByTenant(tid)` | `repo.countByTenant(tid)` | Same signature |
| `repo.countByTenantAndSeverity(tid, severity)` | `repo.countByTenantAndSeverity(tid, severity)` | Same signature |
| `repo.deleteByTenantId(tid)` | `repo.deleteByTenantId(tid)` | Same signature |

**Migration Complexity:** ⭐ MEDIUM (pagination handling already done)

---

### 3. JpaTenantBillingService

**Current File Path:**
```
demo/src/main/java/com/holonplatform/multitenancy/demo/db/JpaTenantBillingService.java
```

**Dependency Replacement:**
```java
// BEFORE
private final SubscriptionJpaRepository subRepo;
private final InvoiceJpaRepository invRepo;

public JpaTenantBillingService(SubscriptionJpaRepository subRepo, InvoiceJpaRepository invRepo) {
    this.subRepo = subRepo;
    this.invRepo = invRepo;
}

// AFTER
private final SubscriptionDatastoreHelper subRepo;
private final InvoiceDatastoreHelper invRepo;

public JpaTenantBillingService(SubscriptionDatastoreHelper subRepo, InvoiceDatastoreHelper invRepo) {
    this.subRepo = subRepo;
    this.invRepo = invRepo;
}
```

**Method Call Replacements:**
| JPA Repository Method | Datastore Helper Equivalent | Notes |
|---|---|---|
| `subRepo.save(sub)` | `subRepo.save(sub)` | Same signature |
| `subRepo.findById(id)` | `subRepo.findById(id)` | Same signature |
| `subRepo.deleteById(id)` | `subRepo.deleteById(id)` | Same signature |
| `invRepo.findByTenant(tid, pageable)` | `invRepo.findByTenant(tid, pageable)` | Same signature |
| `invRepo.countByTenant(tid)` | `invRepo.countByTenant(tid)` | Same signature |
| `invRepo.deleteByTenantId(tid)` | `invRepo.deleteByTenantId(tid)` | Same signature |

**Migration Complexity:** ⭐ LOW

---

### 4. JpaTenantSettingsService

**Current File Path:**
```
demo/src/main/java/com/holonplatform/multitenancy/demo/db/JpaTenantSettingsService.java
```

**Dependency Replacement:**
```java
// BEFORE
private final TenantSettingJpaRepository tenantRepo;
private final PlanSettingJpaRepository planRepo;

public JpaTenantSettingsService(TenantSettingJpaRepository tenantRepo, PlanSettingJpaRepository planRepo) {
    this.tenantRepo = tenantRepo;
    this.planRepo = planRepo;
}

// AFTER
private final TenantSettingDatastoreHelper tenantRepo;
private final PlanSettingDatastoreHelper planRepo;

public JpaTenantSettingsService(TenantSettingDatastoreHelper tenantRepo, PlanSettingDatastoreHelper planRepo) {
    this.tenantRepo = tenantRepo;
    this.planRepo = planRepo;
}
```

**Method Call Replacements:**
| JPA Repository Method | Datastore Helper Equivalent | Notes |
|---|---|---|
| `tenantRepo.save(setting)` | `tenantRepo.save(setting)` | Same signature |
| `tenantRepo.findByTenant(tid)` | `tenantRepo.findByTenant(tid)` | Same signature |
| `tenantRepo.deleteByTenantId(tid)` | `tenantRepo.deleteByTenantId(tid)` | Same signature |
| `planRepo.save(setting)` | `planRepo.save(setting)` | Same signature |
| `planRepo.findByPlan(plan)` | `planRepo.findByPlan(plan)` | Same signature |

**Migration Complexity:** ⭐ MEDIUM (composite key handling already abstracted)

---

### 5. JpaTenantUserService

**Current File Path:**
```
demo/src/main/java/com/holonplatform/multitenancy/demo/db/JpaTenantUserService.java
```

**Dependency Replacement:**
```java
// BEFORE
private final TenantUserJpaRepository repo;

public JpaTenantUserService(TenantUserJpaRepository repo) {
    this.repo = repo;
}

// AFTER
private final TenantUserDatastoreHelper repo;

public JpaTenantUserService(TenantUserDatastoreHelper repo) {
    this.repo = repo;
}
```

**Method Call Replacements:**
| JPA Repository Method | Datastore Helper Equivalent | Notes |
|---|---|---|
| `repo.save(user)` | `repo.save(user)` | Same signature |
| `repo.findActiveByTenant(tid, pageable)` | `repo.findActiveByTenant(tid, pageable)` | Same signature |
| `repo.findByTenantAndRole(tid, role, pageable)` | `repo.findByTenantAndRole(tid, role, pageable)` | Same signature |
| `repo.findByTenantAndStatus(tid, status, pageable)` | `repo.findByTenantAndStatus(tid, status, pageable)` | Same signature |
| `repo.findByTenantAndEmail(tid, email)` | `repo.findByTenantAndEmail(tid, email)` | Same signature |
| `repo.countActiveByTenant(tid)` | `repo.countActiveByTenant(tid)` | Same signature |
| `repo.countByTenantAndStatus(tid, status)` | `repo.countByTenantAndStatus(tid, status)` | Same signature |
| `repo.countByTenantAndRole(tid, role)` | `repo.countByTenantAndRole(tid, role)` | Same signature |
| `repo.deleteByTenantId(tid)` | `repo.deleteByTenantId(tid)` | Same signature |

**Migration Complexity:** ⭐⭐ MEDIUM (most methods, but all signatures match)

---

### 6. JpaTenantRepository

**Current File Path:**
```
demo/src/main/java/com/holonplatform/multitenancy/demo/db/JpaTenantRepository.java
```

**Dependency Replacement:**
```java
// BEFORE
private final TenantJpaRepository repo;

public JpaTenantRepository(TenantJpaRepository repo) {
    this.repo = repo;
}

// AFTER
private final TenantDatastoreHelper repo;

public JpaTenantRepository(TenantDatastoreHelper repo) {
    this.repo = repo;
}
```

**Method Call Replacements:**
| JPA Repository Method | Datastore Helper Equivalent | Notes |
|---|---|---|
| `repo.save(entity)` | `repo.save(entity)` | Same signature |
| `repo.findById(id)` | `repo.findById(id)` | Same signature |
| `repo.existsById(id)` | `repo.existsById(id)` | Same signature |

**Migration Complexity:** ⭐ LOW

---

## Configuration Update

### File: DemoDatabaseConfig.java

**Current Location:**
```
demo/src/main/java/com/holonplatform/multitenancy/demo/db/DemoDatabaseConfig.java
```

**Option 1: Replace Repository Injections (Simple)**
```java
// BEFORE
@Bean
@ConditionalOnMissingBean(TenantAdminService.class)
public TenantAdminService tenantAdminService(TenantJpaRepository repo) {
    return new JpaTenantAdminService(repo);
}

// AFTER
@Bean
@ConditionalOnMissingBean(TenantAdminService.class)
public TenantAdminService tenantAdminService(TenantDatastoreHelper repo) {
    return new JpaTenantAdminService(repo);
}
```

**Option 2: Create New DatastoreDatabaseConfig (Recommended)**
```java
package com.holonplatform.multitenancy.demo.db;

import com.holonplatform.multitenancy.demo.db.repo.datastore.*;
import com.holonplatform.multitenant.admin.service.TenantAdminService;
import com.holonplatform.multitenant.audit.TenantAuditService;
// ... other imports

@Configuration
public class DatastoreDatabaseConfig {

    @Bean
    @ConditionalOnMissingBean(TenantAdminService.class)
    public TenantAdminService tenantAdminService(TenantDatastoreHelper repo) {
        return new JpaTenantAdminService(repo);
    }
    
    // ... other bean definitions using Datastore helpers
}
```

**Benefit of Option 2:**
- Keeps both JPA and Datastore implementations available
- Easy to switch via profile or property
- Allows gradual migration if needed

---

## Testing Strategy

### Unit Tests
Create test files mirroring service implementations:
```
demo/src/test/java/com/holonplatform/multitenancy/demo/db/
├── JpaTenantAdminServiceTest.java
├── JpaTenantAuditServiceTest.java
├── JpaTenantBillingServiceTest.java
├── JpaTenantSettingsServiceTest.java
├── JpaTenantUserServiceTest.java
└── JpaTenantRepositoryTest.java
```

### Integration Tests with Testcontainers
```java
@Testcontainers
class JpaTenantAdminServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15"));
    
    @Autowired
    TenantAdminService service;
    
    @Test
    void testFindAll() {
        // Test service methods with real database
    }
}
```

---

## Common Issues & Solutions

### Issue 1: Compilation Error "Cannot resolve symbol TenantDatastoreHelper"
**Cause:** Import statement missing or helper class not in classpath
**Solution:** 
```java
import com.holonplatform.multitenancy.demo.db.repo.datastore.TenantDatastoreHelper;
```

### Issue 2: Pagination Results Different
**Cause:** Offset calculation difference between Spring Data and Holon
**Solution:** Verify offset calculation in helper:
```java
.offset((long) pageable.getPageNumber() * pageable.getPageSize())
```

### Issue 3: Case-Insensitive Search Not Working
**Cause:** Database collation or LIKE syntax differences
**Solution:** Use Datastore's `likeIgnoreCase()` method:
```java
.filter(target -> target.get(Entity_.EMAIL).likeIgnoreCase(email))
```

### Issue 4: Composite Key Queries Returning Wrong Results
**Cause:** Missing AND condition in filter chain
**Solution:** Chain filters properly:
```java
.filter(target -> target.get(Entity_.TENANT_ID).eq(tenantId)
        .and(target.get(Entity_.SETTING_KEY).eq(key)))
```

---

## Rollback Instructions

If migration causes issues:

1. **Keep Git History:** All deleted JPA repositories are in git history
   ```bash
   git log --diff-filter=D --summary | grep delete | grep -i repository
   ```

2. **Restore JPA Repositories:**
   ```bash
   git checkout HEAD~N -- demo/src/main/java/.../repo/*JpaRepository.java
   ```

3. **Restore Original Services:**
   Keep original JpaTenant*Service.java files unchanged

4. **Switch Configuration:**
   Use original DemoDatabaseConfig with @ConditionalOnBean checks

---

## Verification Checklist

- [ ] All 6 demo services updated to use Datastore helpers
- [ ] All imports updated (no JpaRepository imports remaining)
- [ ] DemoDatabaseConfig bean registrations updated
- [ ] `mvn clean compile` passes with no errors
- [ ] Unit tests pass for all services
- [ ] Integration tests pass with test database
- [ ] Manual smoke tests with H2 database
- [ ] Performance benchmarks acceptable
- [ ] Pagination behavior verified
- [ ] Search functionality verified
- [ ] Composite key lookups verified

---

## Timeline Estimate

| Task | Estimated Time |
|------|--------|
| Update JpaTenantAdminService | 10 minutes |
| Update JpaTenantAuditService | 15 minutes |
| Update JpaTenantBillingService | 10 minutes |
| Update JpaTenantSettingsService | 15 minutes |
| Update JpaTenantUserService | 20 minutes |
| Update JpaTenantRepository | 5 minutes |
| Update DemoDatabaseConfig | 10 minutes |
| **Subtotal: Services** | **85 minutes** |
| Write unit tests | 60 minutes |
| Write integration tests | 45 minutes |
| Manual testing & verification | 30 minutes |
| **TOTAL ESTIMATED TIME** | **~4 hours** |

---

## References & Resources

- **Holon Datastore Pattern:** [Internal Wiki]
- **Composite Key Handling:** Section 4.5 of Holon Datastore Guide
- **Property Sets & Metamodels:** Section 3.2 of Holon Core Reference
- **Migration Examples:** This document and JPARepository_Migration_Report.md


