package com.springlab.lab.micro.core;

/**
 * The result of one call as the CALLER sees it, plus {@code handlerInvoked}, which only the lab knows: the caller of a timed-out call
 * cannot tell whether the work happened, but the lab can show whether it did. {@code simulatedMillis} is simulated time.
 */
public record CallResult<R>(Outcome outcome, R value, String code, long simulatedMillis, boolean handlerInvoked, String spanId) {

    public boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }
}
