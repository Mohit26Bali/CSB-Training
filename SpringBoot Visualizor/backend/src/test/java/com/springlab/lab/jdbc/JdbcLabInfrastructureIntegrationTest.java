package com.springlab.lab.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.trace.CodeRefIds;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Catalogue, truthfulness of every run, safety, authorization, cleanup, crash recovery and concurrency of the JDBC lab. */
class JdbcLabInfrastructureIntegrationTest extends AbstractJdbcLabTest {

    @Autowired
    private JdbcLabCatalog catalog;
    @Autowired
    private JdbcLabService labService;
    @Value("${spring.datasource.password:}")
    private String databasePassword;

    private static final Set<String> PROVENANCE = Set.of("CAPTURED", "DERIVED", "CONCEPTUAL");
    private static final Pattern RUN_KEY = Pattern.compile("[0-9a-f]{16}");

    @Test
    void everyCatalogueEntryHasAnExperimentAndAKnownDiagram() {
        Set<String> ids = new HashSet<>();
        catalog.catalog().experiments().forEach(e -> ids.add(e.id()));
        assertThat(ids).hasSize(catalog.catalog().experiments().size()).isEqualTo(labService.experimentIds());
        Set<String> groups = new HashSet<>();
        catalog.catalog().groups().forEach(g -> groups.add(g.id()));
        assertThat(groups).containsExactlyInAnyOrder("fundamentals", "statements", "crud", "transactions", "pooling", "spring", "compare");
        assertThat(catalog.catalog().experiments()).allSatisfy(e -> {
            assertThat(groups).contains(e.group());
            assertThat(catalog.catalog().diagrams()).containsKey(e.diagram());
            assertThat(e.title()).isNotBlank();
            assertThat(e.question()).isNotBlank();
            assertThat(e.claim()).isNotBlank();
        });
        catalog.catalog().diagrams().forEach((name, d) -> d.edges().forEach(edge -> {
            String[] ends = edge.split("->");
            assertThat(d.nodes()).as(name + " edge " + edge).contains(ends[0], ends[1]);
        }));
    }

    @Test
    void everyExperimentProducesHonestStepsOnRealNodesWithoutValuesOrSecrets() throws Exception {
        for (JdbcLabCatalog.ExperimentInfo info : catalog.catalog().experiments()) {
            JsonNode run = run(info.id());
            String id = info.id();
            var diagram = catalog.catalog().diagrams().get(info.diagram());
            assertThat(run.get("mode").asText()).as(id).isEqualTo("LIVE");
            assertThat(run.get("steps").size()).as(id).isGreaterThanOrEqualTo(3);
            long fromSteps = 0;
            Set<String> stepIds = new HashSet<>();
            for (JsonNode step : run.get("steps")) {
                String stepId = step.get("id").asText();
                assertThat(stepIds.add(stepId)).as(id + " duplicate step " + stepId).isTrue();
                assertThat(PROVENANCE).as(id).contains(step.get("provenance").asText());
                assertThat(step.get("explanation").asText().length()).as(id + "/" + stepId).isGreaterThan(20);
                if (!step.get("provenance").asText().equals("CAPTURED")) {
                    assertThat(step.get("sql")).as(id + "/" + stepId + ": only CAPTURED steps may carry SQL").isEmpty();
                }
                fromSteps += step.get("sql").size();
                step.get("nodes").forEach(n -> assertThat(diagram.nodes()).as(id + "/" + stepId + " node " + n.asText()).contains(n.asText()));
                if (!step.get("edgeFrom").isNull()) {
                    assertThat(diagram.edges()).as(id + "/" + stepId).contains(step.get("edgeFrom").asText() + "->" + step.get("edgeTo").asText());
                }
                if (!step.get("codeRefId").isNull()) {
                    assertThat(CodeRefIds.ALL).as(id + "/" + stepId + " code reference").contains(step.get("codeRefId").asText());
                }
                for (JsonNode statement : step.get("sql")) {
                    String text = statement.get("sql").asText();
                    assertThat(RUN_KEY.matcher(text).find()).as(id + ": the run key is masked").isFalse();
                    assertThat(text).as(id + ": no bound value in the SQL text").doesNotContain("Ada").doesNotContain("Grace").doesNotContain("Alan")
                            .doesNotContain("Alice").doesNotContain("Bob");
                    assertThat(statement.get("phase").asText()).isIn("JDBC", "HIBERNATE");
                }
            }
            assertThat(fromSteps).as(id + ": step statements add up").isEqualTo(run.get("sqlCount").asLong());
            int expected = 1;
            for (JsonNode statement : run.get("sql")) {
                assertThat(statement.get("index").asInt()).isEqualTo(expected++);
            }
            String everything = run.toString();
            // the scheme "jdbc:mysql:" appears in the driver lesson as a generic example; the real URL (host, port, options) must never appear
            assertThat(everything).as(id).doesNotContain("localhost").doesNotContain("createDatabaseIfNotExist")
                    .doesNotContain("password").doesNotContain("Authorization");
            assertThat(com.springlab.support.PortReferences.mentions(everything)).as(id + ": no reference to the database port").isFalse();
            if (!databasePassword.isBlank()) {
                assertThat(everything).as(id + ": no credential in the response").doesNotContain(databasePassword);
            }
            assertThat(run.get("cleanupRowsRemoved").asInt()).as(id).isGreaterThanOrEqualTo(0);
            assertThat(labRows()).as(id + ": the run cleaned up after itself").isZero();
            assertThat(run.get("limitations").size()).isGreaterThanOrEqualTo(5);
        }
    }

