package com.holonplatform.multitenant.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SchemaNameResolver}.
 */
class SchemaNameResolverTest {

	@Test
	void withPrefix_appendsPrefixToTenantId() {
		SchemaNameResolver resolver = SchemaNameResolver.withPrefix("tenant_");
		assertThat(resolver.resolveSchema("acme")).isEqualTo("tenant_acme");
	}

	@Test
	void withPrefixAndSuffix_appendsBoth() {
		SchemaNameResolver resolver = SchemaNameResolver.withPrefixAndSuffix("t_", "_db");
		assertThat(resolver.resolveSchema("acme")).isEqualTo("t_acme_db");
	}

	@Test
	void identity_returnsTenantIdAsIs() {
		SchemaNameResolver resolver = SchemaNameResolver.identity();
		assertThat(resolver.resolveSchema("acme")).isEqualTo("acme");
	}

	@Test
	void withPrefix_emptyPrefixReturnsIdentity() {
		SchemaNameResolver resolver = SchemaNameResolver.withPrefix("");
		assertThat(resolver.resolveSchema("acme")).isEqualTo("acme");
	}

	@Test
	void withPrefix_throwsOnNullPrefix() {
		assertThatThrownBy(() -> SchemaNameResolver.withPrefix(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void withPrefixAndSuffix_throwsOnNullPrefix() {
		assertThatThrownBy(() -> SchemaNameResolver.withPrefixAndSuffix(null, "_db"))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void withPrefixAndSuffix_throwsOnNullSuffix() {
		assertThatThrownBy(() -> SchemaNameResolver.withPrefixAndSuffix("t_", null))
				.isInstanceOf(NullPointerException.class);
	}
}

