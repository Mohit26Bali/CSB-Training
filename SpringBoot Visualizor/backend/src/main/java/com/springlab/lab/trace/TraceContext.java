package com.springlab.lab.trace;

/** Holds the collector of the traced request running on the current thread; null for every untraced request. */
public final class TraceContext {

    private static final ThreadLocal<TraceCollector> CURRENT = new ThreadLocal<>();

    private TraceContext() {
    }

    public static TraceCollector current() {
        return CURRENT.get();
    }

    public static void bind(TraceCollector collector) {
        CURRENT.set(collector);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
