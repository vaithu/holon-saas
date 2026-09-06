package com.holonplatform.multitenant.testing;

import com.holonplatform.multitenant.config.EntityValidationProperties;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EntityMappingValidationRunner}, reusing the synthetic bug-reproducing
 * entities declared in {@link HolonEntityMappingValidatorTest}.
 */
class EntityMappingValidationRunnerTest {

	@Test
	void doesNothing_whenDisabled() {
		EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
		EntityValidationProperties properties = new EntityValidationProperties();
		properties.setEnabled(false);

		new EntityMappingValidationRunner(entityManagerFactory, properties).onApplicationEvent(mockEvent());

		verifyNoInteractions(entityManagerFactory);
	}

	@Test
	void doesNotThrow_whenNoEntitiesFound() {
		EntityManagerFactory entityManagerFactory = mockEmf();
		EntityValidationProperties properties = new EntityValidationProperties();

		assertThatCode(() -> new EntityMappingValidationRunner(entityManagerFactory, properties)
				.onApplicationEvent(mockEvent())).doesNotThrowAnyException();
	}

	@Test
	void doesNotThrow_forWellFormedEntity_evenWithFailFastEnabled() {
		EntityManagerFactory entityManagerFactory = mockEmf(HolonEntityMappingValidatorTest.WellFormedEntity.class);
		EntityValidationProperties properties = new EntityValidationProperties();
		properties.setFailFast(true);

		assertThatCode(() -> new EntityMappingValidationRunner(entityManagerFactory, properties)
				.onApplicationEvent(mockEvent())).doesNotThrowAnyException();
	}

	@Test
	void onlyLogs_byDefault_whenErrorsFound() {
		EntityManagerFactory entityManagerFactory = mockEmf(HolonEntityMappingValidatorTest.GetterOnlyEntity.class);
		EntityValidationProperties properties = new EntityValidationProperties(); // failFast defaults to false

		// Must not throw — default behavior is log-only, not fail-startup.
		assertThatCode(() -> new EntityMappingValidationRunner(entityManagerFactory, properties)
				.onApplicationEvent(mockEvent())).doesNotThrowAnyException();
	}

	@Test
	void throws_whenFailFastEnabled_andErrorsFound() {
		EntityManagerFactory entityManagerFactory = mockEmf(HolonEntityMappingValidatorTest.GetterOnlyEntity.class);
		EntityValidationProperties properties = new EntityValidationProperties();
		properties.setFailFast(true);

		assertThatThrownBy(() -> new EntityMappingValidationRunner(entityManagerFactory, properties)
				.onApplicationEvent(mockEvent()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("GetterOnlyEntity")
				.hasMessageContaining("createdAt");
	}

	@Test
	void respectsAcknowledgedProperties_forElementCollectionCheck_evenWithFailFastEnabled() {
		EntityManagerFactory entityManagerFactory =
				mockEmf(HolonEntityMappingValidatorTest.ElementCollectionEntity.class);
		EntityValidationProperties properties = new EntityValidationProperties();
		properties.setFailFast(true);
		properties.setAcknowledgedProperties(
				List.of(HolonEntityMappingValidatorTest.ElementCollectionEntity.class.getName() + "#attributes"));

		assertThatCode(() -> new EntityMappingValidationRunner(entityManagerFactory, properties)
				.onApplicationEvent(mockEvent())).doesNotThrowAnyException();
	}

	@Test
	void doesNotThrow_forComputedGetterWithNoBackingField_evenWithFailFastEnabled_andNoAcknowledgment() {
		// No backing field + no write method is recognized as safe unconditionally (see
		// HolonEntityMappingValidator) — proves an application never needs to acknowledge this
		// exact shape, unlike the ElementCollection case above.
		EntityManagerFactory entityManagerFactory =
				mockEmf(HolonEntityMappingValidatorTest.ComputedGetterEntity.class);
		EntityValidationProperties properties = new EntityValidationProperties();
		properties.setFailFast(true);

		assertThatCode(() -> new EntityMappingValidationRunner(entityManagerFactory, properties)
				.onApplicationEvent(mockEvent())).doesNotThrowAnyException();
	}

	@Test
	void frameworkAcknowledgedProperties_includesTenantDetailsElementCollectionFields() {
		assertThat(EntityMappingValidationRunner.FRAMEWORK_ACKNOWLEDGED_PROPERTIES)
				.contains(
						com.holonplatform.multitenant.TenantDetails.class.getName() + "#features",
						com.holonplatform.multitenant.TenantDetails.class.getName() + "#attributes");
	}

	@Test
	void doesNotThrow_forRealTenantDetailsEntity_evenWithFailFastEnabled_andNoAppConfiguredAcknowledgment() {
		// The exact false-positive this session hit for real: TenantDetails is genuinely safe
		// (persisted via a plain JpaRepository, never Holon's Datastore — see
		// TenantDetailsRepository's Javadoc), but every application's EntityManagerFactory
		// metamodel includes it since it's a real @Entity. Proves FRAMEWORK_ACKNOWLEDGED_PROPERTIES
		// is actually wired into onApplicationEvent, not just declared.
		EntityManagerFactory entityManagerFactory =
				mockEmf(com.holonplatform.multitenant.TenantDetails.class);
		EntityValidationProperties properties = new EntityValidationProperties();
		properties.setFailFast(true);

		assertThatCode(() -> new EntityMappingValidationRunner(entityManagerFactory, properties)
				.onApplicationEvent(mockEvent())).doesNotThrowAnyException();
	}

	@SuppressWarnings("unchecked")
	private static EntityManagerFactory mockEmf(Class<?>... entityClasses) {
		EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);
		Metamodel metamodel = mock(Metamodel.class);
		Set<EntityType<?>> entityTypes = new LinkedHashSet<>();
		for (Class<?> entityClass : entityClasses) {
			EntityType<?> entityType = mock(EntityType.class);
			doReturn(entityClass).when(entityType).getJavaType();
			entityTypes.add(entityType);
		}
		when(metamodel.getEntities()).thenReturn(entityTypes);
		when(entityManagerFactory.getMetamodel()).thenReturn(metamodel);
		return entityManagerFactory;
	}

	private static ApplicationReadyEvent mockEvent() {
		return mock(ApplicationReadyEvent.class);
	}
}
