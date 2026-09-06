# 🚀 Phase 4: REST APIs & E2E Testing - Comprehensive Plan

**Target Date**: September 13-27, 2026  
**Duration**: 2 weeks  
**Objective**: Add REST API layer and complete E2E testing

---

## 📋 Phase 4 Overview

### Goals
1. **REST API Layer**: Auto-generate REST endpoints using Holon Spring REST
2. **API Documentation**: OpenAPI/Swagger documentation
3. **E2E Testing**: Playwright browser automation tests
4. **Performance**: Load testing and optimization
5. **Deployment**: Prepare for production deployment

### Scope

| Component | Deliverables | Effort |
|-----------|--------------|--------|
| REST APIs | 4 REST controllers + HATEOAS | 3 days |
| OpenAPI | Swagger documentation + UI | 1 day |
| E2E Tests | Playwright test suite | 3 days |
| Performance | Load testing + optimization | 2 days |
| Deployment | Docker + K8s configs | 2 days |
| **Total** | | **11 days** |

---

## 🎯 Step 1: REST API Controllers (Days 1-3)

### 1.1 Add Dependency
Update root `pom.xml`:
```xml
<dependency>
    <groupId>com.holon-platform.core</groupId>
    <artifactId>holon-spring-rest</artifactId>
    <version>${holon-core.version}</version>
</dependency>
```

### 1.2 Create Audit REST Controller
**File**: `tenant-audit/src/main/java/.../rest/AuditRestController.java`

```java
@RestController
@RequestMapping("/api/v1/audit")
@HolonPermission("AUDIT_VIEW")
public class AuditRestController {
    
    private final AuditDatastoreService service;
    
    @GetMapping
    public ResponseEntity<Page<AuditEntry>> list(
        @RequestParam(required = false) String action,
        @PageableDefault(size = 20) Pageable pageable) {
        // Return paginated audit entries with HATEOAS links
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<AuditEntry> get(@PathVariable Long id) {
        // Return specific audit entry
    }
    
    @PostMapping
    @HolonPermission("AUDIT_WRITE")
    public ResponseEntity<AuditEntry> create(@RequestBody AuditEntryRequest req) {
        // Create new audit entry
    }
}
```

**Features**:
- [ ] Paginated listing with sorting
- [ ] Item detail endpoint
- [ ] Create endpoint (if needed)
- [ ] HATEOAS links (self, first, last, next, prev)
- [ ] @HolonPermission guards
- [ ] Input validation
- [ ] Error handling

### 1.3 Create Users REST Controller
**File**: `tenant-users/src/main/java/.../rest/UsersRestController.java`

```java
@RestController
@RequestMapping("/api/v1/users")
public class UsersRestController {
    
    @GetMapping
    @HolonPermission("USER_VIEW")
    public ResponseEntity<Page<TenantUser>> list(
        @RequestParam(required = false) TenantRole role,
        @PageableDefault(size = 20) Pageable pageable) {
        // List users with role filter
    }
    
    @GetMapping("/{id}")
    @HolonPermission("USER_VIEW")
    public ResponseEntity<TenantUser> get(@PathVariable Long id) {
        // Get user details
    }
    
    @PostMapping
    @HolonPermission("USER_INVITE")
    public ResponseEntity<TenantUser> invite(@RequestBody UserInviteRequest req) {
        // Invite new user to tenant
    }
    
    @PutMapping("/{id}/role")
    @HolonPermission("USER_MANAGE")
    public ResponseEntity<TenantUser> updateRole(
        @PathVariable Long id,
        @RequestBody RoleChangeRequest req) {
        // Update user role
    }
    
    @DeleteMapping("/{id}")
    @HolonPermission("USER_REMOVE")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        // Remove user from tenant
    }
}
```

**Features**:
- [ ] List with role filtering
- [ ] Get user details
- [ ] Invite users
- [ ] Update roles
- [ ] Remove users
- [ ] @HolonPermission on each endpoint
- [ ] Validation with @Valid
- [ ] Error responses (400, 403, 404)

### 1.4 Create Billing REST Controller
**File**: `tenant-billing/src/main/java/.../rest/BillingRestController.java`

```java
@RestController
@RequestMapping("/api/v1/billing")
public class BillingRestController {
    
    // Invoices
    @GetMapping("/invoices")
    public ResponseEntity<Page<Invoice>> listInvoices(
        @RequestParam(required = false) InvoiceStatus status,
        @PageableDefault(size = 20) Pageable pageable) {
        // List invoices with status filter
    }
    
    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<Invoice> getInvoice(@PathVariable String invoiceId) {
        // Get invoice details
    }
    
    // Subscriptions
    @GetMapping("/subscription")
    public ResponseEntity<Subscription> getSubscription() {
        // Get current subscription
    }
    
    @PostMapping("/subscription/upgrade")
    @HolonPermission("BILLING_MANAGE")
    public ResponseEntity<Subscription> upgrade(@RequestBody PlanChangeRequest req) {
        // Upgrade subscription plan
    }
    
    @PostMapping("/subscription/cancel")
    @HolonPermission("BILLING_MANAGE")
    public ResponseEntity<Subscription> cancel() {
        // Cancel subscription
    }
}
```

