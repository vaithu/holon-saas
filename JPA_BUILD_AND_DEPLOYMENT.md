# JPA Conversion - Build & Deployment Guide

## Building the Project

### Step 1: Ensure IDE Recognizes New Dependencies

After the JPA migration, your IDE may show "Cannot resolve symbol" warnings for Spring Data JPA classes until Maven downloads and indexes the new dependencies.

**Note**: This project uses **holon-starter-jpa-hibernate**, which is Holon Platform's integrated JPA/Hibernate starter. It provides:
- ✅ Spring Data JPA with JpaRepository support
- ✅ Hibernate 6.6.0+ ORM
- ✅ Holon Platform's JPA integration
- ✅ Holon Datastore compatibility

**Solution**:
```bash
# From the workspace root:
.\mvnw clean install -DskipTests

# Or if using Unix/Linux/Mac:
./mvnw clean install -DskipTests
```

This will:
- ✅ Download and cache all JPA/Hibernate dependencies
- ✅ Compile all modules with the new JPA entities
- ✅ Run Flyway migrations to create tables
- ✅ Skip tests (which may need updates to work with JPA)

### Step 2: Refresh IDE

After the Maven build completes:
- **IntelliJ IDEA**: Go to `File > Invalidate Caches > Invalidate and Restart`
- **VS Code**: Reload the Java Language Server: `Java: Clean Java Language Server Workspace`

This will re-index the classpath and resolve all symbol errors.

## Running the Application

### Development Mode (H2 In-Memory Database)

The demo application uses H2 in-memory database configured in `application.properties`:

```bash
cd demo
../mvnw spring-boot:run
```

Or from the root:
```bash
./mvnw spring-boot:run -pl demo
```

The application will:
1. ✅ Run Flyway migrations on startup (V1, V2)
2. ✅ Create `audit_entries` and `tenant_users` tables
3. ✅ Launch at `http://localhost:8080`
4. ✅ H2 Console available at `http://localhost:8080/h2-console`

**H2 Console Credentials** (from application.properties):
- URL: `jdbc:h2:mem:demodb`
- User: `sa`
- Password: (empty)

### Verifying JPA Setup

Once the application is running, verify the tables were created:

**Option 1: H2 Web Console**
```
1. Navigate to http://localhost:8080/h2-console
2. Click "Connect" (credentials already filled)
3. Run SQL queries:
   - SELECT * FROM audit_entries;
   - SELECT * FROM tenant_users;
   - SELECT * FROM tenant_user_attributes;
```

**Option 2: Application Logs**
Look for Flyway migration messages during startup:
```
INFO ... Flyway : Validated 2 migrations (execution time 5ms)
INFO ... Flyway : Creating schema history table "PUBLIC"."FLYWAY_SCHEMA_HISTORY"
INFO ... Flyway : Current version of schema "PUBLIC": << Empty >>
INFO ... Flyway : Migrating schema "PUBLIC" to version 1
INFO ... Flyway : Migrating schema "PUBLIC" to version 2
INFO ... Flyway : Successfully applied 2 migrations to schema "PUBLIC" (execution time 23ms)
```

**Option 3: Debug Query Logging**
Add to `application.properties` to see all generated SQL:
```properties
# Show all SQL queries
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.use_sql_comments=true
spring.jpa.properties.hibernate.format_sql=true

# Log query performance
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.stat=DEBUG
```

## Updating Services to Use JPA Repositories

The following services still need to be updated to use the new JPA repositories instead of in-memory storage:

### 1. InMemoryTenantAuditService
**Location**: `tenant-audit/src/main/java/com/holonplatform/multitenant/audit/internal/InMemoryTenantAuditService.java`

**Current Implementation**: Uses `ConcurrentHashMap<String, Queue<AuditEntry>>`

**Required Changes**:
```java
@Service
public class InMemoryTenantAuditService implements TenantAuditService {
    
    private final AuditEntryRepository repository;  // Inject repository
    
    @Autowired
    public InMemoryTenantAuditService(AuditEntryRepository repository) {
        this.repository = repository;
    }
    
    @Override
    @Transactional
    public void audit(AuditEntry entry) {
        repository.save(entry);  // Replace ConcurrentHashMap.add()
    }
    
    @Override
    public Page<AuditEntry> getEntries(String tenantId, Pageable pageable) {
        return repository.findByTenantId(tenantId, pageable);  // Replace from map
    }
    
    // ... more methods using repository queries
}
```

### 2. InMemoryTenantUserService
**Location**: `tenant-users/src/main/java/com/holonplatform/multitenant/users/internal/InMemoryTenantUserService.java`

**Current Implementation**: Uses `ConcurrentHashMap<String, TenantUser>` with email index

**Required Changes**:
```java
@Service
public class InMemoryTenantUserService implements TenantUserService {
    
    private final TenantUserRepository repository;  // Inject repository
    
    @Autowired
    public InMemoryTenantUserService(TenantUserRepository repository) {
        this.repository = repository;
    }
    
    @Override
    @Transactional
    public TenantUser createUser(TenantUser user) {
        return repository.save(user);  // Replace ConcurrentHashMap.put()
    }
    
    @Override
    public Optional<TenantUser> getUserByEmail(String tenantId, String email) {
        return repository.findByTenantIdAndEmail(tenantId, email);  // Replace from map
    }
    
    @Override
    public Page<TenantUser> listUsers(String tenantId, Pageable pageable) {
        return repository.findByTenantId(tenantId, pageable);  // Replace paginate from map
    }
    
    // ... more methods using repository queries
}
```

