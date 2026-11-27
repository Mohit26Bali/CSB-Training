package com.springlab.coding.execution;

import java.util.List;

/**
 * The learner-safe result of one judged run. It has no field that could carry hidden test inputs or expected values, and no field
 * for the learner program's own output: a message is present only for a VISIBLE test.
 *
 * @param compilerOutput compiler diagnostics (file name and line only, never a host path); empty if the code compiled
 * @param peakKb         indicative peak memory of the sandboxed JVM, or -1 when it could not be measured
 * @param cpuMillis      indicative CPU time of the sandboxed JVM, or -1
 */
public record ExecutionResult(Verdict verdict, List<TestOutcome> tests, int passed, int total, String compilerOutput,
                              long wallMillis, long peakKb, long cpuMillis) {

    /** status is one of PASS, FAIL, NOT_RUN, ERROR, TIMEOUT. */
    public record TestOutcome(String name, boolean visible, String status, String message) {
    }

    public ExecutionResult {
        tests = tests == null ? List.of() : List.copyOf(tests);
        compilerOutput = compilerOutput == null ? "" : compilerOutput;
    }

    /** A result that carries only a verdict (nothing ran, or nothing can be trusted). */
    public static ExecutionResult of(Verdict verdict) {
        return new ExecutionResult(verdict, List.of(), 0, 0, "", 0, -1, -1);
    }

    public static ExecutionResult of(Verdict verdict, long wallMillis) {
        return new ExecutionResult(verdict, List.of(), 0, 0, "", wallMillis, -1, -1);
    }
}
