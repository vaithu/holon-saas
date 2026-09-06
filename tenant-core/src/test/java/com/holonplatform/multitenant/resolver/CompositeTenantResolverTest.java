package com.holonplatform.multitenant.resolver;

import com.holonplatform.multitenant.TenantResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CompositeTenantResolver}.
 */
class CompositeTenantResolverTest {

	// -------------------------------------------------------------------------
	// Positive cases
	// -------------------------------------------------------------------------

	@Test
	void resolveTenantId_returnsFirstNonEmpty() {
		TenantResolver alwaysEmpty = request -> Optional.empty();
		TenantResolver returnsAcme = request -> Optional.of("acme");
		TenantResolver neverCalled = request -> { throw new AssertionError("Should not be reached"); };

		CompositeTenantResolver composite = new CompositeTenantResolver(alwaysEmpty, returnsAcme, neverCalled);

		assertThat(composite.resolveTenantId(new MockHttpServletRequest())).contains("acme");
	}

	@Test
	void resolveTenantId_respectsOrderField() {
		// Resolver with order=5 should be tried before order=10 even when passed second
		TenantResolver order10 = new TenantResolver() {
			@Override public Optional<String> resolveTenantId(jakarta.servlet.http.HttpServletRequest r) {
				return Optional.of("order10");
			}
			@Override public int getOrder() { return 10; }
		};
		TenantResolver order5 = new TenantResolver() {
			@Override public Optional<String> resolveTenantId(jakarta.servlet.http.HttpServletRequest r) {
				return Optional.of("order5");
			}
			@Override public int getOrder() { return 5; }
		};

		CompositeTenantResolver composite = new CompositeTenantResolver(order10, order5);

		assertThat(composite.resolveTenantId(new MockHttpServletRequest())).contains("order5");
	}

	@Test
	void resolveTenantId_returnsEmpty_whenAllEmpty() {
		TenantResolver r1 = request -> Optional.empty();
		TenantResolver r2 = request -> Optional.empty();

		CompositeTenantResolver composite = new CompositeTenantResolver(r1, r2);

		assertThat(composite.resolveTenantId(new MockHttpServletRequest())).isEmpty();
	}

	@Test
	void canBeConstructedFromList() {
		List<TenantResolver> resolvers = List.of(
				request -> Optional.of("from-list")
		);
		CompositeTenantResolver composite = new CompositeTenantResolver(resolvers);

		assertThat(composite.resolveTenantId(new MockHttpServletRequest())).contains("from-list");
	}

	// -------------------------------------------------------------------------
	// Negative cases
	// -------------------------------------------------------------------------

	@Test
	void constructor_throwsOnNullArray() {
		assertThatThrownBy(() -> new CompositeTenantResolver((TenantResolver[]) null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void constructor_throwsOnEmptyArray() {
		assertThatThrownBy(() -> new CompositeTenantResolver(new TenantResolver[0]))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void constructor_throwsOnEmptyList() {
		assertThatThrownBy(() -> new CompositeTenantResolver(List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void getResolvers_isImmutable() {
		CompositeTenantResolver composite = new CompositeTenantResolver(request -> Optional.empty());

		assertThatThrownBy(() -> composite.getResolvers().add(request -> Optional.empty()))
				.isInstanceOf(UnsupportedOperationException.class);
	}
}

