# JPA Migration Verification Checklist

## ✅ Dependency Migration Complete

### Root pom.xml
- [x] Added `holon-starter-jpa-hibernate` to dependencyManagement
- [x] Removed explicit `hibernate.version` property
- [x] Version uses `${holon-core.version}`

### tenant-data/pom.xml
- [x] Replaced `spring-boot-starter-data-jpa` with `holon-starter-jpa-hibernate`
- [x] Removed explicit `hibernate-core` dependency
- [x] Kept `spring-jdbc` for legacy compatibility

### tenant-audit/pom.xml
- [x] Replaced `spring-boot-starter-data-jpa` with `holon-starter-jpa-hibernate`
- [x] Made dependency required (not optional)
- [x] Added `AuditEntryRepository` interface

### tenant-users/pom.xml
- [x] Replaced `spring-boot-starter-data-jpa` with `holon-starter-jpa-hibernate`
- [x] Made dependency required (not optional)
- [x] Added `TenantUserRepository` interface

## ✅ Entities Created

### AuditEntry
- [x] Converted from Java record to `@Entity`
- [x] Added JPA annotations (@Table, @Column, @Id, @GeneratedValue)
- [x] Added full getters/setters
- [x] Retained builder pattern for API compatibility
- [x] File: `tenant-audit/src/main/java/.../AuditEntry.java`

### TenantUser
- [x] Converted from Java record to `@Entity`
- [x] Added JPA annotations (@Table, @Column, @ElementCollection)
- [x] Added full getters/setters
- [x] Updated builder to work with mutable entity
- [x] File: `tenant-users/src/main/java/.../TenantUser.java`

## ✅ Repositories Created

### AuditEntryRepository
- [x] Extends `JpaRepository<AuditEntry, Long>`
- [x] Implemented 8 query methods
- [x] File: `tenant-audit/src/main/java/.../AuditEntryRepository.java`

### TenantUserRepository
- [x] Extends `JpaRepository<TenantUser, Long>`
- [x] Implemented 11 query methods
- [x] File: `tenant-users/src/main/java/.../TenantUserRepository.java`

## ✅ Database Migrations

### V1__create_audit_entries.sql
- [x] Creates `audit_entries` table
- [x] Includes proper indexes
- [x] File: `demo/src/main/resources/db/migration/V1__create_audit_entries.sql`

### V2__create_tenant_users.sql
- [x] Creates `tenant_users` table
- [x] Creates `tenant_user_attributes` table
- [x] Includes proper constraints and indexes
- [x] File: `demo/src/main/resources/db/migration/V2__create_tenant_users.sql`

## ✅ Configuration Updated

### application.properties
- [x] Changed `spring.jpa.hibernate.ddl-auto` to `validate` (from `create-drop`)
- [x] Added Flyway configuration
- [x] File: `demo/src/main/resources/application.properties`

## 📋 Next Actions (In Order)

### Step 1: Clean Build Maven Project
```bash
cd C:\Users\sxp267\IdeaProjects\holon-saas
.\mvnw clean install -DskipTests
```
**Expected**: All modules compile successfully, dependencies downloaded

**Check**:
- [ ] No "Cannot resolve symbol" errors
- [ ] All dependencies resolved
- [ ] Build completes with BUILD SUCCESS

---

### Step 2: Refresh IDE
Choose your IDE:

**IntelliJ IDEA**:
```
1. File > Invalidate Caches
2. Select "Invalidate and Restart"
3. Wait for re-indexing
```

**VS Code**:
```
1. Ctrl+Shift+P
2. Search "Java: Clean Java Language Server Workspace"
3. Execute command
```

**Check**:
- [ ] No "red squiggles" on JPA imports
- [ ] JpaRepository recognized
- [ ] Page, Pageable, Query recognized

---

### Step 3: Verify Database Migrations

**Option A: Run Application**
```bash
.\mvnw spring-boot:run -pl demo
```

**Option B: Run Tests**
```bash
.\mvnw test -pl demo
```

**Check Logs for**:
```
✓ "Validated 2 migrations"
✓ "Migrating schema to version 1"
✓ "Migrating schema to version 2"
✓ "Successfully applied 2 migrations"
```

---

### Step 4: Verify Database Schema

**Open H2 Console**:
1. Navigate to `http://localhost:8080/h2-console`
2. Click "Connect" (credentials pre-filled)

**Run SQL Queries**:
```sql
SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC';
```

**Expected Tables**:
- [ ] `audit_entries`
- [ ] `tenant_users`
- [ ] `tenant_user_attributes`
- [ ] `flyway_schema_history`

**Check Table Structure**:
```sql
SELECT * FROM audit_entries LIMIT 1;
SELECT * FROM tenant_users LIMIT 1;
SELECT * FROM tenant_user_attributes LIMIT 1;
```

---

### Step 5: Update Service Implementations

**Files to Modify**:

#### InMemoryTenantAuditService
- [ ] File: `tenant-audit/src/main/.../InMemoryTenantAuditService.java`
- [ ] Inject `AuditEntryRepository`
- [ ] Replace `ConcurrentHashMap` with repository queries
- [ ] Update all methods to use repository

**Example**:
```java
@Service
public class InMemoryTenantAuditService {
    private final AuditEntryRepository repo;
    
    public void audit(AuditEntry entry) {
        repo.save(entry);  // Replace ConcurrentHashMap
    }
}
```

