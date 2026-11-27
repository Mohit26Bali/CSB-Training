package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Topics 7 and 8 against real MySQL. */
class ResilienceAndObservabilityIntegrationTest extends AbstractMicroLabTest {

    // ---------------------------------------------------------------------------------------------------- retry

    @Test
    void aTransientFailureIsRetriedWithDeterministicBackoffUntilItSucceeds() throws Exception {
        JsonNode run = run("res-retry-backoff");
        assertThat(evidence(run, "transient", "Attempt 1")).startsWith("attempt 1: TECHNICAL_ERROR");
        assertThat(evidence(run, "transient", "Attempt 2")).startsWith("attempt 2 after waiting 100 ms: TECHNICAL_ERROR");
        assertThat(evidence(run, "transient", "Attempt 3")).startsWith("attempt 3 after waiting 200 ms: SUCCESS");
        assertThat(evidence(run, "transient", "Final outcome")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "transient", "Calls that reached Inventory")).isEqualTo("3");
        assertThat(evidence(run, "transient", "Time spent, including waits (simulated)")).as("3 calls of 18 ms plus waits of 100 and 200").isEqualTo("354 ms");
        assertThat(evidence(run, "backoff-schedule", "Wait before retry 1")).isEqualTo("100 ms (simulated)");
        assertThat(evidence(run, "backoff-schedule", "Wait before retry 2")).isEqualTo("200 ms (simulated)");
        assertThat(evidence(run, "backoff-schedule", "Randomness or jitter")).startsWith("none");
        assertThat(evidence(run, "seed", "Retry implementation")).contains("not Resilience4j");
    }

    @Test
    void retriesAreBoundedEvenWhenTheServiceNeverRecovers() throws Exception {
        JsonNode run = run(student, "res-retry-backoff", "{\"maxAttempts\":2,\"transientFailures\":4}");
        assertThat(evidence(run, "transient", "Final outcome")).isEqualTo("TECHNICAL_ERROR (SimulatedFault)");
        assertThat(evidence(run, "transient", "Calls that reached Inventory")).isEqualTo("2");
        assertThat(run(student, "res-retry-backoff", "{\"maxAttempts\":1,\"transientFailures\":1}").get("result").get("steps").get(1).get("evidence").toString()).contains("attempt 1");
        JsonNode five = run(student, "res-retry-backoff", "{\"maxAttempts\":5,\"transientFailures\":0}");
        assertThat(evidence(five, "backoff-schedule", "Wait before retry 3")).isEqualTo("400 ms (simulated)");
        assertThat(evidence(five, "backoff-schedule", "Wait before retry 4")).as("capped at 800").isEqualTo("800 ms (simulated)");
    }

    @Test
    void aBusinessRefusalIsNotRetriedAndATimeoutIsRetriedOnlyWhenSafe() throws Exception {
        JsonNode run = run("res-retry-backoff");
        assertThat(evidence(run, "business-refusal", "Final outcome")).isEqualTo("BUSINESS_FAILURE (OUT_OF_STOCK)");
        assertThat(evidence(run, "business-refusal", "Attempts made")).isEqualTo("1");
        assertThat(evidence(run, "business-refusal", "Calls that reached Inventory")).isEqualTo("1");

        assertThat(evidence(run, "timeout-rule", "Without the idempotent flag: attempts")).isEqualTo("1");
        assertThat(evidence(run, "timeout-rule", "With the flag and a key: attempts")).isEqualTo("3");
        assertThat(evidenceStartingWith(run, "timeout-rule", "Charges for the first order")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "timeout-rule", "Charges for the retried order")).as("three attempts, one charge: the key made the retries harmless").isEqualTo("1");
    }

    // ---------------------------------------------------------------------------------------------------- circuit breaker

    @Test
    void theBreakerGoesClosedOpenHalfOpenClosedAndBackToOpenWithEveryTransitionRecorded() throws Exception {
        JsonNode run = run("res-circuit-breaker");
        assertThat(evidence(run, "closed", "Breaker state")).isEqualTo("CLOSED");
        assertThat(evidence(run, "closed", "Last price remembered for the fallback")).isEqualTo("4.50");
        assertThat(evidence(run, "failures-open-it", "Call 1")).startsWith("TECHNICAL_ERROR").contains("fallback (stale)").contains("breaker CLOSED, consecutive failures 1");
        assertThat(evidence(run, "failures-open-it", "Call 3")).contains("breaker OPEN, consecutive failures 3");
        assertThat(evidence(run, "failures-open-it", "Breaker state")).isEqualTo("OPEN");

        assertThat(evidence(run, "open", "Call 1")).startsWith("CIRCUIT_OPEN").contains("fallback (stale)");
        assertThat(evidence(run, "open", "Calls that reached the Product service")).as("the failing service gets no traffic").isEqualTo("0");
        assertThat(evidence(run, "open", "Calls refused by the breaker")).isEqualTo("3");

        assertThat(evidence(run, "half-open-recovers", "Probe")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "half-open-recovers", "Answered from")).contains("fresh");
        assertThat(evidence(run, "half-open-recovers", "Breaker state")).isEqualTo("CLOSED");
        assertThat(evidence(run, "half-open-fails", "Probe")).startsWith("TECHNICAL_ERROR");
        assertThat(evidence(run, "half-open-fails", "Breaker state")).isEqualTo("OPEN");

        List<String> transitions = new ArrayList<>();
        for (JsonNode e : step(run, "transitions").get("evidence")) {
            if (e.get("label").asText().startsWith("Transition")) {
                transitions.add(e.get("value").asText().split(" at ")[0]);
            }
        }
        assertThat(transitions).containsExactly("CLOSED → OPEN", "OPEN → HALF_OPEN", "HALF_OPEN → CLOSED", "CLOSED → OPEN", "OPEN → HALF_OPEN", "HALF_OPEN → OPEN");
        assertThat(evidenceStartingWith(run, "transitions", "Calls spared")).isEqualTo("3");
    }

    @Test
    void refusedCallsAreVisibleInTheTraceWithoutReachingTheService() throws Exception {
        JsonNode run = run("res-circuit-breaker");
        int refused = 0;
        for (JsonNode s : run.get("result").get("spans")) {
            if (s.get("status").asText().equals("CIRCUIT_OPEN")) {
                refused++;
                assertThat(s.get("simulatedMillis").asInt()).isZero();
            }
        }
        assertThat(refused).isGreaterThanOrEqualTo(3);
    }

    @Test
    void theBreakerFollowsTheWhatIfThresholdAndOpenPeriod() throws Exception {
        JsonNode run = run(student, "res-circuit-breaker", "{\"failureThreshold\":2,\"openMillis\":500}");
        assertThat(evidence(run, "failures-open-it", "Call 2")).contains("breaker OPEN, consecutive failures 2");
        assertThat(evidence(run, "half-open-recovers", "Waited (simulated)")).isEqualTo("500 ms");
        assertThat(evidence(run, "half-open-recovers", "Breaker state")).isEqualTo("CLOSED");
    }

    // ---------------------------------------------------------------------------------------------------- idempotency

    @Test
    void anIdempotencyKeyMakesTheRetryOfATimedOutChargeHarmless() throws Exception {
        JsonNode run = run("res-idempotency-key");
        assertThat(evidence(run, "first-attempt", "Caller sees")).isEqualTo("TIMEOUT (RESPONSE_TOO_SLOW)");
        assertThat(evidenceStartingWith(run, "first-attempt", "Payments CHARGED")).as("the money WAS taken").isEqualTo("1");
        assertThat(evidence(run, "retry", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "retry", "Answer was a replay of the first payment")).isEqualTo("true");
        assertThat(evidenceStartingWith(run, "retry", "Payments CHARGED")).isEqualTo("1");
        assertThat(evidence(run, "outcome", "Times the customer was charged")).isEqualTo("1");
        assertThat(evidence(run, "outcome", "Protected by the idempotency key")).isEqualTo("true");
    }

    @Test
    void withoutAKeyTheRetryChargesTheCustomerTwice() throws Exception {
        JsonNode run = run(student, "res-idempotency-key", "{\"useKey\":false}");
        assertThat(evidence(run, "retry", "Answer was a replay of the first payment")).isEqualTo("false");
        assertThat(evidence(run, "outcome", "Times the customer was charged")).isEqualTo("2");
        assertThat(evidence(run, "outcome", "Protected by the idempotency key")).isEqualTo("false");
    }

    // ---------------------------------------------------------------------------------------------------- amplification and bulkhead

    @Test
    void retriesAtTwoLayersMultiplyTheLoadOnTheFailingService() throws Exception {
        JsonNode run = run("res-retry-amplification");
        assertThat(evidence(run, "baseline", "Calls that reached Inventory")).isEqualTo("1");
        assertThat(evidence(run, "failing", "Order requests made by the client")).isEqualTo("3");
        assertThat(evidence(run, "failing", "Calls that reached the failing Inventory")).isEqualTo("9");
        assertThat(evidenceStartingWith(run, "failing", "Order rows created by the retries")).as("each client retry ran the whole order again").isEqualTo("3");
        assertThat(evidence(run, "amplification", "Expected calls to Inventory")).isEqualTo("9");
        assertThat(evidence(run, "amplification", "Amplification factor")).isEqualTo("9x");
        assertThat(evidence(run, "amplification", "Matches client attempts x order attempts")).isEqualTo("true");
        JsonNode small = run(student, "res-retry-amplification", "{\"clientAttempts\":2,\"orderAttempts\":2}");
        assertThat(evidence(small, "amplification", "Observed calls to Inventory")).isEqualTo("4");
        JsonNode none = run(student, "res-retry-amplification", "{\"clientAttempts\":1,\"orderAttempts\":1}");
        assertThat(evidence(none, "amplification", "Observed calls to Inventory")).isEqualTo("1");
    }

    @Test
    void perServiceBulkheadsContainASaturatedPaymentService() throws Exception {
        JsonNode run = run("res-bulkhead");
        assertThat(evidence(run, "saturate", "Permits available to Payment")).isEqualTo("0");
        assertThat(evidence(run, "saturate", "Permits available to Inventory")).isEqualTo("2");
        assertThat(evidence(run, "payment-rejected", "Caller sees")).isEqualTo("BULKHEAD_REJECTED (BULKHEAD_FULL)");
        assertThat(evidence(run, "payment-rejected", "Handler ran (known only to the lab)")).isEqualTo("false");
        assertThat(evidence(run, "others", "Inventory call")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "others", "Product call")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "others", "User call")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "released", "Permits available to Payment")).isEqualTo("2");
        assertThat(evidence(run, "released", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "isolation", "Other services stayed available")).isEqualTo("true");
    }

    @Test
    void aSharedPoolLetsOneSlowServiceStarveEveryOtherService() throws Exception {
        JsonNode run = run(student, "res-bulkhead", "{\"permits\":\"shared\"}");
        assertThat(evidence(run, "saturate", "Permits available to Inventory")).isEqualTo("0");
        assertThat(evidence(run, "others", "Inventory call")).startsWith("BULKHEAD_REJECTED");
        assertThat(evidence(run, "others", "Product call")).startsWith("BULKHEAD_REJECTED");
        assertThat(evidence(run, "isolation", "Other services stayed available")).isEqualTo("false");
        assertThat(evidence(run, "released", "Permits available to Payment")).as("permits are returned in both policies").isEqualTo("2");
    }

    // ---------------------------------------------------------------------------------------------------- observability

    @Test
    void oneOrderIsOneTraceWithParentChildSpansUnderOneCorrelationId() throws Exception {
        JsonNode run = run("obs-trace-order");
        JsonNode spans = run.get("result").get("spans");
        JsonNode order = null;
        List<JsonNode> children = new ArrayList<>();
        for (JsonNode s : spans) {
            if (s.get("service").asText().equals("order")) {
                order = s;
            }
        }
        assertThat(order).isNotNull();
        for (JsonNode s : spans) {
            if (!s.get("parentSpanId").isNull() && s.get("parentSpanId").asText().equals(order.get("spanId").asText())) {
                children.add(s);
            }
        }
        assertThat(order.get("parentSpanId").isNull()).as("the request is the root").isTrue();
        assertThat(children).extracting(c -> c.get("service").asText()).containsExactlyInAnyOrder("user", "product", "inventory");
        for (JsonNode c : children) {
            assertThat(c.get("correlationId").asText()).isEqualTo(order.get("correlationId").asText());
            assertThat(c.get("status").asText()).isEqualTo("SUCCESS");
            assertThat(c.get("startMillis").asLong()).isGreaterThanOrEqualTo(order.get("startMillis").asLong());
            assertThat(c.get("measuredMicros").asLong()).isGreaterThanOrEqualTo(0);
            assertThat(c.get("correlationPropagated").asBoolean()).isTrue();
        }
        assertThat(order.get("simulatedMillis").asLong()).as("20 own + 12 + 95 (slow product) + 18").isEqualTo(145);
        assertThat(evidence(run, "span-tree", "Spans in the trace")).isEqualTo("4");
        assertThat(evidence(run, "span-tree", "Distinct correlation ids")).isEqualTo("1");
        assertThat(evidence(run, "span-tree", "Every parent link points at a span of this trace")).isEqualTo("true");
        assertThat(evidence(run, "span-tree", "Timing basis")).contains("simulated").contains("measuredMicros");
        assertThat(evidence(run, "seed", "Tracing")).contains("SIMULATION").contains("not OpenTelemetry");
        assertThat(step(run, "not-opentelemetry").get("explanation").asText()).contains("the network and the collector are not simulated");
    }

    @Test
    void selfTimeAttributesTheLatencyToTheServiceThatSpentIt() throws Exception {
        JsonNode slowProduct = run("obs-trace-order");
        assertThat(evidence(slowProduct, "latency-attribution", "Service responsible for most of the time")).isEqualTo("product");
        assertThat(evidence(slowProduct, "latency-attribution", "Self time of product")).startsWith("95 ms of 145 ms");
        JsonNode nobodySlow = run(student, "obs-trace-order", "{\"slowService\":\"none\"}");
        assertThat(evidence(nobodySlow, "latency-attribution", "Service responsible for most of the time")).isEqualTo("order");
        JsonNode slowInventory = run(student, "obs-trace-order", "{\"slowService\":\"inventory\",\"delayMillis\":120}");
        assertThat(evidence(slowInventory, "latency-attribution", "Service responsible for most of the time")).isEqualTo("inventory");
    }

    @Test
    void aDownstreamFailureCanBeTracedBackToTheSpanWhereItStarted() throws Exception {
        JsonNode run = run("obs-failure-propagation");
        assertThat(evidence(run, "failing-request", "User sees")).isEqualTo("BUSINESS_FAILURE (INVENTORY_TECHNICAL_ERROR)");
        assertThat(evidence(run, "find-the-cause", "Span where the failure started")).isEqualTo("inventory.reserve TECHNICAL_ERROR");
        assertThat(evidence(run, "find-the-cause", "What the Order service reported upward")).isEqualTo("BUSINESS_FAILURE (INVENTORY_TECHNICAL_ERROR)");
        assertThat(evidence(run, "find-the-cause", "Failure found by following parent links from the root")).isEqualTo("true");
        assertThat(evidence(run, "find-the-cause", "Spans that report a failure")).isEqualTo("2 of 4");

        JsonNode slowUser = run(student, "obs-failure-propagation", "{\"failingService\":\"user\",\"fault\":\"slow\"}");
        assertThat(evidence(slowUser, "failing-request", "User sees")).isEqualTo("BUSINESS_FAILURE (USER_TIMEOUT)");
        assertThat(evidence(slowUser, "find-the-cause", "Span where the failure started")).isEqualTo("user.getUser TIMEOUT");
        JsonNode downProduct = run(student, "obs-failure-propagation", "{\"failingService\":\"product\",\"fault\":\"down\"}");
        assertThat(evidence(downProduct, "find-the-cause", "Span where the failure started")).isEqualTo("product.getProduct UNAVAILABLE");
    }

    @Test
    void whenCorrelationIsNotPassedOnTheRequestCannotBeReassembled() throws Exception {
        JsonNode broken = run("obs-missing-correlation");
        assertThat(evidence(broken, "what-is-lost", "Spans with no parent besides the request itself")).isEqualTo("3");
        assertThat(evidence(broken, "what-is-lost", "Distinct correlation ids for ONE request")).isEqualTo("4");
        assertThat(evidence(broken, "what-is-lost", "The request can be reassembled from the recorded data")).isEqualTo("false");
        JsonNode intact = run(student, "obs-missing-correlation", "{\"propagate\":true}");
        assertThat(evidence(intact, "what-is-lost", "Spans with no parent besides the request itself")).isEqualTo("0");
        assertThat(evidence(intact, "what-is-lost", "Distinct correlation ids for ONE request")).isEqualTo("1");
        assertThat(evidence(intact, "what-is-lost", "The request can be reassembled from the recorded data")).isEqualTo("true");
    }

    @Test
    void theCorrelationIdCrossesAnEventOnlyIfTheEventCarriesIt() throws Exception {
        JsonNode carried = run("obs-async-correlation");
        assertThat(evidence(carried, "same-trace", "The consumer work can be tied to the request")).isEqualTo("true");
        assertThat(evidence(carried, "same-trace", "Parent span of the consumer span")).startsWith("none");
        JsonNode lost = run(student, "obs-async-correlation", "{\"propagateInEvent\":false}");
        assertThat(evidence(lost, "same-trace", "The consumer work can be tied to the request")).isEqualTo("false");
        assertThat(evidence(lost, "same-trace", "Order request correlation id")).isNotEqualTo(evidence(lost, "same-trace", "Consumer correlation id"));
    }

    @Test
    void tracesAreOnlyReadableByTheRunOwnerAndHoldNoSecrets() throws Exception {
        String runId = createRun(student, "obs-trace-order").get("runId").asText();
        execute(student, runId);
        var other = newUser("micro_lab_trace_" + System.nanoTime(), com.springlab.entity.Role.STUDENT);
        getAs("/api/lab/microservices/runs/" + runId, other).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        String text = getAs("/api/lab/microservices/runs/" + runId, student).andReturn().getResponse().getContentAsString().toLowerCase();
        for (String forbidden : List.of("authorization", "bearer", "password", "jwt", "secret", "localhost")) {
            assertThat(text).doesNotContain(forbidden);
        }
        assertThat(com.springlab.support.PortReferences.mentions(text)).as("a reference to the database port").isFalse();
    }
}
