package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Topic 6 against real MySQL: orchestration, choreography, compensation and a failing compensation. */
class SagaIntegrationTest extends AbstractMicroLabTest {

    private JsonNode orchestrated(String scenario) throws Exception {
        return run(student, "saga-orchestrated", "{\"scenario\":\"" + scenario + "\"}");
    }

    private JsonNode choreographed(String scenario) throws Exception {
        return run(student, "saga-choreographed", "{\"scenario\":\"" + scenario + "\"}");
    }

    // ---------------------------------------------------------------------------------------------------- orchestration

    @Test
    void aSuccessfulOrchestratedSagaCommitsEveryStepAndNothingIsCompensated() throws Exception {
        JsonNode run = orchestrated("success");
        assertThat(evidence(run, "saga", "Saga ended")).isEqualTo("PLACED");
        assertThat(evidence(run, "saga", "Saga log 2 RESERVE_STOCK")).startsWith("COMPLETED");
        assertThat(evidence(run, "saga", "Saga log 3 CHARGE_PAYMENT")).startsWith("COMPLETED");
        assertThat(evidence(run, "saga", "Saga log 4 CONFIRM_ORDER")).startsWith("COMPLETED");
        assertThat(evidenceStartingWith(run, "states", "Order service: order status")).isEqualTo("PLACED");
        assertThat(evidenceStartingWith(run, "states", "Inventory service: stock")).isEqualTo("3");
        assertThat(evidenceStartingWith(run, "states", "Payment service: payments")).isEqualTo("1 / 0");
        assertThat(evidence(run, "not-a-rollback", "Compensating business operations that completed")).isEqualTo("0");
    }

    @Test
    void aPaymentFailureAfterTheReservationIsCompensatedByARealRelease() throws Exception {
        JsonNode run = orchestrated("payment-fails");
        assertThat(evidence(run, "saga", "Saga log 2 RESERVE_STOCK")).startsWith("COMPLETED");
        assertThat(evidence(run, "saga", "Saga log 3 CHARGE_PAYMENT")).isEqualTo("FAILED (BUSINESS_FAILURE (DECLINED))");
        assertThat(evidence(run, "saga", "Saga log 4 COMPENSATE_RESERVE_STOCK")).startsWith("COMPENSATED").contains("new business operation");
        assertThat(evidence(run, "saga", "Saga ended")).isEqualTo("CANCELLED");
        assertThat(evidenceStartingWith(run, "states", "Order service: order status")).isEqualTo("CANCELLED");
        assertThat(evidenceStartingWith(run, "states", "Inventory service: stock")).as("the stock is back").isEqualTo("5");
        assertThat(evidenceStartingWith(run, "states", "Inventory service: reservations")).as("but the reservation stays as RELEASED history").isEqualTo("0 / 1");
        assertThat(evidenceStartingWith(run, "states", "Payment service: payments")).isEqualTo("0 / 0");
        assertThat(step(run, "saga").get("sql").toString()).contains("inventory:").contains("order:")
                .as("a declined payment is refused before the Payment service writes anything").doesNotContain("payment:");
        boolean declinedCallRecorded = false;
        for (JsonNode e : step(run, "saga").get("evidence")) {
            declinedCallRecorded |= e.get("value").asText().contains("payment.charge: BUSINESS_FAILURE (DECLINED)");
        }
        assertThat(declinedCallRecorded).isTrue();
    }

    @Test
    void compensationIsNotARollbackAndEachServiceCommittedItsOwnTransaction() throws Exception {
        JsonNode run = orchestrated("payment-fails");
        assertThat(evidence(run, "not-a-rollback", "Database rollbacks during the whole saga")).isEqualTo("0");
        assertThat(evidence(run, "not-a-rollback", "Compensating business operations that completed")).isEqualTo("1");
        assertThat(evidence(run, "not-a-rollback", "The reversed reservation still exists as history (read by the lab inspector, not a service call)")).isEqualTo("true");
        assertThat(Long.parseLong(evidence(run, "not-a-rollback", "Local transactions committed (all services)"))).as("several separate commits").isGreaterThanOrEqualTo(4);
        assertThat(step(run, "not-a-rollback").get("title").asText()).contains("not a rollback");
        assertThat(step(run, "not-a-rollback").get("explanation").asText()).contains("new operation that reverses them");
        assertThat(step(run, "saga").get("explanation").asText()).doesNotContain("rolled back");
    }

