package com.holonplatform.multitenant.demo.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Supplies a second, deliberately <strong>not</strong> tenant-aware {@link DataSource} that
 * always lands on the database's default schema.
 *
 * <h2>Why a separate DataSource is needed</h2>
 * <p>{@code tenant-data} registers {@code TenantAwareDataSource} as the {@code @Primary}
 * DataSource. It routes every connection to {@code tenant_<id>} whenever a tenant is bound to
 * the {@code TenantContext}, and only falls back to the default schema when no tenant is bound.
 *
 * <p>That is exactly right for tenant-owned data, but wrong for the two things that are
 * inherently tenant-less:
 * <ul>
 *   <li>the platform tables ({@code tenant}, {@code demo_account}, ...), and</li>
 *   <li>the Flyway migration that creates them.</li>
 * </ul>
 *
 * <p>Routing those through the primary DataSource would make their location depend on whatever
 * tenant happened to be bound on the calling thread &mdash; so, for example, reading credentials
 * while a tenant is bound would look for {@code demo_account} inside that tenant's schema and
 * fail. Pinning them to their own DataSource removes that entire class of bug.
 *
 * <h2>Why the platform DataSource is not a bean</h2>
 * <p>It is created as a plain object rather than being exposed as a {@code @Bean}. Holon's JPA
 * auto-configuration selects the DataSource to build the {@code EntityManagerFactory} from by
 * type and does <em>not</em> honour {@code @Primary}; with two DataSource beans present it bound
 * the EMF to this one, which silently sent every tenant-scoped JPA query to the default schema
 * and produced "Table TENANT_USERS not found". Keeping exactly one DataSource bean in the
 * context makes that misbinding impossible.
 */
@Configuration(proxyBeanMethods = false)
public class PlatformDataSourceConfig {

    private static DataSource createPlatformDataSource(String url, String username, String password,
            String driverClassName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        dataSource.setDriverClassName(driverClassName);
        return dataSource;
    }

    /**
     * Creates the platform schema.
     *
     * <p>Migration runs while this bean is constructed, which is before anything can read or
     * write a platform table. Spring Boot's own Flyway auto-configuration is disabled
     * ({@code spring.flyway.enabled=false}) because it would bind to the {@code @Primary}
     * tenant-aware DataSource instead of this one.
     *
     * <p>The per-tenant schemas are migrated separately, by the framework's
     * {@code TenantSchemaInitializer}, from {@code classpath:db/tenant-migration}.
     */
    @Bean(initMethod = "migrate")
    public Flyway platformFlyway(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:sa}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.driver-class-name:org.h2.Driver}") String driverClassName) {

        return Flyway.configure()
                .dataSource(createPlatformDataSource(url, username, password, driverClassName))
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    /**
     * @param platformFlyway declared only to force the platform migration to complete before
     *                       any component can execute a statement through this template
     */
    @Bean
    public JdbcTemplate platformJdbcTemplate(
            Flyway platformFlyway,
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username:sa}") String username,
            @Value("${spring.datasource.password:}") String password,
            @Value("${spring.datasource.driver-class-name:org.h2.Driver}") String driverClassName) {

        return new JdbcTemplate(createPlatformDataSource(url, username, password, driverClassName));
    }
}
