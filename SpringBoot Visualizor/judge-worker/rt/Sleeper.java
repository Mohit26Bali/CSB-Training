/** Provided to the learner. The sandboxed run injects a recording implementation, so no test ever sleeps. */
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}
