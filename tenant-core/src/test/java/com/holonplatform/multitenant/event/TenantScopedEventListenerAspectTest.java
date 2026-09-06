package com.holonplatform.multitenant.event;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for {@link TenantScopedEventListenerAspect}.
 *
 * <p>Bootstraps a minimal Spring context with AOP enabled and a plain {@code @EventListener}
 * bean, proving the aspect actually intercepts real Spring event dispatch (not just a
 * hand-constructed {@code ProceedingJoinPoint}), mirroring the pattern used by
 * {@code tenant-audit}'s {@code AuditAspectTest}.
 */
@SpringBootTest(classes = TenantScopedEventListenerAspectTest.TestConfig.class)
class TenantScopedEventListenerAspectTest {

	@Autowired
	ApplicationEventPublisher publisher;
	@Autowired
	RecordingListener listener;

	@AfterEach
	void clearContext() {
		TenantContext.clear();
		listener.clearObserved();
	}

	@Test
	void eventListener_hasTenantContextBound_forTenantScopedEvent() {
		TenantDetails tenant = TenantDetails.builder("acme").build();

		publisher.publishEvent(new FakeTenantScopedEvent(this, tenant));

		assertThat(listener.getObservedTenantIds()).containsExactly(Optional.of("acme"));
	}

	@Test
	void eventListener_leavesContextUnchanged_forNonTenantScopedEvent() {
		publisher.publishEvent(new PlainEvent(this));

		assertThat(listener.getObservedTenantIds()).containsExactly(Optional.empty());
	}

	@Test
	void eventListener_restoresPreviousTenant_afterHandlingScopedEvent() {
		TenantContext.set(TenantDetails.builder("outer-tenant").build());

		publisher.publishEvent(new FakeTenantScopedEvent(this, TenantDetails.builder("inner-tenant").build()));

		assertThat(listener.getObservedTenantIds()).containsExactly(Optional.of("inner-tenant"));
		assertThat(TenantContext.require().tenantId()).isEqualTo("outer-tenant");
	}

	@Test
	void eventListener_leavesContextUnchanged_whenTenantScopedEventReturnsNullTenant() {
		publisher.publishEvent(new FakeTenantScopedEvent(this, null));

		assertThat(listener.getObservedTenantIds()).containsExactly(Optional.empty());
	}

	// -------------------------------------------------------------------------
	// Fixtures
	// -------------------------------------------------------------------------

	static class FakeTenantScopedEvent extends ApplicationEvent implements TenantScopedEvent {
		private final TenantDetails tenant;

		FakeTenantScopedEvent(Object source, TenantDetails tenant) {
			super(source);
			this.tenant = tenant;
		}

		@Override
		public TenantDetails getTenant() {
			return tenant;
		}
	}

	static class PlainEvent extends ApplicationEvent {
		PlainEvent(Object source) {
			super(source);
		}
	}

	@Component
	static class RecordingListener {
		private final List<Optional<String>> observedTenantIds = new ArrayList<>();

		// Deliberately two narrow listener methods (not one method with parameter type
		// ApplicationEvent) — a broad ApplicationEvent parameter would also match Spring's own
		// internal lifecycle events (ContextRefreshedEvent, ApplicationReadyEvent, etc.),
		// polluting the observed list with unrelated framework noise.
		@EventListener
		public void onFakeTenantScopedEvent(FakeTenantScopedEvent event) {
			observedTenantIds.add(TenantContext.currentTenantId());
		}

		@EventListener
		public void onPlainEvent(PlainEvent event) {
			observedTenantIds.add(TenantContext.currentTenantId());
		}

		// Plain field access on this bean from the test would read the CGLIB proxy's own
		// (Objenesis-instantiated, therefore uninitialized) field copy rather than the real
		// target instance's state — go through a method call instead, which the proxy
		// correctly delegates to the real target.
		List<Optional<String>> getObservedTenantIds() {
			return observedTenantIds;
		}

		void clearObserved() {
			observedTenantIds.clear();
		}
	}

	@Configuration
	@EnableAspectJAutoProxy
	static class TestConfig {

		@Bean
		TenantScopedEventListenerAspect tenantScopedEventListenerAspect() {
			return new TenantScopedEventListenerAspect();
		}

		@Bean
		RecordingListener recordingListener() {
			return new RecordingListener();
		}
	}
}