**Features**:
- [ ] List invoices with filtering
- [ ] Get invoice details
- [ ] Get current subscription
- [ ] Upgrade subscription
- [ ] Cancel subscription
- [ ] Payment tracking
- [ ] Invoice export

---

## 🔗 Step 2: HATEOAS & API Documentation (Day 4)

### 2.1 Add HATEOAS Support
Update `application.properties`:
```properties
holon.rest.enable-hateoas=true
holon.rest.hateoas-prefix=/api/v1
```

**Implement in each controller**:
```java
@GetMapping
public ResponseEntity<PagedModel<EntityModel<AuditEntry>>> list(
    @PageableDefault(size = 20) Pageable pageable,
    PagedResourcesAssembler<AuditEntry> assembler) {
    
    Page<AuditEntry> page = service.find(pageable);
    return ResponseEntity.ok(assembler.toModel(page));
}
```

### 2.2 Add OpenAPI/Swagger
**Update pom.xml**:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.0.0</version>
</dependency>
```

**Update application.properties**:
```properties
springdoc.api-docs.path=/api/docs
springdoc.swagger-ui.path=/api/swagger-ui.html
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.operations-sorter=method
springdoc.swagger-ui.tags-sorter=alpha
```

### 2.3 Annotate Controllers for Swagger
```java
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit", description = "Audit log API endpoints")
public class AuditRestController {
    
    @GetMapping
    @Operation(summary = "List audit entries", description = "Get paginated audit log entries for current tenant")
    @ApiResponse(responseCode = "200", description = "Successful retrieval")
    @ApiResponse(responseCode = "403", description = "Access denied")
    public ResponseEntity<Page<AuditEntry>> list(
        @ParameterObject Pageable pageable) {
        // ...
    }
}
```

---

## 🧪 Step 3: E2E Testing with Playwright (Days 5-7)

### 3.1 Add Playwright Dependency
**File**: `pom.xml`
```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.40.0</version>
    <scope>test</scope>
</dependency>
```

### 3.2 Audit List View Test
**File**: `tenant-vaadin/src/test/java/.../AuditListingViewTest.java`

```java
@Test
void testAuditListingViewRenders() {
    page.navigate("http://localhost:8080/audit");
    
    // Verify page elements
    expect(page.locator("text=Audit Log")).toBeVisible();
    expect(page.locator("[role=grid]")).toBeVisible();
    
    // Verify grid has rows
    var rows = page.locator("[role=row]");
    expect(rows).toHaveCount(20, new LocatorAssertions.ToHaveCountOptions().setTimeout(5000));
}

@Test
void testAuditListingPagination() {
    page.navigate("http://localhost:8080/audit");
    
    // Click next page
    page.locator("button:has-text('Next')").click();
    
    // Verify rows changed
    expect(page.locator("[role=row]").first()).not.toContainText("first_row_id");
}

@Test
void testAuditSorting() {
    page.navigate("http://localhost:8080/audit");
    
    // Click Action column header
    page.locator("text=Action").click();
    
    // Verify sort order changed
    var firstAction = page.locator("[role=row]").first().locator("td").nth(2);
    expect(firstAction).toContainText("TENANT_ONBOARDED");
}
```

### 3.3 User Listing View Test
**File**: `tenant-vaadin/src/test/java/.../UserListingViewTest.java`

```java
@Test
void testUserListingViewRenders() {
    page.navigate("http://localhost:8080/users");
    
    expect(page.locator("text=Team Members")).toBeVisible();
    expect(page.locator("[role=grid]")).toBeVisible();
}

@Test
void testOnlyAdminCanInviteUsers() {
    // Login as VIEWER
    loginAsViewer();
    page.navigate("http://localhost:8080/users");
    
    // Verify "Invite User" button is hidden
    expect(page.locator("button:has-text('Invite User')")).not.toBeVisible();
    
    // Login as ADMIN
    logout();
    loginAsAdmin();
    page.navigate("http://localhost:8080/users");
    
    // Verify button is visible
    expect(page.locator("button:has-text('Invite User')")).toBeVisible();
}
```

### 3.4 API Testing
**File**: `src/test/java/.../AuditRestControllerTest.java`

```java
@Test
void testListAuditEntries() {
    given()
        .header("Authorization", "Bearer " + token)
    .when()
        .get("/api/v1/audit?page=0&size=20")
    .then()
        .statusCode(200)
        .body("content", hasSize(20))
        .body("_links.self.href", notNullValue());
}

