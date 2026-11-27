package com.springlab.lab.micro.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Calls through the service bus with the usual protections: a circuit breaker, bounded retries with backoff, and an optional
 * fallback. This is SpringLab's own small implementation, deterministic and driven by the simulated clock; it is not Resilience4j.
 *
 * <p>What is retried: UNAVAILABLE, TECHNICAL_ERROR and BULKHEAD_REJECTED (the work was not done or could not be known to be harmful),
 * and TIMEOUT only when the caller says the operation is idempotent (the work may have been done). A BUSINESS_FAILURE is never
 * retried: the answer would be the same. A call refused by an OPEN breaker is not retried either.
 */
public final class ResilientCaller {

    /** One attempt. {@code backoffBeforeMillis} is the simulated wait before it (0 for the first). */
    public record Attempt(int number, Outcome outcome, String code, int backoffBeforeMillis) {
        public String describe() {
            return "attempt " + number + (backoffBeforeMillis > 0 ? " after waiting " + backoffBeforeMillis + " ms" : "") + ": " + outcome
                    + (code == null ? "" : " (" + code + ")");
        }
    }

    public record Result<R>(CallResult<R> last, List<Attempt> attempts, boolean fallbackUsed, R fallbackValue) {
        public boolean succeeded() {
            return last.isSuccess();
        }
    }

    private ResilientCaller() {
    }

    public static <Q, R> Result<R> call(ServiceBus bus, ServiceName from, Operation<Q, R> operation, Q request, CallOptions options, RetryPolicy policy,
                                        CircuitBreaker breaker, Supplier<R> fallback, boolean idempotent, Predicate<CallResult<R>> alsoRetry) {
        List<Attempt> attempts = new ArrayList<>();
        CallResult<R> last = null;
        for (int number = 1; number <= policy.maxAttempts(); number++) {
            int backoff = 0;
            if (number > 1) {
                backoff = policy.backoffBefore(number - 1);
                bus.pause(backoff);
            }
            if (breaker != null && !breaker.allowRequest()) {
                last = bus.refuse(from, operation, Outcome.CIRCUIT_OPEN, "CIRCUIT_OPEN");
                attempts.add(new Attempt(number, last.outcome(), last.code(), backoff));
                break; // an open breaker is not retried
            }
            last = bus.call(from, operation, request, options);
            attempts.add(new Attempt(number, last.outcome(), last.code(), backoff));
            if (breaker != null) {
                if (counts(last.outcome())) {
                    breaker.recordFailure();
                } else {
                    breaker.recordSuccess();
                }
            }
            if (!retriable(last, idempotent, alsoRetry)) {
                break;
            }
        }
        boolean useFallback = !last.isSuccess() && fallback != null;
        return new Result<>(last, attempts, useFallback, useFallback ? fallback.get() : null);
    }

    /** Failures that say something about the HEALTH of the dependency. A business refusal does not. */
    static boolean counts(Outcome outcome) {
        return outcome == Outcome.UNAVAILABLE || outcome == Outcome.TIMEOUT || outcome == Outcome.TECHNICAL_ERROR || outcome == Outcome.BULKHEAD_REJECTED;
    }

    private static <R> boolean retriable(CallResult<R> result, boolean idempotent, Predicate<CallResult<R>> alsoRetry) {
        return switch (result.outcome()) {
            case UNAVAILABLE, TECHNICAL_ERROR, BULKHEAD_REJECTED -> true;
            case TIMEOUT -> idempotent;
            case BUSINESS_FAILURE -> alsoRetry != null && alsoRetry.test(result);
            case SUCCESS, CIRCUIT_OPEN -> false;
        };
    }
}
