# Holon Starter JPA Hibernate Integration

## Overview

The workspace has been updated to use **holon-starter-jpa-hibernate** instead of the standard Spring Boot Data JPA starter. This provides seamless integration with Holon Platform's ecosystem while maintaining full Spring Data JPA compatibility.

## What Changed

### Before
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-core</artifactId>
    <version>6.6.0.Final</version>
</dependency>
```

### After
```xml
<dependency>
    <groupId>com.holon-platform.core</groupId>
    <artifactId>holon-starter-jpa-hibernate</artifactId>
</dependency>
```

## Benefits of holon-starter-jpa-hibernate

1. **Integrated JPA/Hibernate** - Includes Spring Data JPA + Hibernate 6.6+ with proper version alignment
2. **Holon Datastore Compatibility** - Seamless integration with Holon Platform's Datastore abstraction layer
3. **Automatic Configuration** - Spring Boot auto-configuration for common JPA/Hibernate settings
4. **Version Management** - Holon Platform ensures compatibility between all components
5. **Entity Metadata** - Enhanced support for Holon's @PropertySet and property set interfaces

## What's Included

`holon-starter-jpa-hibernate` provides:

```
├── Spring Data JPA (data query/repository support)
├── Hibernate ORM 6.6.0+
├── Jakarta Persistence API 3.x
├── Jakarta Transaction API
├── Holon Core JPA integration
└── Auto-configuration for Spring Boot
```

## Updated Modules

### ✅ tenant-data
- Now uses `holon-starter-jpa-hibernate`
- Manages multi-tenant DataSource routing and Flyway migrations
- Uses JPA/Hibernate for all database operations

### ✅ tenant-audit
- Now uses `holon-starter-jpa-hibernate`
- Defines `AuditEntry` JPA entity and repository
- Provides audit log persistence with Holon integration

### ✅ tenant-users
- Now uses `holon-starter-jpa-hibernate`
- Defines `TenantUser` JPA entity and repository
- Supports user membership and role management

## Key Features

### 1. Repository Support
All repositories now automatically extend `JpaRepository` and support:
```java
@Repository
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {
    Page<AuditEntry> findByTenantId(String tenantId, Pageable pageable);
    // ... custom queries
}
```

### 2. Entity Persistence
JPA entities are automatically managed:
```java
@Entity
@Table(name = "audit_entries")
public class AuditEntry { ... }
```

### 3. Holon Property Sets Integration
Entities can be enhanced with Holon property sets for advanced UI features:
```java
@PropertySet
public interface AuditEntryProperties {
    // Property metadata for UI binding
}
```

## Application Configuration

The following settings in `application.properties` work seamlessly with holon-starter-jpa-hibernate:

```properties
# JPA/Hibernate Configuration
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=validate        # Flyway handles schema
spring.jpa.show-sql=false
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.format_sql=true

# Flyway Database Migration
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

## Migration to Holon Datastore (Future)

With holon-starter-jpa-hibernate in place, services can optionally be enhanced with Holon's Datastore abstraction:

```java
@Service
public class AuditService {
    private final DataStore dataStore;
    
    // Use Holon's DataStore query API
    List<AuditEntry> results = dataStore
        .query(AuditEntry.class)
        .filter(query("tenantId").eq("acme"))
        .list();
}
```

This provides:
- ✅ Unified query API across different datasources (SQL, NoSQL, etc.)
- ✅ Automatic type-safe queries
- ✅ Multi-tenant filtering support
- ✅ Property set integration

## Compilation and IDE Integration

### Resolving IDE Warnings

If your IDE shows "Cannot resolve symbol" for JPA classes after switching to holon-starter-jpa-hibernate:

1. **IntelliJ IDEA**:
   - Go to `File > Invalidate Caches`
   - Select "Invalidate and Restart"
   - IDE will re-index dependencies

2. **VS Code**:
   - Press `Ctrl+Shift+P`
   - Search "Java: Clean Java Language Server Workspace"
   - Execute command

3. **Build with Maven**:
   ```bash
   .\mvnw clean install -DskipTests
   ```

### Dependency Download

The first build after changing to holon-starter-jpa-hibernate will download:
- Holon Core JPA module
- Spring Data JPA
- Hibernate 6.6.0+
- Associated transitive dependencies

Depending on internet speed, this may take 30-60 seconds.

## Testing

### Unit Tests with JPA Repositories

```java
@DataJpaTest
class AuditEntryRepositoryTest {
    @Autowired
    private AuditEntryRepository repository;
    
    @Test
    void shouldFindByTenantId() {
        AuditEntry entry = new AuditEntry(...);
        repository.save(entry);
        
        Page<AuditEntry> results = 
            repository.findByTenantId("test", PageRequest.of(0, 10));
        
        assertThat(results).hasSize(1);
    }
}
```

### Integration Tests with Spring Boot

```java
@SpringBootTest
class AuditServiceIntegrationTest {
    @Autowired
    private AuditService service;
    
    @Test
    void shouldPersistAuditEntry() {
        service.audit(AuditEntry.builder(...).build());
        // Verify using repository
    }
}
```

## Production Deployment

### Database Schema Management

Use Flyway migrations exclusively with holon-starter-jpa-hibernate:

```properties
# Always validate, never auto-generate
spring.jpa.hibernate.ddl-auto=validate

# Flyway handles all schema creation/migration
spring.flyway.enabled=true
spring.flyway.clean-disabled=true  # Prevent accidental drops
```

### Connection Pooling

Configure HikariCP for optimal performance:

```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

### Query Optimization

Enable Hibernate statistics for production monitoring:

```properties
# Log slow queries (production: disable in logs)
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.generate_statistics=true
spring.jpa.properties.hibernate.use_sql_comments=true

# Batch operations for better performance
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true
```

## Troubleshooting

### Issue: "Cannot resolve symbol 'JpaRepository'"

**Cause**: Maven hasn't downloaded holon-starter-jpa-hibernate yet

**Solution**:
```bash
.\mvnw clean install -DskipTests
# Then refresh IDE
```

### Issue: "No suitable DatabaseFactory found"

**Cause**: Flyway can't determine database dialect

**Solution**: Ensure `spring.jpa.database-platform` is set in `application.properties`

### Issue: Application starts but no tables created

**Cause**: Flyway disabled or migrations not found

**Solution**:
1. Check `spring.flyway.enabled=true`
2. Verify migration files in `resources/db/migration/`
3. Check logs for "Successfully applied X migrations"

### Issue: OutOfMemoryError during build

**Cause**: Maven building too many modules in parallel

**Solution**:
```bash
.\mvnw -T 1 clean install  # Single-threaded build
```

## Summary

The migration to **holon-starter-jpa-hibernate** provides:

✅ Full Spring Data JPA repository support  
✅ Hibernate 6.6+ ORM engine  
✅ Holon Platform integration  
✅ Simplified dependency management  
✅ Consistent version alignment  
✅ Production-ready configuration  

All existing repositories (`AuditEntryRepository`, `TenantUserRepository`) work identically with holon-starter-jpa-hibernate, while gaining access to Holon's ecosystem features.

---

**Next Steps**:
1. Run `.\mvnw clean install -DskipTests`
2. Update service implementations to use repositories
3. Verify database migrations run on startup
4. Test CRUD operations with H2 console
5. Deploy to production with Flyway schema management


