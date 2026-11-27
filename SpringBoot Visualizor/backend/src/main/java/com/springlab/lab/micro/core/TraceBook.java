package com.springlab.lab.micro.core;

import java.util.ArrayList;
import java.util.List;

/** The spans and call records of ONE run, in the order the calls finished. Never shared between runs or users. */
public final class TraceBook {

    private final List<Span> spans = new ArrayList<>();
    private final List<CallRecord> calls = new ArrayList<>();

    synchronized void add(Span span, CallRecord call) {
        spans.add(span);
        calls.add(call);
    }

    public synchronized int callCount() {
        return calls.size();
    }

    public synchronized List<CallRecord> callsSince(int from) {
        return List.copyOf(calls.subList(from, calls.size()));
    }

    public synchronized List<Span> spans() {
        return List.copyOf(spans);
    }

    public synchronized List<CallRecord> calls() {
        return List.copyOf(calls);
    }
}
