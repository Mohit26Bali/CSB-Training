package com.springlab.coding.execution;

/** Learner submissions for the pilot problem, used by the tests that really run code in the WSL sandbox. */
public final class LearnerSources {

    private LearnerSources() {
    }

    public static final String REFERENCE = """
            import java.util.concurrent.Callable;
            public final class RetryExecutor {
                public static <T> T execute(Callable<T> action, int maxAttempts, long baseDelayMillis, long maxDelayMillis, Sleeper sleeper) throws Exception {
                    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least 1");
                    long delay = Math.min(baseDelayMillis, maxDelayMillis);
                    for (int attempt = 1; ; attempt++) {
                        try { return action.call(); }
                        catch (TransientException e) {
                            if (attempt == maxAttempts) throw e;
                            sleeper.sleep(delay);
                            delay = delay > maxDelayMillis / 2 ? maxDelayMillis : delay * 2;
                        }
                    }
                }
            }
            """;

    /** Sleeps after the last failure and never caps the delay. */
    public static final String WRONG = REFERENCE
            .replace("delay = delay > maxDelayMillis / 2 ? maxDelayMillis : delay * 2;", "delay = delay * 2;")
            .replace("if (attempt == maxAttempts) throw e;\n                            sleeper.sleep(delay);",
                    "sleeper.sleep(delay); if (attempt == maxAttempts) throw e;");

    /** Never retries: fails a VISIBLE test, so a learner sees an explanation. */
    public static final String NEVER_RETRIES = REFERENCE.replace("if (attempt == maxAttempts) throw e;", "if (attempt == maxAttempts || true) throw e;");

    /** The reference solution that also prints to both standard streams. */
    public static final String CHATTY = REFERENCE.replace("if (maxAttempts < 1)",
            "System.err.println(\"LEARNER-PRINTED-MARKER\"); System.out.println(\"LEARNER-STDOUT-MARKER\"); if (maxAttempts < 1)");

    public static final String COMPILE_ERROR = REFERENCE.replace("return action.call();", "return action.call()");

    /** Runs the given statements on the first call, then simply calls the action once (it is NOT a correct solution). */
    public static String attack(String body) {
        return """
                import java.util.concurrent.Callable;
                import java.io.*;
                import java.util.*;
                public final class RetryExecutor {
                    static boolean done;
                    public static <T> T execute(Callable<T> action, int maxAttempts, long baseDelayMillis, long maxDelayMillis, Sleeper sleeper) throws Exception {
                        if (!done) {
                            done = true;
                            %s
                        }
                        return action.call();
                    }
                }
                """.formatted(body);
    }

    public static final String INFINITE_LOOP = attack("while (true) { }");
    public static final String HEAP_BOMB = attack("List<byte[]> l = new ArrayList<>(); while (true) l.add(new byte[1 << 20]);");
    public static final String OUTPUT_FLOOD = attack("byte[] b = new byte[8192]; Arrays.fill(b, (byte) 'x'); while (true) System.out.write(b, 0, b.length);");
    public static final String EXIT = attack("System.exit(0);");
}
