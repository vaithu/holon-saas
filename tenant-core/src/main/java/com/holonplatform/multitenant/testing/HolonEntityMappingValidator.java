package com.holonplatform.multitenant.testing;

import com.holonplatform.core.beans.BeanIntrospector;
import com.holonplatform.core.beans.BeanProperty;
import com.holonplatform.core.beans.BeanPropertySet;
import jakarta.persistence.ElementCollection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Build-time / test-time validator that scans JPA entity classes for the exact class of
 * "bean-shape" defects that Holon's {@code BeanDatastoreHelper}/{@code JpaDatastore} only
 * detect at <em>runtime</em> — typically the first time a real user triggers the affected
 * code path (a signup, an update, ...), not during development or CI.
 *
 * <p>Every check below corresponds to a genuine, previously-encountered production bug
 * (see each check's message for the exact runtime symptom it prevents). None of these are
 * hypothetical: entities across {@code holon-saas} itself and its {@code crm-minimal} demo
 * app hit every single one of them during initial development, each discovered only by a
 * live signup attempt throwing a cryptic {@code PropertyReadException} or silently losing
 * data.
 *
 * <h2>Usage</h2>
 * <p>Add a single test to any consuming application, listing every {@code @Entity} class
 * that is (or might be) persisted via Holon's Datastore (directly, or via a
 * {@code BeanDatastoreHelper}):
 * <pre>{@code
 * @Test
 * void allEntitiesAreHolonDatastoreSafe() {
 *     HolonEntityMappingValidator.assertValid(
 *             CrmAccount.class,
 *             MyOtherEntity.class);
 * }
 * }</pre>
 * If an entity has a deliberately computed/derived property (e.g. an {@code isActive()}
 * convenience method backed by no field, intentionally excluded from persistence via a
 * custom {@code BeanPropertyPostProcessor}), acknowledge it explicitly rather than silencing
 * the whole check:
 * <pre>{@code
 * HolonEntityMappingValidator.assertValid(
 *         Set.of(MyEntity.class.getName() + "#active"),
 *         MyEntity.class);
 * }</pre>
 */
public final class HolonEntityMappingValidator {

	private HolonEntityMappingValidator() {
	}

	/** Severity of a reported {@link Issue}. */
	public enum Severity {
		/** Will cause a runtime failure (or silent data loss) the first time the affected path runs. */
		ERROR,
		/** Suspicious, but not guaranteed to fail — worth a human look. */
		WARNING,
		/**
		 * Confirmed safe by construction — nothing to fix, nothing to look at. Reported purely
		 * for transparency (so the exact reasoning is discoverable if ever doubted), not because
		 * any action is expected. Callers that log findings should generally log these at a
		 * lower level than {@link #WARNING} (e.g. {@code DEBUG}) to avoid startup-log noise for
		 * a non-issue.
		 */
		INFO
	}

	/** A single validation finding. */
	public static final class Issue {

		private final Class<?> entityClass;
		private final String propertyName;
		private final Severity severity;
		private final String message;

		private Issue(Class<?> entityClass, String propertyName, Severity severity, String message) {
			this.entityClass = entityClass;
			this.propertyName = propertyName;
			this.severity = severity;
			this.message = message;
		}

		public Class<?> getEntityClass() {
			return entityClass;
		}

		public String getPropertyName() {
			return propertyName;
		}

		public Severity getSeverity() {
			return severity;
		}

		public String getMessage() {
			return message;
		}

		@Override
		public String toString() {
			return "[" + severity + "] " + entityClass.getSimpleName()
					+ (propertyName != null ? "#" + propertyName : "") + ": " + message;
		}
	}

	/**
	 * Validates the given entity classes with no acknowledged exceptions.
	 *
	 * @see #validate(Set, Class[])
	 */
	public static List<Issue> validate(Class<?>... entityClasses) {
		return validate(Collections.emptySet(), entityClasses);
	}

	/**
	 * Validates the given entity classes.
	 *
	 * @param acknowledgedProperties properties to skip the "missing write method with a backing
	 *                               field" check and the {@code @ElementCollection} check for,
	 *                               formatted as {@code fully.qualified.ClassName#propertyName}.
	 *                               Use this for a field/property whose exclusion you've
	 *                               separately verified is safe — e.g. an
	 *                               {@code @ElementCollection} field on an entity confirmed to
	 *                               be accessed only via a plain {@code JpaRepository}, never
	 *                               Holon's Datastore. (Purely computed properties with
	 *                               <em>no backing field at all</em>, like an {@code isActive()}
	 *                               convenience method, don't need acknowledging — they're
	 *                               already recognized as safe automatically, since
	 *                               {@code TenantCoreBeanPropertyFix} in {@code holon-saas}
	 *                               excludes that exact shape from Holon's Datastore write path
	 *                               unconditionally.)
	 * @param entityClasses          the entity classes to validate
	 * @return all findings, both {@link Severity#ERROR} and {@link Severity#WARNING}
	 */
	public static List<Issue> validate(Set<String> acknowledgedProperties, Class<?>... entityClasses) {
		List<Issue> issues = new ArrayList<>();
		for (Class<?> entityClass : entityClasses) {
			issues.addAll(validateOne(entityClass, acknowledgedProperties));
		}
		return issues;
	}

