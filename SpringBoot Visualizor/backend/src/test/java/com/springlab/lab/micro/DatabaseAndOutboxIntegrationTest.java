package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Topic 5 and the transactional outbox, against real MySQL. */
class DatabaseAndOutboxIntegrationTest extends AbstractMicroLabTest {

    // ---------------------------------------------------------------------------------------------------- database per service

    @Test
    void eachServiceKeepsItsOwnDataAndOrderLearnsTheStockOnlyThroughTheContract() throws Exception {
        JsonNode run = run("dbs-ownership-contracts");
        assertThat(evidenceStartingWith(run, "order-writes", "Stock")).isEqualTo("8");
        assertThat(evidence(run, "inventory-alone", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "inventory-alone", "Stock")).isEqualTo("13");
        assertThat(evidenceStartingWith(run, "inventory-alone", "Orders PLACED unchanged")).isEqualTo("true");
        assertThat(evidence(run, "ask-by-contract", "Stock level answered by Inventory")).isEqualTo("13");
        assertThat(evidence(run, "tables-per-service", "Tables touched by the order service")).isEqualTo("micro_lab_orders");
        assertThat(evidence(run, "tables-per-service", "Physical databases they used")).startsWith("1 ");
        assertThat(step(run, "logical-vs-physical").get("explanation").asText()).contains("LOGICAL isolation").contains("Separate physical databases");
    }

    @Test
    void aServiceReachingIntoAnotherServicesTableIsRefusedBeforeMySqlSeesIt() throws Exception {
        JsonNode run = run("dbs-forbidden-access");
        assertThat(evidence(run, "own-table", "Succeeded")).isEqualTo("true");
        assertThat(evidence(run, "foreign-table", "Succeeded")).isEqualTo("false");
        assertThat(evidence(run, "foreign-table", "Result")).isEqualTo("OwnershipViolation: The order service may not touch micro_lab_stock, which belongs to inventory");
        assertThat(evidence(run, "foreign-table", "Statement sent to MySQL")).isEqualTo("false");
        assertThat(step(run, "foreign-table").get("sql")).as("the refused statement never reached the driver").isEmpty();
        assertThat(step(run, "own-table").get("sql")).isNotEmpty();
        assertThat(evidence(run, "through-contract", "Stock level answered by Inventory")).isEqualTo("5");
        assertThat(step(run, "physical-enforcement").get("explanation").asText()).contains("not a security boundary");
    }

