/** Provided to the learner: a source of time that tests control, so no exercise ever reads the real clock. */
public interface Clock {
    long nowMillis();
}
