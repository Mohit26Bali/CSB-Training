package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.trace.CodeRefIds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every experiment of the catalogue, run for real: honest provenance, valid diagram ids, no secrets, deterministic, isolated, cleaned up. */
class MicroCatalogueIntegrationTest extends AbstractMicroLabTest {

    static final Set<String> TRIGGER_LABELS = Set.of("user", "product", "order", "inventory", "payment", "notification", "registry", "gateway",
            "monolith", "shared-db", "lab-inspector");
    static final List<String> FORBIDDEN = List.of("localhost", "password", "mohit", "authorization", "bearer", "jwt", "secret", "jdbc:mysql");

    List<JsonNode> catalogue() throws Exception {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode e : json(getAs("/api/lab/microservices/experiments", student).andExpect(status().isOk()))) {
            out.add(e);
        }
        return out;
    }

    @Test
    void theCatalogueHasThirtySevenExperimentsAcrossAllEightTopics() throws Exception {
        Map<String, Integer> perTopic = new HashMap<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode e : catalogue()) {
            perTopic.merge(e.get("topic").asText(), 1, Integer::sum);
            assertThat(ids.add(e.get("id").asText())).as("unique id").isTrue();
            assertThat(e.get("question").asText()).isNotBlank();
            assertThat(e.get("claim").asText()).isNotBlank();
        }
        assertThat(ids).hasSize(37);
        assertThat(perTopic).containsEntry("monolith", 4).containsEntry("communication", 6).containsEntry("gateway", 4).containsEntry("discovery", 4)
                .containsEntry("database-per-service", 4).containsEntry("saga", 6).containsEntry("resilience", 5).containsEntry("observability", 4);
    }

    @Test
    void everyExperimentIsStructurallyHonest() throws Exception {
        Set<String> sawChannel = new HashSet<>();
        for (JsonNode meta : catalogue()) {
            String id = meta.get("id").asText();
            JsonNode run = run(student, id, "{}");
            assertThat(run.get("state").asText()).as(id).isEqualTo("COMPLETED");
            JsonNode result = run.get("result");
            assertThat(result.get("errorCode").isNull() || !result.has("errorCode")).as(id + " error").isTrue();
            assertThat(result.get("environment").asText()).contains("no message broker").contains("no network");

            Set<String> stepIds = new HashSet<>();
            boolean captured = false;
            for (JsonNode s : result.get("steps")) {
                String sid = s.get("id").asText();
                assertThat(stepIds.add(sid)).as(id + "/" + sid + " unique").isTrue();
                String provenance = s.get("provenance").asText();
                assertThat(provenance).isIn("CAPTURED", "DERIVED", "CONCEPTUAL");
                captured |= provenance.equals("CAPTURED");
                if (!provenance.equals("CAPTURED")) {
                    assertThat(s.get("sql")).as(id + "/" + sid + " only CAPTURED steps carry SQL").isEmpty();
                }
                if (provenance.equals("CONCEPTUAL")) {
                    assertThat(s.get("evidence")).as(id + "/" + sid + " a concept observes nothing").isEmpty();
                }
                if (provenance.equals("DERIVED")) {
                    assertThat(s.get("evidence")).as(id + "/" + sid + " derived from evidence").isNotEmpty();
                }
                assertThat(s.get("explanation").asText().length()).as(id + "/" + sid).isGreaterThan(40);
                for (JsonNode n : s.get("nodes")) {
                    assertThat(DIAGRAM_NODES).as(id + "/" + sid + " node").contains(n.asText());
                }
                if (!s.get("edgeFrom").isNull()) {
                    assertThat(DIAGRAM_NODES).contains(s.get("edgeFrom").asText());
                    assertThat(DIAGRAM_NODES).contains(s.get("edgeTo").asText());
                } else {
                    assertThat(s.get("edgeTo").isNull()).isTrue();
                }
                if (!s.get("codeRefId").isNull()) {
                    assertThat(CodeRefIds.ALL).as(id + "/" + sid + " code reference").contains(s.get("codeRefId").asText());
                }
                for (JsonNode q : s.get("sql")) {
                    assertThat(q.get("phase").asText()).isEqualTo("SERVICE DB");
                    assertThat(TRIGGER_LABELS).contains(q.get("trigger").asText().split(":", 2)[0]);
                    assertThat(q.get("sql").asText()).as(id + " SQL holds placeholders, not values").doesNotContain("'").doesNotContainPattern("[0-9a-f]{32}");
                }
                for (JsonNode e : s.get("evidence")) {
                    if (e.get("label").asText().equals("Event mechanism")) {
                        sawChannel.add(id);
                        assertThat(e.get("value").asText()).as(id).contains("SIMULATED").contains("not Kafka or RabbitMQ");
                    }
                }
            }
            assertThat(result.get("steps").size()).as(id).isGreaterThanOrEqualTo(4);
            assertThat(captured).as(id + " has at least one CAPTURED step").isTrue();
            assertThat(stepIds).as(id).containsAnyOf("what-this-cannot-show", "what-the-channel-is", "what-fixes-it", "contracts-not-tables", "how-to-split",
                    "sync-trade-offs", "retry-warning", "real-brokers", "exactly-once", "real-gateways", "defence-in-depth", "limiter-caveats", "single-point",
                    "static-vs-dynamic", "real-balancers", "health-checks", "client-retries", "choosing", "reducing-coupling", "logical-vs-physical",
                    "physical-enforcement", "living-without-fks", "other-costs", "the-gap", "outbox-costs", "effect-not-delivery", "orchestration-trade-offs", "what-this-shows",
                    "two-phase-commit", "why-bounded", "tuning", "keys-in-practice", "controlling-retries", "sizing", "not-opentelemetry", "traces-vs-logs", "propagation",
                    "span-links");

            JsonNode spans = result.get("spans");
            Set<String> spanIds = new HashSet<>();
            for (JsonNode sp : spans) {
                spanIds.add(sp.get("spanId").asText());
            }
            for (JsonNode sp : spans) {
                if (!sp.get("parentSpanId").isNull()) {
                    assertThat(spanIds).as(id + " parent span exists").contains(sp.get("parentSpanId").asText());
                }
                assertThat(sp.get("kind").asText()).isIn("SYNC", "EVENT");
            }
            assertThat(result.get("sqlCount").asInt()).isEqualTo(result.get("sql").size());
            for (String forbidden : FORBIDDEN) {
                assertThat(run.toString().toLowerCase()).as(id + " must not contain " + forbidden).doesNotContain(forbidden);
            }
            // the database port as a reference (host:3306, port=3306, a URL), not the digits as such: a timestamp can contain them
            assertThat(com.springlab.support.PortReferences.mentions(run.toString())).as(id + " must not refer to the database port").isFalse();
            assertThat(labRows()).as(id + " leaves no row").isZero();
            assertThat(result.get("cleanupRowsRemoved").asInt()).as(id).isGreaterThan(0);
        }
        assertThat(sawChannel).as("experiments using the channel label it SIMULATED").contains("comm-async-event", "comm-async-duplicate", "comm-sync-vs-async", "mono-coupling");
    }

    @Test
    void runningAnExperimentTwiceGivesIdenticalEvidenceBecauseNothingDependsOnTiming() throws Exception {
        for (JsonNode meta : catalogue()) {
            String id = meta.get("id").asText();
            assertThat(allEvidence(run(student, id, "{}"))).as(id).isEqualTo(allEvidence(run(student, id, "{}")));
        }
    }

    @Test
    void applicationTablesAreNeverTouched() throws Exception {
        long users = jdbc.queryForObject("select count(*) from users", Long.class);
        long questions = jdbc.queryForObject("select count(*) from questions", Long.class);
        long topics = jdbc.queryForObject("select count(*) from topics", Long.class);
        long attempts = jdbc.queryForObject("select count(*) from attempts", Long.class);
        for (JsonNode meta : catalogue()) {
            run(student, meta.get("id").asText(), "{}");
        }
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("select count(*) from questions", Long.class)).isEqualTo(questions);
        assertThat(jdbc.queryForObject("select count(*) from topics", Long.class)).isEqualTo(topics);
        assertThat(jdbc.queryForObject("select count(*) from attempts", Long.class)).isEqualTo(attempts);
    }

    @Test
    void sixUsersRunningDifferentExperimentsAtOnceGetTheirSoloResults() throws Exception {
        List<String> ids = List.of("mono-order-workflow", "comm-async-duplicate", "gw-rate-limit", "disc-stale-registration", "comm-sync-vs-async",
                "mono-boundaries");
        List<List<String>> solo = new ArrayList<>();
        for (String id : ids) {
            solo.add(allEvidence(run(student, id, "{}")));
        }
        List<User> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(newUser("micro_lab_mix" + i + "_" + System.nanoTime(), Role.STUDENT));
        }
        for (int round = 0; round < 3; round++) {
            ExecutorService pool = Executors.newFixedThreadPool(6);
            try {
                List<Future<JsonNode>> futures = new ArrayList<>();
                for (int i = 0; i < 6; i++) {
                    User u = six.get(i);
                    String id = ids.get(i);
                    futures.add(pool.submit(() -> run(u, id, "{}")));
                }
                for (int i = 0; i < 6; i++) {
                    assertThat(allEvidence(futures.get(i).get())).as("round " + round + " " + ids.get(i)).isEqualTo(solo.get(i));
                }
            } finally {
                pool.shutdown();
            }
        }
        assertThat(labRows()).isZero();
    }

    @Test
    void sixUsersRunningTheSameStatefulExperimentDoNotShareRegistryOrLimiterState() throws Exception {
        List<String> solo = allEvidence(run(student, "gw-rate-limit", "{}"));
        List<User> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(newUser("micro_lab_same" + i + "_" + System.nanoTime(), Role.STUDENT));
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (User u : six) {
                futures.add(pool.submit(() -> run(u, "gw-rate-limit", "{}")));
            }
            for (Future<JsonNode> f : futures) {
                assertThat(allEvidence(f.get())).isEqualTo(solo);
            }
        } finally {
            pool.shutdown();
        }
    }
}
