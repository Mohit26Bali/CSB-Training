package com.springlab.lab.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.trace.CodeRefIds;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Catalogue, mapping metadata, truthfulness of every run, authorization, isolation and concurrency. */
class JpaLabInfrastructureIntegrationTest extends AbstractJpaLabTest {

    @Autowired
    private LabJpaCatalog catalog;
    @Autowired
    private JpaLabService labService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private static final Set<String> PROVENANCE = Set.of("CAPTURED", "DERIVED", "CONCEPTUAL");
    private static final Set<String> LAB_ENTITIES = Set.of("Account", "Course", "Department", "Employee", "Member", "Profile",
            "Project", "Room", "Student", "Task", "Team");

    // ------------------------------------------------------------------------------------------- catalogue

    @Test
    void everyCatalogueEntryHasAnExperimentAndTheOtherWayRound() {
        Set<String> ids = new HashSet<>();
        catalog.catalog().experiments().forEach(e -> ids.add(e.id()));
        assertThat(ids).hasSize(catalog.catalog().experiments().size()).isEqualTo(labService.experimentIds());
        Set<String> groups = new HashSet<>();
        catalog.catalog().groups().forEach(g -> groups.add(g.id()));
        assertThat(catalog.catalog().experiments()).allSatisfy(e -> {
            assertThat(groups).contains(e.group());
            assertThat(e.title()).isNotBlank();
            assertThat(e.question()).isNotBlank();
            assertThat(e.claim()).isNotBlank();
            assertThat(e.diagram()).isIn("er", "flow");
        });
        assertThat(groups).containsExactlyInAnyOrder("one-to-one", "owning-side", "many-to-many", "cascade", "fetching", "n-plus-one",
                "persistence-context");
    }

    // ----------------------------------------------------------------------------------------------- mapping

    @Test
    void theMappingDescribesOnlyTheLabEntitiesAndReportsTheRealAnnotationsAndConstraints() throws Exception {
        JsonNode mapping = getJson("/api/lab/jpa/mapping");
        Set<String> entityIds = new HashSet<>();
        mapping.get("entities").forEach(e -> entityIds.add(e.get("id").asText()));
        assertThat(entityIds).isEqualTo(LAB_ENTITIES);

        JsonNode employeeDepartment = attribute(mapping, "Employee", "department");
        assertThat(employeeDepartment.get("association").asText()).isEqualTo("ManyToOne");
        assertThat(employeeDepartment.get("owning").asBoolean()).isTrue();
        assertThat(employeeDepartment.get("fetch").asText()).isEqualTo("LAZY");
        assertThat(employeeDepartment.get("joinColumn").asText()).isEqualTo("department_id");

        JsonNode departmentEmployees = attribute(mapping, "Department", "employees");
        assertThat(departmentEmployees.get("association").asText()).isEqualTo("OneToMany");
        assertThat(departmentEmployees.get("owning").asBoolean()).isFalse();
        assertThat(departmentEmployees.get("mappedBy").asText()).isEqualTo("department");
        assertThat(departmentEmployees.get("fetch").asText()).isEqualTo("LAZY");
        assertThat(departmentEmployees.get("annotation").asText()).isEqualTo("@OneToMany(mappedBy = \"department\")");

        assertThat(attribute(mapping, "Task", "project").get("fetch").asText()).isEqualTo("EAGER");
        JsonNode profileAccount = attribute(mapping, "Profile", "account");
        assertThat(profileAccount.get("owning").asBoolean()).isTrue();
        assertThat(attribute(mapping, "Account", "profile").get("owning").asBoolean()).isFalse();

        JsonNode courses = attribute(mapping, "Student", "courses");
        assertThat(courses.get("joinTable").get("name").asText()).isEqualTo("jpa_lab_enrollment");
        assertThat(courses.get("joinTable").get("joinColumn").asText()).isEqualTo("student_id");
        assertThat(courses.get("joinTable").get("inverseJoinColumn").asText()).isEqualTo("course_id");
        assertThat(attribute(mapping, "Course", "students").get("mappedBy").asText()).isEqualTo("courses");

        assertThat(attribute(mapping, "Team", "members").get("cascade").toString()).isEqualTo("[\"ALL\"]");
        assertThat(attribute(mapping, "Team", "members").get("orphanRemoval").asBoolean()).isTrue();
        assertThat(attribute(mapping, "Project", "tasks").get("cascade").toString()).isEqualTo("[\"ALL\"]");
        assertThat(attribute(mapping, "Project", "tasks").get("orphanRemoval").asBoolean()).isFalse();

        // database side, read from information_schema
        JsonNode employeeTable = table(mapping, "jpa_lab_employee");
        assertThat(foreignKeys(employeeTable)).containsExactly("department_id -> jpa_lab_department.id");
        assertThat(column(table(mapping, "jpa_lab_profile"), "account_id").get("unique").asBoolean()).isTrue();
        JsonNode enrollment = table(mapping, "jpa_lab_enrollment");
        assertThat(enrollment.get("joinTable").asBoolean()).isTrue();
        assertThat(foreignKeys(enrollment)).containsExactlyInAnyOrder("student_id -> jpa_lab_student.id", "course_id -> jpa_lab_course.id");
        assertThat(column(enrollment, "student_id").get("primaryKey").asBoolean()).isTrue();
        assertThat(column(enrollment, "course_id").get("primaryKey").asBoolean()).isTrue();
        assertThat(table(mapping, "jpa_lab_department").get("foreignKeys")).isEmpty(); // the inverse side has no foreign key

        Set<String> edgeIds = new HashSet<>();
        mapping.get("edges").forEach(e -> edgeIds.add(e.get("id").asText()));
        assertThat(edgeIds).contains("Employee->Department", "Department->Employee", "jpa_lab_employee->jpa_lab_department",
                "jpa_lab_enrollment->jpa_lab_student", "Employee->jpa_lab_employee");
        assertThat(mapping.get("facts").get("openInView").asBoolean()).isFalse();
        assertThat(mapping.get("facts").get("hibernateVersion").asText()).startsWith("6.");
        assertThat(mapping.toString()).doesNotContain("users").doesNotContain("password");
    }

