# 🏢 SaaS Features Implementation Guide

**Goal:** Implement core SaaS features (tenant onboarding, billing, plans, user management) using holon-saas  
**Timeline:** Weeks 2-3 of your SaaS build  
**Effort:** Can be done incrementally

---

## 🎯 Core SaaS Features You Already Have

Holon-saas comes with **MANY features already built**:

| Feature | Module | Status | Your Task |
|---------|--------|--------|-----------|
| **Tenant Management** | `tenant-core` | ✅ Complete | Just use it |
| **User Management** | `tenant-users` | ✅ Complete | Extend if needed |
| **Multi-tenant Isolation** | `tenant-security` | ✅ Complete | Just use it |
| **Role-Based Access** | `tenant-permissions` | ✅ Complete | Configure roles |
| **Billing & Invoicing** | `tenant-billing` | ✅ Complete | Integrate payment |
| **Subscription Plans** | `tenant-core` | ✅ Complete | Define plans |
| **Audit Logging** | `tenant-audit` | ✅ Complete | Just works! |
| **Rate Limiting** | `tenant-limits` | ✅ Complete | Configure limits |
| **Settings Management** | `tenant-settings` | ✅ Complete | Define settings |
| **Onboarding Pipeline** | `tenant-onboarding` | ✅ Complete | Customize flow |
| **Feature Flags** | `tenant-security` | ✅ Complete | @CheckTenantFeature |
| **Quotas** | `tenant-quotas` | ✅ Complete | @TenantLimit |

---

## 🚀 Quick Start: Enable SaaS Features

### **1. Tenant Plans (Already Defined)**

Holon-saas has 4 built-in plans:

```java
public enum TenantPlan {
    FREE,          // Limited features, small quotas
    STARTER,       // Small business
    PROFESSIONAL,  // Mid-size business
    ENTERPRISE     // Large enterprise
}
```

**Use them to gate features:**

```java
@Service
public class ReportService {
    
    @RequiresPlan(TenantPlan.PROFESSIONAL)
    public Report generateAdvancedReport() {
        // Only PROFESSIONAL+ can call this
    }
    
    @RequiresPlan(TenantPlan.ENTERPRISE)
    public void customIntegration() {
        // Only ENTERPRISE can call this
    }
}
```

---

### **2. Tenant Features (Already Defined)**

```java
public enum TenantFeature {
    ADVANCED_REPORTS,
    AUDIT_LOG,
    API_ACCESS,
    CUSTOM_INTEGRATIONS,
    PRIORITY_SUPPORT,
    WHITE_LABEL,
    SSO
}
```

**Use them to gate features:**

```java
@Service
public class ReportService {
    
    @CheckTenantFeature(TenantFeature.ADVANCED_REPORTS)
    public Report generateAdvancedReport() {
        // Only tenants with ADVANCED_REPORTS feature
    }
    
    @CheckTenantFeature(TenantFeature.API_ACCESS)
    @PostMapping("/api/data")
    public ResponseEntity<?> apiEndpoint() {
        // API only available if tenant has API_ACCESS feature
    }
}
```

---

### **3. Tenant Roles (Already Defined)**

```java
public enum TenantRole {
    OWNER,      // Full access, can invite users, manage billing
    ADMIN,      // Most access, can manage users
    MEMBER,     // Normal access
    VIEWER      // Read-only access
}
```

**Use them for role-based access:**

```java
@Service
public class BillingService {
    
    @Secured("ROLE_OWNER")
    public void updatePaymentMethod() {
        // Only OWNER can change payment
    }
    
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    public void inviteUser() {
        // OWNER or ADMIN can invite
    }
    
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    public void viewReports() {
        // MEMBER and above can view reports
    }
}
```

---

## 💳 Billing Implementation Guide

### **Step 1: Use Built-in Billing Services**

```java
@Autowired
private TenantBillingService billingService;

// Create a subscription when tenant signs up
public void onboardTenant(String tenantId, TenantPlan plan) {
    Subscription subscription = Subscription.builder(tenantId)
        .plan(plan)
        .status(SubscriptionStatus.ACTIVE)
        .periodStart(Instant.now())
        .periodEnd(Instant.now().plus(1, ChronoUnit.MONTHS))
        .externalRef("stripe_" + UUID.randomUUID())  // Link to Stripe
        .build();
    
    billingService.save(subscription);
}

// Create invoice
public void generateInvoice(String tenantId) {
    Invoice invoice = Invoice.builder("inv-" + UUID.randomUUID(), tenantId)
        .amountDue(new BigDecimal("99.00"))
        .currency("USD")
        .status(InvoiceStatus.OPEN)
        .issuedAt(Instant.now())
        .dueDate(Instant.now().plus(30, ChronoUnit.DAYS))
        .build();
    
    billingService.saveInvoice(invoice);
}

// Upgrade plan
public void upgradePlan(String tenantId, TenantPlan newPlan) {
    TenantDetails tenant = tenantService.getTenantDetails(tenantId);
    tenant = tenant.withPlan(newPlan);
    tenantAdminService.save(tenant);
}
```

