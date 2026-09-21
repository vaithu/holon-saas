package com.holonplatform.multitenant.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Spring Data JPA entity representing a billing invoice for a tenant.
 *
 * <p>Instances are treated as immutable value objects by application code (mutator methods
 * such as {@link #markPaid(Instant)} return a new instance rather than modifying the current
 * one); persistence-related mutation is handled internally by JPA/Hibernate via field access.
 */
@Entity(name = "invoice")
@Table(name = "tenant_billing_invoice", indexes = {
        @Index(name = "idx_tenant_billing_invoice_tenant", columnList = "TENANT_ID"),
        @Index(name = "idx_tenant_billing_invoice_issued", columnList = "ISSUED_AT")
})
public class Invoice {

    /** unique identifier for this invoice (never null) */
    @Id
    @Column(name = "INVOICE_ID", nullable = false, length = 100)
    private String invoiceId;

    /** tenant this invoice belongs to (never null) */
    @Column(name = "TENANT_ID", nullable = false, length = 100)
    private String tenantId;

    /** total amount due; never null */
    @Column(name = "AMOUNT_DUE", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountDue;

    /** ISO 4217 currency code, e.g. "USD" (never null) */
    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    /** current payment status (never null) */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 50)
    private InvoiceStatus status;

    /** when the invoice was created (never null) */
    @Column(name = "ISSUED_AT", nullable = false)
    private Instant issuedAt;

    /** when the invoice was paid; null if not yet paid */
    @Column(name = "PAID_AT")
    private Instant paidAt;

    /** external billing system reference; nullable */
    @Column(name = "EXTERNAL_REF", length = 255)
    private String externalRef;

    /**
     * JPA-required no-arg constructor.
     *
     * <p>Must be {@code public} — Holon's JPA Datastore ({@code JpaSave}) instantiates
     * entities reflectively via {@code Class.newInstance()}, which performs a normal Java
     * access check; a {@code protected}/{@code private} constructor fails at runtime with
     * {@code IllegalAccessException}.
     */
    public Invoice() {
    }

    public Invoice(String invoiceId, String tenantId, BigDecimal amountDue, String currency,
            InvoiceStatus status, Instant issuedAt, Instant paidAt, String externalRef) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.amountDue = Objects.requireNonNull(amountDue, "amountDue must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.paidAt = paidAt;
        this.externalRef = externalRef;
    }

    // ── Accessors ───────────────────────────────────────────────────────────────
    // Kept as record-style accessor methods (no "get" prefix) for backward
    // compatibility with existing usages across the codebase.

    public String invoiceId()          { return invoiceId; }
    public String tenantId()           { return tenantId; }
    public BigDecimal amountDue()      { return amountDue; }
    public String currency()           { return currency; }
    public InvoiceStatus status()      { return status; }
    public Instant issuedAt()          { return issuedAt; }
    public Instant paidAt()            { return paidAt; }
    public String externalRef()        { return externalRef; }

    // ── JavaBean accessors ──────────────────────────────────────────────────────
    //
    // Required for Holon Datastore persistence — see the equivalent block on Subscription for
    // why. Holon's BeanIntrospector only recognises getXxx/setXxx, so without these the
    // introspected PropertySet is empty and every read fails with "Null or empty selection".

    public String getInvoiceId()                       { return invoiceId; }
    public void setInvoiceId(String invoiceId)         { this.invoiceId = invoiceId; }

    public String getTenantId()                        { return tenantId; }
    public void setTenantId(String tenantId)           { this.tenantId = tenantId; }

    public BigDecimal getAmountDue()                   { return amountDue; }
    public void setAmountDue(BigDecimal amountDue)     { this.amountDue = amountDue; }

    public String getCurrency()                        { return currency; }
    public void setCurrency(String currency)           { this.currency = currency; }

    public InvoiceStatus getStatus()                   { return status; }
    public void setStatus(InvoiceStatus status)        { this.status = status; }

    public Instant getIssuedAt()                       { return issuedAt; }
    public void setIssuedAt(Instant issuedAt)          { this.issuedAt = issuedAt; }

    public Instant getPaidAt()                         { return paidAt; }
    public void setPaidAt(Instant paidAt)              { this.paidAt = paidAt; }

    public String getExternalRef()                     { return externalRef; }
    public void setExternalRef(String externalRef)     { this.externalRef = externalRef; }

    /** Returns {@code true} if this invoice has been paid. */
    public boolean isPaid() {
        return status == InvoiceStatus.PAID;
    }

    /** Returns a copy with status changed to PAID and paidAt set. */
    public Invoice markPaid(Instant paidInstant) {
        return new Invoice(invoiceId, tenantId, amountDue, currency,
                InvoiceStatus.PAID, issuedAt, paidInstant, externalRef);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Invoice other)) return false;
        return Objects.equals(invoiceId, other.invoiceId)
                && Objects.equals(tenantId, other.tenantId)
                && Objects.equals(amountDue, other.amountDue)
                && Objects.equals(currency, other.currency)
                && status == other.status
                && Objects.equals(issuedAt, other.issuedAt)
                && Objects.equals(paidAt, other.paidAt)
                && Objects.equals(externalRef, other.externalRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invoiceId, tenantId, amountDue, currency, status, issuedAt, paidAt, externalRef);
    }

    @Override
    public String toString() {
        return "Invoice[invoiceId=" + invoiceId + ", tenantId=" + tenantId + ", amountDue=" + amountDue
                + ", currency=" + currency + ", status=" + status + ", issuedAt=" + issuedAt
                + ", paidAt=" + paidAt + ", externalRef=" + externalRef + "]";
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Builder builder(String invoiceId, String tenantId) {
        return new Builder(invoiceId, tenantId);
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static final class Builder {

        private final String invoiceId;
        private final String tenantId;
        private BigDecimal    amountDue   = BigDecimal.ZERO;
        private String        currency    = "USD";
        private InvoiceStatus status      = InvoiceStatus.OPEN;
        private Instant       issuedAt    = Instant.now();
        private Instant       paidAt;
        private String        externalRef;

        private Builder(String invoiceId, String tenantId) {
            this.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId must not be null");
            this.tenantId  = Objects.requireNonNull(tenantId,  "tenantId must not be null");
        }

        public Builder amountDue(BigDecimal a)    { this.amountDue   = Objects.requireNonNull(a); return this; }
        public Builder currency(String c)          { this.currency    = Objects.requireNonNull(c); return this; }
        public Builder status(InvoiceStatus s)     { this.status      = Objects.requireNonNull(s); return this; }
        public Builder issuedAt(Instant t)         { this.issuedAt    = Objects.requireNonNull(t); return this; }
        public Builder paidAt(Instant t)           { this.paidAt      = t; return this; }
        public Builder externalRef(String r)       { this.externalRef = r; return this; }

        public Invoice build() {
            return new Invoice(invoiceId, tenantId, amountDue, currency,
                    status, issuedAt, paidAt, externalRef);
        }
    }
}

