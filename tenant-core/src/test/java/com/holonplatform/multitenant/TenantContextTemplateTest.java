package com.holonplatform.multitenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantContextTemplate}.
 */
class TenantContextTemplateTest {

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	@Test
	void run_bindsTenantForDurationOfAction() {
		TenantDetails tenant = TenantDetails.builder("acme").build();
		AtomicBoolean sawTenantBound = new AtomicBoolean(false);

		TenantContextTemplate.run(tenant, () -> {
			sawTenantBound.set(TenantContext.get().map(TenantDetails::tenantId).equals(java.util.Optional.of("acme")));
		});

		assertThat(sawTenantBound).isTrue();
	}

	@Test
	void run_clearsContext_whenNothingWasPreviouslyBound() {
		TenantContextTemplate.run(TenantDetails.builder("acme").build(), () -> {
		});

		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void run_restoresPreviousTenant_whenNestedInsideAnotherContext() {
		TenantDetails outer = TenantDetails.builder("outer-tenant").build();
		TenantDetails inner = TenantDetails.builder("inner-tenant").build();
		TenantContext.set(outer);

		TenantContextTemplate.run(inner, () -> assertThat(TenantContext.require().tenantId()).isEqualTo("inner-tenant"));

		// The outer context must be restored, not cleared, once the inner block completes
		assertThat(TenantContext.require().tenantId()).isEqualTo("outer-tenant");
	}

	@Test
	void run_restoresPreviousTenant_evenWhenActionThrows() {
		TenantDetails outer = TenantDetails.builder("outer-tenant").build();
		TenantContext.set(outer);

		assertThatThrownBy(() -> TenantContextTemplate.run(TenantDetails.builder("inner-tenant").build(), () -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(TenantContext.require().tenantId()).isEqualTo("outer-tenant");
	}

	@Test
	void run_throwsOnNullTenant() {
		assertThatThrownBy(() -> TenantContextTemplate.run(null, () -> {
		})).isInstanceOf(NullPointerException.class);
	}

	@Test
	void run_throwsOnNullAction() {
		assertThatThrownBy(() -> TenantContextTemplate.run(TenantDetails.builder("acme").build(), null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void call_returnsActionResult_andRestoresPreviousContext() {
		TenantDetails outer = TenantDetails.builder("outer-tenant").build();
		TenantContext.set(outer);

		String result = TenantContextTemplate.call(TenantDetails.builder("inner-tenant").build(),
				() -> TenantContext.require().tenantId());

		assertThat(result).isEqualTo("inner-tenant");
		assertThat(TenantContext.require().tenantId()).isEqualTo("outer-tenant");
	}

	@Test
	void call_restoresPreviousTenant_evenWhenActionThrows() {
		TenantContext.set(TenantDetails.builder("outer-tenant").build());

		assertThatThrownBy(() -> TenantContextTemplate.call(TenantDetails.builder("inner-tenant").build(), () -> {
			throw new IllegalStateException("boom");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(TenantContext.require().tenantId()).isEqualTo("outer-tenant");
	}

	@Test
	void call_throwsOnNullTenant() {
		assertThatThrownBy(() -> TenantContextTemplate.call(null, () -> "x"))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void call_throwsOnNullAction() {
		assertThatThrownBy(() -> TenantContextTemplate.call(TenantDetails.builder("acme").build(), null))
				.isInstanceOf(NullPointerException.class);
	}

	// -------------------------------------------------------------------------
	// Low-level bind/restoreBound primitives
	// -------------------------------------------------------------------------

	@Test
	void bind_returnsNull_whenNothingPreviouslyBound() {
		TenantDetails previous = TenantContextTemplate.bind(TenantDetails.builder("acme").build());

		assertThat(previous).isNull();
		assertThat(TenantContext.require().tenantId()).isEqualTo("acme");
	}

	@Test
	void bind_returnsPreviousTenant_whenAlreadyBound() {
		TenantDetails outer = TenantDetails.builder("outer-tenant").build();
		TenantContext.set(outer);

		TenantDetails previous = TenantContextTemplate.bind(TenantDetails.builder("inner-tenant").build());

		assertThat(previous).isSameAs(outer);
		assertThat(TenantContext.require().tenantId()).isEqualTo("inner-tenant");
	}

	@Test
	void restoreBound_clearsContext_whenPreviousIsNull() {
		TenantContextTemplate.bind(TenantDetails.builder("acme").build());

		TenantContextTemplate.restoreBound(null);

		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void restoreBound_reinstatesPreviousTenant() {
		TenantDetails outer = TenantDetails.builder("outer-tenant").build();
		TenantDetails previous = TenantContextTemplate.bind(outer);
		TenantContextTemplate.bind(TenantDetails.builder("inner-tenant").build());

		TenantContextTemplate.restoreBound(outer);

		assertThat(TenantContext.require().tenantId()).isEqualTo("outer-tenant");
		assertThat(previous).isNull(); // sanity: outer was itself the first thing bound in this test
	}

	@Test
	void bind_throwsOnNullTenant() {
		assertThatThrownBy(() -> TenantContextTemplate.bind(null)).isInstanceOf(NullPointerException.class);
	}
}
