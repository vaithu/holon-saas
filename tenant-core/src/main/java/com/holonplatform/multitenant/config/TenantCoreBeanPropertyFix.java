package com.holonplatform.multitenant.config;

import com.holonplatform.core.beans.BeanIntrospector;
import com.holonplatform.core.beans.IgnoreMode;

/**
 * Registers a JVM-wide {@code BeanPropertyPostProcessor} (via {@link BeanIntrospector}'s
 * global singleton) that fully excludes any Bean property with <em>neither</em> a backing
 * field <em>nor</em> a write method from Holon's bean property model.
 *
 * <h2>Why this is needed</h2>
 * <p>Several {@code holon-saas} entities (e.g. {@link com.holonplatform.multitenant.TenantDetails#isActive()},
 * {@code Invoice#isPaid()}, {@code Subscription#isTrialing()}/{@code isAccessible()},
 * {@code UserInvitation#isExpired()}/{@code isPending()},
 * {@code WhatsAppConversation#isOpen()}/{@code isFreeWindow()}, {@code WhatsAppMessage#isDelivered()},
 * {@code TenantUser#isActive()}) expose convenience, purely-computed boolean accessors
 * following the JavaBean {@code isXxx()} getter convention. These exist for application
 * code only — they are not persisted columns and have no backing field.
 *
 * <p>Holon's bean introspection nonetheless detects them as candidate writable properties
 * (matching the JavaBean convention), which crashes {@code JpaSave}/{@code JpaDatastore}
 * the moment such an entity is saved via the Datastore, with:
 * <pre>{@code
 * Property$PropertyReadException: No write method and no accessible field available to
 * write property [BeanProperty [getName()=active, getType()=boolean]] on bean class [...]
 * }</pre>
 *
 * <h2>Why {@code @jakarta.persistence.Transient} does NOT fix this</h2>
 * <p>Annotating such a method with {@code @Transient} does not help:
 * {@code holon-jpa-bean-processors}' own {@code JpaTransientBeanPropertyPostProcessor} sets
 * {@link IgnoreMode#IGNORE_INCLUDE_NESTED} for {@code @Transient} properties — a mode
 * intended to suppress a <em>nested bean class's own sub-properties</em>. For a scalar
 * property (nothing nested to suppress), this mode has no effect: the property remains
 * fully visible to, and breaks, {@code write()}. {@link IgnoreMode#IGNORE_PROPERTY} is the
 * mode that actually excludes a property outright — verified empirically against
 * {@code holon-core} 12.0.0's {@code DefaultBeanPropertySet}. Nothing in the (published,
 * unmodifiable) {@code holon-jpa-bean-processors} artifact ever applies that mode for a
 * plain {@code @Transient} scalar getter, so the fix must live here instead.
 *
 * <h2>Why this rule is safe to apply globally</h2>
 * <p>A property with neither a backing field nor a write method can never legitimately be
 * written by any Holon Datastore operation regardless of which bean class it belongs to —
 * so pre-emptively excluding it (rather than crashing) is always the correct behavior.
 * This mirrors {@link BeanIntrospector}'s own singleton, JVM/classloader-wide design.
 *
 * <p>Public and callable from other {@code holon-saas} modules: any module that
 * bean-introspects a shipped entity (e.g. {@code TenantUserDatastoreHelper} calling
 * {@code BeanDatastoreHelper.of(datastore, TenantUser.class)}) must invoke
 * {@link #ensureRegistered()} <em>before</em> that first introspection, because Holon's
 * {@link BeanIntrospector} caches each class's {@code PropertySet} on first use — registering
 * the rule afterwards is too late for an already-cached (un-fixed) entity.
 */
public final class TenantCoreBeanPropertyFix {

	private static volatile boolean registered = false;

	private TenantCoreBeanPropertyFix() {
	}

	/**
	 * Registers the post-processor exactly once per classloader. Safe to call repeatedly
	 * (e.g. from multiple {@code @Bean} methods or on Spring DevTools restart).
	 */
	public static synchronized void ensureRegistered() {
		if (registered) {
			return;
		}
		BeanIntrospector.get().addBeanPropertyPostProcessor((builder, beanOrNestedClass) -> {
			if (builder.getField().isEmpty() && builder.getWriteMethod().isEmpty()) {
				builder.ignoreMode(IgnoreMode.IGNORE_PROPERTY);
			}
			return builder;
		});
		registered = true;
	}
}
