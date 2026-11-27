package com.springlab.coding.execution;

import java.util.List;
import java.util.Set;

/**
 * What the execution layer is asked to do. There is no path, command, argument or classpath in it: the problem comes from a fixed
 * allow-list, the tests come from the backend's own database, and everything else the sandbox needs is decided by the worker and the
 * configuration, never by the caller.
 *
 * <p>{@link #toString()} never includes the source or the tests, so a request cannot leak learner code or test data into a log line.
 *
 * @param tests the tests to run, chosen by the backend. Empty means "the worker's built-in tests" (the retry exercise only; used by
 *              infrastructure tests).
 */
public record ExecutionRequest(Problem problem, Mode mode, byte[] source, List<? extends WorkerTest> tests) {

    /**
     * The exercises the worker can judge. The constant is the HARNESS KIND stored with the problem in the database and passed to the worker
     * as a fixed name. {@code operations} is the complete list of operations the exercise's scripts may use (empty for the retry exercise,
     * whose tests are not scripts). The class a learner must declare is fixed by the worker for each kind.
     */
    public enum Problem {
        RETRY_EXECUTOR(Set.of()),
        CIRCUIT_BREAKER(Set.of("new", "at", "allow", "ok", "fail", "state")),
        BULKHEAD(Set.of("new", "call", "start", "finish", "inflight", "permits")),
        IDEMPOTENCY(Set.of("new", "at", "handle", "runs")),
        SAGA(Set.of("new", "run", "calls", "pending")),
        OUTBOX(Set.of("events", "failpub", "failmark", "relay", "consume", "delivered", "applied", "unpublished")),
        // Phase F6: the everyday exercises share one reflective adapter in the worker ("new" builds the object, "call" calls a method)
        PASSWORD_VALIDATOR(Set.of("new", "call")),
        LOAN_ELIGIBILITY(Set.of("new", "call")),
        SALARY_CALCULATOR(Set.of("new", "call")),
        BANK_ACCOUNT(Set.of("new", "call")),
        TRAINING_REGISTRY(Set.of("new", "call")),
        GRADE_CALCULATOR(Set.of("new", "call")),
        REQUEST_VALIDATOR(Set.of("new", "call")),
        PRODUCT_SERVICE(Set.of("new", "call")),
        ERROR_MAPPER(Set.of("new", "call")),
        BEAN_REGISTRY(Set.of("new", "call"));

        private final Set<String> operations;

        Problem(Set<String> operations) {
            this.operations = operations;
        }

        public Set<String> operations() {
            return operations;
        }

        public boolean scripted() {
            return this != RETRY_EXECUTOR;
        }
    }

    /** RUN judges the visible tests only; SUBMIT judges all of them. */
    public enum Mode {
        RUN, SUBMIT
    }

    public ExecutionRequest {
        if (problem == null || mode == null || source == null) {
            throw new IllegalArgumentException("problem, mode and source are required");
        }
        source = source.clone();
        tests = tests == null ? List.of() : List.copyOf(tests);
        if (tests.size() > 50) {
            throw new IllegalArgumentException("too many tests");
        }
        for (WorkerTest t : tests) {
            // a test must belong to the kind of exercise: scripted tests only for scripted exercises, and the other way round
            if ((t instanceof ScriptTestSpec) != problem.scripted()) {
                throw new IllegalArgumentException("a test does not match the exercise");
            }
        }
    }

    public ExecutionRequest(Problem problem, Mode mode, byte[] source) {
        this(problem, mode, source, List.of());
    }

    @Override
    public byte[] source() {
        return source.clone();
    }

    int sourceLength() {
        return source.length;
    }

    @Override
    public String toString() {
        return "ExecutionRequest[problem=" + problem + ", mode=" + mode + ", sourceBytes=" + source.length + ", tests=" + tests.size() + "]";
    }
}