    @Test
    void anInventoryRefusalNeedsNoCompensationBecauseNothingWasDone() throws Exception {
        JsonNode run = orchestrated("inventory-fails");
        assertThat(evidence(run, "saga", "Saga log 2 RESERVE_STOCK")).isEqualTo("FAILED (BUSINESS_FAILURE (OUT_OF_STOCK))");
        assertThat(evidence(run, "saga", "Saga ended")).isEqualTo("REJECTED");
        assertThat(evidenceStartingWith(run, "states", "Inventory service: reservations")).isEqualTo("0 / 0");
        assertThat(evidence(run, "not-a-rollback", "Compensating business operations that completed")).isEqualTo("0");
    }

    @Test
    void aFailureAfterThePaymentRefundsItAndThenReleasesTheStockInReverseOrder() throws Exception {
        JsonNode run = orchestrated("confirm-fails");
        assertThat(evidence(run, "saga", "Saga log 4 CONFIRM_ORDER")).startsWith("FAILED");
        assertThat(evidence(run, "saga", "Saga log 5 COMPENSATE_CHARGE_PAYMENT")).startsWith("COMPENSATED");
        assertThat(evidence(run, "saga", "Saga log 6 COMPENSATE_RESERVE_STOCK")).startsWith("COMPENSATED");
        assertThat(evidence(run, "saga", "Saga ended")).isEqualTo("CANCELLED");
        assertThat(evidenceStartingWith(run, "states", "Payment service: payments")).as("CHARGED / REFUNDED: the payment stays visible as REFUNDED").isEqualTo("0 / 1");
        assertThat(evidenceStartingWith(run, "states", "Inventory service: stock")).isEqualTo("5");
        assertThat(evidence(run, "not-a-rollback", "Compensating business operations that completed")).isEqualTo("2");
        assertThat(evidence(run, "not-a-rollback", "Database rollbacks during the whole saga")).isEqualTo("0");
    }

    @Test
    void aFailingCompensationLeavesTheSagaWaitingAndAResumeCompletesIt() throws Exception {
        JsonNode run = orchestrated("compensation-fails");
        assertThat(evidence(run, "saga", "Saga log 4 COMPENSATE_RESERVE_STOCK")).startsWith("COMPENSATION_FAILED").contains("UNAVAILABLE");
        assertThat(evidence(run, "saga", "Saga ended")).isEqualTo("COMPENSATION_FAILED");
        assertThat(evidenceStartingWith(run, "states", "Order service: order status")).isEqualTo("COMPENSATION_FAILED");
        assertThat(evidenceStartingWith(run, "states", "Inventory service: stock")).as("the stock is still taken").isEqualTo("3");
        assertThat(evidenceStartingWith(run, "states", "Inventory service: reservations")).isEqualTo("1 / 0");

        assertThat(evidence(run, "resume", "Saga ended")).isEqualTo("CANCELLED");
        assertThat(evidence(run, "resume", "Saga log 4 COMPENSATE_RESERVE_STOCK")).startsWith("COMPENSATED");
        assertThat(evidenceStartingWith(run, "resume", "Stock")).isEqualTo("5");
    }

    // ---------------------------------------------------------------------------------------------------- choreography

