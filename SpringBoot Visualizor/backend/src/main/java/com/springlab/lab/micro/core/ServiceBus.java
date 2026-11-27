package com.springlab.lab.micro.core;

import com.springlab.lab.micro.core.ServiceBehaviours.Applied;
import com.springlab.lab.micro.core.ServiceBehaviours.Mode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

/**
 * The in-process "network" of one run. Every cross-service interaction goes through {@link #call}, which is the single choke point
 * that applies the injected behaviour, the bulkhead and the timeout, runs the target's handler, and records a span and a call record.
 *
 * <p>What is real and what is simulated, exactly:
 * <ul>
 *   <li>REAL: the handler runs as ordinary Java code, with its own local database transaction; a handler exception is caught and
 *       reported as TECHNICAL_ERROR; permits are real {@link java.util.concurrent.Semaphore} permits.</li>
 *   <li>SIMULATED: latency and timeouts (a logical clock, no sleeping), unavailability and lost requests (decided by the
 *       injected {@link ServiceBehaviours}), the network itself (a method call: no sockets, no serialization).</li>
 * </ul>
 * Not thread-safe on purpose: a bus belongs to one run, which executes on one thread.
 */
public final class ServiceBus {

    /** Upper bound on calls per run, so no experiment (or bug) can run away. */
    public static final int MAX_CALLS = 200;

    /** Deterministic base latency of each service, in simulated milliseconds. */
    private static final Map<ServiceName, Integer> BASE_LATENCY = new EnumMap<>(ServiceName.class);

    static {
        BASE_LATENCY.put(ServiceName.GATEWAY, 3);
        BASE_LATENCY.put(ServiceName.REGISTRY, 4);
        BASE_LATENCY.put(ServiceName.NOTIFICATION, 8);
        BASE_LATENCY.put(ServiceName.USER, 12);
        BASE_LATENCY.put(ServiceName.PRODUCT, 15);
        BASE_LATENCY.put(ServiceName.INVENTORY, 18);
        BASE_LATENCY.put(ServiceName.ORDER, 20);
        BASE_LATENCY.put(ServiceName.PAYMENT, 35);
    }

    private static final class Frame {
        final String spanId;
        final String correlationId;
        long childMillis;

        Frame(String spanId, String correlationId) {
            this.spanId = spanId;
            this.correlationId = correlationId;
        }
    }

    private final HandlerRegistry handlers;
    private final SimClock clock;
    private final ServiceBehaviours behaviours;
    private final Bulkheads bulkheads;
    private final TraceBook traces;
    private final boolean propagateCorrelation;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private int spanSeq;
    private int correlationSeq;
    private int uncorrelatedSeq;
    private String requestCorrelation = "corr-0";

    public ServiceBus(HandlerRegistry handlers, SimClock clock, ServiceBehaviours behaviours, Bulkheads bulkheads, TraceBook traces,
                      boolean propagateCorrelation) {
        this.handlers = handlers;
        this.clock = clock;
        this.behaviours = behaviours;
        this.bulkheads = bulkheads;
        this.traces = traces;
        this.propagateCorrelation = propagateCorrelation;
    }

    public static int baseLatency(ServiceName service) {
        return BASE_LATENCY.get(service);
    }

    public SimClock clock() {
        return clock;
    }

    public TraceBook traces() {
        return traces;
    }

    public Bulkheads bulkheads() {
        return bulkheads;
    }

    /** Begins a new external request: the calls that follow share one correlation id (as an edge or gateway would assign it). */
    public String startRequest() {
        requestCorrelation = "corr-" + (++correlationSeq);
        return requestCorrelation;
    }

    public String currentCorrelation() {
        return requestCorrelation;
    }

    /** Continues an existing trace: the calls that follow share {@code correlationId}. Used to carry a correlation id inside an event. */
    public void useCorrelation(String correlationId) {
        this.requestCorrelation = correlationId;
    }

    /**
     * Waiting (for example a retry backoff) on the SIMULATED clock. The time is counted as time spent by the enclosing span, so a
     * service that retries is correctly shown as slower. Nothing sleeps.
     */
    public void pause(long millis) {
        clock.advanceTo(clock.now() + millis);
        Frame enclosing = frames.peek();
        if (enclosing != null) {
            enclosing.childMillis += millis;
        }
    }

    /**
     * Records a call that was refused LOCALLY (for example by an open circuit breaker) so it still appears in the trace. The target
     * service is never reached and takes no time.
     */
    public <Q, R> CallResult<R> refuse(ServiceName from, Operation<Q, R> operation, Outcome outcome, String code) {
        Frame parent = frames.peek();
        String correlation = parent != null && propagateCorrelation ? parent.correlationId : (parent == null ? requestCorrelation : "uncorrelated-" + (++uncorrelatedSeq));
        boolean propagated = parent == null || propagateCorrelation;
        String spanId = "span-" + (++spanSeq);
        String parentSpan = parent == null || !propagated ? null : parent.spanId;
        ServiceName target = operation.service();
        traces.add(new Span(spanId, parentSpan, correlation, target.id(), operation.name(), outcome.name(), clock.now(), 0, 0, propagated, null, "SYNC"),
                new CallRecord(traces.callCount() + 1, ServiceName.nodeOf(from), target.id(), operation.name(), outcome, code, 0, 0, false, spanId,
                        parentSpan, correlation, null, "SYNC"));
        return new CallResult<>(outcome, null, code, 0, false, spanId);
    }

