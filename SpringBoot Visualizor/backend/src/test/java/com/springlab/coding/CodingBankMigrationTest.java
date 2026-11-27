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
 * The V12 migration (Phase F5: five more problems) on throwaway schemas of the local MySQL: from an empty database, and from a database that
 * is at V11 and already holds learner history on the pilot. The pilot, its tests and that history must come through byte for byte.
 */
class CodingBankMigrationTest extends AbstractSpringBootTest {

    private static final String PILOT = "retry-with-bounded-backoff";

    /** slug -> harness kind, number of tests (3 visible each). The numbers are fixed by the migration and by the problem statements. */
    private static final Map<String, Object[]> BANK = Map.of(
            "circuit-breaker-state-machine", new Object[]{"CIRCUIT_BREAKER", 13},
            "bulkhead-concurrency-limiter", new Object[]{"BULKHEAD", 12},
            "idempotency-key-handler", new Object[]{"IDEMPOTENCY", 11},
            "saga-compensation-coordinator", new Object[]{"SAGA", 11},
            "transactional-outbox-relay", new Object[]{"OUTBOX", 13});

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
        return "springlab_f5_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    void anEmptyDatabaseMigratesThroughV12WithTheFiveNewProblemsAndTheirTests() throws Exception {
        String schema = scratchName();
        try {
            assertThat(flyway(schema, "latest").migrate().success).isTrue();
            JdbcTemplate j = jdbcOn(schema);
            assertThat(j.queryForObject("select count(*) from flyway_schema_history where version = '12' and success = 1", Integer.class)).isEqualTo(1);
            assertThat(j.queryForObject("select count(*) from coding_problems", Integer.class)).as("pilot + 5 (V12) + 10 (V13)").isEqualTo(16);

            for (var e : BANK.entrySet()) {
                String slug = e.getKey();
                String harness = (String) e.getValue()[0];
                int tests = (Integer) e.getValue()[1];
                Map<String, Object> p = j.queryForMap("select p.id, p.harness_kind, p.language, p.current_version, p.active, t.slug as topic from coding_problems p "
                        + "join topics t on t.id = p.topic_id where p.slug = ?", slug);
                assertThat(p).as(slug).containsEntry("harness_kind", harness).containsEntry("language", "java17").containsEntry("topic", "microservices");
                assertThat(((Number) p.get("current_version")).intValue()).isEqualTo(1);
                assertThat(j.queryForObject("select count(*) from coding_problem_versions where problem_id = ?", Integer.class, p.get("id"))).as(slug).isEqualTo(1);

                List<Map<String, Object>> rows = j.queryForList("select c.sort_order, c.name, c.visible, c.input_spec, c.expected_spec from coding_test_cases c "
                        + "join coding_problem_versions v on v.id = c.problem_version_id where v.problem_id = ? order by c.sort_order", p.get("id"));
                assertThat(rows).as(slug).hasSize(tests);
                assertThat(rows.stream().filter(r -> Boolean.TRUE.equals(r.get("visible")) || Integer.valueOf(1).equals(r.get("visible"))).count()).as(slug).isEqualTo(3);
                ExecutionRequest.Problem kind = ExecutionRequest.Problem.valueOf(harness);
                for (int i = 0; i < rows.size(); i++) {
                    assertThat(((Number) rows.get(i).get("sort_order")).intValue()).isEqualTo(i);
                    // every stored test is a valid script for ITS exercise: allowed operations, allowed characters, one expected line per operation
                    JsonNode in = mapper.readTree((String) rows.get(i).get("input_spec"));
                    JsonNode ex = mapper.readTree((String) rows.get(i).get("expected_spec"));
                    List<String> script = new ArrayList<>();
                    in.get("script").forEach(n -> script.add(n.asText()));
                    List<String> trace = new ArrayList<>();
                    ex.get("trace").forEach(n -> trace.add(n.asText()));
                    ScriptTestSpec.of(kind, (String) rows.get(i).get("name"), true, script, trace);
                }
            }
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    @Test
    void aV11DatabaseWithPilotHistoryMigratesToV12AndThePilotIsUntouched() {
        String schema = scratchName();
        try {
            assertThat(flyway(schema, "11").migrate().success).isTrue();
            JdbcTemplate j = jdbcOn(schema);
            assertThat(j.queryForObject("select count(*) from coding_problems", Integer.class)).isEqualTo(1);
            // learner history on the pilot, as a deployed system would have
            j.update("insert into users (username, email, password_hash, role, enabled, created_at) values ('hist_user', 'hist@example.com', 'x', 'STUDENT', true, now(6))");
            long user = j.queryForObject("select id from users where username = 'hist_user'", Long.class);
            long problem = j.queryForObject("select id from coding_problems where slug = ?", Long.class, PILOT);
            long version = j.queryForObject("select id from coding_problem_versions where problem_id = ?", Long.class, problem);
            j.update("insert into coding_submissions (user_id, problem_id, problem_version_id, source_code, source_sha256, verdict, passed, total, created_at) "
                    + "values (?, ?, ?, 'class RetryExecutor {}', repeat('b', 64), 'WRONG_ANSWER', 5, 12, now(6))", user, problem, version);
            String before = pilotFingerprint(j);

            assertThat(flyway(schema, "latest").migrate().success).isTrue();

            assertThat(pilotFingerprint(j)).as("the pilot, its version, its tests and the learner's history are exactly as they were").isEqualTo(before);
            assertThat(j.queryForObject("select count(*) from coding_problems", Integer.class)).as("pilot + 5 (V12) + 10 (V13)").isEqualTo(16);
            assertThat(j.queryForObject("select current_version from coding_problems where slug = ?", Integer.class, PILOT)).isEqualTo(1);
            assertThat(j.queryForObject("select count(*) from coding_problem_versions where problem_id = ?", Integer.class, problem)).isEqualTo(1);
            assertThat(j.queryForObject("select count(*) from coding_submissions where user_id = ?", Integer.class, user)).isEqualTo(1);
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    @Test
    void theWidenedHarnessConstraintStillRefusesAnUnknownKind() {
        String schema = scratchName();
        try {
            assertThat(flyway(schema, "latest").migrate().success).isTrue();
            JdbcTemplate j = jdbcOn(schema);
            long topic = j.queryForObject("select id from topics where slug = 'microservices'", Long.class);
            assertThatThrownBy(() -> j.update("insert into coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at) "
                    + "values (?, 'x3', 't', 'EASY', 'java17', 'SHELL', 1, true, now(6), now(6))", topic)).hasMessageContaining("ck_coding_problems_harness");
            // and a known kind is accepted
            j.update("insert into coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at) "
                    + "values (?, 'x4', 't', 'EASY', 'java17', 'OUTBOX', 1, true, now(6), now(6))", topic);
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    /** A digest over every row that belongs to the pilot: problem, versions, tests, and all submissions and results against it. */
    private static String pilotFingerprint(JdbcTemplate j) {
        StringBuilder all = new StringBuilder();
        for (String q : List.of(
                "select concat_ws('|', p.id, p.slug, p.title, p.difficulty, p.language, p.harness_kind, p.current_version, p.active) from coding_problems p where p.slug = 'retry-with-bounded-backoff'",
                "select concat_ws('|', v.id, v.version, v.summary, v.statement_md, v.objectives_json, v.constraints_md, v.examples_json, v.starter_code) from coding_problem_versions v "
                        + "join coding_problems p on p.id = v.problem_id where p.slug = 'retry-with-bounded-backoff' order by v.id",
                "select concat_ws('|', c.id, c.sort_order, c.name, c.concept, c.visible, c.input_spec, c.expected_spec) from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                        + "join coding_problems p on p.id = v.problem_id where p.slug = 'retry-with-bounded-backoff' order by c.id",
                "select concat_ws('|', s.id, s.user_id, s.problem_version_id, s.source_code, s.verdict, s.passed, s.total) from coding_submissions s "
                        + "join coding_problems p on p.id = s.problem_id where p.slug = 'retry-with-bounded-backoff' order by s.id")) {
            for (String row : j.queryForList(q, String.class)) {
                all.append(row).append('\n');
            }
        }
        return Integer.toHexString(all.toString().hashCode()) + ":" + all.length();
    }
}
