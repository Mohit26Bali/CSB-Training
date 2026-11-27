package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.trace.CodeRefIds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The foundation experiment against real MySQL: real service calls, real local transactions, honest provenance and cleanup. */
class FailureSemanticsIntegrationTest extends AbstractMicroLabTest {

    static final String ID = "comm-failure-semantics";
    static final Set<String> NODES = DIAGRAM_NODES;

    @Test
    void theCatalogueListsTheExperimentWithItsWhatIfOptions() throws Exception {
        JsonNode catalogue = json(getAs("/api/lab/microservices/experiments", student).andExpect(status().isOk()));
        JsonNode e = null;
        for (JsonNode x : catalogue) {
            if (x.get("id").asText().equals(ID)) {
                e = x;
            }
        }
        assertThat(e).isNotNull();
        assertThat(e.get("topic").asText()).isEqualTo("communication");
        assertThat(e.get("whatIfs")).hasSize(2);
        assertThat(e.get("whatIfs").get(0).get("min").asInt()).isEqualTo(50);
    }

    @Test
    void metaAndTopicsDescribeTheEightServicesAndTopics() throws Exception {
        JsonNode meta = json(getAs("/api/lab/microservices/meta", student).andExpect(status().isOk()));
        assertThat(meta.get("services")).hasSize(8);
        assertThat(meta.get("environment").asText()).contains("one Java process").contains("no network");
        JsonNode inventory = null;
        for (JsonNode s : meta.get("services")) {
            if (s.get("id").asText().equals("inventory")) {
                inventory = s;
            }
        }
        assertThat(inventory.get("ownedTables")).extracting(JsonNode::asText).containsExactlyInAnyOrder("micro_lab_stock", "micro_lab_reservations");
        assertThat(json(getAs("/api/lab/microservices/topics", student).andExpect(status().isOk()))).hasSize(8);
    }

    @Test
    void everyOutcomeIsObservedForRealAndEachMeansSomethingDifferent() throws Exception {
        JsonNode run = run(ID);
        assertThat(run.get("state").asText()).isEqualTo("COMPLETED");

        assertThat(evidence(run, "success", "Caller sees")).isEqualTo("SUCCESS");
        assertThat(evidenceStartingWith(run, "success", "Stock")).isEqualTo("3");

        assertThat(evidence(run, "business-failure", "Caller sees")).isEqualTo("BUSINESS_FAILURE (OUT_OF_STOCK)");
        assertThat(evidence(run, "business-failure", "Handler ran (known only to the lab)")).isEqualTo("true");
        assertThat(evidenceStartingWith(run, "business-failure", "Stock")).as("a refusal changes nothing").isEqualTo("3");

        assertThat(evidence(run, "unavailable", "Caller sees")).isEqualTo("UNAVAILABLE (CONNECTION_REFUSED)");
        assertThat(evidence(run, "unavailable", "Handler ran (known only to the lab)")).isEqualTo("false");
        assertThat(evidenceStartingWith(run, "unavailable", "Stock")).isEqualTo("3");

        assertThat(evidence(run, "timeout-work-done", "Caller sees")).isEqualTo("TIMEOUT (RESPONSE_TOO_SLOW)");
        assertThat(evidence(run, "timeout-work-done", "Handler ran (known only to the lab)")).isEqualTo("true");
        assertThat(evidenceStartingWith(run, "timeout-work-done", "Stock")).as("the caller saw a timeout but the stock WAS reserved").isEqualTo("2");
        assertThat(evidenceStartingWith(run, "timeout-work-done", "Reservations")).isEqualTo("2");

        assertThat(evidence(run, "timeout-request-lost", "Caller sees")).as("identical to the caller").isEqualTo("TIMEOUT (REQUEST_LOST)");
        assertThat(evidence(run, "timeout-request-lost", "Handler ran (known only to the lab)")).isEqualTo("false");
        assertThat(evidenceStartingWith(run, "timeout-request-lost", "Stock")).isEqualTo("2");

        assertThat(evidence(run, "technical-error", "Caller sees")).isEqualTo("TECHNICAL_ERROR (SimulatedFault)");
        assertThat(evidence(run, "technical-error", "Handler ran (known only to the lab)")).isEqualTo("false");
        assertThat(evidenceStartingWith(run, "technical-error", "Stock")).isEqualTo("2");
    }

