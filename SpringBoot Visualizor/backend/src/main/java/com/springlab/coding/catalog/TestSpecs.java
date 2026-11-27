package com.springlab.coding.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.coding.dto.CodingDtos;
import com.springlab.coding.execution.ExecutionRequest;
import com.springlab.coding.execution.ScriptTestSpec;
import com.springlab.coding.execution.TestSpec;
import com.springlab.coding.execution.WorkerTest;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns stored test rows into what the worker needs ({@link TestSpec}) and, for VISIBLE tests only, into a sentence a learner can read.
 * Any row that cannot be read exactly is an {@link IllegalStateException}: a broken test must stop a run, never be skipped.
 */
public final class TestSpecs {

    private final ObjectMapper mapper;

    public TestSpecs(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The test as the worker needs it, for the exercise whose harness kind is given. Scripted exercises read a script and a trace. */
    public WorkerTest toWorkerTest(String harnessKind, CodingProblemRepository.TestRow row) {
        ExecutionRequest.Problem problem;
        try {
            problem = ExecutionRequest.Problem.valueOf(harnessKind);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("unknown harness kind", e);
        }
        return problem.scripted() ? toScriptSpec(problem, row) : toSpec(row);
    }

    private ScriptTestSpec toScriptSpec(ExecutionRequest.Problem problem, CodingProblemRepository.TestRow row) {
        try {
            JsonNode in = mapper.readTree(row.inputSpec());
            JsonNode ex = mapper.readTree(row.expectedSpec());
            return ScriptTestSpec.of(problem, row.name(), row.visible(), textArray(in, "script"), textArray(ex, "trace"));
        } catch (Exception e) {
            // never include the row content in the message
            throw new IllegalStateException("test " + row.id() + " is not readable", e);
        }
    }

    private static List<String> textArray(JsonNode n, String field) {
        JsonNode a = n.get(field);
        if (a == null || !a.isArray()) {
            throw new IllegalStateException("bad field " + field);
        }
        List<String> out = new ArrayList<>();
        for (JsonNode v : a) {
            if (!v.isTextual()) {
                throw new IllegalStateException("bad field " + field);
            }
            out.add(v.asText());
        }
        return out;
    }

    /** What a learner may read about a VISIBLE test of any exercise. */
    public CodingDtos.SampleTest describeVisible(String harnessKind, CodingProblemRepository.TestRow row) {
        WorkerTest t = toWorkerTest(harnessKind, row);
        if (!row.visible()) {
            throw new IllegalArgumentException("a hidden test is never described");
        }
        if (t instanceof ScriptTestSpec s) {
            StringBuilder in = new StringBuilder();
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < s.script().size(); i++) {
                if (i > 0) {
                    in.append('\n');
                    out.append('\n');
                }
                in.append(i + 1).append(". ").append(s.script().get(i));
                out.append(i + 1).append(". ").append(s.trace().get(i));
            }
            return new CodingDtos.SampleTest(row.name(), in.toString(), out.toString());
        }
        return describeVisible(row);
    }

    public TestSpec toSpec(CodingProblemRepository.TestRow row) {
        try {
            JsonNode in = mapper.readTree(row.inputSpec());
            JsonNode ex = mapper.readTree(row.expectedSpec());
            List<String> steps = new ArrayList<>();
            for (JsonNode s : in.get("steps")) {
                steps.add(s.asText());
            }
            List<Long> sleeps = new ArrayList<>();
            for (JsonNode s : ex.get("sleeps")) {
                if (!s.isIntegralNumber()) {
                    throw new IllegalStateException("bad sleeps");
                }
                sleeps.add(s.asLong());
            }
            return new TestSpec(row.name(), row.visible(), requireInt(in, "maxAttempts"), requireLong(in, "baseDelayMillis"), requireLong(in, "maxDelayMillis"), steps,
                    requireText(ex, "kind"), requireText(ex, "exceptionClass"), requireInt(ex, "identity"), requireInt(ex, "calls"), sleeps, requireText(ex, "value"));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            // never include the row content in the message
            throw new IllegalStateException("test " + row.id() + " is not readable", e);
        }
    }

    /** Only for a visible test. Hidden tests have no description and asking for one is a programming error. */
    public CodingDtos.SampleTest describeVisible(CodingProblemRepository.TestRow row) {
        if (!row.visible()) {
            throw new IllegalArgumentException("a hidden test is never described");
        }
        TestSpec t = toSpec(row);
        StringBuilder in = new StringBuilder("maxAttempts = " + t.maxAttempts() + ", baseDelayMillis = " + t.baseDelayMillis() + ", maxDelayMillis = " + t.maxDelayMillis()
                + "; the operation: ");
        List<String> parts = new ArrayList<>();
        for (String s : t.steps()) {
            String text = s.substring(2);
            parts.add(switch (s.charAt(0)) {
                case 'R' -> "returns \"" + text + "\"";
                case 'T' -> "throws TransientException(\"" + text + "\")";
                default -> "throws IllegalStateException(\"" + text + "\")";
            });
        }
        in.append(parts.isEmpty() ? "is never scripted" : String.join(", then ", parts));
        StringBuilder out = new StringBuilder();
        if (t.expectedKind().equals("RETURN")) {
            out.append("returns \"").append(t.expectedValue()).append('"');
        } else if (t.expectedExceptionClass().equals("TransientException")) {
            out.append("rethrows the last TransientException (the same object, from call ").append(t.expectedCalls()).append(')');
        } else {
            out.append("throws ").append(t.expectedExceptionClass().replace("java.lang.", ""));
        }
        out.append("; the operation ran ").append(t.expectedCalls()).append(" time(s); sleeper called with ").append(t.expectedSleeps());
        return new CodingDtos.SampleTest(row.name(), in.toString(), out.toString());
    }

    private static int requireInt(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || !v.isInt()) {
            throw new IllegalStateException("bad field " + f);
        }
        return v.asInt();
    }

    private static long requireLong(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || !v.isIntegralNumber() || !v.canConvertToLong()) {
            throw new IllegalStateException("bad field " + f);
        }
        return v.asLong();
    }

    private static String requireText(JsonNode n, String f) {
        JsonNode v = n.get(f);
        if (v == null || !v.isTextual()) {
            throw new IllegalStateException("bad field " + f);
        }
        return v.asText();
    }
}
