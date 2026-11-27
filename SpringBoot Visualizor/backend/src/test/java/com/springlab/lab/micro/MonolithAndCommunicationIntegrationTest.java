package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Topics 1 and 2 against real MySQL: each experiment's claim is checked against what the services and tables really did. */
class MonolithAndCommunicationIntegrationTest extends AbstractMicroLabTest {

    // ---------------------------------------------------------------------------------------------------- topic 1

    @Test
    void aFailureAfterTheReservationRollsTheMonolithBackButNotTheServices() throws Exception {
        JsonNode run = run("mono-order-workflow");
        assertThat(evidence(run, "monolith", "Transaction")).isEqualTo("rolled back (IllegalStateException)");
        assertThat(evidenceStartingWith(run, "monolith", "Stock")).as("the monolith's stock is back to 5").isEqualTo("5");
        assertThat(evidenceStartingWith(run, "monolith", "Orders PLACED / PENDING")).isEqualTo("0 / 0");
        assertThat(evidence(run, "monolith", "Other database calls observed")).contains("rollback");

        assertThat(evidence(run, "distributed", "Caller sees")).isEqualTo("TECHNICAL_ERROR (IllegalStateException)");
        assertThat(evidenceStartingWith(run, "distributed", "Stock")).as("the stock stays reserved: Inventory had already committed").isEqualTo("3");
        assertThat(evidenceStartingWith(run, "distributed", "Orders PLACED / PENDING")).isEqualTo("0 / 1");

        assertThat(evidence(run, "comparison", "Monolith ended consistent")).isEqualTo("true");
        assertThat(evidence(run, "comparison", "Services ended consistent")).isEqualTo("false");
        assertThat(run.toString()).doesNotContain("injected failure after");
    }

    @Test
    void withoutAFailureBothVersionsCommitAndEndConsistent() throws Exception {
        JsonNode run = run(student, "mono-order-workflow", "{\"failure\":\"none\"}");
        assertThat(evidence(run, "monolith", "Transaction")).isEqualTo("committed");
        assertThat(evidence(run, "monolith", "Other database calls observed")).contains("commit");
        assertThat(evidenceStartingWith(run, "monolith", "Orders PLACED / PENDING")).isEqualTo("1 / 0");
        assertThat(evidence(run, "distributed", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "distributed", "Orders PLACED / PENDING")).isEqualTo("1 / 0");
        assertThat(evidence(run, "comparison", "Services ended consistent")).isEqualTo("true");
    }

    @Test
    void theMonolithRunsInOneConnectionAndTheServicesInSeveral() throws Exception {
        JsonNode run = run("mono-order-workflow");
        String monolith = step(run, "monolith").get("sql").toString();
        assertThat(monolith).contains("monolith:").contains("micro_lab_users").contains("micro_lab_products").contains("micro_lab_stock").contains("micro_lab_orders");
        String distributed = step(run, "distributed").get("sql").toString();
        assertThat(distributed).contains("order:").contains("inventory:").contains("user:").contains("product:");
        assertThat(distributed).doesNotContain("monolith:");
    }

    @Test
    void aHardDependencyStopsOrdersButASoftOneDoesNot() throws Exception {
        JsonNode run = run("mono-coupling");
        assertThat(evidence(run, "healthy", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "hard-dependency-down", "Caller sees")).isEqualTo("BUSINESS_FAILURE (INVENTORY_UNAVAILABLE)");
        assertThat(evidenceStartingWith(run, "hard-dependency-down", "Orders PLACED")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "hard-dependency-down", "Orders rejected")).isEqualTo("1");