    @Test
    void theBulkheadKeepsOneSaturatedServiceFromStarvingAnother() throws Exception {
        JsonNode separate = run(student, ID, "{\"bulkheadPolicy\":\"per-service\"}");
        assertThat(evidence(separate, "bulkhead", "Inventory call")).isEqualTo("BULKHEAD_REJECTED");
        assertThat(evidence(separate, "bulkhead", "Product call")).isEqualTo("SUCCESS");
        assertThat(evidence(separate, "bulkhead", "Permits left for Inventory after the held requests finish")).isEqualTo("2");

        JsonNode pooled = run(student, ID, "{\"bulkheadPolicy\":\"shared\"}");
        assertThat(evidence(pooled, "bulkhead", "Inventory call")).isEqualTo("BULKHEAD_REJECTED");
        assertThat(evidence(pooled, "bulkhead", "Product call")).as("one shared pool: Product is starved by Inventory's saturation")
                .isEqualTo("BULKHEAD_REJECTED");
    }

    @Test
    void theDerivedStepCountsWhatWasRecordedAndFindsTheTimeoutThatHidWork() throws Exception {
        JsonNode run = run(ID);
        assertThat(evidence(run, "what-the-caller-knows", "Timeouts where the work was nevertheless done")).isEqualTo("1");
        assertThat(evidenceStartingWith(run, "what-the-caller-knows", "TIMEOUT")).contains("unknown");
        assertThat(evidenceStartingWith(run, "what-the-caller-knows", "BULKHEAD_REJECTED")).contains("never reached");
    }

    @Test
    void whatIfTimeoutChangesTheOutcomeOfAFastCallOnly() throws Exception {
        JsonNode tight = run(student, ID, "{\"callTimeoutMillis\":50}");
        assertThat(evidence(tight, "success", "Caller sees")).as("18 ms < 50 ms").isEqualTo("SUCCESS");
        assertThat(evidence(tight, "timeout-work-done", "Caller sees")).startsWith("TIMEOUT");
        JsonNode loose = run(student, ID, "{\"callTimeoutMillis\":500}");
        assertThat(evidence(loose, "timeout-work-done", "Caller sees")).startsWith("TIMEOUT");
        assertThat(loose.get("whatIf").get("callTimeoutMillis").asText()).isEqualTo("500");
    }

    @Test
    void provenanceIsAssignedPerStepAndSqlAppearsOnlyOnCapturedSteps() throws Exception {
        JsonNode run = run(ID);
        List<String> provenance = new ArrayList<>();
        for (JsonNode s : run.get("result").get("steps")) {
            provenance.add(s.get("id").asText() + ":" + s.get("provenance").asText());
            if (!s.get("provenance").asText().equals("CAPTURED")) {
                assertThat(s.get("sql")).as(s.get("id").asText()).isEmpty();
            }
        }
        assertThat(provenance).contains("success:CAPTURED", "bulkhead:CAPTURED", "what-the-caller-knows:DERIVED", "what-this-cannot-show:CONCEPTUAL");
        assertThat(step(run, "success").get("sql")).isNotEmpty();
        assertThat(step(run, "unavailable").get("sql")).as("the service was never reached, so only the lab inspector's read appears").hasSize(2);
        for (JsonNode s : step(run, "unavailable").get("sql")) {
            assertThat(s.get("trigger").asText()).startsWith("lab-inspector");
        }
        for (JsonNode s : run.get("result").get("sql")) {
            assertThat(s.get("phase").asText()).isEqualTo("SERVICE DB");
            assertThat(s.get("sql").asText()).doesNotContain("'").doesNotContainPattern("[0-9a-f]{32}");
        }
        assertThat(run.get("result").get("sqlCount").asInt()).isEqualTo(run.get("result").get("sql").size());
    }

    @Test
    void sqlIsAttributedToTheServiceThatIssuedIt() throws Exception {
        JsonNode run = run(ID);
        String triggers = step(run, "success").get("sql").toString();
        assertThat(triggers).contains("inventory:").contains("micro_lab_stock").contains("micro_lab_reservations");
        assertThat(step(run, "seed").get("sql").toString()).contains("product:").contains("inventory:");
        assertThat(evidenceStartingWith(run, "success", "Other database calls")).contains("setAutoCommit").contains("commit");
    }

    @Test
    void stepsOnlyUseTheDiagramsNodesAndEdges() throws Exception {
        JsonNode run = run(ID);
        for (JsonNode s : run.get("result").get("steps")) {
            for (JsonNode n : s.get("nodes")) {
                assertThat(NODES).as(s.get("id").asText()).contains(n.asText());
            }
            if (!s.get("edgeFrom").isNull()) {
                assertThat(NODES).contains(s.get("edgeFrom").asText());
                assertThat(NODES).contains(s.get("edgeTo").asText());
            }
        }
    }

