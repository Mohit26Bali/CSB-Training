package com.springlab.lab.micro.core;

/**
 * Bounded retries with deterministic exponential backoff. {@code maxAttempts} counts the first call, so 1 means no retry. The wait
 * before retry n is {@code baseBackoffMillis * 2^(n-1)}, capped at {@code maxBackoffMillis}: no randomness, no jitter, and it is
 * waited on the SIMULATED clock, so a run is fast and repeatable.
 */
public record RetryPolicy(int maxAttempts, int baseBackoffMillis, int maxBackoffMillis) {

    public RetryPolicy {
        if (maxAttempts < 1 || maxAttempts > 10 || baseBackoffMillis < 0 || maxBackoffMillis < baseBackoffMillis) {
            throw new IllegalArgumentException("invalid retry policy");
        }
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, 0, 0);
    }

    public static RetryPolicy of(int maxAttempts) {
        return new RetryPolicy(maxAttempts, 100, 800);
    }

    /** The wait before the {@code retryNumber}-th retry (1 = the first retry). */
    public int backoffBefore(int retryNumber) {
        long delay = (long) baseBackoffMillis << Math.min(retryNumber - 1, 20);
        return (int) Math.min(delay, maxBackoffMillis);
    }
}