    /** Models a request that is still being processed by {@code service} and therefore holds one of its permits. */
    public Bulkheads.Permit holdPermit(ServiceName service) {
        Bulkheads.Permit permit = bulkheads.tryAcquire(service);
        if (permit == null) {
            throw new IllegalStateException("No permit left to hold for " + service.id());
        }
        return permit;
    }

    public <Q, R> CallResult<R> call(ServiceName from, Operation<Q, R> operation, Q request) {
        return call(from, operation, request, CallOptions.defaults());
    }

    public <Q, R> CallResult<R> call(ServiceName from, Operation<Q, R> operation, Q request, CallOptions options) {
        return invoke(ServiceName.nodeOf(from), "SYNC", operation, request, options);
    }

    /**
     * Delivers an EVENT to a consumer on behalf of the (simulated) message channel. It uses the same machinery as a synchronous call
     * (behaviours, bulkhead, spans) but is recorded as an event: the sender did not wait for it.
     */
    public <Q, R> CallResult<R> deliver(Operation<Q, R> operation, Q event, CallOptions options) {
        return invoke("broker", "EVENT", operation, event, options);
    }

    private <Q, R> CallResult<R> invoke(String fromId, String kind, Operation<Q, R> operation, Q request, CallOptions options) {
        if (traces.callCount() >= MAX_CALLS) {
            throw new IllegalStateException("A run may make at most " + MAX_CALLS + " service calls");
        }
        ServiceName target = operation.service();
        ServiceHandler<Q, R> handler = handlers.find(operation);

        Frame parent = frames.peek();
        String correlation;
        boolean propagated = true;
        if (parent == null) {
            correlation = requestCorrelation;
        } else if (propagateCorrelation) {
            correlation = parent.correlationId;
        } else {
            correlation = "uncorrelated-" + (++uncorrelatedSeq);
            propagated = false;
        }
        Frame frame = new Frame("span-" + (++spanSeq), correlation);
        long start = clock.now();
        long startedNanos = System.nanoTime();

        Outcome outcome;
        String code = null;
        R value = null;
        boolean invoked = false;
        long own = BASE_LATENCY.get(target);

        Bulkheads.Permit permit = bulkheads.tryAcquire(target);
        if (permit == null) {
            outcome = Outcome.BULKHEAD_REJECTED;
            code = "BULKHEAD_FULL";
            own = 0;
        } else {
            try {
                Applied applied = behaviours.next(target, options.instance());
                if (applied.mode() == Mode.DOWN) {
                    outcome = Outcome.UNAVAILABLE;
                    code = "CONNECTION_REFUSED";
                    own = 1;
                } else if (applied.mode() == Mode.REQUEST_LOST) {
                    outcome = Outcome.TIMEOUT;
                    code = "REQUEST_LOST";
                    own = options.timeoutMillis();
                } else {
                    if (applied.mode() == Mode.DELAYED) {
                        own += applied.delayMillis();
                    }
                    outcome = Outcome.SUCCESS;
                    frames.push(frame);
                    try {
                        if (applied.mode() == Mode.ERROR) {
                            throw new SimulatedFault("injected fault");
                        }
                        invoked = true;
                        ServiceResult<R> result = handler.handle(new CallContext(target, correlation, frame.spanId, this), request);
                        if (result.ok()) {
                            value = result.value();
                        } else {
                            outcome = Outcome.BUSINESS_FAILURE;
                            code = result.code();
                        }
                    } catch (RuntimeException e) {
                        outcome = Outcome.TECHNICAL_ERROR;
                        code = e.getClass().getSimpleName(); // the class only: a message could contain data
                        value = null;
                    } finally {
                        frames.pop();
                    }
                    if (outcome != Outcome.TECHNICAL_ERROR || invoked) {
                        long total = own + frame.childMillis;
                        if (total > options.timeoutMillis()) {
                            outcome = Outcome.TIMEOUT;
                            code = "RESPONSE_TOO_SLOW";
                            value = null;
                            own = options.timeoutMillis() - frame.childMillis;
                        }
                    }
                }
            } finally {
                permit.close();
            }
        }

        long duration = Math.min(own + frame.childMillis, outcome == Outcome.TIMEOUT ? options.timeoutMillis() : Long.MAX_VALUE);
        clock.advanceTo(start + duration);
        Frame enclosing = frames.peek();
        if (enclosing != null) {
            enclosing.childMillis += duration;
        }
        long measured = (System.nanoTime() - startedNanos) / 1_000;
        String parentSpan = enclosing == null ? null : enclosing.spanId;
        traces.add(new Span(frame.spanId, propagated ? parentSpan : null, correlation, target.id(), operation.name(), outcome.name(), start,
                        duration, measured, propagated, options.instance(), kind),
                new CallRecord(traces.callCount() + 1, fromId, target.id(), operation.name(), outcome, code, duration, measured, invoked,
                        frame.spanId, propagated ? parentSpan : null, correlation, options.instance(), kind));
        return new CallResult<>(outcome, value, code, duration, invoked, frame.spanId);
    }
}
