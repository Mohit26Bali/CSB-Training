package com.springlab.lab.micro.core;

/**
 * One unit of work in the in-process trace of a run: a service call. This is a SIMULATION of distributed tracing concepts
 * (parent/child spans, correlation ids), not OpenTelemetry, and nothing is propagated over a network. {@code startMillis} and
 * {@code simulatedMillis} are SIMULATED time; {@code measuredMicros} is the real elapsed time of the in-process call (MEASURED).
 * {@code correlationPropagated} is false when the caller "forgot" to pass the correlation id on, which breaks the chain.
 */
public record Span(String spanId, String parentSpanId, String correlationId, String service, String operation, String status,
                   long startMillis, long simulatedMillis, long measuredMicros, boolean correlationPropagated, String instance, String kind) {
}
