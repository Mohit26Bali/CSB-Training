package com.springlab.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.coding.execution.ExecutionRequest;
import com.springlab.coding.execution.ScriptTestSpec;
import com.springlab.support.AbstractSpringBootTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The V13 migration (Phase F6: the easy tier) on throwaway schemas: from an empty database, and from a database that is at V12 and already holds
 * learner history on the pilot and on a microservices problem. Everything that existed must come through unchanged.
 */
class CodingEasyMigrationTest extends AbstractSpringBootTest {

    /** slug -> harness kind, topic, number of tests (3 visible each). */
    private static final Map<String, Object[]> EASY = Map.ofEntries(
            Map.entry("password-validator", new Object[]{"PASSWORD_VALIDATOR", "java-core", 8}),
            Map.entry("loan-eligibility", new Object[]{"LOAN_ELIGIBILITY", "java-core", 9}),
            Map.entry("salary-calculator", new Object[]{"SALARY_CALCULATOR", "java-core", 9}),
            Map.entry("bank-account", new Object[]{"BANK_ACCOUNT", "java-core", 8}),
            Map.entry("training-registry", new Object[]{"TRAINING_REGISTRY", "java-core", 10}),
            Map.entry("grade-calculator-fix", new Object[]{"GRADE_CALCULATOR", "java-core", 7}),
            Map.entry("request-validator", new Object[]{"REQUEST_VALIDATOR", "spring-boot-basics", 8}),
            Map.entry("product-service-crud", new Object[]{"PRODUCT_SERVICE", "spring-boot-basics", 10}),
            Map.entry("exception-to-http-status", new Object[]{"ERROR_MAPPER", "spring-boot-basics", 8}),
            Map.entry("bean-registry-primary", new Object[]{"BEAN_REGISTRY", "spring-boot-basics", 11}));

    private static final List<String> EARLIER = List.of("retry-with-bounded-backoff", "circuit-breaker-state-machine", "bulkhead-concurrency-limiter",
            "idempotency-key-handler", "saga-compensation-coordinator", "transactional-outbox-relay");

    @Autowired
    private Environment env;
    @Autowired
    private JdbcTemplate mainJdbc;
    @Autowired
    private ObjectMapper mapper;

    private String url(String schema) {
        return env.getRequiredProperty("spring.datasource.url").replaceFirst("/[^/?]+\\?", "/" + schema + "?");
    }

