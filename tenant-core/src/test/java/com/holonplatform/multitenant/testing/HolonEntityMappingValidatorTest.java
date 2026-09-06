package com.holonplatform.multitenant.testing;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@link HolonEntityMappingValidator} actually detects every real bug class it claims
 * to — each nested test entity below reproduces the exact shape of a genuine production bug
 * hit during {@code holon-saas}/{@code crm-minimal} development this session.
 */
class HolonEntityMappingValidatorTest {

	// -------------------------------------------------------------------------
	// Positive case: a genuinely well-formed entity reports no issues at all.
	// -------------------------------------------------------------------------

	@Test
	void wellFormedEntity_reportsNoIssues() {
		assertThat(HolonEntityMappingValidator.validate(WellFormedEntity.class)).isEmpty();
		HolonEntityMappingValidator.assertValid(WellFormedEntity.class); // must not throw
	}

	@Entity(name = "wellformedentity")
	public static class WellFormedEntity {
		@Id
		@Column(name = "id")
		private Long id;

		@Column(name = "name")
		private String name;

		public WellFormedEntity() {
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	// -------------------------------------------------------------------------
	// CHECK: missing write method (the exact CrmAccount#createdAt bug).
	// -------------------------------------------------------------------------

	@Test
	void propertyWithGetterButNoSetter_isReportedAsError() {
		var issues = HolonEntityMappingValidator.validate(GetterOnlyEntity.class);

		assertThat(issues)
				.filteredOn(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.ERROR)
				.anySatisfy(issue -> {
					assertThat(issue.getPropertyName()).isEqualTo("createdAt");
					assertThat(issue.getMessage()).contains("No write method");
				});

		assertThatThrownBy(() -> HolonEntityMappingValidator.assertValid(GetterOnlyEntity.class))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("createdAt");
	}

	@Test
	void propertyWithGetterButNoSetter_canBeAcknowledged() {
		var acknowledged = Set.of(GetterOnlyEntity.class.getName() + "#createdAt");
		var issues = HolonEntityMappingValidator.validate(acknowledged, GetterOnlyEntity.class);

		assertThat(issues).noneMatch(i -> "createdAt".equals(i.getPropertyName()));
		HolonEntityMappingValidator.assertValid(acknowledged, GetterOnlyEntity.class); // must not throw
	}

	@Entity(name = "getteronlyentity")
	public static class GetterOnlyEntity {
		@Id
		private Long id;

		@Column(name = "created_at")
		private Instant createdAt = Instant.now(); // no setCreatedAt(...) — the original CrmAccount bug

		public GetterOnlyEntity() {
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Instant getCreatedAt() {
			return createdAt;
		}
	}

	// -------------------------------------------------------------------------
	// CHECK: computed getter with no backing field at all (the TenantUser#isActive() bug).
	// -------------------------------------------------------------------------

	@Test
	void computedBooleanGetter_withNoBackingField_isReportedAsInfo_notError() {
		var issues = HolonEntityMappingValidator.validate(ComputedGetterEntity.class);

		// Not an ERROR: no backing field + no write method is exactly the shape
		// TenantCoreBeanPropertyFix excludes unconditionally at runtime — safe by
		// construction, not something the caller needs to acknowledge.
		assertThat(issues).noneMatch(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.ERROR
				&& "active".equals(i.getPropertyName()));
		// INFO, not WARNING: confirmed safe, nothing to look at — see Severity.INFO's Javadoc.
		assertThat(issues)
				.filteredOn(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.INFO)
				.anySatisfy(issue -> {
					assertThat(issue.getPropertyName()).isEqualTo("active");
					assertThat(issue.getMessage()).contains("TenantCoreBeanPropertyFix");
				});

		HolonEntityMappingValidator.assertValid(ComputedGetterEntity.class); // must not throw — INFO only
	}

	@Entity(name = "computedgetterentity")
	public static class ComputedGetterEntity {
		@Id
		private Long id;

		@Column(name = "status")
		private String status;

		public ComputedGetterEntity() {
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getStatus() {
			return status;
		}

		public void setStatus(String status) {
			this.status = status;
		}

		/** Purely computed — no backing field, no setter. Matches TenantUser#isActive(). */
		public boolean isActive() {
			return "ACTIVE".equals(status);
		}
	}

	// -------------------------------------------------------------------------
	// CHECK: @ElementCollection field (the inner-join-drops-empty-row bug).
	// -------------------------------------------------------------------------

	@Test
	void elementCollectionField_isReportedAsError() {
		var issues = HolonEntityMappingValidator.validate(ElementCollectionEntity.class);

		assertThat(issues)
				.filteredOn(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.ERROR)
				.anySatisfy(issue -> {
					assertThat(issue.getPropertyName()).isEqualTo("attributes");
					assertThat(issue.getMessage()).contains("SILENTLY DROP");
				});
	}

	@Test
	void elementCollectionField_canBeAcknowledged() {
		var acknowledged = Set.of(ElementCollectionEntity.class.getName() + "#attributes");
		var issues = HolonEntityMappingValidator.validate(acknowledged, ElementCollectionEntity.class);

		assertThat(issues).noneMatch(i -> "attributes".equals(i.getPropertyName()));
		HolonEntityMappingValidator.assertValid(acknowledged, ElementCollectionEntity.class); // must not throw
	}

	@Entity(name = "elementcollectionentity")
	public static class ElementCollectionEntity {
		@Id
		private Long id;

		@ElementCollection
		private Map<String, String> attributes = new HashMap<>();

		public ElementCollectionEntity() {
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public Map<String, String> getAttributes() {
			return attributes;
		}

		public void setAttributes(Map<String, String> attributes) {
			this.attributes = attributes;
		}
	}

	// -------------------------------------------------------------------------
	// CHECK: misleading @Transient (does not behave like most developers expect).
	// -------------------------------------------------------------------------

	@Test
	void transientAnnotatedGetter_isReportedAsWarning() {
		var issues = HolonEntityMappingValidator.validate(TransientGetterEntity.class);

		assertThat(issues)
				.filteredOn(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.WARNING)
				.anySatisfy(issue -> {
					assertThat(issue.getPropertyName()).isEqualTo("getDisplayLabel");
					assertThat(issue.getMessage()).contains("IGNORE_INCLUDE_NESTED");
				});
	}

	@Entity(name = "transientgetterentity")
	public static class TransientGetterEntity {
		@Id
		private Long id;

		@Column(name = "name")
		private String name;

		public TransientGetterEntity() {
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		@Transient
		public String getDisplayLabel() {
			return "[" + name + "]";
		}
	}

	// -------------------------------------------------------------------------
	// CHECK: non-public no-arg constructor.
	// -------------------------------------------------------------------------

	@Test
	void nonPublicNoArgConstructor_isReportedAsError() {
		var issues = HolonEntityMappingValidator.validate(ProtectedConstructorEntity.class);

		assertThat(issues)
				.filteredOn(i -> i.getSeverity() == HolonEntityMappingValidator.Severity.ERROR)
				.anySatisfy(issue -> assertThat(issue.getMessage()).contains("not public"));
	}

	@Entity(name = "protectedconstructorentity")
	public static class ProtectedConstructorEntity {
		@Id
		private Long id;

		protected ProtectedConstructorEntity() {
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}
	}
}