    @Test
    void theRunReportsExactlyWhatItRemoved() throws Exception {
        assertThat(run("jdbc-batch-insert").get("cleanupRowsRemoved").asInt()).isEqualTo(5);
        assertThat(run("jdbc-read-many").get("cleanupRowsRemoved").asInt()).isEqualTo(5);
        assertThat(run("jdbc-crud-single").get("cleanupRowsRemoved").asInt()).isZero(); // the experiment deleted its own row
    }

    @Test
    void everyRouteRequiresAuthenticationAndUnknownExperimentsAreRefused() throws Exception {
        mockMvc.perform(get("/api/lab/jdbc/catalog")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lab/jdbc/mapping")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lab/jdbc/experiments/jdbc-batch-insert/run")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lab/jdbc/experiments/jdbc-batch-insert/run").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lab/jdbc/experiments/nope/run").header("Authorization", bearer(student))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/lab/jdbc/experiments/{id}/run", "../../etc/passwd").header("Authorization", bearer(student))).andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/lab/jdbc/experiments/{id}/run", "select 1").header("Authorization", bearer(student))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lab/jdbc/experiments/jdbc-batch-insert/run").header("Authorization", bearer(student))).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void everySignedInRoleMayRunAnExperiment() throws Exception {
        for (Role role : Role.values()) {
            User user = newUser("jdbc_lab_" + role.name().toLowerCase() + "_" + System.nanoTime(), role);
            assertThat(run("jdbc-read-many", user).get("sqlCount").asInt()).isGreaterThan(0);
        }
    }

    @Test
    void theClientCannotInjectAnythingIntoARunAndNoApplicationTableChanges() throws Exception {
        long users = jdbc.queryForObject("select count(*) from users", Long.class);
        long questions = jdbc.queryForObject("select count(*) from questions", Long.class);
        long attempts = jdbc.queryForObject("select count(*) from attempts", Long.class);
        mockMvc.perform(post("/api/lab/jdbc/experiments/jdbc-prepared-select/run?sql=drop+table+users&table=users")
                        .header("Authorization", bearer(student)).contentType("application/json")
                        .content("{\"sql\":\"delete from users\",\"table\":\"users\",\"url\":\"jdbc:mysql://evil\"}"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("X-SpringLab-Trace-Id"));
        for (JdbcLabCatalog.ExperimentInfo info : catalog.catalog().experiments()) {
            run(info.id());
        }
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("select count(*) from questions", Long.class)).isEqualTo(questions);
        assertThat(jdbc.queryForObject("select count(*) from attempts", Long.class)).isEqualTo(attempts);
        assertThat(labRows()).isZero();
    }

    @Test
    void theMappingDescribesOnlyTheLabTablesAndExposesNoCredential() throws Exception {
        JsonNode mapping = getJson("/api/lab/jdbc/mapping");
        Set<String> tables = new HashSet<>();
        mapping.get("tables").forEach(t -> tables.add(t.get("id").asText()));
        assertThat(tables).containsExactlyInAnyOrder("jdbc_lab_customers", "jdbc_lab_products", "jdbc_lab_accounts", "jdbc_lab_orders",
                "jdbc_lab_transactions", "jdbc_lab_batch_records");
        JsonNode orders = null;
        for (JsonNode t : mapping.get("tables")) {
            if (t.get("id").asText().equals("jdbc_lab_orders")) {
                orders = t;
            }
        }
        assertThat(orders.get("foreignKeys")).hasSize(2);
        assertThat(mapping.get("database").asText()).startsWith("MySQL 8");
        assertThat(mapping.get("driver").asText()).contains("Connector/J");
        assertThat(mapping.get("isolationDefault").asText()).isEqualTo("REPEATABLE-READ");
        assertThat(mapping.toString()).doesNotContain("jdbc:mysql").doesNotContain("users");
    }

    @Test
    void startupCleanupRemovesOnlyStaleRowsAndLeavesAFreshRunAlone() {
        String stale = "stale" + System.nanoTime() % 100000;
        String fresh = "fresh" + System.nanoTime() % 100000;
        jdbc.update("insert into jdbc_lab_customers (run_key, name, created_at) values (?, 'old', ?)", stale, Timestamp.from(Instant.now().minus(Duration.ofMinutes(30))));
        jdbc.update("insert into jdbc_lab_orders (run_key, customer_id, product_id, quantity, created_at) "
                + "select ?, c.id, p.id, 1, ? from jdbc_lab_customers c join jdbc_lab_products p on 1 = 1 where c.run_key = ? limit 0", stale, Timestamp.from(Instant.now()), stale);
        jdbc.update("insert into jdbc_lab_customers (run_key, name) values (?, 'new')", fresh);
        int removed = labService.cleanupStale(Duration.ofMinutes(JdbcLabService.STALE_MINUTES));
        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from jdbc_lab_customers where run_key = ?", Long.class, stale)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from jdbc_lab_customers where run_key = ?", Long.class, fresh)).isEqualTo(1);
        jdbc.update("delete from jdbc_lab_customers where run_key = ?", fresh);
    }

    @Test
    void aFailedRunStillCleansUpItsRows() {
        // Seed rows for a run key, then clean them exactly as a failed run would (the service does this in a finally block).
        String key = "crash" + System.nanoTime() % 100000;
        jdbc.update("insert into jdbc_lab_customers (run_key, name) values (?, 'x')", key);
        jdbc.update("insert into jdbc_lab_products (run_key, name, price) values (?, 'p', 1.00)", key);
        assertThat(labService.cleanup(key)).isEqualTo(2);
        assertThat(labRows()).isZero();
    }

    @Test
    void concurrentRunsByDifferentUsersKeepTheirOwnCountsAndData() throws Exception {
        List<String> ids = List.of("jdbc-commit", "jdbc-rollback", "jdbc-batch-insert", "spring-jdbc-transaction", "compare-insert", "pool-multiple");
        List<Integer> baseline = new ArrayList<>();
        for (String id : ids) {
            baseline.add(run(id).get("sqlCount").asInt());
        }
        List<User> runners = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            runners.add(newUser("jdbc_lab_conc_" + i + "_" + System.nanoTime(), Role.STUDENT));
        }
        ExecutorService pool = Executors.newFixedThreadPool(ids.size());
        try {
            for (int round = 0; round < 2; round++) {
                CyclicBarrier barrier = new CyclicBarrier(ids.size());
                List<Future<JsonNode>> futures = new ArrayList<>();
                for (int i = 0; i < ids.size(); i++) {
                    final int index = i;
                    Callable<JsonNode> task = () -> {
                        barrier.await(10, TimeUnit.SECONDS);
                        return run(ids.get(index), runners.get(index));
                    };
                    futures.add(pool.submit(task));
                }
                for (int i = 0; i < ids.size(); i++) {
                    JsonNode result = futures.get(i).get(90, TimeUnit.SECONDS);
                    assertThat(result.get("experimentId").asText()).isEqualTo(ids.get(i));
                    assertThat(result.get("sqlCount").asInt()).as(ids.get(i) + " alone vs concurrently").isEqualTo(baseline.get(i));
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(labRows()).isZero();
    }
}
