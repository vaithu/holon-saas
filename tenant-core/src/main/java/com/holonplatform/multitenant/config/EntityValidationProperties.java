package com.holonplatform.multitenant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configures the automatic, boot-time run of
 * {@link com.holonplatform.multitenant.testing.HolonEntityMappingValidator} against every JPA
 * entity discoverable via the application's {@code EntityManagerFactory} metamodel.
 *
 * <p>This gives every application built on {@code holon-saas} "free" protection against the
 * entity-mapping bugs {@code HolonEntityMappingValidator} catches (missing accessor methods,
 * non-public no-arg constructors, {@code @ElementCollection} inner-join risk, misleading
 * {@code @Transient} usage) even if the application never writes the recommended dedicated test
 * for it.
 */
@ConfigurationProperties(prefix = "holon.tenant.entity-validation")
public class EntityValidationProperties {

	/**
	 * Whether to automatically validate every JPA-managed entity at application startup
	 * ({@code ApplicationReadyEvent}). Enabled by default.
	 */
	private boolean enabled = true;

	/**
	 * Whether an {@link com.holonplatform.multitenant.testing.HolonEntityMappingValidator.Severity#ERROR}
	 * finding should fail application startup outright ({@code true}) or only be logged at
	 * {@code ERROR} level ({@code false}, the default). Set this to {@code true} in dev/CI
	 * profiles to catch entity-mapping bugs immediately at boot instead of downstream as a
	 * cryptic SQL exception the first time the affected entity is actually persisted.
	 */
	private boolean failFast = false;

	/**
	 * Properties to acknowledge as intentionally-excluded computed/derived properties, in the
	 * format {@code fully.qualified.ClassName#propertyName} — see
	 * {@link com.holonplatform.multitenant.testing.HolonEntityMappingValidator#validate(java.util.Set, Class[])}.
	 */
	private List<String> acknowledgedProperties = new ArrayList<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isFailFast() {
		return failFast;
	}

	public void setFailFast(boolean failFast) {
		this.failFast = failFast;
	}

	public List<String> getAcknowledgedProperties() {
		return acknowledgedProperties;
	}

	public void setAcknowledgedProperties(List<String> acknowledgedProperties) {
		this.acknowledgedProperties = acknowledgedProperties;
	}
}
