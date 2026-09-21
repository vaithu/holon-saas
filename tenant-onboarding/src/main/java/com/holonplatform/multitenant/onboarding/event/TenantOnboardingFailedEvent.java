package com.holonplatform.multitenant.onboarding.event;

import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Spring application event published by
 * {@link com.holonplatform.multitenant.onboarding.TenantOnboardingService}
 * when schema provisioning or Flyway migration fails.
 *
 * <p>When this event fires, the record written for the tenant at the start of onboarding has
 * been removed, so the identifier is free for re-use once the root cause is corrected. If the
 * configured {@link com.holonplatform.multitenant.onboarding.TenantRepository} does not support
 * removal, onboarding falls back to marking the record {@code DELETED} — which still satisfies
 * {@code exists()}, so in that case the identifier stays unavailable and clearing it is an
 * operator's job.
 *
 * <h2>Typical listeners</h2>
 * <ul>
 *   <li>Alert ops team via Slack / PagerDuty</li>
 *   <li>Write structured failure record to audit log</li>
 *   <li>Trigger automatic retry after a delay</li>
 * </ul>
 */
public final class TenantOnboardingFailedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final String    tenantId;
    private final Throwable cause;

    public TenantOnboardingFailedEvent(Object source, String tenantId, Throwable cause) {
        super(source);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.cause    = cause;
    }

    /** The tenant ID whose onboarding failed. */
    public String getTenantId() {
        return tenantId;
    }

    /** The root exception that caused the failure (may be null if cause was unknown). */
    public Throwable getCause() {
        return cause;
    }
}