    @Test
    void mySqlEnforcesForeignKeysInsideAServiceButNothingEnforcesThemAcrossServices() throws Exception {
        JsonNode run = run("dbs-foreign-keys");
        assertThat(evidence(run, "inside-one-service", "Caller sees")).isEqualTo("TECHNICAL_ERROR (DataIntegrityViolationException)");
        assertThat(step(run, "inside-one-service").get("sql").toString()).contains("1451");
        assertThat(evidenceStartingWith(run, "inside-one-service", "Stock row still exists")).isEqualTo("true");

        assertThat(evidence(run, "across-services", "Product deleted")).isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "across-services", "Order for the deleted product still exists")).isEqualTo("true");
        assertThat(evidence(run, "across-services", "Order asks Product for it now")).isEqualTo("BUSINESS_FAILURE (NOT_FOUND)");
        assertThat(evidenceStartingWith(run, "across-services", "Stock rows of the deleted product")).isEqualTo("1");

        assertThat(evidenceStartingWith(run, "copied-data", "Price copied into the order")).isEqualTo("4.50");
        assertThat(evidence(run, "copied-data", "Price now in the Product service")).isEqualTo("6.00");
        assertThat(evidence(run, "cost", "Copy differs from its source")).isEqualTo("true");
        assertThat(evidence(run, "cost", "Dangling reference possible across services")).isEqualTo("true");
        assertThat(evidence(run, "cost", "Dangling reference possible inside one service")).isEqualTo("false");
    }

    @Test
    void readingASharedTableBreaksWhenTheOwnerChangesItAndTheContractDoesNot() throws Exception {
        JsonNode run = run("dbs-shared-coupling");
        assertThat(evidence(run, "direct-read", "Result")).isEqualTo("read 5");
        assertThat(evidence(run, "schema-changed", "Succeeded")).isEqualTo("false");
        assertThat(evidence(run, "schema-changed", "Result")).contains("1054");
        assertThat(evidence(run, "schema-changed", "Simulation")).contains("table was not altered");
        assertThat(evidence(run, "through-contract", "Stock level answered by Inventory")).isEqualTo("5");
        assertThat(evidence(run, "coupling", "Direct access broke when the schema changed")).isEqualTo("true");
        assertThat(evidence(run, "coupling", "Contract access broke when the schema changed")).isEqualTo("false");
        assertThat(step(run, "direct-read").get("sql").toString()).contains("shared-db:");
    }

    // ---------------------------------------------------------------------------------------------------- outbox

    @Test
    void commitThenPublishLosesTheEventWhenTheProcessFailsInBetween() throws Exception {
        JsonNode run = run("outbox-dual-write-gap");
        assertThat(step(run, "the-gap").get("provenance").asText()).isEqualTo("CONCEPTUAL");
        assertThat(step(run, "the-gap").get("explanation").asText()).contains("NOT a transactional outbox");
        assertThat(evidenceStartingWith(run, "dual-write-works", "Notifications sent")).isEqualTo("1");
        assertThat(evidence(run, "dual-write-crash", "Caller sees")).isEqualTo("TECHNICAL_ERROR (IllegalStateException)");
        assertThat(evidenceStartingWith(run, "dual-write-crash", "Orders PLACED")).as("the second order IS committed").isEqualTo("2");
        assertThat(evidence(run, "dual-write-crash", "Events published")).as("but its event never was").isEqualTo("1");
        assertThat(evidence(run, "consequence", "Orders nobody was told about")).isEqualTo("1");
        assertThat(evidence(run, "consequence", "Notifications that will never be sent")).isEqualTo("1");
        assertThat(run.toString()).doesNotContain("injected crash between");
    }

    @Test
    void theOrderAndItsOutboxRowCommitTogetherAndRollBackTogether() throws Exception {
        JsonNode run = run("outbox-commit-rollback");
        assertThat(evidence(run, "committed-together", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "committed-together", "Orders PLACED")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "committed-together", "Outbox rows PENDING")).isEqualTo("1");
        assertThat(evidence(run, "committed-together", "Events published")).as("nothing is published by the transaction itself").isEqualTo("0");
        assertThat(evidence(run, "committed-together", "Other database calls observed")).contains("order: connection obtained").contains("setAutoCommit(false)").contains("commit()");
        assertThat(step(run, "committed-together").get("sql").toString()).contains("micro_lab_orders").contains("micro_lab_outbox");

        assertThat(evidence(run, "rolled-back-together", "Caller sees")).isEqualTo("TECHNICAL_ERROR (IllegalStateException)");
        assertThat(evidenceStartingWith(run, "rolled-back-together", "Orders PLACED")).as("the failed order's row is gone").isEqualTo("1");
        assertThat(evidenceStartingWith(run, "rolled-back-together", "Outbox rows PENDING")).as("and so is its outbox row").isEqualTo("1");
        assertThat(evidence(run, "rolled-back-together", "Other database calls observed")).contains("rollback()");
        assertThat(step(run, "rolled-back-together").get("explanation").asText()).contains("IS a database rollback");
    }

    @Test
    void thePublicationHappensLaterByARelayAndTheConsumerIsASeparateStep() throws Exception {
        JsonNode run = run("outbox-commit-rollback");
        assertThat(evidence(run, "relay", "Events published")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "relay", "Outbox rows PENDING")).isEqualTo("0");
        assertThat(evidenceStartingWith(run, "relay", "Outbox rows PUBLISHED")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "relay", "Notifications sent")).as("the consumer has not run yet").isEqualTo("0");
        assertThat(evidenceStartingWith(run, "consume", "Notifications sent")).isEqualTo("1");
        assertThat(evidence(run, "never-out-of-step", "After the commit: orders = outbox rows")).isEqualTo("true");
        assertThat(evidence(run, "never-out-of-step", "After the rollback: orders = outbox rows")).isEqualTo("true");
        assertThat(evidence(run, "never-out-of-step", "Delivery guarantee")).contains("exactly-once is not claimed");
    }

    @Test
    void aRelayCrashRepublishesTheSameMessageIdAndAnIdempotentConsumerActsOnce() throws Exception {
        JsonNode run = run("outbox-duplicate-delivery");
        assertThat(evidence(run, "relay-crashes", "Caller sees")).isEqualTo("TECHNICAL_ERROR (IllegalStateException)");
        assertThat(evidence(run, "relay-crashes", "Events published")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "relay-crashes", "Outbox rows PENDING")).as("not marked: the relay died first").isEqualTo("1");
        assertThat(evidence(run, "relay-again", "Events published")).as("published a second time").isEqualTo("2");
        assertThat(evidenceStartingWith(run, "relay-again", "Outbox rows PUBLISHED")).isEqualTo("1");
        assertThat(evidence(run, "deliver", "Deliveries")).isEqualTo("2");
        assertThat(evidence(run, "deliver", "Distinct message ids")).as("the same id both times").isEqualTo("1");
        assertThat(evidenceStartingWith(run, "deliver", "Notifications sent")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "deliver", "Processed message ids")).isEqualTo("1");
        assertThat(evidence(run, "at-least-once", "Times one outbox row was published")).isEqualTo("2");
        assertThat(evidence(run, "at-least-once", "The side effect happened once")).isEqualTo("true");
        assertThat(evidence(run, "at-least-once", "Delivery guarantee")).contains("exactly-once delivery is not provided");
    }

    @Test
    void aNaiveConsumerRepeatsTheEffectOfAnOutboxDuplicate() throws Exception {
        JsonNode run = run(student, "outbox-duplicate-delivery", "{\"consumer\":\"naive\"}");
        assertThat(evidence(run, "deliver", "Deliveries")).isEqualTo("2");
        assertThat(evidenceStartingWith(run, "deliver", "Notifications sent")).isEqualTo("2");
        assertThat(evidenceStartingWith(run, "deliver", "Processed message ids")).isEqualTo("0");
        assertThat(evidence(run, "at-least-once", "The side effect happened once")).isEqualTo("false");
    }
}
