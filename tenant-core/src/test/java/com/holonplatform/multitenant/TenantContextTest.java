package com.holonplatform.multitenant;

import com.holonplatform.multitenant.exception.TenantContextException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TenantContext}.
 */
class TenantContextTest {

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	// -------------------------------------------------------------------------
	// Positive cases
	// -------------------------------------------------------------------------

	@Test
	void set_andGet_returnsSameTenant() {
		TenantDetails tenant = TenantDetails.builder("acme").name("Acme Corp").build();

		TenantContext.set(tenant);

		assertThat(TenantContext.get()).isPresent().contains(tenant);
	}

	@Test
	void isSet_returnsTrueAfterSet() {
		TenantContext.set(TenantDetails.builder("acme").build());
		assertThat(TenantContext.isSet()).isTrue();
	}

	@Test
	void isSet_returnsFalseInitially() {
		assertThat(TenantContext.isSet()).isFalse();
	}

	@Test
	void get_returnsEmptyWhenNotSet() {
		assertThat(TenantContext.get()).isEmpty();
	}

	@Test
	void require_returnsTenantWhenSet() {
		TenantDetails tenant = TenantDetails.builder("acme").build();
		TenantContext.set(tenant);

		assertThat(TenantContext.require()).isSameAs(tenant);
	}

	@Test
	void clear_removesContext() {
		TenantContext.set(TenantDetails.builder("acme").build());
		TenantContext.clear();

		assertThat(TenantContext.isSet()).isFalse();
		assertThat(TenantContext.get()).isEmpty();
	}

	@Test
	void clear_isIdempotent_whenAlreadyClear() {
		assertThatCode(() -> {
			TenantContext.clear();
			TenantContext.clear();
		}).doesNotThrowAnyException();
	}

	@Test
	void currentTenantId_returnsTenantId_whenSet() {
		TenantContext.set(TenantDetails.builder("acme").build());
		assertThat(TenantContext.currentTenantId()).isPresent().contains("acme");
	}

	@Test
	void currentTenantId_returnsEmpty_whenNotSet() {
		assertThat(TenantContext.currentTenantId()).isEmpty();
	}

	@Test
	void set_replacesExistingContext() {
		TenantDetails first  = TenantDetails.builder("alpha").build();
		TenantDetails second = TenantDetails.builder("beta").build();

		TenantContext.set(first);
		TenantContext.set(second);

		assertThat(TenantContext.require().tenantId()).isEqualTo("beta");
	}

	// -------------------------------------------------------------------------
	// Negative cases
	// -------------------------------------------------------------------------

	@Test
	void set_throwsOnNullTenant() {
		assertThatThrownBy(() -> TenantContext.set(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void require_throwsTenantContextException_whenNotSet() {
		assertThatThrownBy(TenantContext::require)
				.isInstanceOf(TenantContextException.class)
				.hasMessageContaining("No TenantDetails");
	}

	// -------------------------------------------------------------------------
	// Thread isolation
	// -------------------------------------------------------------------------

	@Test
	void context_isThreadLocal_differentThreadsHaveIndependentContext() throws InterruptedException {
		TenantContext.set(TenantDetails.builder("main-thread-tenant").build());

		Optional<String>[] childResult = new Optional[1];
		Thread child = new Thread(() -> childResult[0] = TenantContext.currentTenantId());
		child.start();
		child.join();

		// Child thread must NOT inherit the parent's context (ThreadLocal, not InheritableThreadLocal)
		assertThat(childResult[0]).isEmpty();
		// Parent still sees its value
		assertThat(TenantContext.currentTenantId()).contains("main-thread-tenant");
	}
}

