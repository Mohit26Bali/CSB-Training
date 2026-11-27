import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

/**
 * The part of the judge that runs INSIDE the sandbox, next to the untrusted learner code. It compiles the one learner source file
 * (annotation processing off), loads it, and then does what the trusted driver asks over a line protocol on stdin/stdout:
 * "run this scripted scenario and tell me what you observed".
 *
 * <p>It deliberately knows nothing about expected results. The hidden tests (inputs AND expected observations) live in the driver,
 * outside the sandbox. Everything this class sends back is an observation made by code that shares a JVM with the learner, so it is
 * evidence about the learner's behaviour, not a trusted verdict: the driver compares it with its own expectation.
 *
 * <p>What the learner could still do from here: read the scenario inputs (they are sent to this process), and forge observation
 * frames if it extracts the per-run nonce from this class by reflection. Forging helps only a learner who already knows the expected
 * observation, which is the answer to the exercise. See the F2a report.
 */
public final class Launcher {

    private static PrintStream proto;
    private static String nonce;

    private static String b64(String s) {
        return s.isEmpty() ? "-" : Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static synchronized void send(String line) {
        proto.println("@@" + nonce + " " + line);
        proto.flush();
    }

    public static void main(String[] args) throws Exception {
        proto = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        // Whatever the learner prints goes to stderr, which the driver treats as untrusted output. The learner can still write to
        // file descriptor 1 directly; such lines have no valid nonce and are counted as output, never as results.
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
        InputStream rawIn = System.in;
        System.setIn(InputStream.nullInputStream());
        BufferedReader in = new BufferedReader(new InputStreamReader(rawIn, StandardCharsets.UTF_8));

        String first = in.readLine();
        if (first == null || !first.startsWith("NONCE ")) {
            return;
        }
        nonce = first.substring(6).trim();

        String kind = args.length > 0 ? args[0] : "RETRY_EXECUTOR";
        Adapter adapter = Adapters.of(kind);
        if (adapter == null && !kind.equals("RETRY_EXECUTOR")) {
            return; // an unknown harness: the driver treats the early end as an internal error
        }
        Class<?> learner = compileAndLoad(adapter == null ? "RetryExecutor" : adapter.className());
        if (learner == null) {
            return;
        }
        Method execute = null;
        if (adapter == null) {
            try {
                execute = learner.getMethod("execute", Callable.class, int.class, long.class, long.class, Sleeper.class);
                if (!Modifier.isStatic(execute.getModifiers())) {
                    throw new NoSuchMethodException("not static");
                }
            } catch (NoSuchMethodException e) {
                send("CE " + b64("RetryExecutor must declare: public static <T> T execute(Callable<T> action, int maxAttempts, "
                        + "long baseDelayMillis, long maxDelayMillis, Sleeper sleeper) throws Exception"));
                return;
            }
        } else {
            String missing = adapter.check(learner);
            if (missing != null) {
                send("CE " + b64(missing));
                return;
            }
        }
        send("READY");

        String line;
        while ((line = in.readLine()) != null) {
            if (line.equals("END")) {
                send("STAT " + vmHighWaterKb() + " " + cpuMillis());
                return;
            }
            if (line.startsWith("RUN ")) {
                if (adapter == null) {
                    runScenario(execute, line.substring(4).trim().split(" "));
                } else {
                    runScript(adapter, learner, line.substring(4).trim().split(" "));
                }
            }
        }
    }

    /** A scripted session: "RUN id b64(script)". The answer is one observation line per operation. */
    private static void runScript(Adapter adapter, Class<?> learner, String[] f) {
        int id = Integer.parseInt(f[0]);
        List<String> script = List.of(new String(Base64.getUrlDecoder().decode(f[1]), StandardCharsets.UTF_8).split("\n"));
        List<String> trace;
        try {
            trace = adapter.run(learner, script);
        } catch (Throwable t) {
            trace = List.of("EX:" + Adapter.name(t));
        }
        send("OBS " + id + " " + b64(String.join("\n", trace)));
    }

    private static Class<?> compileAndLoad(String className) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path out = Files.createDirectories(Path.of("/tmp/classes"));
        boolean ok;
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            ok = compiler.getTask(null, files, diagnostics,
                    // -proc:none: no annotation processor can run code at compile time. A fake sourcepath stops javac from
                    // pulling in other sources, and -implicit:none writes classes only for the file we gave it.
                    List.of("-proc:none", "-Xlint:none", "-implicit:none", "-encoding", "UTF-8", "-sourcepath", "/nonexistent",
                            "-classpath", "/judge/rt", "-d", out.toString()),
                    null, files.getJavaFileObjects(Path.of("/src/" + className + ".java"))).call();
        }
        if (!ok) {
            StringBuilder text = new StringBuilder();
            int shown = 0;
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) {
                    continue;
                }
                if (shown++ >= 10) {
                    text.append("... more errors not shown\n");
                    break;
                }
                text.append(className).append(".java:").append(d.getLineNumber()).append(": error: ").append(d.getMessage(Locale.ENGLISH)).append('\n');
            }
            send("CE " + b64(text.toString()));
            return null;
        }
        try (Stream<Path> produced = Files.list(out)) {
            for (Path p : (Iterable<Path>) produced::iterator) {
                String name = p.getFileName().toString();
                if (!name.matches(className + "(\\$[A-Za-z0-9_$]+)?\\.class")) {
                    send("CE " + b64("Only the class " + className + " (and its nested classes) may be declared; found " + name));
                    return null;
                }
            }
        }
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, Launcher.class.getClassLoader());
        return loader.loadClass(className);
    }

    private static void runScenario(Method execute, String[] f) {
        int id = Integer.parseInt(f[0]);
        int maxAttempts = Integer.parseInt(f[1]);
        long base = Long.parseLong(f[2]);
        long cap = Long.parseLong(f[3]);
        String[] steps = f[4].equals("-") ? new String[0] : f[4].split(",");

        List<Throwable> made = new ArrayList<>();
        List<Long> sleeps = new ArrayList<>();
        int[] calls = {0};
        Callable<Object> action = () -> {
            int i = calls[0]++;
            if (i >= steps.length) {
                IllegalStateException unscripted = new IllegalStateException("unscripted call");
                made.add(unscripted);
                throw unscripted;
            }
            String text = steps[i].substring(2);
            switch (steps[i].charAt(0)) {
                case 'R':
                    return text;
                case 'T': {
                    TransientException t = new TransientException(text);
                    made.add(t);
                    throw t;
                }
                default: {
                    IllegalStateException o = new IllegalStateException(text);
                    made.add(o);
                    throw o;
                }
            }
        };
        Sleeper sleeper = millis -> {
            if (sleeps.size() >= 500) {
                throw new IllegalStateException("too many sleeps");
            }
            sleeps.add(millis);
        };

        String kind;
        String value = "";
        String exClass = "";
        int ident = -1;
        try {
            Object result = execute.invoke(null, action, maxAttempts, base, cap, sleeper);
            kind = "RETURN";
            value = String.valueOf(result);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            kind = "THROW";
            exClass = t.getClass().getName();
            for (int i = 0; i < made.size(); i++) {
                if (made.get(i) == t) {
                    ident = i;
                }
            }
        } catch (Throwable t) {
            kind = "THROW";
            exClass = t.getClass().getName();
        }
        StringBuilder csv = new StringBuilder();
        for (Long s : sleeps) {
            csv.append(csv.length() == 0 ? "" : ",").append(s);
        }
        send("OBS " + id + " " + kind + " " + b64(exClass) + " " + ident + " " + calls[0] + " " + (csv.length() == 0 ? "-" : csv) + " " + b64(value));
    }

    private static long vmHighWaterKb() {
        try {
            for (String l : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (l.startsWith("VmHWM:")) {
                    return Long.parseLong(l.replaceAll("[^0-9]", ""));
                }
            }
        } catch (Exception ignored) {
            // indicative only
        }
        return -1;
    }

    private static long cpuMillis() {
        return ProcessHandle.current().info().totalCpuDuration().map(java.time.Duration::toMillis).orElse(-1L);
    }
}
