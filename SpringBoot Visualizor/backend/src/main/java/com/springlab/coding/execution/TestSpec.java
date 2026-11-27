package com.springlab.coding.execution;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One test of the pilot problem as the backend hands it to the worker. The backend reads tests from its database and is the only
 * source of them: nothing in a request from a learner can add or change one.
 *
 * <p>The worker's trusted driver (outside the sandbox) receives the whole test. It sends only the INPUT (attempts, delays and the
 * scripted behaviour of the action) into the sandbox and compares the observation that comes back with the EXPECTED part, which
 * therefore never enters the sandbox.
 *
 * <p>Every value is restricted to a small alphabet, so a test cannot carry anything that the worker's line format could misread.
 *
 * @param steps    the scripted outcomes of the action, one per call: {@code R:text} returns, {@code T:text} throws a TransientException,
 *                 {@code O:text} throws an IllegalStateException
 */
public record TestSpec(String name, boolean visible, int maxAttempts, long baseDelayMillis, long maxDelayMillis, List<String> steps,
                       String expectedKind, String expectedExceptionClass, int expectedIdentity, int expectedCalls,
                       List<Long> expectedSleeps, String expectedValue) implements WorkerTest {

    private static final Pattern STEP = Pattern.compile("[RTO]:[A-Za-z0-9]{1,20}");
    private static final Pattern VALUE = Pattern.compile("[A-Za-z0-9]{0,40}");
    private static final Set<String> EXCEPTION_CLASSES = Set.of("", "TransientException", "java.lang.IllegalStateException", "java.lang.IllegalArgumentException");

    public TestSpec {
        if (name == null || name.isBlank() || name.length() > 200 || name.chars().anyMatch(c -> c < 0x20)) {
            throw new IllegalArgumentException("invalid test name");
        }
        if (baseDelayMillis < 0 || maxDelayMillis < 0) {
            throw new IllegalArgumentException("delays must not be negative");
        }
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (steps.size() > 100 || !steps.stream().allMatch(s -> STEP.matcher(s).matches())) {
            throw new IllegalArgumentException("invalid steps");
        }
        if (!"RETURN".equals(expectedKind) && !"THROW".equals(expectedKind)) {
            throw new IllegalArgumentException("invalid expected kind");
        }
        if (expectedExceptionClass == null || !EXCEPTION_CLASSES.contains(expectedExceptionClass)) {
            throw new IllegalArgumentException("invalid expected exception class");
        }
        if (expectedValue == null || !VALUE.matcher(expectedValue).matches()) {
            throw new IllegalArgumentException("invalid expected value");
        }
        if (expectedCalls < 0 || expectedCalls > 1000 || expectedIdentity < -1 || expectedIdentity > 1000) {
            throw new IllegalArgumentException("invalid expected counters");
        }
        expectedSleeps = expectedSleeps == null ? List.of() : List.copyOf(expectedSleeps);
        if (expectedSleeps.size() > 500 || expectedSleeps.stream().anyMatch(v -> v == null || v < 0)) {
            throw new IllegalArgumentException("invalid expected sleeps");
        }
    }

    /** The worker's line format for this test (see {@code Scenarios.fromWire} in the judge-worker). */
    @Override
    public String toWireLine(int index) {
        return "TEST " + index + " " + (visible ? 1 : 0) + " " + b64(name) + " " + maxAttempts + " " + baseDelayMillis + " " + maxDelayMillis + " "
                + (steps.isEmpty() ? "-" : String.join(",", steps)) + " " + expectedKind + " " + b64(expectedExceptionClass) + " " + expectedIdentity + " "
                + expectedCalls + " " + (expectedSleeps.isEmpty() ? "-" : String.join(",", expectedSleeps.stream().map(String::valueOf).toList())) + " "
                + b64(expectedValue);
    }

    private static String b64(String s) {
        return s.isEmpty() ? "-" : Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** The expected values are test data: they must never reach a log line or a learner through a stray toString. */
    @Override
    public String toString() {
        return "TestSpec[" + (visible ? "visible" : "hidden") + "]";
    }
}