---

### **Step 2: Integrate with Payment Gateway (Stripe Example)**

```java
@Service
public class StripePaymentService {
    
    private final TenantBillingService billingService;
    
    // When Stripe webhook notifies of successful payment
    @PostMapping("/webhook/stripe")
    public ResponseEntity<?> handleStripeWebhook(@RequestBody String payload) {
        Event event = Stripe.parseWebhookPayload(payload);
        
        if ("charge.succeeded".equals(event.getType())) {
            String tenantId = event.getMetadata("tenantId");
            String invoiceId = event.getMetadata("invoiceId");
            BigDecimal amount = event.getAmount();
            
            // Mark invoice as paid
            Invoice invoice = billingService.getInvoice(invoiceId);
            invoice = invoice.withStatus(InvoiceStatus.PAID)
                .withPaidAt(Instant.now());
            billingService.saveInvoice(invoice);
            
            // Log audit event
            auditService.log(AuditEntry.builder(tenantId, AuditAction.PAYMENT_RECEIVED)
                .amount(amount)
                .description("Payment received via Stripe")
                .build());
        }
        
        return ResponseEntity.ok().build();
    }
    
    // Generate payment link for invoice
    public String getPaymentLink(String tenantId, String invoiceId) {
        Invoice invoice = billingService.getInvoice(invoiceId);
        
        Session session = Session.create(
            SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("https://yourdomain.com/billing/success")
                .setCancelUrl("https://yourdomain.com/billing/cancelled")
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(invoice.getAmountDue().longValue() * 100)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Invoice #" + invoiceId)
                                        .build()
                                )
                                .build()
                        )
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("tenantId", tenantId)
                .putMetadata("invoiceId", invoiceId)
                .build()
        );
        
        return session.getUrl();
    }
}
```

---

## 👥 User Management Implementation

### **Step 1: Use Built-in User Service**

```java
@Autowired
private TenantUserService userService;

// Invite a user to tenant
public void inviteUser(String tenantId, String email, TenantRole role) {
    userService.invite(tenantId, UUID.randomUUID().toString(), email, role);
    
    // Log audit
    auditService.log(AuditEntry.builder(tenantId, AuditAction.USER_INVITED)
        .actor(getCurrentUser())
        .resourceType("User")
        .resourceId(email)
        .description("Invited " + email + " as " + role)
        .build());
}

// Activate invited user
public void activateUser(String tenantId, String userId) {
    userService.activate(tenantId, userId);
}

// Change user role
public void updateUserRole(String tenantId, String userId, TenantRole newRole) {
    // Holon-saas will handle this
    userService.updateRole(tenantId, userId, newRole);
}

// Remove user from tenant
public void removeUser(String tenantId, String userId) {
    userService.suspend(tenantId, userId);
}

// Record login
public void recordLogin(String tenantId, String userId) {
    userService.recordLogin(tenantId, userId);
}
```

---

## 🎯 Feature Gating by Plan

**Define what features are available at each plan level:**

