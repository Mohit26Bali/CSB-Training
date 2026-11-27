package com.springlab.coding.catalog;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Reads the problem catalogue. This is the only class that reads hidden test rows, and it hands them to the service, never to a DTO. */
@Repository
public class CodingProblemRepository {

    public record TopicRow(String slug, String name, String description, int problemCount) {
    }

    public record ProblemRow(long id, String topicSlug, String topicName, String slug, String title, String difficulty, String language,
                             String harnessKind, int currentVersion) {
    }

    public record VersionRow(long id, long problemId, int version, String summary, String statementMd, String objectivesJson,
                             String constraintsMd, String examplesJson, String starterCode) {
    }

    public record TestRow(long id, int sortOrder, String name, String concept, boolean visible, String inputSpec, String expectedSpec) {
        /** Test data never appears in a log line or an exception message through toString. */
        @Override
        public String toString() {
            return "TestRow[" + id + (visible ? ", visible" : ", hidden") + "]";
        }
    }

    public record ProblemListRow(ProblemRow problem, String summary, boolean accepted, int attempts) {
    }

    private static final String PROBLEM_SELECT = "select p.id, t.slug as topic_slug, t.name as topic_name, p.slug, p.title, p.difficulty, p.language, "
            + "p.harness_kind, p.current_version from coding_problems p join topics t on t.id = p.topic_id ";

    private final JdbcTemplate jdbc;

    public CodingProblemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static ProblemRow problem(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProblemRow(rs.getLong("id"), rs.getString("topic_slug"), rs.getString("topic_name"), rs.getString("slug"), rs.getString("title"),
                rs.getString("difficulty"), rs.getString("language"), rs.getString("harness_kind"), rs.getInt("current_version"));
    }

    public List<TopicRow> topicsWithProblems() {
        return jdbc.query("select t.slug, t.name, t.description, count(p.id) as n from topics t join coding_problems p on p.topic_id = t.id and p.active = true "
                + "group by t.id, t.slug, t.name, t.description order by t.name",
                (rs, i) -> new TopicRow(rs.getString("slug"), rs.getString("name"), rs.getString("description"), rs.getInt("n")));
    }

    /** Active problems, optionally narrowed by topic slug and difficulty, with the caller's own progress. */
    public List<ProblemListRow> list(long userId, String topicSlug, String difficulty) {
        StringBuilder sql = new StringBuilder("select p.id, t.slug as topic_slug, t.name as topic_name, p.slug, p.title, p.difficulty, p.language, p.harness_kind, "
                + "p.current_version, v.summary, coalesce(g.attempts, 0) as attempts, (g.accepted_at is not null) as accepted "
                + "from coding_problems p join topics t on t.id = p.topic_id "
                + "join coding_problem_versions v on v.problem_id = p.id and v.version = p.current_version "
                + "left join coding_progress g on g.problem_id = p.id and g.user_id = ? where p.active = true");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(userId);
        if (topicSlug != null) {
            sql.append(" and t.slug = ?");
            args.add(topicSlug);
        }
        if (difficulty != null) {
            sql.append(" and p.difficulty = ?");
            args.add(difficulty);
        }
        sql.append(" order by t.name, field(p.difficulty, 'EASY', 'MEDIUM', 'HARD'), p.title");
        return jdbc.query(sql.toString(), (rs, i) -> new ProblemListRow(problem(rs), rs.getString("summary"), rs.getBoolean("accepted"), rs.getInt("attempts")), args.toArray());
    }

    public boolean topicExists(String slug) {
        Integer n = jdbc.queryForObject("select count(*) from topics where slug = ?", Integer.class, slug);
        return n != null && n > 0;
    }

    public Optional<ProblemRow> findActiveBySlug(String slug) {
        return jdbc.query(PROBLEM_SELECT + "where p.slug = ? and p.active = true", (rs, i) -> problem(rs), slug).stream().findFirst();
    }

    public Optional<ProblemRow> findById(long id) {
        return jdbc.query(PROBLEM_SELECT + "where p.id = ?", (rs, i) -> problem(rs), id).stream().findFirst();
    }

    public Optional<VersionRow> version(long problemId, int version) {
        return jdbc.query("select id, problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code "
                + "from coding_problem_versions where problem_id = ? and version = ?",
                (rs, i) -> new VersionRow(rs.getLong("id"), rs.getLong("problem_id"), rs.getInt("version"), rs.getString("summary"), rs.getString("statement_md"),
                        rs.getString("objectives_json"), rs.getString("constraints_md"), rs.getString("examples_json"), rs.getString("starter_code")),
                problemId, version).stream().findFirst();
    }

    /** All tests of a version in their fixed order, hidden ones included. Callers choose what to do with them. */
    public List<TestRow> tests(long versionId) {
        return jdbc.query("select id, sort_order, name, concept, visible, input_spec, expected_spec from coding_test_cases where problem_version_id = ? order by sort_order",
                (rs, i) -> new TestRow(rs.getLong("id"), rs.getInt("sort_order"), rs.getString("name"), rs.getString("concept"), rs.getBoolean("visible"),
                        rs.getString("input_spec"), rs.getString("expected_spec")), versionId);
    }
}