	/**
	 * Convenience assertion for direct use in a JUnit test: validates the given entity classes
	 * and throws an {@link AssertionError} listing every {@link Severity#ERROR} finding (if any).
	 * {@link Severity#WARNING} findings are printed to stderr but do not fail the assertion.
	 *
	 * @throws AssertionError if any {@link Severity#ERROR} issue is found
	 */
	public static void assertValid(Class<?>... entityClasses) {
		assertValid(Collections.emptySet(), entityClasses);
	}

	/**
	 * Same as {@link #assertValid(Class[])}, with acknowledged properties — see
	 * {@link #validate(Set, Class[])}.
	 */
	public static void assertValid(Set<String> acknowledgedProperties, Class<?>... entityClasses) {
		List<Issue> issues = validate(acknowledgedProperties, entityClasses);

		List<Issue> warnings = issues.stream().filter(i -> i.getSeverity() == Severity.WARNING)
				.collect(Collectors.toList());
		if (!warnings.isEmpty()) {
			System.err.println("HolonEntityMappingValidator: " + warnings.size() + " warning(s):");
			warnings.forEach(w -> System.err.println("  " + w));
		}

		List<Issue> errors = issues.stream().filter(i -> i.getSeverity() == Severity.ERROR)
				.collect(Collectors.toList());
		if (!errors.isEmpty()) {
			String summary = errors.stream().map(Issue::toString).collect(Collectors.joining("\n  ", "\n  ", ""));
			throw new AssertionError(
					"HolonEntityMappingValidator found " + errors.size()
							+ " entity mapping error(s) that WILL cause a runtime failure "
							+ "(or silent data loss) when this entity is actually used:" + summary);
		}
	}

	// -------------------------------------------------------------------------
	// Per-class checks
	// -------------------------------------------------------------------------

	private static List<Issue> validateOne(Class<?> entityClass, Set<String> acknowledgedProperties) {
		List<Issue> issues = new ArrayList<>();

		checkNoArgConstructorIsPublic(entityClass, issues);
		checkBeanPropertyReadWriteMethods(entityClass, acknowledgedProperties, issues);
		checkElementCollectionFields(entityClass, acknowledgedProperties, issues);
		checkMisleadingTransientAnnotations(entityClass, issues);

		return issues;
	}

	/**
	 * CHECK: the entity's no-arg constructor (if declared) must be {@code public}.
	 *
	 * <p>Real bug this prevents: Holon's {@code JpaSave} instantiates entities via
	 * {@code Class.newInstance()} — a plain reflective call that enforces normal Java access
	 * control (unlike {@code Constructor.setAccessible(true)}). A {@code protected} or
	 * {@code private} no-arg constructor compiles fine but fails at runtime, the first time
	 * the entity is ever saved, with {@code IllegalAccessException} — not caught by any test
	 * that only exercises reads.
	 */
	private static void checkNoArgConstructorIsPublic(Class<?> entityClass, List<Issue> issues) {
		try {
			Constructor<?> ctor = entityClass.getDeclaredConstructor();
			if (!Modifier.isPublic(ctor.getModifiers())) {
				issues.add(new Issue(entityClass, null, Severity.ERROR,
						"No-arg constructor is not public (found: "
								+ Modifier.toString(ctor.getModifiers()) + "). Holon's JpaSave "
								+ "instantiates entities via Class.newInstance(), which enforces normal "
								+ "Java access control — a non-public constructor fails at runtime with "
								+ "IllegalAccessException the first time this entity is saved. Make it public."));
			}
		} catch (NoSuchMethodException e) {
			issues.add(new Issue(entityClass, null, Severity.ERROR,
					"No no-arg constructor found. JPA/Holon requires one (even if only used "
							+ "reflectively) — add a public no-arg constructor."));
		}
	}

