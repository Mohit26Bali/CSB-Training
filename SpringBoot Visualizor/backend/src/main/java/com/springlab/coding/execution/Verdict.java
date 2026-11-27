package com.springlab.coding.execution;

/**
 * The outcome of judging one submission. Only {@link #ACCEPTED} means the learner's code passed every test it was run against.
 * Everything that is not a clear result of the learner's code (a crashed worker, a malformed answer, a lost sandbox) is
 * {@link #INTERNAL_ERROR}: the system never turns a doubt into an acceptance.
 */
public enum Verdict {
    ACCEPTED,
    WRONG_ANSWER,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT,
    MEMORY_LIMIT,
    OUTPUT_LIMIT,
    /** Refused before any code ran (empty, too large, not UTF-8). */
    INVALID_SUBMISSION,
    /** Our failure, not the learner's. */
    INTERNAL_ERROR,
    /** Cancelled by the user or by a shutdown; whatever the sandbox did afterwards is discarded. */
    CANCELLED
}
