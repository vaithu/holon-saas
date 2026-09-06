package com.holonplatform.multitenant.data.datasource;

import com.holonplatform.multitenant.TenantContext;
import com.holonplatform.multitenant.TenantDetails;
import com.holonplatform.multitenant.data.TenantDataSourceRegistry;
import com.holonplatform.multitenant.data.TenantSchemaStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantAwareDataSource}.
 *
 * <p>Since the {@code masterDataSource}-named-bean requirement was removed, every case
 * (including SHARED and the no-tenant fallback) now routes through
 * {@link TenantDataSourceRegistry#getDataSource(String)}, where {@code null} means the
 * platform/bootstrap connection.
 */
class TenantAwareDataSourceTest {

	@AfterEach
	void clearContext() {
		TenantContext.clear();
	}

	// -------------------------------------------------------------------------
	// SHARED strategy
	// -------------------------------------------------------------------------

	@Test
	void shared_strategy_alwaysRoutesThroughNullTenantId() throws SQLException {
		DataSource shared   = mock(DataSource.class);
		Connection mockConn = mock(Connection.class);
		when(shared.getConnection()).thenReturn(mockConn);

		TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
		when(registry.getDataSource(null)).thenReturn(shared);

		TenantAwareDataSource ds = new TenantAwareDataSource(TenantSchemaStrategy.SHARED, registry);

		try (Connection conn = ds.getConnection()) {
			verify(registry, times(1)).getDataSource(null);
		}
	}

	@Test
	void shared_strategy_ignoresTenantInContext() throws SQLException {
		DataSource shared   = mock(DataSource.class);
		Connection mockConn = mock(Connection.class);
		when(shared.getConnection()).thenReturn(mockConn);

		TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
		when(registry.getDataSource(null)).thenReturn(shared);

		TenantContext.set(TenantDetails.builder("acme").build());

		TenantAwareDataSource ds = new TenantAwareDataSource(TenantSchemaStrategy.SHARED, registry);
		ds.getConnection();

		verify(registry, times(1)).getDataSource(null);
		verify(registry, never()).getDataSource("acme");
	}

	// -------------------------------------------------------------------------
	// SCHEMA_PER_TENANT strategy
	// -------------------------------------------------------------------------

	@Test
	void schemaTenantStrategy_routesToTenantDataSource_whenTenantSet() throws SQLException {
		TenantDetails tenant = TenantDetails.builder("acme").build();
		TenantContext.set(tenant);

		DataSource tenantDs = mock(DataSource.class);
		Connection conn     = mock(Connection.class);
		when(tenantDs.getConnection()).thenReturn(conn);

		TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
		when(registry.getDataSource("acme")).thenReturn(tenantDs);

		TenantAwareDataSource ds = new TenantAwareDataSource(
				TenantSchemaStrategy.SCHEMA_PER_TENANT, registry);

		ds.getConnection();

		verify(registry).getDataSource("acme");
		verify(tenantDs).getConnection();
	}

	@Test
	void schemaTenantStrategy_fallsBackToBootstrapConnection_whenNoTenantInContext() throws SQLException {
		DataSource bootstrap = mock(DataSource.class);
		Connection conn      = mock(Connection.class);
		when(bootstrap.getConnection()).thenReturn(conn);

		TenantDataSourceRegistry registry = mock(TenantDataSourceRegistry.class);
		when(registry.getDataSource(null)).thenReturn(bootstrap);

		TenantAwareDataSource ds = new TenantAwareDataSource(
				TenantSchemaStrategy.SCHEMA_PER_TENANT, registry);

		// No tenant in context
		ds.getConnection();

		verify(registry).getDataSource(null);
	}

	// -------------------------------------------------------------------------
	// Construction
	// -------------------------------------------------------------------------

	@Test
	void constructor_throwsOnNullStrategy() {
		assertThatThrownBy(() -> new TenantAwareDataSource(null, mock(TenantDataSourceRegistry.class)))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void constructor_throwsOnNullRegistry() {
		assertThatThrownBy(() ->
				new TenantAwareDataSource(TenantSchemaStrategy.SHARED, null))
				.isInstanceOf(NullPointerException.class);
	}
}