    @Test
    void spansFormValidTreesWithSimulatedAndMeasuredTimingsKeptApart() throws Exception {
        JsonNode run = run(ID);
        JsonNode spans = run.get("result").get("spans");
        assertThat(spans.size()).isGreaterThanOrEqualTo(9);
        Set<String> ids = new java.util.HashSet<>();
        for (JsonNode s : spans) {
            assertThat(ids.add(s.get("spanId").asText())).as("unique span id").isTrue();
            assertThat(s.get("simulatedMillis").asLong()).isGreaterThanOrEqualTo(0);
            assertThat(s.get("measuredMicros").asLong()).isGreaterThanOrEqualTo(0);
            assertThat(s.get("correlationId").asText()).startsWith("corr-");
            assertThat(s.get("correlationPropagated").asBoolean()).isTrue();
        }
        JsonNode tooSlow = null;
        for (JsonNode s : spans) {
            if (s.get("status").asText().equals("TIMEOUT") && s.get("operation").asText().equals("reserve")) {
                tooSlow = s;
                break;
            }
        }
        assertThat(tooSlow.get("simulatedMillis").asInt()).as("a timed-out span lasts as long as the caller's timeout").isEqualTo(100);
        assertThat(run.get("result").get("calls").size()).isEqualTo(spans.size());
    }

    @Test
    void nothingSensitiveAppearsInTheResponse() throws Exception {
        String text = run(ID).toString().toLowerCase();
        for (String forbidden : List.of("localhost", "password", "mohit", "authorization", "bearer", "jwt", "secret", "jdbc:mysql")) {
            assertThat(text).as(forbidden).doesNotContain(forbidden);
        }
        assertThat(com.springlab.support.PortReferences.mentions(text)).as("a reference to the database port").isFalse();
    }

    @Test
    void theRunRemovesItsOwnRowsAndLeavesApplicationDataAlone() throws Exception {
        long topics = jdbc.queryForObject("select count(*) from topics", Long.class);
        long questions = jdbc.queryForObject("select count(*) from questions", Long.class);
        long attempts = jdbc.queryForObject("select count(*) from attempts", Long.class);
        JsonNode run = run(ID);
        assertThat(run.get("result").get("cleanupRowsRemoved").asInt()).as("product 1 + stock 1 + two reservations").isEqualTo(4);
        assertThat(labRows()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from topics", Long.class)).isEqualTo(topics);
        assertThat(jdbc.queryForObject("select count(*) from questions", Long.class)).isEqualTo(questions);
        assertThat(jdbc.queryForObject("select count(*) from attempts", Long.class)).isEqualTo(attempts);
    }

    @Test
    void codeReferencesOfStepsAreRealAndSomeStepsHaveOne() throws Exception {
        JsonNode run = run(ID);
        int withRef = 0;
        for (JsonNode s : run.get("result").get("steps")) {
            if (!s.get("codeRefId").isNull()) {
                withRef++;
                assertThat(CodeRefIds.ALL).as(s.get("id").asText()).contains(s.get("codeRefId").asText());
            }
        }
        assertThat(withRef).isGreaterThanOrEqualTo(4);
    }

    @Test
    void teachersAndAdminsMayRunToo() throws Exception {
        User teacher = newUser("micro_lab_teacher_" + System.nanoTime(), Role.TEACHER);
        User admin = newUser("micro_lab_admin_" + System.nanoTime(), Role.ADMIN);
        assertThat(run(teacher, ID, "{}").get("state").asText()).isEqualTo("COMPLETED");
        assertThat(run(admin, ID, "{}").get("state").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void sixUsersRunningAtOnceGetTheSameResultsAsOneUserAlone() throws Exception {
        JsonNode baseline = run(ID);
        List<String> expected = signature(baseline);
        List<User> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(newUser("micro_lab_c" + i + "_" + System.nanoTime(), Role.STUDENT));
        }
        for (int round = 0; round < 2; round++) {
            ExecutorService pool = Executors.newFixedThreadPool(6);
            try {
                List<Future<JsonNode>> futures = new ArrayList<>();
                for (User u : six) {
                    futures.add(pool.submit(() -> run(u, ID, "{}")));
                }
                for (Future<JsonNode> f : futures) {
                    assertThat(signature(f.get())).isEqualTo(expected);
                }
            } finally {
                pool.shutdown();
            }
        }
        assertThat(labRows()).isZero();
    }

    /** The caller-visible outcomes of every step: identical for every run if runs do not interfere. */
    private static List<String> signature(JsonNode run) {
        List<String> out = new ArrayList<>();
        for (JsonNode s : run.get("result").get("steps")) {
            for (JsonNode e : s.get("evidence")) {
                String label = e.get("label").asText();
                if (label.equals("Caller sees") || label.startsWith("Stock") || label.startsWith("Reservations") || label.equals("Inventory call")
                        || label.equals("Product call")) {
                    out.add(s.get("id").asText() + "/" + label + "=" + e.get("value").asText());
                }
            }
        }
        return out;
    }
}