    @Test
    void aSuccessfulChoreographyPlacesTheOrderWithoutAnyCoordinator() throws Exception {
        JsonNode run = choreographed("success");
        assertThat(step(run, "what-this-shows").get("provenance").asText()).isEqualTo("CONCEPTUAL");
        assertThat(evidence(run, "start", "Order status")).isEqualTo("PENDING");
        assertThat(evidenceStartingWith(run, "start", "Stock")).as("only a row and an event exist so far").isEqualTo("5");
        assertThat(evidence(run, "start", "Events pending for Inventory")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "inventory-reserves", "Stock")).isEqualTo("3");
        assertThat(evidenceStartingWith(run, "payment-reacts", "Payments CHARGED")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "order-confirms", "Order status")).isEqualTo("PLACED");
        assertThat(evidence(run, "no-coordinator", "A component that held the whole saga state")).contains("none");
    }

    @Test
    void theIntermediateStateBetweenTwoLocalTransactionsIsVisibleToOtherReaders() throws Exception {
        JsonNode run = choreographed("payment-fails");
        assertThat(step(run, "in-between").get("provenance").asText()).isEqualTo("CAPTURED");
        assertThat(evidenceStartingWith(run, "in-between", "Order service: order status")).isEqualTo("PENDING");
        assertThat(evidenceStartingWith(run, "in-between", "Inventory service: stock")).as("already reduced").isEqualTo("3");
        assertThat(evidenceStartingWith(run, "in-between", "Payment service: payments")).isEqualTo("0 / 0");
        assertThat(step(run, "in-between").get("explanation").asText()).contains("no isolation across services");
    }

    @Test
    void inChoreographyTheCompensationIsAnEventReaction() throws Exception {
        JsonNode run = choreographed("payment-fails");
        assertThat(evidenceStartingWith(run, "payment-reacts", "Payments CHARGED")).isEqualTo("0");
        assertThat(evidence(run, "payment-reacts", "Events pending for Inventory")).as("PaymentFailed waits for Inventory").isEqualTo("1");
        assertThat(evidence(run, "inventory-compensates", "Delivery")).isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "inventory-compensates", "Stock")).isEqualTo("5");
        assertThat(evidenceStartingWith(run, "inventory-compensates", "Reservations RELEASED")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "order-reacts", "Order status")).isEqualTo("CANCELLED");
        assertThat(evidence(run, "no-coordinator", "Database rollbacks during the whole saga")).isEqualTo("0");
        assertThat(step(run, "inventory-compensates").get("explanation").asText()).contains("not a database rollback");
    }

    @Test
    void aCompensationThatCannotRunYetWaitsAsAPendingEventAndCompletesOnRecovery() throws Exception {
        JsonNode run = choreographed("compensation-fails");
        assertThat(evidence(run, "inventory-compensates", "Delivery")).isEqualTo("UNAVAILABLE (CONNECTION_REFUSED)");
        assertThat(evidenceStartingWith(run, "inventory-compensates", "Stock")).as("still taken").isEqualTo("3");
        assertThat(evidence(run, "inventory-compensates", "Events pending for Inventory")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "order-reacts", "Order status")).as("nobody told Order yet").isEqualTo("PENDING");

        assertThat(evidenceStartingWith(run, "recovery", "Stock")).isEqualTo("5");
        assertThat(evidenceStartingWith(run, "recovery", "Order status")).isEqualTo("CANCELLED");
        assertThat(evidenceStartingWith(run, "no-coordinator", "Inventory service: reservations")).isEqualTo("0 / 1");
    }

    // ---------------------------------------------------------------------------------------------------- rollback vs compensation

    @Test
    void aRollbackLeavesNothingAndACompensationLeavesHistory() throws Exception {
        JsonNode run = run("saga-compensation-vs-rollback");
        assertThat(Long.parseLong(evidence(run, "rollback", "Database rollbacks observed"))).isGreaterThanOrEqualTo(1);
        assertThat(evidenceStartingWith(run, "rollback", "Stock")).isEqualTo("5");
        assertThat(evidenceStartingWith(run, "rollback", "Orders for the product")).isEqualTo("0");
        assertThat(evidenceStartingWith(run, "rollback", "Reservations")).isEqualTo("0");

        assertThat(evidence(run, "compensation", "Saga ended")).isEqualTo("CANCELLED");
        assertThat(evidence(run, "compensation", "Database rollbacks observed")).isEqualTo("0");
        assertThat(evidenceStartingWith(run, "compensation", "Stock")).isEqualTo("5");
        assertThat(evidenceStartingWith(run, "compensation", "Reservations RELEASED")).isEqualTo("1");

        assertThat(evidence(run, "difference", "After the rollback: reservation rows left for that product")).isEqualTo("0");
        assertThat(evidence(run, "difference", "After the compensation: reservation rows left for that product (history)")).isEqualTo("1");
        assertThat(evidence(run, "difference", "After the compensation: those rows are RELEASED")).isEqualTo("true");
        assertThat(evidence(run, "difference", "A rollback happened in the saga")).isEqualTo("false");
        assertThat(step(run, "two-phase-commit").get("provenance").asText()).isEqualTo("CONCEPTUAL");
        assertThat(step(run, "two-phase-commit").get("explanation").asText()).contains("is not run here");
    }

    @Test
    void sixUsersRunningTheSameSagaGetTheSameResultsAndNeverShareState() throws Exception {
        java.util.List<String> solo = allEvidence(orchestrated("compensation-fails"));
        java.util.List<com.springlab.entity.User> six = new java.util.ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(newUser("micro_lab_saga" + i + "_" + System.nanoTime(), com.springlab.entity.Role.STUDENT));
        }
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        try {
            java.util.List<java.util.concurrent.Future<JsonNode>> futures = new java.util.ArrayList<>();
            for (com.springlab.entity.User u : six) {
                futures.add(pool.submit(() -> run(u, "saga-orchestrated", "{\"scenario\":\"compensation-fails\"}")));
            }
            for (java.util.concurrent.Future<JsonNode> f : futures) {
                assertThat(allEvidence(f.get())).isEqualTo(solo);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(labRows()).isZero();
    }
}
