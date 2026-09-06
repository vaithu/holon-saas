# JPA Migration Summary

This document summarizes the conversion of the holon-saas workspace to use JPA (Java Persistence API with Hibernate/holon-starter-jpa-hibernate) as the exclusive persistence framework.

## Changes Made

### 1. Dependencies Added

#### Root pom.xml
- Added **holon-starter-jpa-hibernate** (Holon's integrated JPA/Hibernate starter) to dependencyManagement
- This replaces the need for separate spring-boot-starter-data-jpa and explicit hibernate-core dependencies

#### tenant-data pom.xml
- Replaced `spring-boot-starter-data-jpa` with `holon-starter-jpa-hibernate`
- Removed explicit `hibernate-core` dependency (included in holon-starter)
- Kept `spring-jdbc` for legacy datasource management

#### tenant-audit pom.xml
- Replaced `spring-boot-starter-data-jpa` with `holon-starter-jpa-hibernate`

#### tenant-users pom.xml
- Replaced `spring-boot-starter-data-jpa` with `holon-starter-jpa-hibernate`

### 2. Domain Entities Converted to JPA

#### AuditEntry (tenant-audit module)
- **Before**: Immutable Java record
- **After**: JPA `@Entity` with mutable properties and getters/setters
- **Table**: `audit_entries` with indexes on (tenant_id, created_at) and (tenant_id, action)
- **Features**:
  - Auto-generated primary key (IDENTITY)
  - Enum mapping for `action` and `severity` (STRING)
  - Comprehensive getters/setters
  - Builder pattern retained for API compatibility

#### TenantUser (tenant-users module)
- **Before**: Immutable Java record with mutation helpers (withRole, withStatus, withLastLogin)
- **After**: JPA `@Entity` with mutable properties and getters/setters
- **Table**: `tenant_users` with:
  - Unique constraint on (tenant_id, email)
  - Indexes on (tenant_id, user_id), (tenant_id, email), (user_id, status)
- **Features**:
  - Auto-generated primary key (IDENTITY)
  - Enum mapping for `role` and `status` (STRING)
  - `@ElementCollection` for attributes map (separate `tenant_user_attributes` table)
  - Builder pattern updated to work with mutable entity
  - Convenience methods retained (effectiveName, isActive, hasAtLeastRole)
  - New helper methods (getAttribute, setAttribute)

### 3. JPA Repositories Created

#### AuditEntryRepository (tenant-audit module)
Located: `tenant-audit/src/main/java/com/holonplatform/multitenant/audit/AuditEntryRepository.java`

Methods:
- `findByTenantId(String tenantId, Pageable pageable)` - Paginated list of tenant audit entries
- `findByTenantIdAndAction(...)` - Filter by action
- `findByTenantIdAndActor(...)` - Filter by actor
- `findByTenantIdAndSeverity(...)` - Filter by severity
- `findByTenantIdAndResourceTypeAndResourceId(...)` - Find entries for specific resource
- `findByTenantIdAndDateRange(...)` - Custom query for date range filtering
- `countByTenantId(String tenantId)` - Count entries for tenant
- `findFirstByTenantIdOrderByCreatedAtDesc(...)` - Get most recent entry

#### TenantUserRepository (tenant-users module)
Located: `tenant-users/src/main/java/com/holonplatform/multitenant/users/TenantUserRepository.java`

Methods:
- `findByTenantIdAndUserId(...)` - Find specific user membership
- `findByTenantIdAndEmail(...)` - Find by email within tenant
- `findByTenantId(String tenantId, Pageable pageable)` - Paginated list
- `findByTenantIdAndRole(...)` - Filter by role
- `findByTenantIdAndStatus(...)` - Filter by status
- `countByTenantId(String tenantId)` - Count users in tenant
- `countActiveByTenantId(...)` - Custom query for active users
- `findByUserId(String userId)` - Find all tenant memberships for a user
- `existsByTenantIdAndEmail(...)` - Check email existence
- `deleteByTenantId(String tenantId)` - Cascade delete
- `findByTenantIdAndRoleOrderByCreatedAtDesc(...)` - List with sorting

### 4. Flyway Database Migrations Created

Located: `demo/src/main/resources/db/migration/`

#### V1__create_audit_entries.sql
Creates `audit_entries` table with:
- Primary key (id BIGINT AUTO_INCREMENT)
- Columns: tenant_id, action, actor, resource_type, resource_id, description, ip_address, severity, created_at
- Indexes for optimal query performance

#### V2__create_tenant_users.sql
Creates `tenant_users` table with:
- Primary key (id BIGINT AUTO_INCREMENT)
- Columns: user_id, tenant_id, email, display_name, role, status, created_at, last_login_at
- Unique constraint on (tenant_id, email)
- Indexes for tenant queries, email lookups, and status filtering
- Creates `tenant_user_attributes` table for ElementCollection mapping

### 5. Application Configuration Updated

**File**: `demo/src/main/resources/application.properties`

Changes:
- Switched `spring.jpa.hibernate.ddl-auto` from `create-drop` to `validate`
  - ✅ Flyway now handles schema creation and migrations
  - ✅ Hibernate only validates existing schema
- Added `spring.jpa.properties.hibernate.format_sql=true` for readable SQL logging
- Added Flyway configuration:
  - `spring.flyway.enabled=true`
  - `spring.flyway.locations=classpath:db/migration`

## Next Steps Required

### 1. Update Service Implementations

Services currently using in-memory `ConcurrentHashMap` storage need to be updated to use the new JPA repositories:

**InMemoryTenantAuditService** (tenant-audit module)
- Inject `AuditEntryRepository`
- Replace `ConcurrentHashMap<String, Queue<AuditEntry>>` with repository queries
- Update methods to use JPA queries instead of in-memory collections

**InMemoryTenantUserService** (tenant-users module)
- Inject `TenantUserRepository`
- Replace `ConcurrentHashMap<String, TenantUser>` with repository queries
- Update email index to use `findByTenantIdAndEmail()`
- Update all query methods to use JPA queries

### 2. Test Updates

Unit tests currently mocking in-memory services need to:
- Use `@DataJpaTest` for repository testing
- Configure Testcontainers for PostgreSQL (if not using H2)
- Update test data setup to persist via repositories
- Mock repositories using `@MockBean` where needed

### 3. Schema Migration for Other Modules

Review other domain entities in modules like:
- `tenant-billing`
- `tenant-permissions`
- `tenant-quotas`
- `tenant-settings`
- `tenant-onboarding`

These may also need JPA conversion and migration files.

### 4. Transaction Management

Review service layer for:
- `@Transactional` annotations on service methods
- Proper cascade delete configurations in entities
- Lazy vs Eager loading strategies
- N+1 query problems in repository queries

### 5. Multi-Tenancy Integration

Ensure:
- Tenant isolation is enforced at the repository query level
- TenantContext is properly propagated for JPA queries
- Consider using Hibernate Filters for automatic tenant filtering

### 6. Performance Optimization

- Add `@Query` annotations for complex queries
- Use `@EntityGraph` for eager loading relationships
- Profile and optimize generated SQL using Hibernate statistics
- Consider pagination limits for large datasets

## Configuration Recommendations

For production deployments:

```properties
# Use Validate mode with Flyway in production
spring.jpa.hibernate.ddl-auto=validate

# Connection pooling
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5

# Hibernate optimizations
spring.jpa.properties.hibernate.batch_size=20
spring.jpa.properties.hibernate.fetch_size=50
spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# Query logging (disable in production)
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.use_sql_comments=true
```

## Backward Compatibility Notes

- Entity builders maintain backward compatibility with record constructor API
- Convenience methods (`effectiveName()`, `isActive()`, `hasAtLeastRole()`) preserved
- Record mutation methods (`with*()`) removed - use setters instead
- Services interface remains unchanged (repositories are internal implementations)

## Testing the Migration

1. **Unit Tests**: Verify repository queries return expected results
2. **Integration Tests**: Use `@DataJpaTest` with test database
3. **Service Tests**: Mock repositories and test business logic
4. **Manual Testing**: Run the application and verify CRUD operations
5. **Migration Verification**: Check Flyway migration status at startup

## Known Issues & Considerations

1. **ElementCollection Performance**: The `tenant_user_attributes` map should be kept small (< 100 entries) as all attributes are loaded eagerly by default.

2. **Pagination**: All list operations use pagination to prevent loading large datasets into memory.

3. **Date/Time Handling**: All timestamps use `Instant` type mapped to TIMESTAMP columns (UTC-based).

4. **Enum Storage**: Enums are stored as STRING type for better database portability.

5. **Null Handling**: Nullable columns are explicitly configured; defaults are handled in constructors/builders.

---

**Migration Date**: August 30, 2026  
**Java Version**: 25  
**Spring Boot Version**: 4.1.0  
**Hibernate Version**: 6.6.0.Final  
**Vaadin Version**: 25.2.1