        assertThat(evidence(run, "soft-dependency-down", "Caller sees")).as("Notification is down but the order is accepted").isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "soft-dependency-down", "Delivery attempt")).isEqualTo("UNAVAILABLE (CONNECTION_REFUSED)");
        assertThat(evidenceStartingWith(run, "soft-dependency-down", "Events still pending")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "soft-dependency-down", "Notifications sent")).isEqualTo("0");

        assertThat(evidenceStartingWith(run, "availability", "Operation needing 3 hard")).isEqualTo("99.70%");
        assertThat(evidenceStartingWith(run, "availability", "Assumption")).contains("illustrative");
    }

    @Test
    void whicheverHardDependencyIsDownTheOrderFailsWithItsName() throws Exception {
        for (String service : new String[] {"user", "product"}) {
            JsonNode run = run(student, "mono-coupling", "{\"hardDependency\":\"" + service + "\"}");
            assertThat(evidence(run, "hard-dependency-down", "Caller sees")).isEqualTo("BUSINESS_FAILURE (" + service.toUpperCase() + "_UNAVAILABLE)");
            assertThat(evidenceStartingWith(run, "hard-dependency-down", "Orders rejected")).as("no order row exists yet").isEqualTo("0");
        }
    }

    @Test
    void eachServiceTouchesOnlyItsOwnTablesAndNoTableIsSharedAcrossServices() throws Exception {
        JsonNode run = run("mono-boundaries");
        assertThat(evidence(run, "who-touched-what", "Tables touched by the order service")).isEqualTo("micro_lab_orders");
        assertThat(evidence(run, "who-touched-what", "Tables touched by the user service")).isEqualTo("micro_lab_users");
        assertThat(evidence(run, "who-touched-what", "Tables touched by the product service")).isEqualTo("micro_lab_products");
        assertThat(evidence(run, "who-touched-what", "Tables touched by the inventory service")).contains("micro_lab_stock").contains("micro_lab_reservations")
                .doesNotContain("micro_lab_orders");
        assertThat(evidence(run, "who-touched-what", "Tables touched by the notification service")).isEqualTo("micro_lab_notifications");
        assertThat(evidence(run, "who-touched-what", "A table touched by two services")).isEqualTo("false");
    }

    @Test
    void splittingEveryStepIsEstimatedFromObservedNumbersAndLabelledIllustrative() throws Exception {
        JsonNode run = run("mono-over-split");
        assertThat(evidence(run, "coarse-grained", "Service calls in the workflow")).isEqualTo("4");
        assertThat(evidence(run, "over-split", "Hops with business-sized services")).isEqualTo("3");
        assertThat(evidence(run, "over-split", "Hops if every step were a service")).isEqualTo("7");
        assertThat(evidenceStartingWith(run, "over-split", "Operation needing 7")).isEqualTo("99.30%");
        assertThat(evidenceStartingWith(run, "over-split", "Assumption")).contains("illustrative");
        assertThat(step(run, "over-split").get("provenance").asText()).isEqualTo("DERIVED");
        assertThat(step(run, "over-split").get("explanation").asText()).contains("not a benchmark");
    }

    // ---------------------------------------------------------------------------------------------------- topic 2

    @Test
    void synchronousCallsAddUpAndTheOrderRowIsPlaced() throws Exception {
        JsonNode run = run("comm-sync-order-calls");
        assertThat(evidence(run, "place-order", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "place-order", "Orders PLACED")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "place-order", "Stock")).isEqualTo("3");
        assertThat(evidence(run, "time-adds-up", "Total equals own time plus the sum of the calls")).isEqualTo("true");
        assertThat(evidence(run, "time-adds-up", "Sum of the dependency calls (simulated)")).isEqualTo("45 ms");
        // The three calls are children of the order request: a real parent/child tree.
        JsonNode spans = run.get("result").get("spans");
        int children = 0;
        for (JsonNode s : spans) {
            if (s.get("operation").asText().equals("getUser") || s.get("operation").asText().equals("getProduct") || s.get("operation").asText().equals("reserve")) {
                if (!s.get("parentSpanId").isNull()) {
                    children++;
                }
            }
        }
        assertThat(children).isGreaterThanOrEqualTo(3);
    }

    @Test
    void aSlowInventoryLeavesTheOrderPendingWithTheStockReserved() throws Exception {
        JsonNode run = run("comm-sync-failure");
        assertThat(evidence(run, "faulted", "Caller sees")).isEqualTo("BUSINESS_FAILURE (INVENTORY_TIMEOUT)");
        assertThat(evidenceStartingWith(run, "faulted", "Orders PENDING")).as("the order cannot know what happened").isEqualTo("1");
        assertThat(evidenceStartingWith(run, "faulted", "Stock")).as("but the stock WAS reserved: 5 - 1 - 1").isEqualTo("3");
        assertThat(evidenceStartingWith(run, "faulted", "Reservations")).isEqualTo("2");
        assertThat(evidence(run, "conclusion", "What can be concluded")).contains("unknown");
    }

    @Test
    void anUnavailableOrFailingInventoryRejectsTheOrderAndChangesNothing() throws Exception {
        JsonNode down = run(student, "comm-sync-failure", "{\"fault\":\"down\"}");
        assertThat(evidence(down, "faulted", "Caller sees")).isEqualTo("BUSINESS_FAILURE (INVENTORY_UNAVAILABLE)");
        assertThat(evidenceStartingWith(down, "faulted", "Orders REJECTED")).isEqualTo("1");
        assertThat(evidenceStartingWith(down, "faulted", "Orders PENDING")).isEqualTo("0");
        assertThat(evidenceStartingWith(down, "faulted", "Stock")).isEqualTo("4");
        assertThat(evidence(down, "conclusion", "What can be concluded")).contains("safe");

        JsonNode error = run(student, "comm-sync-failure", "{\"fault\":\"error\"}");
        assertThat(evidence(error, "faulted", "Caller sees")).isEqualTo("BUSINESS_FAILURE (INVENTORY_TECHNICAL_ERROR)");
    }

    @Test
    void aFailureBeforeTheOrderRowExistsLeavesNoOrderBehind() throws Exception {
        JsonNode run = run(student, "comm-sync-failure", "{\"failingService\":\"user\",\"fault\":\"slow\"}");
        assertThat(evidence(run, "faulted", "Caller sees")).isEqualTo("BUSINESS_FAILURE (USER_TIMEOUT)");
        assertThat(evidenceStartingWith(run, "faulted", "Orders PENDING")).isEqualTo("0");
        assertThat(evidenceStartingWith(run, "faulted", "Orders REJECTED")).isEqualTo("0");
        assertThat(evidenceStartingWith(run, "faulted", "Stock")).as("Inventory was never called").isEqualTo("4");
    }

    @Test
    void anEventIsPublishedAfterTheOrderAndConsumedLaterInAnotherRequest() throws Exception {
        JsonNode run = run("comm-async-event");
        assertThat(step(run, "what-the-channel-is").get("provenance").asText()).isEqualTo("CONCEPTUAL");
        assertThat(evidence(run, "publish", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "publish", "Event mechanism")).contains("SIMULATED").contains("not Kafka or RabbitMQ");
        assertThat(evidence(run, "publish", "Events published")).isEqualTo("1");
        assertThat(evidence(run, "publish", "Events pending for Notification")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "publish", "Notifications sent")).as("the consumer has not seen it yet").isEqualTo("0");
        assertThat(evidence(run, "consume", "First attempt")).isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "consume", "Notifications sent")).isEqualTo("1");

        JsonNode calls = run.get("result").get("calls");
        JsonNode event = null;
        for (JsonNode c : calls) {
            if (c.get("kind").asText().equals("EVENT")) {
                event = c;
            }
        }
        assertThat(event).as("the delivery is recorded as an event call").isNotNull();
        assertThat(event.get("from").asText()).isEqualTo("broker");
        assertThat(event.get("to").asText()).isEqualTo("notification");
        assertThat(event.get("parentSpanId").isNull()).as("a delivery is not a child of the order request").isTrue();
        for (JsonNode c : calls) {
            if (!c.get("kind").asText().equals("EVENT")) {
                assertThat(c.get("kind").asText()).isEqualTo("SYNC");
            }
        }
    }

    @Test
    void aNaiveConsumerRepeatsItsEffectOnADuplicateDelivery() throws Exception {
        JsonNode run = run("comm-async-duplicate");
        assertThat(evidenceStartingWith(run, "first-delivery", "Notifications sent")).isEqualTo("1");
        assertThat(evidence(run, "duplicate-delivery", "Duplicate delivery 1")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "conclusion", "Notifications for one order")).isEqualTo("2");
        assertThat(evidence(run, "conclusion", "Side effect repeated")).isEqualTo("true");
    }

    @Test
    void anIdempotentConsumerIgnoresDuplicatesNoMatterHowManyArrive() throws Exception {
        JsonNode run = run(student, "comm-async-duplicate", "{\"consumer\":\"idempotent\",\"extraDeliveries\":3}");
        assertThat(evidence(run, "duplicate-delivery", "Duplicate delivery 3")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "conclusion", "Notifications for one order")).isEqualTo("1");
        assertThat(evidence(run, "conclusion", "Side effect repeated")).isEqualTo("false");
        assertThat(evidenceStartingWith(run, "duplicate-delivery", "Processed message ids")).as("one id recorded once").isEqualTo("1");
        JsonNode naive = run(student, "comm-async-duplicate", "{\"consumer\":\"naive\",\"extraDeliveries\":3}");
        assertThat(evidence(naive, "conclusion", "Notifications for one order")).isEqualTo("4");
    }

    @Test
    void synchronousNotificationReportsFailureForAnOrderThatWasPlaced() throws Exception {
        JsonNode run = run("comm-sync-vs-async");
        assertThat(evidence(run, "sync-faulted", "Caller sees")).isEqualTo("BUSINESS_FAILURE (NOTIFICATION_UNAVAILABLE)");
        assertThat(evidence(run, "sync-faulted", "The order was placed anyway")).isEqualTo("true");
        assertThat(evidence(run, "async-faulted", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "async-faulted", "Delivery attempt")).isEqualTo("UNAVAILABLE");
        assertThat(evidence(run, "async-faulted", "Events pending for Notification")).isEqualTo("1");
        assertThat(evidence(run, "recovery", "Events pending for Notification")).isEqualTo("0");
        assertThat(evidenceStartingWith(run, "recovery", "Notifications sent")).isEqualTo("3");
        assertThat(evidence(run, "compare", "Difference equals Notification's own time")).isEqualTo("true");
    }

    @Test
    void whenNotificationIsHealthyBothWaysSucceed() throws Exception {
        JsonNode run = run(student, "comm-sync-vs-async", "{\"notificationDown\":false}");
        assertThat(evidence(run, "sync-faulted", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidence(run, "async-faulted", "Delivery attempt")).isEqualTo("SUCCESS");
    }
}
