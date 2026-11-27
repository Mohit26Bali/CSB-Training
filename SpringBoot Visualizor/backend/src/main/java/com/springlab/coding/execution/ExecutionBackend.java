package com.springlab.coding.execution;

import java.time.Duration;

/**
 * Where learner code is run: always OUTSIDE the SpringLab JVM, inside a sandbox the backend does not control. There is
 * deliberately no implementation that compiles or runs learner code in this process, and none may be added: if no safe backend
 * is configured the answer is "unavailable", never "run it here".
 */
public interface ExecutionBackend {

    String name();

    /** Cheap to call repeatedly (implementations cache). Never starts learner code. */
    Availability availability();

    /**
     * Judges one request and returns a result. Blocks until the job ends. It must kill the whole sandbox (every process of it)
     * when {@code hardTimeout} passes or when the thread is interrupted, and in that case must not return a result that claims
     * the learner passed.
     *
     * @param jobId 16 lower-case hex characters, unique per job; also the handle for {@link #cancel}
     * @throws InterruptedException if the calling thread was interrupted (the job has then been cancelled)
     */
    ExecutionResult execute(String jobId, ExecutionRequest request, Duration hardTimeout) throws InterruptedException;

    /** Kills the sandbox of a job. Best effort, idempotent, safe to call for a job that already ended. */
    void cancel(String jobId);
}
