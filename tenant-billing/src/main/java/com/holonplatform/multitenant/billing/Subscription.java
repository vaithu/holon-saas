package com.holonplatform.multitenant.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.holonplatform.multitenant.TenantPlan;

import java.time.Instant;
import java.util.Objects;

/**
 * Spring Data JPA entity representing a tenant's current subscription.
 *
 * <p>Instances are treated as immutable value objects by application code (mutator methods
 * such as {@link #withStatus(SubscriptionStatus)} return a new instance rather than modifying
 * the current one); persistence-related mutation is handled internally by JPA/Hibernate via
 * field access.
 */
@Entity(name = "subscription")
@Table(name = "tenant_billing_subscription", indexes = {
        @Index(name = "idx_tenant_billing_subscription_status", columnList = "STATUS")
})
public class Subscription {

    /** tenant this subscription belongs to; also the primary key (never null) */
    @Id
    @Column(name = "TENANT_ID", nullable = false, length = 100)
    private String tenantId;

    /** current plan (never null) */
    @Enumerated(EnumType.STRING)
    @Column(name = "PLAN", nullable = false, length = 50)
    private TenantPlan plan;

    /** subscription lifecycle status (never null) */
    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 50)
    private SubscriptionStatus status;

    /** start of the current billing period (never null) */
    @Column(name = "PERIOD_START", nullable = false)
    private Instant periodStart;

    /** end of the current billing period; null for open-ended */
    @Column(name = "PERIOD_END")
    private Instant periodEnd;

    /** end of the trial period; null if not in trial */
    @Column(name = "TRIAL_END")
    private Instant trialEnd;

    /** when cancellation was requested; null if not cancelled */
    @Column(name = "CANCELLED_AT")
    private Instant cancelledAt;

    /** external billing system reference (e.g. Stripe subscription ID); nullable */
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
    public Subscription() {
    }

    public Subscription(String tenantId, TenantPlan plan, SubscriptionStatus status, Instant periodStart,
            Instant periodEnd, Instant trialEnd, Instant cancelledAt, String externalRef) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = periodEnd;
        this.trialEnd = trialEnd;
        this.cancelledAt = cancelledAt;
        this.externalRef = externalRef;
    }

    // ── Accessors ───────────────────────────────────────────────────────────────
    // Kept as record-style accessor methods (no "get" prefix) for backward
    // compatibility with existing usages across the codebase.

    public String tenantId()               { return tenantId; }
    public TenantPlan plan()               { return plan; }
    public SubscriptionStatus status()     { return status; }
    public Instant periodStart()           { return periodStart; }
    public Instant periodEnd()             { return periodEnd; }
    public Instant trialEnd()              { return trialEnd; }
    public Instant cancelledAt()           { return cancelledAt; }
    public String externalRef()            { return externalRef; }

    /** Returns {@code true} if the subscription is in trial. */
    public boolean isTrialing() {
        return status == SubscriptionStatus.TRIALING
                && trialEnd != null && Instant.now().isBefore(trialEnd);
    }

    /** Returns {@code true} if paid access is currently allowed. */
    public boolean isAccessible() {
        return status.isAccessible();
    }

    /** Returns a copy with status changed. */
    public Subscription withStatus(SubscriptionStatus newStatus) {
        return new Subscription(tenantId, plan, newStatus, periodStart, periodEnd,
                trialEnd, cancelledAt, externalRef);
    }

    /** Returns a copy with plan and status changed. */
    public Subscription withPlan(TenantPlan newPlan, SubscriptionStatus newStatus) {
        return new Subscription(tenantId, newPlan, newStatus, periodStart, periodEnd,
                trialEnd, cancelledAt, externalRef);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subscription other)) return false;
        return Objects.equals(tenantId, other.tenantId)
                && plan == other.plan
                && status == other.status
                && Objects.equals(periodStart, other.periodStart)
                && Objects.equals(periodEnd, other.periodEnd)
                && Objects.equals(trialEnd, other.trialEnd)
                && Objects.equals(cancelledAt, other.cancelledAt)
                && Objects.equals(externalRef, other.externalRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, plan, status, periodStart, periodEnd, trialEnd, cancelledAt, externalRef);
    }

    @Override
    public String toString() {
        return "Subscription[tenantId=" + tenantId + ", plan=" + plan + ", status=" + status
                + ", periodStart=" + periodStart + ", periodEnd=" + periodEnd + ", trialEnd=" + trialEnd
                + ", cancelledAt=" + cancelledAt + ", externalRef=" + externalRef + "]";
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static Builder builder(String tenantId) {
        return new Builder(tenantId);
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    public static final class Builder {

        private final String tenantId;
        private TenantPlan         plan        = TenantPlan.FREE;
        private SubscriptionStatus status      = SubscriptionStatus.ACTIVE;
        private Instant            periodStart = Instant.now();
        private Instant            periodEnd;
        private Instant            trialEnd;
        private Instant            cancelledAt;
        private String             externalRef;

        private Builder(String tenantId) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        }

        public Builder plan(TenantPlan p)              { this.plan        = Objects.requireNonNull(p); return this; }
        public Builder status(SubscriptionStatus s)    { this.status      = Objects.requireNonNull(s); return this; }
        public Builder periodStart(Instant t)          { this.periodStart = Objects.requireNonNull(t); return this; }
        public Builder periodEnd(Instant t)            { this.periodEnd   = t; return this; }
        public Builder trialEnd(Instant t)             { this.trialEnd    = t; return this; }
        public Builder cancelledAt(Instant t)          { this.cancelledAt = t; return this; }
        public Builder externalRef(String r)           { this.externalRef = r; return this; }

        public Subscription build() {
            return new Subscription(tenantId, plan, status, periodStart, periodEnd,
                    trialEnd, cancelledAt, externalRef);
        }
    }
}

