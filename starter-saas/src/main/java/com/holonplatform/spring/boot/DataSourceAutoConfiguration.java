package com.holonplatform.spring.boot;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Compatibility stub for {@code holon-spring-boot:12.0.0}.
 *
 * <p>That upstream artifact registers this class in its
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * file but accidentally ships without the compiled class.  Spring Boot's context refresh
 * therefore throws a {@link java.io.FileNotFoundException} when it tries to load this
 * auto-configuration.
 *
 * <p>This empty {@code @AutoConfiguration} provides the missing class on the classpath,
 * keeping Spring Boot happy while doing nothing — the DataSource-related configuration
 * that would have been registered here is now handled by
 * {@link com.holonplatform.multitenant.data.config.TenantDataAutoConfiguration}.
 *
 * <p><strong>Remove this class</strong> once {@code holon-spring-boot} ships a fixed
 * release that either includes the real implementation or removes the stale import.
 */
@AutoConfiguration
public class DataSourceAutoConfiguration {
    // Intentionally empty stub — see Javadoc above.
}