@Test
void testUnauthorizedAccessDenied() {
    given()
        // No Authorization header
    .when()
        .get("/api/v1/audit")
    .then()
        .statusCode(401);
}
```

---

## 📊 Step 4: Performance Testing (Days 8-9)

### 4.1 Load Testing with JMeter
**Setup**:
- 1000 concurrent users
- Ramp-up time: 1 minute
- Duration: 5 minutes
- Success criteria: 95th percentile < 500ms

**Test Scenarios**:
1. List audit entries (100 RPS)
2. List users (100 RPS)
3. List invoices (50 RPS)
4. Mixed load (250 RPS total)

### 4.2 Optimize Based on Results
- [ ] Query optimization (add indexes)
- [ ] Caching (Redis for frequently accessed data)
- [ ] Connection pooling (HikariCP tuning)
- [ ] Lazy loading (Vaadin lazy scroll)
- [ ] API response compression (gzip)

---

## 🐳 Step 5: Deployment (Days 10-11)

### 5.1 Docker Configuration
**File**: `Dockerfile`
```dockerfile
FROM openjdk:25-slim
ARG JAR_FILE=demo/target/*.jar
COPY ${JAR_FILE} app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app.jar"]
```

**File**: `docker-compose.yml`
```yaml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/holon_saas
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=postgres
    depends_on:
      - postgres
  
  postgres:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=holon_saas
      - POSTGRES_PASSWORD=postgres
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

### 5.2 Kubernetes Manifests (Optional)
**File**: `k8s/deployment.yaml`
- Deployment with 3 replicas
- Service with LoadBalancer
- ConfigMap for app config
- Secret for database credentials
- Horizontal Pod Autoscaler

---

## ✅ Success Criteria

### REST APIs
- [ ] 4 REST controllers implemented
- [ ] All endpoints have @HolonPermission guards
- [ ] Pagination working (20, 50, 100 items per page)
- [ ] Sorting working on all endpoints
- [ ] Filtering working (by action, role, status)
- [ ] HATEOAS links present
- [ ] Error responses (400, 403, 404, 500)

### API Documentation
- [ ] Swagger UI accessible at /api/swagger-ui.html
- [ ] All endpoints documented
- [ ] All request/response schemas defined
- [ ] Examples provided for each endpoint

### E2E Tests
- [ ] 20+ Playwright test cases
- [ ] Happy path tests
- [ ] Error scenario tests
- [ ] Permission-denied tests
- [ ] Performance baseline tests
- [ ] 100% pass rate

### Performance
- [ ] Response time < 500ms at 95th percentile
- [ ] Throughput > 200 RPS
- [ ] No memory leaks under sustained load
- [ ] Connection pooling working correctly

---

## 📈 Quality Metrics

```
API Coverage:           100% (all 4 entities)
Endpoint Count:         15+ (list, get, create, update, delete)
Test Count:             25+ (API + E2E)
Documentation:          100% (OpenAPI/Swagger)
Performance:            ✅ Sub-500ms at 95th percentile
Security:               ✅ All endpoints guarded
```

---

## 📅 Timeline

```
Week 1 (Sept 13-20):
  Day 1-3:  REST controllers implementation
  Day 4:    HATEOAS + OpenAPI/Swagger
  Day 5-7:  Playwright E2E testing

Week 2 (Sept 21-27):
  Day 8-9:  Performance testing + optimization
  Day 10-11: Deployment preparation + validation
  Day 12:   Buffer for fixes and polish
```

---

## 🔗 Integration with Phase 3

**Builds On**:
- PropertySet metadata for API schema generation
- Datastore services for backend queries
- Holon security framework (@HolonPermission)
- Vaadin views for UI testing

**Enables Phase 5**:
- Mobile app development (using REST APIs)
- Third-party integrations
- Advanced reporting
- Webhooks and automation

---

## 📋 Checklist

### Phase 4a: REST APIs
- [ ] Add holon-spring-rest dependency
- [ ] Create 4 REST controllers
- [ ] Add pagination support
- [ ] Add filtering support
- [ ] Add HATEOAS links
- [ ] Add @HolonPermission guards
- [ ] Add input validation
- [ ] Add error handling
- [ ] Document with Swagger/OpenAPI

### Phase 4b: E2E Testing
- [ ] Add Playwright dependency
- [ ] Create Audit view tests (5+)
- [ ] Create User view tests (5+)
- [ ] Create API tests (10+)
- [ ] Test permission denied scenarios
- [ ] Test multi-tenant isolation
- [ ] Test pagination/sorting
- [ ] Generate test report

### Phase 4c: Performance
- [ ] Run JMeter load tests
- [ ] Measure response times
- [ ] Identify bottlenecks
- [ ] Optimize queries
- [ ] Add caching where needed
- [ ] Document performance profile
- [ ] Create performance baseline

### Phase 4d: Deployment
- [ ] Create Dockerfile
- [ ] Create docker-compose.yml
- [ ] Test Docker build
- [ ] Create K8s manifests (optional)
- [ ] Document deployment process
- [ ] Create runbooks
- [ ] Test prod configuration

---

## 🎯 Success Definition

**Phase 4 is COMPLETE when**:

1. ✅ All 4 REST controllers working
2. ✅ Swagger UI showing all endpoints
3. ✅ All 25+ E2E tests passing
4. ✅ Load test passing (200+ RPS, <500ms)
5. ✅ Docker build successful
6. ✅ All security tests passing
7. ✅ Zero critical issues

**Result**: Production-ready REST API layer with comprehensive testing and deployment automation

---

**Next Step**: Complete Phase 3 (by Sept 13), then proceed to Phase 4 (Sept 13-27)

**Final Status**: Full SaaS platform ready for production deployment by **September 27, 2026** 🚀

