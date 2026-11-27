public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openMillis;
    private final Clock clock;
    private State state = State.CLOSED;
    private int failures;
    private long openedAt;

    public CircuitBreaker(int failureThreshold, long openMillis, Clock clock) {
        if (failureThreshold < 1 || openMillis < 1) {
            throw new IllegalArgumentException("failureThreshold and openMillis must be at least 1");
        }
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
        this.clock = clock;
    }

    public synchronized boolean allowRequest() {
        if (state == State.OPEN && clock.nowMillis() - openedAt > openMillis) {
            state = State.HALF_OPEN; // this call is the single trial
            return true;
        }
        return state == State.CLOSED;
    }

    public synchronized void recordSuccess() {
        if (state == State.CLOSED) {
            failures = 0;
        } else if (state == State.HALF_OPEN) {
            state = State.CLOSED;
            failures = 0;
        }
    }

    public synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
        } else if (state == State.CLOSED && ++failures >= failureThreshold) {
            open();
        }
    }

    private void open() {
        state = State.OPEN;
        openedAt = clock.nowMillis();
        failures = 0;
    }

    public synchronized State state() {
        return state;
    }
}
