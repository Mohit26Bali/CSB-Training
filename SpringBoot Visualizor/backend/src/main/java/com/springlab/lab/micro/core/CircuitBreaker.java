package com.springlab.lab.micro.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A circuit breaker for ONE caller and ONE dependency, driven by the simulated clock. It is SpringLab's own minimal state machine,
 * not a resilience library.
 *
 * <pre>
 *   CLOSED  --(failureThreshold consecutive failures)--&gt;  OPEN
 *   OPEN    --(openMillis have elapsed; the next call is a probe)--&gt;  HALF_OPEN
 *   HALF_OPEN --probe succeeds--&gt;  CLOSED      HALF_OPEN --probe fails--&gt;  OPEN (the timer restarts)
 * </pre>
 *
 * <p>Explicit rules: only technical failures, timeouts, unavailability and bulkhead rejections count as failures; a BUSINESS refusal
 * means the dependency is healthy and resets the count. The breaker leaves OPEN when {@code now - openedAt >= openMillis} (exactly at
 * the boundary it is already half-open). In HALF_OPEN exactly ONE probe call is let through; further calls are refused until it ends.
 */
public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public record Transition(State from, State to, long atMillis, String reason) {
        public String describe() {
            return from + " → " + to + " at " + atMillis + " ms (" + reason + ")";
        }
    }

    private final int failureThreshold;
    private final int openMillis;
    private final SimClock clock;
    private final List<Transition> transitions = new ArrayList<>();
    private State state = State.CLOSED;
    private int consecutiveFailures;
    private long openedAt;
    private boolean probeInFlight;

    public CircuitBreaker(int failureThreshold, int openMillis, SimClock clock) {
        if (failureThreshold < 1 || openMillis < 1) {
            throw new IllegalArgumentException("threshold and open duration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
        this.clock = clock;
    }

    public State state() {
        return state;
    }

    public List<Transition> transitions() {
        return List.copyOf(transitions);
    }

    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    /** True if a call may be made now. In HALF_OPEN this also reserves the single probe. */
    public boolean allowRequest() {
        if (state == State.OPEN && clock.now() - openedAt >= openMillis) {
            moveTo(State.HALF_OPEN, "open duration of " + openMillis + " ms elapsed");
        }
        return switch (state) {
            case CLOSED -> true;
            case OPEN -> false;
            case HALF_OPEN -> {
                if (probeInFlight) {
                    yield false;
                }
                probeInFlight = true;
                yield true;
            }
        };
    }

    public void recordSuccess() {
        probeInFlight = false;
        consecutiveFailures = 0;
        if (state == State.HALF_OPEN) {
            moveTo(State.CLOSED, "the probe call succeeded");
        }
    }

    public void recordFailure() {
        probeInFlight = false;
        if (state == State.HALF_OPEN) {
            openedAt = clock.now();
            moveTo(State.OPEN, "the probe call failed");
            return;
        }
        consecutiveFailures++;
        if (state == State.CLOSED && consecutiveFailures >= failureThreshold) {
            openedAt = clock.now();
            moveTo(State.OPEN, consecutiveFailures + " consecutive failures");
        }
    }

    private void moveTo(State next, String reason) {
        transitions.add(new Transition(state, next, clock.now(), reason));
        state = next;
        if (next == State.CLOSED) {
            consecutiveFailures = 0;
        }
    }
}
