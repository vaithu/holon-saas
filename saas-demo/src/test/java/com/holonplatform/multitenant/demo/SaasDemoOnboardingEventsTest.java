package com.holonplatform.multitenant.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.holonplatform.multitenant.demo.service.OnboardingEventLog;
import com.holonplatform.multitenant.demo.service.OnboardingFailureProbe;

/**
 * F26 — onboarding events, and the failure path that publishes one of them.
 *
 * <p>The success event is exercised by every other test in this module, since every workspace
 * they create is onboarded. What needs arranging is the failure: it is the branch that decides
 * whether a customer whose signup broke can try again, and on a healthy machine it never runs.
 */
@SpringBootTest
class SaasDemoOnboardingEventsTest {

    @Autowired private OnboardingFailureProbe probe;
    @Autowired private OnboardingEventLog     eventLog;

    @Test
    @DisplayName("a failed onboarding frees the workspace name, so the same name can be retried")
    void failedOnboardingReleasesTheIdentifier() {
        String transcript = probe.runFailureThenRetry();

        assertThat(transcript)
                .describedAs("transcript:%n%s", transcript)
                .contains("2. onboarding failed as intended")
                .contains("3. is the workspace name still taken? no")
                .contains("5. retried the same workspace name: succeeded");
    }

    @Test
    @DisplayName("both outcomes are published as events that a listener can act on")
    void bothOutcomesArePublished() {
        probe.runFailureThenRetry();

        List<OnboardingEventLog.Observed> recent = eventLog.recent();

        // The run produces one of each: the rigged attempt fails, the retry succeeds.
        assertThat(recent).extracting(OnboardingEventLog.Observed::outcome)
                .describedAs("observed events: %s", recent)
                .contains("FAILED", "COMPLETED");
    }

    @Test
    @DisplayName("the failure event carries the underlying cause, not just 'onboarding failed'")
    void failureEventReportsWhy() {
        probe.runFailureThenRetry();

        OnboardingEventLog.Observed failure = eventLog.recent().stream()
                .filter(observed -> "FAILED".equals(observed.outcome()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no FAILED event was published"));

        // Without unwrapping the cause chain a listener gets "onboarding failed for tenant x",
        // which is the one thing it already knew.
        assertThat(failure.detail())
                .isNotBlank()
                .doesNotContain("(no cause reported)");
    }
}