    private static JsonNode attribute(JsonNode mapping, String entity, String name) {
        for (JsonNode e : mapping.get("entities")) {
            if (e.get("id").asText().equals(entity)) {
                for (JsonNode a : e.get("attributes")) {
                    if (a.get("name").asText().equals(name)) {
                        return a;
                    }
                }
            }
        }
        throw new AssertionError(entity + "." + name);
    }

    private static JsonNode table(JsonNode mapping, String id) {
        for (JsonNode t : mapping.get("tables")) {
            if (t.get("id").asText().equals(id)) {
                return t;
            }
        }
        throw new AssertionError(id);
    }

    private static JsonNode column(JsonNode table, String name) {
        for (JsonNode c : table.get("columns")) {
            if (c.get("name").asText().equals(name)) {
                return c;
            }
        }
        throw new AssertionError(name);
    }

    private static List<String> foreignKeys(JsonNode table) {
        List<String> keys = new ArrayList<>();
        table.get("foreignKeys").forEach(f -> keys.add(f.get("column").asText() + " -> " + f.get("referencedTable").asText() + "." + f.get("referencedColumn").asText()));
        return keys;
    }

    // ------------------------------------------------------------------------- every run is truthful

    @Test
    void everyExperimentProducesHonestStepsThatPointAtRealNodesAndMatchHibernatesOwnStatementCount() throws Exception {
        JsonNode mapping = getJson("/api/lab/jpa/mapping");
        Set<String> valid = new HashSet<>(catalog.catalog().flowNodes());
        mapping.get("entities").forEach(e -> {
            valid.add(e.get("id").asText());
            e.get("attributes").forEach(a -> valid.add(e.get("id").asText() + "." + a.get("name").asText()));
        });
        mapping.get("tables").forEach(t -> {
            valid.add(t.get("id").asText());
            t.get("columns").forEach(c -> valid.add(t.get("id").asText() + "." + c.get("name").asText()));
        });
        Set<String> edges = new HashSet<>();
        mapping.get("edges").forEach(e -> edges.add(e.get("id").asText()));

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            // Every request first authenticates: the converter reads the caller's role through Hibernate BEFORE the experiment
            // starts observing. Measure that fixed cost with a request that does nothing else, so it can be subtracted exactly.
            statistics.clear();
            getJson("/api/lab/jpa/catalog");
            long authenticationOnly = statistics.getPrepareStatementCount();
            assertThat(authenticationOnly).isEqualTo(1);
            for (LabJpaCatalog.ExperimentInfo info : catalog.catalog().experiments()) {
                statistics.clear();
                JsonNode run = run(info.id());
                long prepared = statistics.getPrepareStatementCount() - authenticationOnly;
                String id = info.id();

                assertThat(run.get("mode").asText()).as(id).isEqualTo("LIVE");
                assertThat(run.get("steps").size()).as(id).isGreaterThanOrEqualTo(2);
                // Independent oracle: Hibernate's own counter of prepared statements equals what the lab captured.
                assertThat(run.get("sqlCount").asLong()).as(id + ": captured statements vs Hibernate statistics").isEqualTo(prepared);
                long fromSteps = 0;
                Set<String> stepIds = new HashSet<>();
                for (JsonNode step : run.get("steps")) {
                    assertThat(stepIds.add(step.get("id").asText())).as(id + " duplicate step id").isTrue();
                    assertThat(PROVENANCE).as(id).contains(step.get("provenance").asText());
                    assertThat(step.get("explanation").asText().length()).as(id + "/" + step.get("id")).isGreaterThan(20);
                    if (!step.get("provenance").asText().equals("CAPTURED")) {
                        assertThat(step.get("sql")).as(id + ": only CAPTURED steps may carry SQL").isEmpty();
                    }
                    fromSteps += step.get("sql").size();
                    step.get("nodes").forEach(n -> assertThat(valid).as(id + " node " + n.asText()).contains(n.asText()));
                    if (!step.get("edgeFrom").isNull()) {
                        String edge = step.get("edgeFrom").asText() + "->" + step.get("edgeTo").asText();
                        boolean flow = catalog.catalog().flowNodes().contains(step.get("edgeFrom").asText())
                                && catalog.catalog().flowNodes().contains(step.get("edgeTo").asText());
                        assertThat(flow || edges.contains(edge)).as(id + " edge " + edge).isTrue();
                    }
                    if (!step.get("codeRefId").isNull()) {
                        assertThat(CodeRefIds.ALL).as(id).contains(step.get("codeRefId").asText());
                    }
                    for (JsonNode statement : step.get("sql")) {
                        String text = statement.get("sql").asText();
                        assertThat(text.replace("escape ''", "")).as(id + ": placeholders only, no bound values").doesNotContain("'");
                        assertThat(text).doesNotContain("Eve").doesNotContain("Ada").doesNotContain("Engineering").doesNotContain("Apollo");
                    }
                }
                assertThat(fromSteps).as(id + ": step statements add up").isEqualTo(run.get("sqlCount").asLong());
                int expected = 1;
                for (JsonNode statement : run.get("sql")) {
                    assertThat(statement.get("index").asInt()).isEqualTo(expected++);
                }
                assertThat(labRows()).as(id + ": the run cleaned up after itself").isZero();
                assertThat(run.get("limitations").size()).isGreaterThanOrEqualTo(3);
            }
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    // ------------------------------------------------------------------------------ authorization

    @Test
    void everyRouteRequiresAuthenticationAndUnknownExperimentsAreRefused() throws Exception {
        mockMvc.perform(get("/api/lab/jpa/catalog")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lab/jpa/mapping")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lab/jpa/experiments/n-plus-one/run")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lab/jpa/experiments/n-plus-one/run").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lab/jpa/experiments/does-not-exist/run").header("Authorization", bearer(student)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/lab/jpa/experiments/{id}/run", "../../etc/passwd").header("Authorization", bearer(student)))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/lab/jpa/experiments/n-plus-one/run").header("Authorization", bearer(student)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void everySignedInRoleMayRunAnExperiment() throws Exception {
        for (Role role : Role.values()) {
            User user = newUser("jpa_lab_" + role.name().toLowerCase() + "_" + System.nanoTime(), role);
            assertThat(run("first-level-cache", user).get("sqlCount").asInt()).isGreaterThan(0);
        }
    }

    @Test
    void theClientCannotInjectAnythingIntoARun() throws Exception {
        // The endpoint reads no body, query string or header that influences what runs: extra input is ignored.
        mockMvc.perform(post("/api/lab/jpa/experiments/first-level-cache/run?sql=drop+table+users&table=users")
                        .header("Authorization", bearer(student)).contentType("application/json")
                        .content("{\"sql\":\"delete from users\",\"table\":\"users\"}"))
                .andExpect(status().isOk());
        assertThat(userRepository.findByUsername(student.getUsername())).isPresent();
    }

    // ---------------------------------------------------------------------------------------- isolation

    @Test
    void runningEveryExperimentChangesNoApplicationTableAndCreatesNoTrace() throws Exception {
        long users = jdbc.queryForObject("select count(*) from users", Long.class);
        long questions = jdbc.queryForObject("select count(*) from questions", Long.class);
        long attempts = jdbc.queryForObject("select count(*) from attempts", Long.class);
        long topics = jdbc.queryForObject("select count(*) from topics", Long.class);
        int tracesBefore = traceStoreSize(); // the store is shared with other tests: compare, do not expect empty
        for (LabJpaCatalog.ExperimentInfo info : catalog.catalog().experiments()) {
            mockMvc.perform(post("/api/lab/jpa/experiments/{id}/run", info.id()).header("Authorization", bearer(student)))
                    .andExpect(status().isOk()).andExpect(header().doesNotExist("X-SpringLab-Trace-Id"));
        }
        assertThat(jdbc.queryForObject("select count(*) from users", Long.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("select count(*) from questions", Long.class)).isEqualTo(questions);
        assertThat(jdbc.queryForObject("select count(*) from attempts", Long.class)).isEqualTo(attempts);
        assertThat(jdbc.queryForObject("select count(*) from topics", Long.class)).isEqualTo(topics);
        assertThat(labRows()).isZero();
        assertThat(traceStoreSize()).isEqualTo(tracesBefore); // lab runs never use the request trace store
    }

    @Autowired
    private com.springlab.lab.trace.TraceStore traceStore;

    private int traceStoreSize() {
        return traceStore.size();
    }

    @Test
    void concurrentRunsDoNotSeeEachOthersStatementsOrData() throws Exception {
        List<String> ids = List.of("n-plus-one", "join-fetch", "owning-inverse-only", "dirty-checking", "cascade-persist-all", "m2m-join-table");
        List<Integer> baseline = new ArrayList<>();
        for (String id : ids) {
            baseline.add(run(id).get("sqlCount").asInt());
        }
        List<User> runners = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            runners.add(newUser("jpa_lab_conc_" + i + "_" + System.nanoTime(), Role.STUDENT));
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
                    JsonNode result = futures.get(i).get(60, TimeUnit.SECONDS);
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
