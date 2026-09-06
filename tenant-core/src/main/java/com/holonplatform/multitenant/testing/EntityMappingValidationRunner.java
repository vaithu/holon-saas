package com.holonplatform.multitenant.testing;

import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.config.EntityValidationProperties;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs {@link HolonEntityMappingValidator} automatically, once, at
 * {@link ApplicationReadyEvent}, against every entity class discoverable via the application's
 * {@link EntityManagerFactory} metamodel.
 *
 * <p>Registered by {@code TenantCoreAutoConfiguration} whenever an {@code EntityManagerFactory}
 * bean is present and {@link EntityValidationProperties#isEnabled()} is {@code true} (the
 * default) — see that properties class for the {@code holon.tenant.entity-validation.*}
 * settings controlling this behavior, including {@code fail-fast} (throw at startup vs. only
 * log) and {@code acknowledged-properties} (intentionally-excluded computed properties).
 *
 * <h2>Framework-owned acknowledgments</h2>
 * <p>Because this runner discovers <em>every</em> entity on the application's
 * {@code EntityManagerFactory} metamodel indiscriminately — including {@code holon-saas}'s own
 * shipped entities, not just the application's — it automatically acknowledges
 * {@link TenantDetails#features()}/{@link TenantDetails#attributes()}
 * ({@code @ElementCollection} fields that are genuinely safe: {@code TenantDetails} is
 * deliberately persisted via a plain {@code JpaRepository}, never Holon's Datastore — see
 * {@code TenantDetailsRepository}'s Javadoc) on top of whatever the application configures via
 * {@code holon.tenant.entity-validation.acknowledged-properties}. Without this, every
 * application would see the identical false-positive error for a framework-owned entity it
 * never wrote and cannot fix.
 */
public class EntityMappingValidationRunner implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger log = LoggerFactory.getLogger(EntityMappingValidationRunner.class);

	/**
	 * Properties/fields owned by {@code holon-saas} itself that are already known-safe despite
	 * matching one of {@link HolonEntityMappingValidator}'s check patterns — see this class's
	 * Javadoc. Not exhaustive by check design: computed properties with no backing field at all
	 * (e.g. {@code isActive()}) don't need listing here, since
	 * {@link HolonEntityMappingValidator} already recognizes that shape as safe unconditionally
	 * (backed by {@code TenantCoreBeanPropertyFix}'s equally unconditional runtime exclusion
	 * rule). This set exists only for the narrower {@code @ElementCollection} case, which
	 * requires a specific, entity-by-entity safety justification instead of a generic rule.
	 */
	static final Set<String> FRAMEWORK_ACKNOWLEDGED_PROPERTIES = Set.of(
			TenantDetails.class.getName() + "#features",
			TenantDetails.class.getName() + "#attributes");

	private final EntityManagerFactory entityManagerFactory;
	private final EntityValidationProperties properties;

	public EntityMappingValidationRunner(EntityManagerFactory entityManagerFactory,
			EntityValidationProperties properties) {
		this.entityManagerFactory = entityManagerFactory;
		this.properties = properties;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		if (!properties.isEnabled()) {
			return;
		}

		Set<Class<?>> entityClasses = entityManagerFactory.getMetamodel().getEntities().stream()
				.map(EntityType::getJavaType)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (entityClasses.isEmpty()) {
			return;
		}

		Set<String> acknowledgedProperties = new HashSet<>(FRAMEWORK_ACKNOWLEDGED_PROPERTIES);
		acknowledgedProperties.addAll(properties.getAcknowledgedProperties());
		List<HolonEntityMappingValidator.Issue> issues = HolonEntityMappingValidator.validate(
				acknowledgedProperties, entityClasses.toArray(new Class<?>[0]));

		// INFO: confirmed safe by construction, nothing to look at — logged at DEBUG only, so a
		// normal startup log isn't cluttered with reassurances nobody needs to act on.
		List<HolonEntityMappingValidator.Issue> infos = issues.stream()
				.filter(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.INFO)
				.collect(Collectors.toList());
		infos.forEach(i -> log.debug("HolonEntityMappingValidator: {}", i));

		List<HolonEntityMappingValidator.Issue> warnings = issues.stream()
				.filter(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.WARNING)
				.collect(Collectors.toList());
		warnings.forEach(w -> log.warn("HolonEntityMappingValidator: {}", w));

		List<HolonEntityMappingValidator.Issue> errors = issues.stream()
				.filter(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.ERROR)
				.collect(Collectors.toList());
		if (errors.isEmpty()) {
			log.debug("HolonEntityMappingValidator: checked {} entities at startup, no errors found ({} warnings)",
					entityClasses.size(), warnings.size());
			return;
		}

		String summary = errors.stream().map(HolonEntityMappingValidator.Issue::toString)
				.collect(Collectors.joining("\n  ", "\n  ", ""));
		if (properties.isFailFast()) {
			throw new IllegalStateException(
					"HolonEntityMappingValidator found " + errors.size() + " entity mapping error(s) at startup "
							+ "that WILL cause a runtime failure (or silent data loss) the first time the "
							+ "affected entity is actually used:" + summary
							+ "\nSet holon.tenant.entity-validation.fail-fast=false to only log these instead of "
							+ "failing startup.");
		}
		log.error("HolonEntityMappingValidator found {} entity mapping error(s) at startup that WILL cause a "
				+ "runtime failure (or silent data loss) the first time the affected entity is actually used:{}"
				+ "\nSet holon.tenant.entity-validation.fail-fast=true to fail startup immediately instead.",
				errors.size(), summary);
	}
}