	/**
	 * CHECK: every non-identifier, non-version bean property must have a write method
	 * (and, less critically, a read method), unless explicitly acknowledged.
	 *
	 * <p>Real bug this prevents: a property with a getter but no setter (e.g. a field
	 * populated only via constructor or default initializer, like
	 * {@code private Instant createdAt = Instant.now();} with no {@code setCreatedAt(...)})
	 * fails at save-time with {@code PropertyReadException: No write method and no accessible
	 * field available to write property [...]}. A computed getter with NO backing field at
	 * all (e.g. {@code isActive()} deriving from other fields) hits the identical exception —
	 * Holon's bean introspection cannot tell "forgot the setter" apart from "intentionally
	 * computed" without either a setter or an explicit exclusion.
	 */
	private static void checkBeanPropertyReadWriteMethods(Class<?> entityClass, Set<String> acknowledgedProperties,
			List<Issue> issues) {
		BeanPropertySet<?> propertySet;
		try {
			propertySet = BeanIntrospector.get().getPropertySet(entityClass);
		} catch (Exception e) {
			issues.add(new Issue(entityClass, null, Severity.WARNING,
					"Could not introspect bean properties via Holon's BeanIntrospector: " + e.getMessage()));
			return;
		}

		propertySet.stream()
				.filter(p -> p instanceof BeanProperty)
				.map(p -> (BeanProperty<?>) p)
				.forEach(property -> {
					if (property.isIdentifier() || property.isVersion()) {
						return; // Holon/JPA handle these specially (generated values, etc.)
					}
					if (property.getParentProperty().isPresent()) {
						// A synthetic, framework-generated nested sub-property (e.g. Holon exposes an
						// implicit "<mapField>.empty" boolean sub-property for Map/Collection-typed
						// fields, to support "IS EMPTY" style query filters) — not something a developer
						// writes a getter/setter for, so it is never a real mapping defect. Confirmed
						// empirically: TenantUser#attributes (a Map<String,String>) surfaces exactly
						// this as a synthetic "empty" property with no read/write method of its own.
						return;
					}
					String key = entityClass.getName() + "#" + property.getName();
					if (acknowledgedProperties.contains(key)) {
						return;
					}

					boolean hasWriteMethod = property.getWriteMethod().isPresent();
					boolean hasReadMethod = property.getReadMethod().isPresent();

					if (!hasWriteMethod) {
						if (property.getField().isEmpty()) {
							// No backing field AND no write method — a purely computed/derived property
							// (e.g. isActive() deriving from other fields), not a forgotten setter. This
							// exact shape is safe in any real holon-saas application: TenantCoreBeanPropertyFix
							// registers a JVM-wide BeanIntrospector rule that unconditionally excludes any
							// property matching this shape from Holon's bean property model
							// (IgnoreMode.IGNORE_PROPERTY) — Holon's Datastore never attempts to write it.
							// Reported as INFO (not WARNING/ERROR) purely for transparency, not because
							// anything needs fixing.
							issues.add(new Issue(entityClass, property.getName(), Severity.INFO,
									"Computed property with no backing field and no write method (type "
											+ property.getType().getSimpleName() + "). Not flagged as an error: "
											+ "TenantCoreBeanPropertyFix (tenant-core) registers a JVM-wide rule "
											+ "that excludes exactly this shape from Holon's Datastore write path "
											+ "automatically. If this property unexpectedly fails at save-time "
											+ "anyway, verify TenantCoreAutoConfiguration is actually being "
											+ "loaded by Spring Boot in this application."));
						} else {
							issues.add(new Issue(entityClass, property.getName(), Severity.ERROR,
									"No write method available for this property (type "
											+ property.getType().getSimpleName() + "), but it DOES have a "
											+ "backing field — Holon's Datastore will fail at save-time with "
											+ "PropertyReadException: 'No write method and no accessible field "
											+ "available to write property'. Add a public setter, OR if this "
											+ "field is intentionally never written via Holon's Datastore, "
											+ "register a BeanPropertyPostProcessor excluding it via "
											+ "IgnoreMode.IGNORE_PROPERTY (see TenantCoreBeanPropertyFix in "
											+ "holon-saas for the pattern — note @Transient does NOT work for "
											+ "this, see the separate @Transient check) and acknowledge \"" + key
											+ "\" in this validator's call."));
						}
					}
					if (!hasReadMethod) {
						issues.add(new Issue(entityClass, property.getName(), Severity.WARNING,
								"No read method available for this property. This will break reads for "
										+ "this property. If you recently added a getter and still see this, "
										+ "try a full clean rebuild first — Holon's BeanIntrospector caches "
										+ "bean shape metadata per classloader, and stale compiled classes "
										+ "from a hot-reload can serve outdated results."));
					}
				});
	}

