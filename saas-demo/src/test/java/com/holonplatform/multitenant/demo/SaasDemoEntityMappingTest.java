package com.holonplatform.multitenant.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import com.holonplatform.multitenant.testing.EntityMappingValidationRunner;
import com.holonplatform.multitenant.testing.HolonEntityMappingValidator;
import com.holonplatform.multitenant.testing.HolonEntityMappingValidator.Issue;
import com.holonplatform.multitenant.testing.HolonEntityMappingValidator.Severity;

import jakarta.persistence.Entity;

/**
 * F27 — every persisted entity is shaped the way Holon's Datastore needs it to be.
 *
 * <h2>Why this test exists at all</h2>
 * <p>Holon reads and writes entities through {@code BeanIntrospector}, which is built on
 * {@code java.beans.Introspector} and therefore sees JavaBean getters and setters — not record
 * accessors, not fields. An entity that Hibernate maps perfectly well can still be invisible or
 * unwritable to the Datastore, and nothing says so at startup. The failure arrives the first
 * time a real user triggers the path, as a {@code PropertyReadException} that names a property
 * rather than the mistake, or as a write that silently drops a column.
 *
 * <p>That is a bad way to find out, and it is entirely avoidable: the defect is visible by
 * reflection long before anyone runs the code. This test is the whole point of
 * {@code HolonEntityMappingValidator} — a class the framework ships and, until now, nothing in
 * this repository actually called.
 *
 * <h2>Why it scans rather than lists</h2>
 * <p>A hand-written list of entity classes is a list that goes stale the moment someone adds an
 * entity, which is exactly when this check is most wanted. Scanning the package means a new
 * {@code @Entity} is covered by this test on the commit that introduces it, without anyone
 * having to remember.
 */
@SpringBootTest
class SaasDemoEntityMappingTest {

    @Autowired(required = false)
    private EntityMappingValidationRunner validationRunner;

    private static final String ENTITY_PACKAGE = "com.holonplatform.multitenant";

    /**
     * Derived, read-only properties that are computed from other fields and deliberately have no
     * setter. Acknowledged individually rather than by switching a check off, so that adding a
     * genuinely unwritable property still fails until someone has looked at it.
     *
     * <p>The two {@code @ElementCollection} fields are the framework's own, and are safe for a
     * specific reason rather than a general one: {@code TenantDetails} is persisted through a
     * plain {@code JpaRepository} and an {@code EntityManager} query, never through Holon's
     * Datastore, so the inner join that would drop rows with empty collections never happens.
     * {@code EntityMappingValidationRunner} acknowledges the same two for the same reason.
     */
    private static final Set<String> ACKNOWLEDGED = Set.of(
            "com.holonplatform.multitenant.TenantDetails#active",
            "com.holonplatform.multitenant.TenantDetails#features",
            "com.holonplatform.multitenant.TenantDetails#attributes");

    @Test
    @DisplayName("every @Entity on the classpath is readable and writable by the Holon Datastore")
    void everyEntityIsDatastoreSafe() {
        Class<?>[] entities = scanForEntities();

        // A scan that finds nothing would make this test pass while checking nothing at all.
        assertThat(entities).hasSizeGreaterThan(5);

        List<Issue> issues = HolonEntityMappingValidator.validate(ACKNOWLEDGED, entities);
        List<Issue> blocking = issues.stream()
                .filter(issue -> issue.getSeverity() != Severity.INFO)
                .toList();

        assertThat(blocking)
                .describedAs("Holon Datastore mapping problems:%n%s", render(blocking))
                .isEmpty();
    }

    @Test
    @DisplayName("the startup validator is actually registered in a real application context")
    void startupValidatorIsWiredIntoTheApplication() {
        // The check above is only a safety net for this repository. What protects a consuming
        // application is EntityMappingValidationRunner running at every boot — and that bean is
        // conditional on an EntityManagerFactory contributed by another auto-configuration, so
        // it is exactly the kind of bean that can quietly fail to exist. Asserting it in a real
        // context is the only way to know it is there.
        assertThat(validationRunner)
                .describedAs("EntityMappingValidationRunner should be registered whenever JPA is in use")
                .isNotNull();
    }

    private static Class<?>[] scanForEntities() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        return scanner.findCandidateComponents(ENTITY_PACKAGE).stream()
                .map(BeanDefinition::getBeanClassName)
                .sorted()
                .map(SaasDemoEntityMappingTest::load)
                .toArray(Class<?>[]::new);
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Scanned entity could not be loaded: " + className, e);
        }
    }

    private static String render(List<Issue> issues) {
        return issues.stream().map(Object::toString).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
}