### Service Layer Pattern

For each service update:

1. **Inject the repository**:
   ```java
   private final SomeRepository repository;
   ```

2. **Add @Transactional to mutation methods**:
   ```java
   @Override
   @Transactional
   public void doSomething() { ... }
   ```

3. **Replace in-memory calls with repository queries**:
   ```java
   // Before: map.put(key, value)
   // After:
   repository.save(entity);
   
   // Before: map.get(key)
   // After:
   repository.findById(id)
   
   // Before: map.remove(key)
   // After:
   repository.deleteById(id)
   
   // Before: map.values().stream().filter(...)
   // After:
   repository.findByTenantId(..., pageable)
   ```

## Database Migration Strategy

### For Development/Demo (Current Setup)
- Uses in-memory H2 database
- Flyway creates tables on each startup
- Safe to drop/recreate during development

### For Production (Recommended)
```properties
# Use validate mode - Flyway handles schema
spring.jpa.hibernate.ddl-auto=validate

# Enable verbose Flyway logging
logging.level.org.flywaydb.core=DEBUG
```

**Steps for Production Deployment**:
1. Run Flyway migrations: `./mvnw flyway:migrate`
2. Validate schema: Hibernate will check compatibility
3. Deploy application
4. Monitor migration logs for success

### Adding New Migrations

If you add new entities or modify schema:

1. Create a new migration file:
   ```
   V3__add_new_table.sql
   V4__add_column_to_existing_table.sql
   ```

2. Place in: `src/main/resources/db/migration/`

3. Flyway automatically runs on next startup

**Naming Convention**: `V{number}__{description}.sql`
- Version numbers must be sequential (V1, V2, V3...)
- Use underscores for word separation
- Flyway enforces versioning

## Testing with JPA

### Unit Tests (Repository Tests)

Use `@DataJpaTest` annotation:

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class AuditEntryRepositoryTest {
    
    @Autowired
    private AuditEntryRepository repository;
    
    @Test
    void shouldFindByTenantId() {
        // Arrange
        AuditEntry entry = new AuditEntry(...);
        repository.save(entry);
        
        // Act
        Page<AuditEntry> results = repository.findByTenantId(
            "test-tenant", PageRequest.of(0, 10)
        );
        
        // Assert
        assertThat(results).hasSize(1);
    }
}
```

### Integration Tests

Use `@SpringBootTest` with Testcontainers for real PostgreSQL:

```java
@SpringBootTest
@Testcontainers
class AuditServiceIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> container = 
        new PostgreSQLContainer<>("postgres:15-alpine");
    
    @Autowired
    private TenantAuditService auditService;
    
    @Test
    void shouldPersistAuditEntry() {
        // Arrange & Act
        AuditEntry entry = AuditEntry.builder("acme", AuditAction.PLAN_CHANGED)
            .actor("admin@acme.com")
            .build();
        auditService.audit(entry);
        
        // Assert
        Page<AuditEntry> results = auditService.getEntries(
            "acme", PageRequest.of(0, 10)
        );
        assertThat(results).hasSize(1);
    }
    
    @DynamicPropertySource
    static void setDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }
}
```

### Running Tests

```bash
# Run all tests (requires Docker for Testcontainers)
./mvnw test

# Skip integration tests
./mvnw test -DskipITs

# Run specific test class
./mvnw test -Dtest=AuditEntryRepositoryTest
```

## Performance Optimization Checklist

After converting to JPA, verify:

- [ ] Connection pooling configured (HikariCP)
- [ ] Flyway migrations run successfully on startup
- [ ] Entity indexes created and used for queries
- [ ] Pagination implemented for list operations
- [ ] N+1 query problems identified and fixed
- [ ] Lazy/Eager loading strategies optimized
- [ ] Transaction boundaries properly defined
- [ ] Query performance logged and monitored

## Troubleshooting

### Issue: "Cannot resolve symbol" for JPA classes

**Solution**:
```bash
./mvnw clean install -DskipTests
# Then refresh IDE cache (Ctrl+Shift+A > "Invalidate Caches" in IntelliJ)
```

### Issue: Flyway migration fails with "Table already exists"

**Solution** (for H2 in-memory, delete database):
```bash
# Stop the application
# Database is deleted when JVM exits
# Restart application
```

For PostgreSQL, you may need to manually drop tables or use `spring.flyway.cleanDisabled=false` carefully.

### Issue: Application starts but no data persisted

**Verify**:
1. Flyway migrations ran (check logs for "Successfully applied")
2. Services are using `@Transactional` on write methods
3. Repository is injected and not null
4. Transactions aren't being rolled back

### Issue: OutOfMemoryError with ElementCollection

**Solution** (for `tenant_user_attributes`):
- Limit attribute count per user
- Consider extracting to separate table if > 100 attributes
- Use `@Fetch(FetchMode.SELECT)` instead of JOIN for large collections

---

**Next Steps**:
1. Run `./mvnw clean install -DskipTests`
2. Update `InMemoryTenantAuditService` to use `AuditEntryRepository`
3. Update `InMemoryTenantUserService` to use `TenantUserRepository`
4. Run application with `./mvnw spring-boot:run -pl demo`
5. Verify data persistence in H2 console
6. Update and run tests


