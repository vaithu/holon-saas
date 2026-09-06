package com.holonplatform.multitenant.users;

import com.holonplatform.multitenant.testing.HolonEntityMappingValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-world dogfooding of {@link HolonEntityMappingValidator} against {@link TenantUser} —
 * the actual entity that originally hit the "computed getter with no backing field" bug
 * ({@code isActive()}) this session, fixed via {@code TenantCoreBeanPropertyFix} (a runtime,
 * JVM-wide {@code BeanPropertyPostProcessor}) rather than a source-code change to the entity
 * itself.
 *
 * <p>{@link HolonEntityMappingValidator} now recognizes this exact shape (no backing field
 * <em>and</em> no write method) as safe by construction — it's precisely what
 * {@code TenantCoreBeanPropertyFix} excludes from Holon's Datastore write path
 * unconditionally, for every class, for the lifetime of the JVM. No acknowledgment is needed;
 * this test proves {@code assertValid(TenantUser.class)} passes with zero setup, which is
 * exactly what let a real running application (this framework's own
 * {@code EntityMappingValidationRunner}, auto-validating every entity at startup) avoid a
 * false-positive error against this framework-shipped entity.
 */
class TenantUserEntityMappingTest {

	@Test
	void tenantUser_isFullyValid_withNoAcknowledgmentNeeded() {
		HolonEntityMappingValidator.assertValid(TenantUser.class); // must not throw
	}

	@Test
	void isActive_isNotReportedAsAnError() {
		var issues = HolonEntityMappingValidator.validate(TenantUser.class);

		assertThat(issues).noneMatch(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.ERROR
				&& "active".equals(i.getPropertyName()));
	}
}