	/**
	 * CHECK: no {@code @ElementCollection} fields (regardless of declared {@code fetch}
	 * type).
	 *
	 * <p>Real bug this prevents: Holon's {@code BeanDatastoreHelper} always projects the
	 * entire bean in a single JPQL query, referencing every mapped property — including
	 * {@code @ElementCollection}-mapped {@code Map}/{@code Set} fields — directly in the
	 * {@code SELECT} list. Hibernate always translates that into an <strong>inner
	 * join</strong> against the collection's join table, regardless of the field's own
	 * declared {@code fetch} type. Whenever the collection happens to be empty (routinely
	 * true for a freshly-created entity), that inner join has no matching row, and the
	 * <strong>entire parent entity silently disappears</strong> from the query result — not
	 * an exception, just missing data, indistinguishable from "the row doesn't exist".
	 */
	private static void checkElementCollectionFields(Class<?> entityClass, Set<String> acknowledgedProperties,
			List<Issue> issues) {
		for (Field field : entityClass.getDeclaredFields()) {
			if (field.isAnnotationPresent(ElementCollection.class)) {
				String key = entityClass.getName() + "#" + field.getName();
				if (acknowledgedProperties.contains(key)) {
					// Acknowledged: the caller has confirmed this entity is only ever accessed via a
					// plain JpaRepository/EntityManager, never Holon's BeanDatastoreHelper — the actual
					// bug this check exists for cannot occur in that case.
					continue;
				}
				issues.add(new Issue(entityClass, field.getName(), Severity.ERROR,
						"@ElementCollection field. If this entity is ever read/written via Holon's "
								+ "BeanDatastoreHelper (directly, or through a *DatastoreHelper wrapper), "
								+ "an inner join against this field's collection table will SILENTLY DROP "
								+ "the entire parent row whenever the collection is empty — no exception, "
								+ "just missing data. Convert this field to a single JSON-serialized column "
								+ "via a JPA AttributeConverter (see StringMapJsonConverter in holon-saas "
								+ "tenant-core) instead, or if this entity is ONLY EVER accessed via a plain "
								+ "JpaRepository/EntityManager (never Holon's Datastore), acknowledge \"" + key
								+ "\" in this validator's call."));
			}
		}
	}

	/**
	 * CHECK (informational): flags {@code @jakarta.persistence.Transient}-annotated
	 * fields/methods, since {@code @Transient} does not behave the way most developers
	 * expect for Holon's Datastore.
	 *
	 * <p>Real bug this prevents: {@code holon-jpa-bean-processors}' own
	 * {@code JpaTransientBeanPropertyPostProcessor} maps {@code @Transient} to
	 * {@code IgnoreMode.IGNORE_INCLUDE_NESTED} — a mode meant to suppress a <em>nested
	 * bean's own sub-properties</em>, which is a no-op for an ordinary scalar property. A
	 * property marked {@code @Transient} expecting it to be excluded from Holon's Datastore
	 * write path will, in fact, still be attempted — this is a silent behavioral surprise,
	 * not a compile or (necessarily) a runtime error, so it is reported as a warning rather
	 * than an error.
	 */
	private static void checkMisleadingTransientAnnotations(Class<?> entityClass, List<Issue> issues) {
		Set<String> transientMemberNames = new LinkedHashSet<>();
		for (Field field : entityClass.getDeclaredFields()) {
			if (hasAnnotation(field.getAnnotations(), "jakarta.persistence.Transient")) {
				transientMemberNames.add(field.getName());
			}
		}
		for (Method method : entityClass.getDeclaredMethods()) {
			if (hasAnnotation(method.getAnnotations(), "jakarta.persistence.Transient")) {
				transientMemberNames.add(method.getName());
			}
		}
		for (String name : transientMemberNames) {
			issues.add(new Issue(entityClass, name, Severity.WARNING,
					"@Transient does not exclude this property from Holon's Datastore write path the "
							+ "way you would expect — it maps internally to IgnoreMode.IGNORE_INCLUDE_NESTED, "
							+ "a no-op for a scalar property, NOT IgnoreMode.IGNORE_PROPERTY. If you intended "
							+ "this property to be excluded from persistence, register a "
							+ "BeanPropertyPostProcessor applying IgnoreMode.IGNORE_PROPERTY instead (see "
							+ "TenantCoreBeanPropertyFix in holon-saas for the pattern)."));
		}
	}

	private static boolean hasAnnotation(Annotation[] annotations, String annotationClassName) {
		return Arrays.stream(annotations)
				.anyMatch(a -> a.annotationType().getName().equals(annotationClassName));
	}
}
