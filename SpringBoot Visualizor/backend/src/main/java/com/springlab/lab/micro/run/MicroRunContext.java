package com.springlab.lab.micro.run;

/** Binds the run being executed to the current thread, so service repositories can reach THEIR run's database access without parameters. */
public final class MicroRunContext {

    private static final ThreadLocal<RunContext> CURRENT = new ThreadLocal<>();

    private MicroRunContext() {
    }

    static void bind(RunContext context) {
        CURRENT.set(context);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static RunContext require() {
        RunContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("No microservices lab run is bound to this thread");
        }
        return context;
    }
}
