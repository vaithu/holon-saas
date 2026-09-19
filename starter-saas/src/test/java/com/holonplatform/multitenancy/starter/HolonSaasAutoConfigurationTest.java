package com.holonplatform.multitenancy.starter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HolonSaasAutoConfiguration}'s Virtual Threads startup check.
 *
 * <p>This starter's high-concurrency design assumes the host application dispatches requests on
 * Virtual Threads. Since an individual auto-configuration cannot force that JVM/servlet-container-wide
 * setting itself, {@link HolonSaasAutoConfiguration#holonSaasReadyMarker} logs a warning instead when
 * it detects the setting is missing or ineffective — these tests assert that warning fires (or doesn't)
 * under the relevant conditions by capturing log output via a Logback {@link ListAppender}.
 */
class HolonSaasAutoConfigurationTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) org.slf4j.LoggerFactory.getLogger(HolonSaasAutoConfiguration.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void warnsWhenVirtualThreadsPropertyIsMissing() {
        MockEnvironment environment = new MockEnvironment();

        HolonSaasAutoConfiguration.holonSaasReadyMarker(environment);

        assertThat(warningMessages()).anyMatch(msg -> msg.contains("Virtual Threads are NOT enabled"));
    }

    @Test
    void warnsWhenVirtualThreadsPropertyIsExplicitlyFalse() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.threads.virtual.enabled", "false");

        HolonSaasAutoConfiguration.holonSaasReadyMarker(environment);

        assertThat(warningMessages()).anyMatch(msg -> msg.contains("Virtual Threads are NOT enabled"));
    }

    @Test
    void noWarningWhenVirtualThreadsAreEnabledOnJava21Plus() {
        // The build only runs on Java 21+ (this project targets Java 25), so when the property is
        // enabled the "wrong Java version" branch must not fire either.
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.threads.virtual.enabled", "true");

        HolonSaasAutoConfiguration.holonSaasReadyMarker(environment);

        assertThat(warningMessages()).isEmpty();
    }

    private java.util.List<String> warningMessages() {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
