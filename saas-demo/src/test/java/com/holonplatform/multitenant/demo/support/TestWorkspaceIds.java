package com.holonplatform.multitenant.demo.support;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates workspace IDs that are unique for every sign-up performed by the demo test suite.
 *
 * <p>Tests used to build workspace IDs from {@code System.nanoTime() % 100000}. Those five digits
 * wrap around every 100 microseconds, so two sign-ups anywhere in a multi-minute run could easily
 * land on the same value. Because the demo application reuses a single cached Spring context (and
 * therefore a single database) across test classes, a repeated ID surfaced as a spurious
 * {@code SignupException: An account for 'owner@...' already exists in this workspace}.
 *
 * <p>The IDs produced here combine a per-JVM random run token with a monotonic counter, so they
 * cannot repeat within a run, nor across runs that happen to share a database. They always satisfy
 * the sign-up service's workspace ID rule: 3-64 characters of lowercase letters, digits or hyphens,
 * starting and ending with a letter or digit.
 */
public final class TestWorkspaceIds {

    private static final String RUN_TOKEN =
            Long.toUnsignedString(new SecureRandom().nextLong(), 36);

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestWorkspaceIds() {
    }

    /**
     * Returns a unique workspace ID starting with the given prefix, for example
     * {@code "acme-3kfz9p1qw8x2b-7"}.
     *
     * @param prefix lowercase prefix identifying the test; must not be null or blank
     * @return a unique, sign-up compatible workspace ID
     */
    public static String uniqueWorkspaceId(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be null or blank");
        }
        return prefix + "-" + RUN_TOKEN + "-" + SEQUENCE.incrementAndGet();
    }

    /**
     * Returns the owner e-mail address conventionally associated with a workspace, that is
     * {@code owner@<workspaceId>.test}. Deriving the address from the (unique) workspace ID keeps
     * owner accounts unique as well.
     *
     * @param workspaceId a workspace ID, typically from {@link #uniqueWorkspaceId(String)}
     * @return the owner e-mail address for that workspace
     */
    public static String ownerEmail(String workspaceId) {
        return "owner@" + workspaceId + ".test";
    }
}
