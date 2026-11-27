package com.springlab.lab.micro.core;

/** Per-call settings chosen by the caller. The timeout is in SIMULATED milliseconds (see {@link SimClock}). {@code instance} is optional. */
public record CallOptions(int timeoutMillis, String instance) {

    public static final int DEFAULT_TIMEOUT_MILLIS = 200;

    public CallOptions {
        if (timeoutMillis < 1 || timeoutMillis > 10_000) {
            throw new IllegalArgumentException("timeout out of range");
        }
    }

    public static CallOptions defaults() {
        return new CallOptions(DEFAULT_TIMEOUT_MILLIS, null);
    }

    public static CallOptions timeout(int millis) {
        return new CallOptions(millis, null);
    }

    /** The same options, aimed at one named instance of the target service (see ServiceBehaviours.setInstance). */
    public CallOptions onInstance(String instanceId) {
        return new CallOptions(timeoutMillis, instanceId);
    }
}
