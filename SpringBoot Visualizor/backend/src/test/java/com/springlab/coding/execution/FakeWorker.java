package com.springlab.coding.execution;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A stand-in for the WSL worker client, started as a real operating-system process by the tests so that the backend's process
 * handling (stdin framing, bounded reads, timeouts, kills, exit codes) is exercised for real. The first argument is the behaviour.
 */
public final class FakeWorker {

    private static final String PASS = "{\"verdict\":\"ACCEPTED\",\"passed\":2,\"total\":2,\"wallMillis\":1500,\"peakKb\":80000,\"cpuMillis\":1400,\"compilerOutput\":\"\","
            + "\"tests\":[{\"name\":\"visible one\",\"visible\":true,\"status\":\"PASS\",\"message\":null},{\"name\":\"hidden one\",\"visible\":false,\"status\":\"PASS\",\"message\":null}]}";

    private static int lastTestCount = -1;
    private static final java.util.List<String> lastTestLines = new java.util.ArrayList<>();

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        switch (mode) {
            case "check-ok" -> System.out.println("{\"ok\":true}");
            case "check-bad" -> System.out.println("{\"ok\":false,\"reason\":\"worker file missing: jdk17/bin/java\"}");
            case "check-garbage" -> System.out.println("whatever");
            case "check-exit" -> System.exit(1);
            case "check-hang", "hang" -> Thread.sleep(600_000);
            case "cancel" -> System.out.println("{\"cancelled\":true}");
            case "accepted" -> {
                readSource(System.in);
                System.out.println(PASS);
            }
            case "stdin-open" -> {
                // the backend must keep stdin open after the source: EOF here would mean "the caller is gone"
                readSource(System.in);
                AtomicBoolean eof = new AtomicBoolean();
                Thread t = new Thread(() -> {
                    try {
                        if (System.in.read() < 0) {
                            eof.set(true);
                        }
                    } catch (Exception ignored) {
                        eof.set(true);
                    }
                });
                t.setDaemon(true);
                t.start();
                Thread.sleep(700);
                System.out.println(eof.get() ? "{\"verdict\":\"INTERNAL_ERROR\",\"passed\":0,\"total\":0,\"wallMillis\":1,\"peakKb\":-1,\"cpuMillis\":-1,\"compilerOutput\":\"\",\"tests\":[]}" : PASS);
            }
            case "echo-tests" -> {
                readSource(System.in);
                // reports how many tests arrived and what the first line looked like (never the expected values, which a test checks separately)
                System.out.println(PASS.replace("visible one", "tests " + lastTestCount).replace("hidden one", lastTestLines.isEmpty() ? "none" : lastTestLines.get(0).split(" ")[0] + " " + lastTestLines.get(0).split(" ").length));
            }
            case "echo-length" -> {
                byte[] source = readSource(System.in);
                System.out.println(PASS.replace("visible one", "length " + source.length));
            }
            case "crash" -> {
                readSource(System.in);
                System.out.print("{\"verdict\":\"ACC");
                System.out.flush();
                System.exit(3);
            }
            case "crash-after-valid-answer" -> {
                readSource(System.in);
                System.out.println(PASS);
                System.out.flush();
                System.exit(5);
            }
            case "garbage" -> {
                readSource(System.in);
                System.out.println("this is not json");
            }
            case "empty" -> readSource(System.in);
            case "huge" -> {
                readSource(System.in);
                byte[] chunk = "x".repeat(1024).getBytes(StandardCharsets.US_ASCII);
                for (int i = 0; i < 400; i++) {
                    System.out.write(chunk, 0, chunk.length);
                }
                System.out.flush();
            }
            case "false-accept" -> {
                readSource(System.in);
                System.out.println("{\"verdict\":\"ACCEPTED\",\"passed\":1,\"total\":3,\"wallMillis\":1,\"peakKb\":-1,\"cpuMillis\":-1,\"compilerOutput\":\"\",\"tests\":["
                        + "{\"name\":\"a\",\"visible\":true,\"status\":\"PASS\",\"message\":null},{\"name\":\"b\",\"visible\":false,\"status\":\"FAIL\",\"message\":null},"
                        + "{\"name\":\"c\",\"visible\":false,\"status\":\"FAIL\",\"message\":null}]}");
            }
            case "hidden-leak" -> {
                readSource(System.in);
                System.out.println("{\"verdict\":\"WRONG_ANSWER\",\"passed\":0,\"total\":1,\"wallMillis\":1,\"peakKb\":-1,\"cpuMillis\":-1,\"compilerOutput\":\"\",\"tests\":["
                        + "{\"name\":\"hidden\",\"visible\":false,\"status\":\"FAIL\",\"message\":\"expected the secret value 42\"}]}");
            }
            case "stderr-flood" -> {
                readSource(System.in);
                byte[] chunk = "e".repeat(1024).getBytes(StandardCharsets.US_ASCII);
                for (int i = 0; i < 2000; i++) {
                    System.err.write(chunk, 0, chunk.length);
                }
                System.err.flush();
                System.out.println(PASS);
            }
            default -> System.exit(9);
        }
    }

    /** Reads the "LEN n" frame the backend sends and returns the n bytes. */
    private static byte[] readSource(InputStream in) throws Exception {
        StringBuilder header = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            header.append((char) c);
        }
        int n = Integer.parseInt(header.substring(4));
        byte[] source = in.readNBytes(n);
        // the tests block: "TESTS k" and k lines
        StringBuilder tests = new StringBuilder();
        while ((c = in.read()) >= 0 && c != '\n') {
            tests.append((char) c);
        }
        int k = Integer.parseInt(tests.substring(6));
        lastTestCount = k;
        for (int i = 0; i < k; i++) {
            StringBuilder line = new StringBuilder();
            while ((c = in.read()) >= 0 && c != '\n') {
                line.append((char) c);
            }
            lastTestLines.add(line.toString());
        }
        return source;
    }
}
