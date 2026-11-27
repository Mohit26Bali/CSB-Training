package com.springlab.lab.micro.core;

/** The lab's record of one service call, including facts the caller does not have ({@code handlerInvoked}). Never contains payloads. */
public record CallRecord(int seq, String from, String to, String operation, Outcome outcome, String code, long simulatedMillis,
                         long measuredMicros, boolean handlerInvoked, String spanId, String parentSpanId, String correlationId, String instance,
                         String kind) {

    /** One line for the step evidence, for example {@code client → inventory.reserve: TIMEOUT (RESPONSE_TOO_SLOW), 100 ms simulated}. */
    public String describe() {
        return ("EVENT".equals(kind) ? "[event] " : "") + from + " → " + to + (instance == null ? "" : "#" + instance) + "." + operation + ": " + outcome + (code == null ? "" : " (" + code + ")") + ", " + simulatedMillis
                + " ms simulated" + (handlerInvoked ? ", handler ran" : ", handler did not run");
    }
}