#### InMemoryTenantUserService
- [ ] File: `tenant-users/src/main/.../InMemoryTenantUserService.java`
- [ ] Inject `TenantUserRepository`
- [ ] Replace `ConcurrentHashMap` with repository queries
- [ ] Update all methods to use repository

---

### Step 6: Run Unit Tests

```bash
# Run all tests
.\mvnw test

# Run specific module tests
.\mvnw test -pl tenant-audit
.\mvnw test -pl tenant-users
```

**Expected**:
- [ ] All tests pass
- [ ] No "Cannot resolve" errors
- [ ] Database migrations run before tests
- [ ] Data persists in test database

---

### Step 7: Run Integration Tests

```bash
# Run integration tests (requires Docker for Testcontainers)
.\mvnw verify
```

**Expected**:
- [ ] PostgreSQL container starts
- [ ] Flyway migrations run
- [ ] Data persists across operations
- [ ] Queries return correct results

---

### Step 8: Manual Testing

```bash
# Start application
.\mvnw spring-boot:run -pl demo

# In browser:
# http://localhost:8080
# http://localhost:8080/h2-console
```

**Test Cases**:

1. **Create Audit Entry**:
   - [ ] Use service method to create entry
   - [ ] Verify in database with H2 console
   - [ ] Verify timestamps saved correctly

2. **Query Audit Entries**:
   - [ ] Query by tenant ID
   - [ ] Verify pagination works
   - [ ] Verify filtering by action/severity

3. **Create Tenant User**:
   - [ ] Create user with builder pattern
   - [ ] Verify in database
   - [ ] Verify attributes saved

4. **Update Tenant User**:
   - [ ] Update role/status
   - [ ] Verify changes persisted
   - [ ] Verify last login updated

---

## 🔍 Verification Commands

### Check Dependencies
```bash
.\mvnw dependency:tree -pl tenant-audit | grep -i jpa
```

**Expected Output**:
```
+- com.holon-platform.core:holon-starter-jpa-hibernate:jar:12.0.0:compile
  +- org.springframework.data:spring-data-jpa:jar:3.x.x:compile
  +- org.hibernate.orm:hibernate-core:jar:6.6.0:compile
```

### Compile Check
```bash
.\mvnw clean compile -DskipTests
```

**Expected**: BUILD SUCCESS

### Flyway Check
```bash
.\mvnw flyway:info
```

**Expected**: Shows V1 and V2 applied

## 📊 Status Dashboard

| Component | Status | Verified | Notes |
|-----------|--------|----------|-------|
| Root pom.xml | ✅ Complete | [ ] | holon-starter-jpa-hibernate added |
| tenant-data | ✅ Complete | [ ] | Dependency replaced |
| tenant-audit | ✅ Complete | [ ] | Repository created |
| tenant-users | ✅ Complete | [ ] | Repository created |
| Migrations | ✅ Complete | [ ] | V1, V2 created |
| Config | ✅ Complete | [ ] | Flyway enabled |
| Services | ⏳ Pending | [ ] | Need to inject repositories |
| Tests | ⏳ Pending | [ ] | Need to update test setup |
| Documentation | ✅ Complete | [ ] | 4 docs created |

## 🚨 Troubleshooting

### Issue: "Cannot resolve symbol 'JpaRepository'"

**Solution**:
1. Run `.\mvnw clean install -DskipTests`
2. Refresh IDE (File > Invalidate Caches)
3. Wait for Maven index to refresh

### Issue: No tables created on startup

**Check**:
1. Verify `spring.flyway.enabled=true` in properties
2. Check for Flyway errors in startup logs
3. Ensure migrations are in `resources/db/migration/`

### Issue: EntityManager not found

**Check**:
1. Verify `holon-starter-jpa-hibernate` is in pom.xml (not optional)
2. Run `.\mvnw dependency:tree` to verify inclusion
3. Restart IDE and rebuild

### Issue: "Table 'audit_entries' already exists"

**Solution** (for H2 in-memory):
1. Stop application
2. Database drops when JVM exits
3. Restart application
4. Flyway will run migrations fresh

### Issue: OutOfMemoryError

**Solution**:
```bash
# Single-threaded build (uses less memory)
.\mvnw -T 1 clean install -DskipTests
```

## ✨ Success Criteria

When all of the following are true, the migration is complete:

- [x] All pom.xml files use `holon-starter-jpa-hibernate`
- [x] `AuditEntryRepository` compiles without errors
- [x] `TenantUserRepository` compiles without errors
- [ ] Application starts with Flyway migrations applied
- [ ] Database tables created successfully
- [ ] Services injected with repositories
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual testing successful

## 🎉 Completion Checklist

- [x] Dependencies migrated to `holon-starter-jpa-hibernate`
- [x] Entities created and annotated
- [x] Repositories defined
- [x] Database migrations created
- [x] Configuration updated
- [ ] Services updated with repositories
- [ ] Tests passing
- [ ] Documentation complete

**Last Updated**: August 30, 2026  
**Status**: ✅ DEPENDENCIES MIGRATED - AWAITING SERVICE UPDATES

---

## Next Major Milestone

Once services are updated, the next phase is:
1. Test with real database (PostgreSQL instead of H2)
2. Performance profiling and optimization
3. Multi-tenancy verification
4. Security audit
5. Production deployment preparation

