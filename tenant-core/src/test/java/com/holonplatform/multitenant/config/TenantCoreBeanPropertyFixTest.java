package com.holonplatform.multitenant.config;

import com.holonplatform.core.beans.BeanIntrospector;
import com.holonplatform.core.property.PropertyBox;
import com.holonplatform.multitenant.TenantDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies {@link TenantCoreBeanPropertyFix} actually prevents the
 * {@code PropertyReadException} that {@link TenantDetails#isActive()} (a computed,
 * field-less boolean getter) would otherwise cause during a Holon Datastore
 * {@code read()}/{@code write()} round-trip.
 */
class TenantCoreBeanPropertyFixTest {

	@Test
	void ensureRegistered_excludesFieldlessWriteMethodlessProperties_fromReadWriteRoundTrip() {
		TenantCoreBeanPropertyFix.ensureRegistered();

		TenantDetails source = TenantDetails.builder("acme").name("Acme").build();
		PropertyBox box = BeanIntrospector.get().read(source);

		TenantDetails target = new TenantDetails();

		// Before the fix, this line throws:
		// Property$PropertyReadException: No write method and no accessible field
		// available to write property [BeanProperty [getName()=active, getType()=boolean]]
		assertThatCode(() -> BeanIntrospector.get().write(box, target)).doesNotThrowAnyException();

		assertThat(target.tenantId()).isEqualTo("acme");
		assertThat(target.name()).isEqualTo("Acme");
		// isActive() still works correctly as ordinary Java code — only Holon's bean
		// property model excludes it, the method itself is untouched.
		assertThat(target.isActive()).isTrue();
	}

	@Test
	void ensureRegistered_isIdempotent() {
		TenantCoreBeanPropertyFix.ensureRegistered();
		TenantCoreBeanPropertyFix.ensureRegistered();
		TenantCoreBeanPropertyFix.ensureRegistered();

		// Calling multiple times must not register the post-processor more than once
		// (which would otherwise be harmless but wasteful); a successful round-trip
		// after multiple calls is enough to demonstrate no adverse duplication effect.
		TenantDetails source = TenantDetails.builder("acme").build();
		PropertyBox box = BeanIntrospector.get().read(source);
		TenantDetails target = new TenantDetails();

		assertThatCode(() -> BeanIntrospector.get().write(box, target)).doesNotThrowAnyException();
	}
}