```java
@Configuration
public class FeatureGatingConfiguration {
    
    /**
     * Map of plan → features available
     */
    public static final Map<TenantPlan, Set<TenantFeature>> PLAN_FEATURES = 
        Map.ofEntries(
            Map.entry(TenantPlan.FREE, Set.of(
                TenantFeature.AUDIT_LOG
            )),
            Map.entry(TenantPlan.STARTER, Set.of(
                TenantFeature.AUDIT_LOG,
                TenantFeature.API_ACCESS
            )),
            Map.entry(TenantPlan.PROFESSIONAL, Set.of(
                TenantFeature.AUDIT_LOG,
                TenantFeature.API_ACCESS,
                TenantFeature.ADVANCED_REPORTS,
                TenantFeature.CUSTOM_INTEGRATIONS
            )),
            Map.entry(TenantPlan.ENTERPRISE, Set.of(
                TenantFeature.AUDIT_LOG,
                TenantFeature.API_ACCESS,
                TenantFeature.ADVANCED_REPORTS,
                TenantFeature.CUSTOM_INTEGRATIONS,
                TenantFeature.PRIORITY_SUPPORT,
                TenantFeature.WHITE_LABEL,
                TenantFeature.SSO
            ))
        );
    
    /**
     * Map of plan → quotas
     */
    public static final Map<TenantPlan, Map<String, Long>> PLAN_QUOTAS = 
        Map.ofEntries(
            Map.entry(TenantPlan.FREE, Map.of(
                "api_calls_per_month", 1000L,
                "storage_gb", 1L,
                "team_members", 1L,
                "reports_per_month", 10L
            )),
            Map.entry(TenantPlan.STARTER, Map.of(
                "api_calls_per_month", 10000L,
                "storage_gb", 10L,
                "team_members", 3L,
                "reports_per_month", 100L
            )),
            Map.entry(TenantPlan.PROFESSIONAL, Map.of(
                "api_calls_per_month", 100000L,
                "storage_gb", 100L,
                "team_members", 10L,
                "reports_per_month", 1000L
            )),
            Map.entry(TenantPlan.ENTERPRISE, Map.of(
                "api_calls_per_month", Long.MAX_VALUE,
                "storage_gb", Long.MAX_VALUE,
                "team_members", Long.MAX_VALUE,
                "reports_per_month", Long.MAX_VALUE
            ))
        );
}
```

---

## 📊 Quotas & Rate Limiting

### **API Rate Limiting**

```java
@Service
public class ApiService {
    
    @RateLimit(limit = 100, window = 60, unit = TimeUnit.SECONDS)
    @PostMapping("/api/data")
    public ResponseEntity<?> apiEndpoint() {
        // Limited to 100 requests per minute
    }
    
    @PostMapping("/api/premium")
    public ResponseEntity<?> premiumEndpoint() {
        // Get quota from tenant settings
        String tenantId = TenantContext.getCurrentTenantId();
        long limitPerHour = settingsService.getLong(tenantId, 
            "api_rate_limit_per_hour", 1000L);
        
        // Apply limit based on plan
        TenantDetails tenant = tenantService.getTenantDetails(tenantId);
        if (tenant.getPlan() == TenantPlan.FREE) {
            limitPerHour = 100;
        }
        
        return apiResponse();
    }
}
```

---

## 📝 Audit & Compliance

### **Automatic Audit Logging**

```java
@Service
public class OrderService {
    
    private final TenantAuditService auditService;
    
    @Audited(
        action = AuditAction.ORDER_CREATED,
        description = "Order created: #{#order.id}",
        severity = AuditSeverity.INFO,
        resourceType = "Order",
        resourceId = "#{#order.id}"
    )
    public Order createOrder(Order order) {
        // Service logic
        return savedOrder;
    }
    
    @Audited(
        action = AuditAction.ORDER_UPDATED,
        description = "Order status changed to #{#order.status}",
        severity = AuditSeverity.INFO,
        resourceType = "Order",
        resourceId = "#{#order.id}"
    )
    public Order updateOrderStatus(Order order, OrderStatus status) {
        order.setStatus(status);
        return save(order);
    }
    
    @Audited(
        action = AuditAction.DATA_EXPORTED,
        description = "Exported #{#count} orders",
        severity = AuditSeverity.WARNING,
        resourceType = "Order"
    )
    public void exportOrders(int count) {
        // Export logic
    }
}
```

**Automatically logged:**
- WHO: Current user
- WHAT: Action performed
- WHEN: Timestamp
- WHERE: Tenant ID
- RESULT: Success/failure

---

## 🎨 Vaadin UI: Using Holon Guards

### **Role-Based Button Visibility**

```java
@Route("orders")
@PageTitle("Orders")
public class OrdersView extends VerticalLayout {
    
    public OrdersView() {
        Button createButton = new Button("Create Order");
        
        // Show button only for ADMIN or OWNER
        createButton.setVisible(
            TenantRoleProvider.hasAnyRole(TenantRole.ADMIN, TenantRole.OWNER)
        );
        
        add(createButton);
    }
}
```

### **Plan-Based Feature Visibility**

```java
@Route("reports")
@TenantPlanRequired(minimum = TenantPlan.PROFESSIONAL)
public class AdvancedReportsView extends Div {
    
    // View only accessible to PROFESSIONAL+ plans
    
    public AdvancedReportsView() {
        // ...
    }
}
```

---

## 📦 Settings Management

### **Store & Retrieve Tenant Settings**

