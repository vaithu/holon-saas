package com.holonplatform.multitenant;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Mutable, JPA-mapped value object that represents a fully resolved tenant and all of its
 * runtime attributes.
 *
 * <p>Instances are created via the static {@link #builder(String)} factory and
 * stored in {@link TenantContext} for the lifetime of each HTTP request thread.
 * They are also directly persistable via a plain Spring Data {@link TenantDetailsRepository}
 * (see the {@code tenant-onboarding} and {@code tenant-admin} modules) — deliberately
 * <strong>not</strong> Holon's {@code BeanDatastoreHelper}, which every other entity in
 * {@code holon-saas} uses; see {@link TenantDetailsRepository}'s Javadoc for why this one
 * entity is different. This class exposes standard JavaBean getters/setters in addition to
 * its original record-style accessors ({@link #tenantId()}, {@link #plan()}, ...) to satisfy
 * both persistence mechanisms.
 *
 * <p><strong>Mutability note:</strong> unlike its previous record-based incarnation,
 * this class is mutable to satisfy JPA/Datastore requirements (no-arg constructor,
 * settable fields). Application code should still treat instances as effectively
 * immutable and prefer {@link #toBuilder()} to derive modified copies rather than
 * calling setters directly, except where required by persistence infrastructure.
 *
 * <h2>Feature resolution</h2>
 * <p>{@link #hasFeature(String)} checks both the explicitly enabled feature set
 * stored on this instance <em>and</em> the default feature set of the tenant's
 * {@link TenantPlan}. Application code should always go through
 * {@code hasFeature} rather than inspecting {@link #features()} directly.
 */
@Entity(name = "tenantdetails")
@Table(name = "tenant", indexes = {
		@Index(name = "idx_tenant_status", columnList = "status"),
		@Index(name = "idx_tenant_plan", columnList = "plan")
})
public class TenantDetails {

	/** unique, URL-safe identifier (e.g. {@code "acme"}, {@code "umbrella-corp"}) */
	@Id
	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	/** human-readable display name (nullable) */
	@Column(name = "name", length = 255)
	private String name;

	/** subscription plan; drives default feature availability */
	@Column(name = "plan", nullable = false, length = 32)
	@Enumerated(EnumType.STRING)
	private TenantPlan plan = TenantPlan.FREE;

	/** lifecycle state; only {@link TenantStatus#ACTIVE} tenants may access the UI */
	@Column(name = "status", nullable = false, length = 32)
	@Enumerated(EnumType.STRING)
	private TenantStatus status = TenantStatus.ACTIVE;

	/** additional per-tenant feature-flag keys beyond the plan defaults */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "tenant_features", joinColumns = @JoinColumn(name = "tenant_id"))
	@Column(name = "feature_key", length = 100)
	private Set<String> features = new HashSet<>();

	/** preferred IETF language tag, e.g. {@code "en-AU"} (nullable -&gt; JVM default) */
	@Column(name = "locale", length = 32)
	private String locale;

	/** preferred IANA timezone ID, e.g. {@code "Australia/Sydney"} (nullable -&gt; JVM default) */
	@Column(name = "timezone", length = 64)
	private String timezone;

	/** optional CSS theme name for white-labelling (nullable) */
	@Column(name = "theme", length = 64)
	private String theme;

	/** arbitrary string key-value metadata for application-specific use */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "tenant_attributes", joinColumns = @JoinColumn(name = "tenant_id"))
	@MapKeyColumn(name = "attr_key", length = 255)
	@Column(name = "attr_value", columnDefinition = "TEXT")
	private Map<String, String> attributes = new HashMap<>();

	/**
	 * JPA / Datastore default constructor.
	 *
	 * <p>Must be {@code public} — Holon's JPA Datastore ({@code JpaSave}) instantiates
	 * entities reflectively via {@code Class.newInstance()}, which performs a normal Java
	 * access check (unlike {@code Constructor.setAccessible(true)}); a {@code protected}
	 * or {@code private} constructor here fails at runtime with
	 * {@code IllegalAccessException}, not at compile time.
	 */
	public TenantDetails() {
	}

	private TenantDetails(String tenantId, String name, TenantPlan plan, TenantStatus status,
			Set<String> features, String locale, String timezone, String theme,
			Map<String, String> attributes) {
		this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
		this.name = name;
		this.plan = Objects.requireNonNull(plan, "plan must not be null");
		this.status = Objects.requireNonNull(status, "status must not be null");
		this.features = features != null ? new HashSet<>(features) : new HashSet<>();
		this.locale = locale;
		this.timezone = timezone;
		this.theme = theme;
		this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
	}

	// -------------------------------------------------------------------------
	// Record-style accessors (kept for source compatibility with existing callers)
	// -------------------------------------------------------------------------

	public String tenantId() {
		return tenantId;
	}

	public String name() {
		return name;
	}

	public TenantPlan plan() {
		return plan;
	}

	public TenantStatus status() {
		return status;
	}

	public Set<String> features() {
		return Collections.unmodifiableSet(features);
	}

	public String locale() {
		return locale;
	}

	public String timezone() {
		return timezone;
	}

	public String theme() {
		return theme;
	}

	public Map<String, String> attributes() {
		return Collections.unmodifiableMap(attributes);
	}

	// -------------------------------------------------------------------------
	// JavaBean getters/setters (required for JPA and Holon BeanDatastoreHelper)
	// -------------------------------------------------------------------------

	public String getTenantId() {
		return tenantId;
	}

	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TenantPlan getPlan() {
		return plan;
	}

	public void setPlan(TenantPlan plan) {
		this.plan = plan;
	}

	public TenantStatus getStatus() {
		return status;
	}

	public void setStatus(TenantStatus status) {
		this.status = status;
	}

	public Set<String> getFeatures() {
		return features;
	}

	public void setFeatures(Set<String> features) {
		this.features = features != null ? new HashSet<>(features) : new HashSet<>();
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getTimezone() {
		return timezone;
	}

	public void setTimezone(String timezone) {
		this.timezone = timezone;
	}

	public String getTheme() {
		return theme;
	}

	public void setTheme(String theme) {
		this.theme = theme;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, String> attributes) {
		this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
	}

	// -------------------------------------------------------------------------
	// Convenience query methods
	// -------------------------------------------------------------------------

	/**
	 * Returns {@code true} if this tenant's status is {@link TenantStatus#ACTIVE}.
	 *
	 * <p>Computed from {@link #status}; not a persisted column. Excluded from Holon's
	 * writable bean property model automatically (see
	 * {@link com.holonplatform.multitenant.config.TenantCoreBeanPropertyFix}) since it has
	 * neither a backing field nor a write method.
	 */
	public boolean isActive() {
		return TenantStatus.ACTIVE == status;
	}

	/**
	 * Returns {@code true} if the named feature is available to this tenant — either
	 * because it is listed in the per-tenant {@link #features()} set or because
	 * the tenant's {@link TenantPlan} includes it by default.
	 *
	 * @param featureKey the feature key (case-sensitive)
	 */
	public boolean hasFeature(String featureKey) {
		return features.contains(featureKey) || plan.includesFeature(featureKey);
	}

	/**
	 * Overload of {@link #hasFeature(String)} accepting a {@link TenantFeature} enum constant.
	 *
	 * @param feature the well-known feature flag
	 */
	public boolean hasFeature(TenantFeature feature) {
		return hasFeature(feature.key());
	}

	/**
	 * Returns a new {@link Builder} pre-populated with all fields of this instance,
	 * allowing selective mutation through the fluent API.
	 */
	public Builder toBuilder() {
		return new Builder(tenantId)
				.name(name)
				.plan(plan)
				.status(status)
				.features(features)
				.locale(locale)
				.timezone(timezone)
				.theme(theme)
				.attributes(attributes);
	}

	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	/**
	 * Starts a fluent {@link Builder} for a new {@code TenantDetails}.
	 *
	 * @param tenantId unique, URL-safe tenant identifier — must not be null
	 */
	public static Builder builder(String tenantId) {
		return new Builder(tenantId);
	}

	// -------------------------------------------------------------------------
	// equals / hashCode / toString
	// -------------------------------------------------------------------------

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof TenantDetails that)) {
			return false;
		}
		return Objects.equals(tenantId, that.tenantId)
				&& Objects.equals(name, that.name)
				&& plan == that.plan
				&& status == that.status
				&& Objects.equals(features, that.features)
				&& Objects.equals(locale, that.locale)
				&& Objects.equals(timezone, that.timezone)
				&& Objects.equals(theme, that.theme)
				&& Objects.equals(attributes, that.attributes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tenantId, name, plan, status, features, locale, timezone, theme, attributes);
	}

	@Override
	public String toString() {
		return "TenantDetails[tenantId=" + tenantId + ", name=" + name + ", plan=" + plan
				+ ", status=" + status + ", features=" + features + ", locale=" + locale
				+ ", timezone=" + timezone + ", theme=" + theme + ", attributes=" + attributes + "]";
	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	/**
	 * Fluent builder for {@link TenantDetails}.
	 *
	 * <p>Defaults: {@code plan = FREE}, {@code status = ACTIVE}.
	 */
	public static final class Builder {

		private final String            tenantId;
		private       String            name;
		private       TenantPlan        plan      = TenantPlan.FREE;
		private       TenantStatus      status    = TenantStatus.ACTIVE;
		private final Set<String>       features  = new HashSet<>();
		private       String            locale;
		private       String            timezone;
		private       String            theme;
		private final Map<String, String> attributes = new HashMap<>();

		private Builder(String tenantId) {
			this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
		}

		public Builder name(String name) {
			this.name = name;
			return this;
		}

		public Builder plan(TenantPlan plan) {
			this.plan = Objects.requireNonNull(plan, "plan must not be null");
			return this;
		}

		public Builder status(TenantStatus status) {
			this.status = Objects.requireNonNull(status, "status must not be null");
			return this;
		}

		/** Adds a single feature key. */
		public Builder feature(String featureKey) {
			this.features.add(featureKey);
			return this;
		}

		/** Adds a well-known feature flag. */
		public Builder feature(TenantFeature feature) {
			this.features.add(feature.key());
			return this;
		}

		/** Adds all feature keys from the supplied set (null-safe). */
		public Builder features(Set<String> features) {
			if (features != null) {
				this.features.addAll(features);
			}
			return this;
		}

		public Builder locale(String locale) {
			this.locale = locale;
			return this;
		}

		public Builder timezone(String timezone) {
			this.timezone = timezone;
			return this;
		}

		public Builder theme(String theme) {
			this.theme = theme;
			return this;
		}

		/** Adds a single metadata attribute. */
		public Builder attribute(String key, String value) {
			this.attributes.put(key, value);
			return this;
		}

		/** Merges the supplied map into the attributes (null-safe). */
		public Builder attributes(Map<String, String> attrs) {
			if (attrs != null) {
				this.attributes.putAll(attrs);
			}
			return this;
		}

		/** Constructs the {@link TenantDetails} record. */
		public TenantDetails build() {
			return new TenantDetails(tenantId, name, plan, status,
					Set.copyOf(features), locale, timezone, theme, Map.copyOf(attributes));
		}
	}
}