    private Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(url(schema), env.getRequiredProperty("spring.datasource.username"), env.getProperty("spring.datasource.password", ""))
                .locations("classpath:db/migration").target(target).load();
    }

    private JdbcTemplate jdbcOn(String schema) {
        return new JdbcTemplate(new DriverManagerDataSource(url(schema), env.getRequiredProperty("spring.datasource.username"), env.getProperty("spring.datasource.password", "")));
    }

    private static String scratchName() {
        return "springlab_f6_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    void anEmptyDatabaseMigratesThroughV13WithTheTenEverydayProblems() throws Exception {
        String schema = scratchName();
        try {
            assertThat(flyway(schema, "latest").migrate().success).isTrue();
            JdbcTemplate j = jdbcOn(schema);
            assertThat(j.queryForObject("select count(*) from flyway_schema_history where version = '13' and success = 1", Integer.class)).isEqualTo(1);
            assertThat(j.queryForObject("select count(*) from coding_problems", Integer.class)).isEqualTo(16);
            assertThat(j.queryForObject("select count(*) from coding_problems p join topics t on t.id = p.topic_id where t.slug = 'java-core'", Integer.class)).isEqualTo(6);
            assertThat(j.queryForObject("select count(*) from coding_problems p join topics t on t.id = p.topic_id where t.slug = 'spring-boot-basics'", Integer.class)).isEqualTo(4);

            for (var e : EASY.entrySet()) {
                String slug = e.getKey();
                Map<String, Object> p = j.queryForMap("select p.id, p.harness_kind, p.language, p.current_version, p.active, p.difficulty, t.slug as topic from coding_problems p "
                        + "join topics t on t.id = p.topic_id where p.slug = ?", slug);
                assertThat(p).as(slug).containsEntry("harness_kind", e.getValue()[0]).containsEntry("topic", e.getValue()[1]).containsEntry("language", "java17");
                assertThat(p.get("difficulty")).isIn("EASY", "MEDIUM");
                assertThat(((Number) p.get("current_version")).intValue()).isEqualTo(1);
                assertThat(j.queryForObject("select count(*) from coding_problem_versions where problem_id = ?", Integer.class, p.get("id"))).as(slug).isEqualTo(1);

                List<Map<String, Object>> rows = j.queryForList("select c.sort_order, c.name, c.visible, c.input_spec, c.expected_spec from coding_test_cases c "
                        + "join coding_problem_versions v on v.id = c.problem_version_id where v.problem_id = ? order by c.sort_order", p.get("id"));
                assertThat(rows).as(slug).hasSize((Integer) e.getValue()[2]);
                assertThat(rows.stream().filter(r -> Boolean.TRUE.equals(r.get("visible")) || Integer.valueOf(1).equals(r.get("visible"))).count()).as(slug).isEqualTo(3);
                ExecutionRequest.Problem kind = ExecutionRequest.Problem.valueOf((String) e.getValue()[0]);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    assertThat(((Number) rows.get(i).get("sort_order")).intValue()).isEqualTo(i);
                    JsonNode in = mapper.readTree((String) rows.get(i).get("input_spec"));
                    JsonNode ex = mapper.readTree((String) rows.get(i).get("expected_spec"));
                    List<String> script = new ArrayList<>();
                    in.get("script").forEach(n -> script.add(n.asText()));
                    List<String> trace = new ArrayList<>();
                    ex.get("trace").forEach(n -> trace.add(n.asText()));
                    // every stored test is a valid script for ITS exercise, and starts by building the object
                    ScriptTestSpec.of(kind, (String) rows.get(i).get("name"), true, script, trace);
                    assertThat(script.get(0)).startsWith("new");
                    names.add((String) rows.get(i).get("name"));
                }
                assertThat(names).as(slug + " test names are unique").doesNotHaveDuplicates();
            }
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    @Test
    void aV12DatabaseWithLearnerHistoryMigratesToV13AndEverythingEarlierIsUntouched() {
        String schema = scratchName();
        try {
            assertThat(flyway(schema, "12").migrate().success).isTrue();
            JdbcTemplate j = jdbcOn(schema);
            assertThat(j.queryForObject("select count(*) from coding_problems", Integer.class)).isEqualTo(6);
            j.update("insert into users (username, email, password_hash, role, enabled, created_at) values ('hist12', 'hist12@example.com', 'x', 'STUDENT', true, now(6))");
            long user = j.queryForObject("select id from users where username = 'hist12'", Long.class);
            for (String slug : List.of("retry-with-bounded-backoff", "saga-compensation-coordinator")) {
                long problem = j.queryForObject("select id from coding_problems where slug = ?", Long.class, slug);
                long version = j.queryForObject("select id from coding_problem_versions where problem_id = ?", Long.class, problem);
                j.update("insert into coding_submissions (user_id, problem_id, problem_version_id, source_code, source_sha256, verdict, passed, total, created_at) "
                        + "values (?, ?, ?, 'class X {}', repeat('c', 64), 'WRONG_ANSWER', 1, 11, now(6))", user, problem, version);
            }
            String before = fingerprint(j);

            assertThat(flyway(schema, "latest").migrate().success).isTrue();

            assertThat(fingerprint(j)).as("the pilot, the five microservices problems, their tests and the learner's history are exactly as they were").isEqualTo(before);
            assertThat(j.queryForObject("select count(*) from coding_problems", Integer.class)).isEqualTo(16);
            assertThat(j.queryForObject("select count(*) from coding_submissions where user_id = ?", Integer.class, user)).isEqualTo(2);
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    @Test
    void theHarnessConstraintNamesEveryKindAndStillRefusesAnythingElse() {
        String schema = scratchName();
        try {
            assertThat(flyway(schema, "latest").migrate().success).isTrue();
            JdbcTemplate j = jdbcOn(schema);
            long topic = j.queryForObject("select id from topics where slug = 'java-core'", Long.class);
            assertThatThrownBy(() -> j.update("insert into coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at) "
                    + "values (?, 'x5', 't', 'EASY', 'java17', 'SHELL', 1, true, now(6), now(6))", topic)).hasMessageContaining("ck_coding_problems_harness");
            // (MySQL compares the CHECK list case-insensitively, so a lower-case name is accepted by the database; the backend matches kinds
            // case-sensitively and would stop such a problem as an internal error, so nothing can be judged with it.)
            int n = 0;
            for (ExecutionRequest.Problem p : ExecutionRequest.Problem.values()) {
                j.update("insert into coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at) "
                        + "values (?, ?, 't', 'EASY', 'java17', ?, 1, false, now(6), now(6))", topic, "kind-" + n++, p.name());
            }
            assertThat(n).as("every harness kind the backend knows is accepted by the database").isEqualTo(ExecutionRequest.Problem.values().length).isEqualTo(16);
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    /** A digest of every row that belongs to a problem that existed before V13: problem, versions, tests, and all submissions against them. */
    private static String fingerprint(JdbcTemplate j) {
        StringBuilder all = new StringBuilder();
        String inList = "('" + String.join("','", EARLIER) + "')";
        for (String q : List.of(
                "select concat_ws('|', p.id, p.slug, p.title, p.difficulty, p.language, p.harness_kind, p.current_version, p.active, p.topic_id) from coding_problems p where p.slug in " + inList + " order by p.id",
                "select concat_ws('|', v.id, v.version, v.summary, v.statement_md, v.objectives_json, v.constraints_md, v.examples_json, v.starter_code) from coding_problem_versions v "
                        + "join coding_problems p on p.id = v.problem_id where p.slug in " + inList + " order by v.id",
                "select concat_ws('|', c.id, c.sort_order, c.name, c.concept, c.visible, c.input_spec, c.expected_spec) from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                        + "join coding_problems p on p.id = v.problem_id where p.slug in " + inList + " order by c.id",
                "select concat_ws('|', s.id, s.user_id, s.problem_version_id, s.source_code, s.verdict, s.passed, s.total) from coding_submissions s "
                        + "join coding_problems p on p.id = s.problem_id where p.slug in " + inList + " order by s.id")) {
            for (String row : j.queryForList(q, String.class)) {
                all.append(row).append('\n');
            }
        }
        return Integer.toHexString(all.toString().hashCode()) + ":" + all.length();
    }
}
