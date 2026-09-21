package com.holonplatform.multitenant.demo.config;

import java.io.IOException;
import java.net.ServerSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import redis.embedded.RedisServer;

import java.util.Map;

/**
 * Starts a real {@code redis-server} in-process, so F23 can be demonstrated without Docker and
 * without a separately installed Redis.
 *
 * <h2>Why an EnvironmentPostProcessor and not a @Bean</h2>
 * <p>The port has to be in the {@code Environment} before Spring Boot's
 * {@code RedisAutoConfiguration} reads {@code spring.data.redis.port} to build the connection
 * factory, and a {@code @Bean} cannot be guaranteed to run that early. An
 * {@link EnvironmentPostProcessor} runs while the environment is still being prepared — before
 * any bean definition is even considered — which is the only point at which a port chosen at
 * runtime can still be injected.
 *
 * <p>The port is chosen by asking the OS for a free one rather than hard-coding 6379: a
 * developer with a real Redis already running would otherwise find the demo silently talking to
 * it, and two demo runs at once would collide.
 *
 * <h2>What this is not</h2>
 * <p>This is a convenience for a demo, not a pattern to copy. A deployment points
 * {@code spring.data.redis.*} at a real server and drops the embedded dependency; nothing else
 * changes, because everything downstream only ever sees a {@code RedisConnectionFactory}.
 */
public class EmbeddedRedisEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedRedisEnvironmentPostProcessor.class);

    /** Set to false to point the demo at a Redis of your own. */
    static final String ENABLED_PROPERTY = "demo.redis.embedded";

    private static RedisServer server;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty(ENABLED_PROPERTY, Boolean.class, Boolean.TRUE)) {
            return;
        }
        // Spring Boot prepares the environment more than once in some contexts (and tests reuse
        // the JVM across contexts), so the server is started at most once per JVM and reused.
        synchronized (EmbeddedRedisEnvironmentPostProcessor.class) {
            if (server == null) {
                server = start();
            }
        }
        environment.getPropertySources().addFirst(new MapPropertySource("embeddedRedis",
                Map.of("spring.data.redis.host", "localhost",
                        "spring.data.redis.port", server.ports().getFirst())));
    }

    private static RedisServer start() {
        try {
            int port = freePort();
            RedisServer started = RedisServer.newRedisServer()
                    .port(port)
                    // The bundled binary defaults to saving to disk on a schedule, which a demo
                    // has no use for and which litters the working directory with dump.rdb.
                    .setting("save \"\"")
                    .setting("appendonly no")
                    .build();
            started.start();
            log.info("Embedded Redis listening on port {}", port);

            // Stopped on JVM exit rather than on context close: the same server is shared by
            // every Spring context in the JVM, and a test suite closes several of them.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> stopQuietly(started)));
            return started;
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the embedded Redis. Set "
                    + ENABLED_PROPERTY + "=false and point spring.data.redis.* at your own.", e);
        }
    }

    private static void stopQuietly(RedisServer started) {
        try {
            started.stop();
        } catch (Exception e) {
            log.debug("Embedded Redis did not stop cleanly", e);
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