```java
@Autowired
private TenantSettingsService settingsService;

// Set a setting
public void setBrandColor(String tenantId, String color) {
    settingsService.set(tenantId, TenantSettingKeys.PRIMARY_COLOR, color);
}

// Get a setting with default
public String getBrandColor(String tenantId) {
    return settingsService.get(tenantId, 
        TenantSettingKeys.PRIMARY_COLOR, "#1E88E5");
}

// Set plan-level default (applies to all tenants with this plan)
public void setPlanDefaults() {
    settingsService.setPlanDefault(TenantPlan.PROFESSIONAL,
        "max_team_members", "10");
}
```

---

## 🚀 Tenant Onboarding Flow

### **Complete Onboarding Pipeline**

```java
@Service
public class OnboardingService {
    
    @Autowired
    private TenantOnboardingService tenantOnboarding;
    
    public void onboardNewCustomer(String tenantName, String ownerEmail, TenantPlan plan) {
        // Step 1: Create onboarding request
        TenantOnboardingRequest request = TenantOnboardingRequest.builder()
            .tenantId(tenantName.toLowerCase().replace(" ", "-"))
            .tenantName(tenantName)
            .ownerEmail(ownerEmail)
            .plan(plan)
            .timezone("America/New_York")
            .locale("en-US")
            .build();
        
        // Step 2: Execute onboarding
        // Automatically creates:
        // - Tenant entry
        // - Database schema (per tenant)
        // - Owner user account
        // - Subscription
        TenantOnboardingResult result = tenantOnboarding.onboard(request);
        
        if (result.isSuccess()) {
            // Step 3: Send welcome email
            mailService.sendWelcomeEmail(ownerEmail, result.getTenantId());
            
            // Step 4: Log onboarding
            auditService.log(AuditEntry.builder(result.getTenantId(), 
                AuditAction.TENANT_ONBOARDED)
                .actor("system")
                .description("New tenant onboarded: " + tenantName)
                .severity(AuditSeverity.INFO)
                .build());
        }
    }
}
```

---

## 📊 Example: Multi-Tenant Datastore Query

```java
@Service
public class OrderRepository {
    
    @Autowired
    private Datastore datastore;
    
    /**
     * Find orders for current tenant
     * TenantContext automatically filters to current tenant
     */
    public Stream<Order> findAll() {
        return datastore.query(Order.class)
            .stream();  // Automatically filtered by tenant!
    }
    
    /**
     * Find orders with custom filter
     */
    public List<Order> findByStatus(OrderStatus status) {
        return datastore.query(Order.class)
            .filter(Order.STATUS.eq(status))
            .list();  // Automatically tenant-isolated
    }
    
    /**
     * Create order in current tenant
     */
    public Order save(Order order) {
        // TenantContext captures tenant_id automatically
        return datastore.insert(Order.class, order);
    }
}
```

---

## 🎯 Implementation Checklist

### **Week 2: SaaS Features**

- [ ] Define your TenantPlan levels (already have defaults)
- [ ] Configure TenantFeature flags for your domain
- [ ] Set up TenantBillingService integration
- [ ] Implement Stripe webhook for payments
- [ ] Create user invitation flow (TenantUserService)
- [ ] Implement role-based access control (@Secured)
- [ ] Set up settings for each tenant
- [ ] Test cross-tenant data isolation

### **Week 3: UI**

- [ ] Create dashboard with role-based visibility
- [ ] Create orders/items CRUD (your domain)
- [ ] Add @TenantPlanRequired guards to premium views
- [ ] Create billing page (show subscription, invoices)
- [ ] Create team management (invite users)
- [ ] Create settings page
- [ ] Test everything in multi-tenant scenario

---

## 💡 Pro Tips

1. **Always use @Audited** on important operations (orders, payments, users)
2. **Always check roles** with @Secured or @PreAuthorize
3. **Always validate tenant** in cross-tenant scenarios
4. **Test cross-tenant access** before deployment
5. **Monitor audit logs** for suspicious activity
6. **Set rate limits** on APIs to prevent abuse
7. **Use feature flags** to roll out gradually

---

## 🚀 Ready to Build?

You now have:
- ✅ Complete multi-tenant isolation
- ✅ User management framework
- ✅ Billing service
- ✅ Audit logging
- ✅ Rate limiting
- ✅ Feature gating
- ✅ Role-based access

**Your job:** Build your domain services (Orders, Customers, Reports, etc.)

**Start:** Implement your first domain service + Vaadin UI view

**Estimated time:** 2-3 days per major feature

🎉 Ready to ship! 🚀

