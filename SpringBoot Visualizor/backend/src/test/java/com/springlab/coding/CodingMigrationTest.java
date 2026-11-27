package com.springlab.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.support.AbstractSpringBootTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The V11 migration on throwaway schemas of the local MySQL: from an empty database, and from a database that is at V10 (with its content).
 * Also checks the seeded pilot against an independent Java model of the exercise, the constraints of the schema, and that nothing outside
 * the coding tables changed.
 */
class CodingMigrationTest extends AbstractSpringBootTest {

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
        return "springlab_f3_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    // ------------------------------------------------------------------------------------------------ migrations

    @Test
    void anEmptyDatabaseMigratesThroughV11WithTheCodingTablesAndThePilot() {
        String schema = scratchName();
        try {
            var result = flyway(schema, "latest").migrate();
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(11);
            JdbcTemplate j = jdbcOn(schema);
            assertThat(j.queryForObject("select max(version + 0) from flyway_schema_history where success = 1", Integer.class)).isGreaterThanOrEqualTo(11);
            assertThat(j.queryForObject("select count(*) from flyway_schema_history where version = '11' and success = 1", Integer.class)).isEqualTo(1);
            for (String table : List.of("coding_problems", "coding_problem_versions", "coding_test_cases", "coding_submissions", "coding_submission_results", "coding_progress")) {
                assertThat(j.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name = ?", Integer.class, schema, table)).as(table).isEqualTo(1);
            }
            assertPilot(j);
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    @Test
    void aV10DatabaseWithContentMigratesToV11WithoutChangingAnythingThatAlreadyExisted() {
        String schema = scratchName();
        try {
            assertThat(flyway(schema, "10").migrate().success).isTrue();
            JdbcTemplate j = jdbcOn(schema);
            assertThat(j.queryForObject("select count(*) from information_schema.tables where table_schema = ? and table_name like 'coding\\_%'", Integer.class, schema)).isZero();
            // put some real content in the V10 database, as a deployed system would have
            j.update("insert into users (username, email, password_hash, role, enabled, created_at) values ('old_user', 'old@example.com', 'x', 'STUDENT', true, now(6))");
            long before = fingerprint(j);
            int topics = j.queryForObject("select count(*) from topics", Integer.class);
            int questions = j.queryForObject("select count(*) from questions", Integer.class);

            assertThat(flyway(schema, "latest").migrate().success).isTrue();

            assertThat(fingerprint(j)).as("V11 changes nothing that existed at V10").isEqualTo(before);
            assertThat(j.queryForObject("select count(*) from topics", Integer.class)).isEqualTo(topics);
            assertThat(j.queryForObject("select count(*) from questions", Integer.class)).isEqualTo(questions);
            assertThat(j.queryForObject("select count(*) from users where username = 'old_user'", Integer.class)).isEqualTo(1);
            assertPilot(j);
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    /** A checksum over the V1-V10 tables, so "nothing changed" is a comparison, not an impression. */
    private static long fingerprint(JdbcTemplate j) {
        long h = 17;
        for (String q : List.of("select group_concat(concat(id, name, slug, description) order by id) from topics",
                "select group_concat(concat(id, topic_id, difficulty, question_text, explanation) order by id) from questions",
                "select group_concat(concat(id, question_id, option_text, correct) order by id) from question_options",
                "select group_concat(concat(id, username, email, role) order by id) from users")) {
            String v = j.queryForObject(q, String.class);
            h = h * 31 + (v == null ? 0 : v.hashCode());
        }
        return h;
    }

    private void assertPilot(JdbcTemplate j) {
        // V11 seeded one problem; V12 (F5) adds five; V13 (F6) adds ten. The pilot itself is checked on its own, and must be exactly what V11 made it.
        assertThat(j.queryForObject("select count(*) from coding_problems", Integer.class)).isEqualTo(16);
        Map<String, Object> p = j.queryForMap("select p.slug, p.title, p.difficulty, p.language, p.harness_kind, p.current_version, p.active, t.slug as topic from coding_problems p join topics t on t.id = p.topic_id "
                + "where p.slug = 'retry-with-bounded-backoff'");
        assertThat(p).containsEntry("slug", "retry-with-bounded-backoff").containsEntry("difficulty", "MEDIUM").containsEntry("language", "java17")
                .containsEntry("harness_kind", "RETRY_EXECUTOR").containsEntry("topic", "microservices");
        assertThat(((Number) p.get("current_version")).intValue()).isEqualTo(1);
        assertThat(j.queryForObject("select count(*) from coding_problem_versions v join coding_problems p on p.id = v.problem_id where p.slug = 'retry-with-bounded-backoff'", Integer.class)).isEqualTo(1);
        List<Map<String, Object>> tests = j.queryForList("select c.sort_order, c.name, c.visible, c.input_spec, c.expected_spec from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                + "join coding_problems p on p.id = v.problem_id where p.slug = 'retry-with-bounded-backoff' order by c.sort_order");
        assertThat(tests).hasSize(12);
        assertThat(tests.stream().filter(t -> Boolean.TRUE.equals(t.get("visible")) || Integer.valueOf(1).equals(t.get("visible"))).count()).isEqualTo(3);
        for (int i = 0; i < tests.size(); i++) {
            assertThat(((Number) tests.get(i).get("sort_order")).intValue()).isEqualTo(i);
        }
        // the seeded expectations equal what an independent Java model of the exercise computes from each input
        for (Map<String, Object> t : tests) {
            try {
                JsonNode expected = mapper.readTree((String) t.get("expected_spec"));
                JsonNode computed = Model.expectedFor(mapper.readTree((String) t.get("input_spec")), mapper);
                assertThat(expected).as((String) t.get("name")).isEqualTo(mapper.readTree(computed.toString()));
            } catch (Exception e) {
                throw new AssertionError("test " + t.get("name") + " is not valid JSON", e);
            }
        }
        assertThat(j.queryForObject("select count(*) from coding_submissions", Integer.class)).isZero();
        assertThat(j.queryForObject("select count(*) from coding_progress", Integer.class)).isZero();
    }

    // ------------------------------------------------------------------------------------------------ schema constraints

    @Test
    void theSchemaRefusesWhatShouldNeverBeStored() {
        String schema = scratchName();
        try {
            assertThat(flyway(schema, "latest").migrate().success).isTrue();
            JdbcTemplate j = jdbcOn(schema);
            long topic = j.queryForObject("select id from topics where slug = 'microservices'", Long.class);
            long problem = j.queryForObject("select id from coding_problems where slug = 'retry-with-bounded-backoff'", Long.class);
            long version = j.queryForObject("select id from coding_problem_versions where problem_id = ?", Long.class, problem);
            j.update("insert into users (username, email, password_hash, role, enabled, created_at) values ('u1', 'u1@example.com', 'x', 'STUDENT', true, now(6))");
            long user = j.queryForObject("select id from users where username = 'u1'", Long.class);

            // a slug is unique; a (problem, version) pair is unique; a test order inside a version is unique
            assertThatThrownBy(() -> j.update("insert into coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at) "
                    + "values (?, 'retry-with-bounded-backoff', 't', 'EASY', 'java17', 'RETRY_EXECUTOR', 1, true, now(6), now(6))", topic)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> j.update("insert into coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at) "
                    + "values (?, 1, 's', 's', '[]', 'c', '[]', 'x', now(6))", problem)).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> j.update("insert into coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) values (?, 0, 'dup', 'c', true, '{}', '{}')", version))
                    .isInstanceOf(DataIntegrityViolationException.class);
            // difficulty, harness and verdict values are constrained
            assertThatThrownBy(() -> j.update("insert into coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at) "
                    + "values (?, 'x1', 't', 'IMPOSSIBLE', 'java17', 'RETRY_EXECUTOR', 1, true, now(6), now(6))", topic))
                    .hasMessageContaining("ck_coding_problems_difficulty");
            assertThatThrownBy(() -> j.update("insert into coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at) "
                    + "values (?, 'x2', 't', 'EASY', 'java17', 'ANYTHING', 1, true, now(6), now(6))", topic))
                    .hasMessageContaining("ck_coding_problems_harness");
            assertThatThrownBy(() -> j.update("insert into coding_submissions (user_id, problem_id, problem_version_id, source_code, source_sha256, verdict, passed, total, created_at) "
                    + "values (?, ?, ?, 'x', repeat('a', 64), 'ACCEPTEDISH', 0, 0, now(6))", user, problem, version))
                    .hasMessageContaining("ck_coding_submissions_verdict");
            // a submission needs a real user, problem and version
            assertThatThrownBy(() -> j.update("insert into coding_submissions (user_id, problem_id, problem_version_id, source_code, source_sha256, verdict, passed, total, created_at) "
                    + "values (999999, ?, ?, 'x', repeat('a', 64), 'ACCEPTED', 0, 0, now(6))", problem, version)).isInstanceOf(DataIntegrityViolationException.class);

            // one progress row per user and problem; results are removed with their submission; a test result needs a real test
            j.update("insert into coding_submissions (user_id, problem_id, problem_version_id, source_code, source_sha256, verdict, passed, total, created_at) "
                    + "values (?, ?, ?, 'x', repeat('a', 64), 'ACCEPTED', 1, 1, now(6))", user, problem, version);
            long submission = j.queryForObject("select max(id) from coding_submissions", Long.class);
            long test = j.queryForObject("select min(id) from coding_test_cases", Long.class);
            j.update("insert into coding_submission_results (submission_id, test_case_id, status) values (?, ?, 'PASS')", submission, test);
            assertThatThrownBy(() -> j.update("insert into coding_submission_results (submission_id, test_case_id, status) values (?, ?, 'PASS')", submission, test))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> j.update("insert into coding_submission_results (submission_id, test_case_id, status) values (?, 999999, 'PASS')", submission))
                    .isInstanceOf(DataIntegrityViolationException.class);
            j.update("insert into coding_progress (user_id, problem_id, attempts, accepted_submissions, latest_submission_id, latest_verdict, latest_at) values (?, ?, 1, 1, ?, 'ACCEPTED', now(6))", user, problem, submission);
            assertThatThrownBy(() -> j.update("insert into coding_progress (user_id, problem_id, attempts, accepted_submissions, latest_submission_id, latest_verdict, latest_at) values (?, ?, 1, 1, ?, 'ACCEPTED', now(6))",
                    user, problem, submission)).isInstanceOf(DataIntegrityViolationException.class);
            j.update("update coding_progress set first_accepted_submission_id = null where user_id = ?", user);
            j.update("delete from coding_progress");
            j.update("delete from coding_submissions where id = ?", submission);
            assertThat(j.queryForObject("select count(*) from coding_submission_results", Integer.class)).as("results go with their submission").isZero();
            // a version that submissions refer to cannot be deleted: history stays interpretable
            j.update("insert into coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at) "
                    + "values (?, 2, 's', 's', '[]', 'c', '[]', 'x', now(6))", problem);
            long v2 = j.queryForObject("select id from coding_problem_versions where version = 2", Long.class);
            j.update("insert into coding_submissions (user_id, problem_id, problem_version_id, source_code, source_sha256, verdict, passed, total, created_at) "
                    + "values (?, ?, ?, 'x', repeat('a', 64), 'WRONG_ANSWER', 0, 0, now(6))", user, problem, v2);
            assertThatThrownBy(() -> j.update("delete from coding_problem_versions where id = ?", v2)).isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            mainJdbc.execute("drop database if exists " + schema);
        }
    }

    // ------------------------------------------------------------------------------------------------ seed data in the test schema

    @Test
    void theSeededTestsRoundTripThroughTheRepositoryAndTheWorkerWireFormat() throws Exception {
        List<Map<String, Object>> rows = mainJdbc.queryForList("select t.id, t.sort_order, t.name, t.concept, t.visible, t.input_spec, t.expected_spec from coding_test_cases t "
                + "join coding_problem_versions v on v.id = t.problem_version_id join coding_problems p on p.id = v.problem_id where p.slug = 'retry-with-bounded-backoff' order by t.sort_order");
        assertThat(rows).hasSize(12);
        var specs = new com.springlab.coding.catalog.TestSpecs(mapper);
        List<String> wire = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            var row = new com.springlab.coding.catalog.CodingProblemRepository.TestRow(((Number) r.get("id")).longValue(), ((Number) r.get("sort_order")).intValue(), (String) r.get("name"),
                    (String) r.get("concept"), Boolean.TRUE.equals(r.get("visible")) || Integer.valueOf(1).equals(r.get("visible")), (String) r.get("input_spec"), (String) r.get("expected_spec"));
            var spec = specs.toSpec(row);
            assertThat(spec.name()).isEqualTo(row.name());
            assertThat(spec.visible()).isEqualTo(row.visible());
            wire.add(spec.toString());
            if (row.visible()) {
                assertThat(specs.describeVisible(row).input()).contains("maxAttempts = ");
            } else {
                assertThatThrownBy(() -> specs.describeVisible(row)).isInstanceOf(IllegalArgumentException.class);
                assertThat(spec.toString()).as("a test's toString never shows its data").isEqualTo("TestSpec[hidden]");
            }
        }
        assertThat(wire.stream().filter(w -> w.equals("TestSpec[visible]")).count()).isEqualTo(3);
    }

    /** An independent model of the exercise in Java, used to check the seeded expectations (the migration was generated by a Python model). */
    static final class Model {
        static JsonNode expectedFor(JsonNode in, ObjectMapper m) {
            int max = in.get("maxAttempts").asInt();
            BigInteger base = BigInteger.valueOf(in.get("baseDelayMillis").asLong());
            BigInteger cap = BigInteger.valueOf(in.get("maxDelayMillis").asLong());
            List<String> steps = new ArrayList<>();
            in.get("steps").forEach(s -> steps.add(s.asText()));
            var out = m.createObjectNode();
            var sleeps = out.putArray("sleeps");
            if (max < 1) {
                out.put("kind", "THROW").put("exceptionClass", "java.lang.IllegalArgumentException").put("identity", -1).put("calls", 0).put("value", "");
                return reorder(out, m);
            }
            int made = 0;
            for (int attempt = 1; attempt <= max; attempt++) {
                String step = attempt <= steps.size() ? steps.get(attempt - 1) : "O:unscripted";
                char kind = step.charAt(0);
                String text = step.substring(2);
                if (kind == 'R') {
                    out.put("kind", "RETURN").put("exceptionClass", "").put("identity", -1).put("calls", attempt).put("value", text);
                    return reorder(out, m);
                }
                made++;
                if (kind == 'O') {
                    out.put("kind", "THROW").put("exceptionClass", "java.lang.IllegalStateException").put("identity", made - 1).put("calls", attempt).put("value", "");
                    return reorder(out, m);
                }
                if (attempt == max) {
                    out.put("kind", "THROW").put("exceptionClass", "TransientException").put("identity", made - 1).put("calls", attempt).put("value", "");
                    return reorder(out, m);
                }
                sleeps.add(base.shiftLeft(attempt - 1).min(cap).longValueExact());
            }
            throw new IllegalStateException();
        }

        private static JsonNode reorder(com.fasterxml.jackson.databind.node.ObjectNode n, ObjectMapper m) {
            return n; // JsonNode equality ignores field order
        }
    }
}
