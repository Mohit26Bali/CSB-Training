import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Real threads, deterministic outcomes: an operation started with "start" holds its permit until the script says "finish", so the script
 * decides exactly how many calls are in flight. Every wait has a short limit, so a learner that deadlocks is reported, not waited for.
 *
 * Operations: new N, call ok|fail (one synchronous call), start ID ok|fail, finish ID, inflight, permits.
 * Observations: OK / FAILED (the operation threw) / FULL (rejected with BulkheadFullException) / RUNNING (a started call is inside its
 * operation) / TIMEOUT / EX:name; numbers for inflight and permits.
 */
final class BulkheadAdapter extends Adapter {

    private static final long WAIT_MILLIS = 800;

    /** One started call, running on its own thread. */
    private static final class Held {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        volatile String outcome;
        volatile boolean fail;
    }

    private Class<?> type;
    private Object bulkhead;
    private final Map<String, Held> held = new HashMap<>();

    @Override
    String className() {
        return "Bulkhead";
    }

    @Override
    String check(Class<?> learner) {
        try {
            learner.getConstructor(int.class);
            learner.getMethod("execute", Callable.class);
            learner.getMethod("inFlight");
            learner.getMethod("availablePermits");
            return null;
        } catch (NoSuchMethodException e) {
            return "Bulkhead must declare: public Bulkhead(int maxConcurrent), public <T> T execute(Callable<T> operation) throws Exception, "
                    + "public int inFlight() and public int availablePermits()";
        }
    }

    @Override
    void begin(Class<?> learner) {
        type = learner;
        bulkhead = null;
        held.clear();
    }

    @Override
    void end() {
        for (Held h : held.values()) {
            h.release.countDown();
        }
    }

    private Object instance() {
        if (bulkhead == null) {
            throw new IllegalStateException("NoInstance");
        }
        return bulkhead;
    }

    private static String outcomeOf(Throwable thrown, Throwable ours) {
        if (thrown == null) {
            return "OK";
        }
        if (thrown == ours) {
            return "FAILED";
        }
        if (thrown instanceof BulkheadFullException) {
            return "FULL";
        }
        return "EX:" + name(thrown);
    }

    private Object execute(Callable<Object> op) throws Throwable {
        try {
            return type.getMethod("execute", Callable.class).invoke(instance(), op);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Override
    String step(String[] t) throws Exception {
        switch (t[0]) {
            case "new":
                bulkhead = null;
                bulkhead = type.getConstructor(int.class).newInstance(Integer.parseInt(need(t, 2)));
                return "-";
            case "inflight":
                return String.valueOf(type.getMethod("inFlight").invoke(instance()));
            case "permits":
                return String.valueOf(type.getMethod("availablePermits").invoke(instance()));
            case "call": {
                // on its own thread with a time limit, so a bulkhead that blocks is reported as TIMEOUT instead of hanging the test
                boolean fail = need(t, 2).equals("fail");
                IllegalStateException boom = new IllegalStateException("boom");
                String[] result = new String[1];
                Thread caller = new Thread(() -> {
                    try {
                        execute(() -> {
                            if (fail) {
                                throw boom;
                            }
                            return "done";
                        });
                        result[0] = "OK";
                    } catch (Throwable e) {
                        result[0] = outcomeOf(e, boom);
                    }
                }, "bulkhead-call");
                caller.setDaemon(true);
                caller.start();
                caller.join(WAIT_MILLIS);
                return result[0] == null ? "TIMEOUT" : result[0];
            }
            case "start": {
                String id = need(t, 2);
                Held h = new Held();
                h.fail = need(t, 3).equals("fail");
                held.put(id, h);
                IllegalStateException boom = new IllegalStateException("boom");
                Thread thread = new Thread(() -> {
                    try {
                        execute(() -> {
                            h.entered.countDown();
                            h.release.await();
                            if (h.fail) {
                                throw boom;
                            }
                            return "done";
                        });
                        h.outcome = "OK";
                    } catch (Throwable e) {
                        h.outcome = outcomeOf(e, boom);
                    } finally {
                        h.done.countDown();
                    }
                }, "bulkhead-" + id);
                thread.setDaemon(true);
                thread.start();
                // wait until the call is either inside its operation or already over (rejected)
                long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MILLIS);
                while (System.nanoTime() < end) {
                    if (h.entered.getCount() == 0) {
                        return "RUNNING";
                    }
                    if (h.done.getCount() == 0) {
                        return h.outcome;
                    }
                    Thread.sleep(2);
                }
                return "TIMEOUT";
            }
            case "finish": {
                Held h = held.get(need(t, 2));
                if (h == null) {
                    return "EX:NoSuchCall";
                }
                h.release.countDown();
                return h.done.await(WAIT_MILLIS, TimeUnit.MILLISECONDS) ? h.outcome : "TIMEOUT";
            }
            default:
                throw new BadScript();
        }
    }
}
