package com.springlab.coding.submission;

import com.springlab.coding.dto.CodingDtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Persistence of FULL submissions, their per-test results and the progress derived from them. A submission, its results and the progress
 * change are written in ONE transaction, so progress can never move without the submission that caused it (or the other way round).
 * Every read is scoped by the owner's user id in the SQL itself.
 */
@Repository
public class CodingSubmissionStore {

    /** Verdicts that are a result of the learner's code. INTERNAL_ERROR and CANCELLED are stored but are not attempts. */
    static final Set<String> COUNTED = Set.of("ACCEPTED", "WRONG_ANSWER", "COMPILE_ERROR", "RUNTIME_ERROR", "TIME_LIMIT", "MEMORY_LIMIT", "OUTPUT_LIMIT");

    public record NewSubmission(long userId, long problemId, long versionId, String sourceCode, String sourceSha256, String verdict, int passed,
                                int total, String compilerOutput, int judgeMillis, Instant createdAt) {
    }

    public record NewResult(long testCaseId, String status, String message) {
    }

    public record StoredSubmission(long id, long problemId, String problemSlug, int version, String verdict, int passed, int total,
                                   String compilerOutput, Instant createdAt, String sourceCode) {
    }

    public record ResultRow(int sortOrder, String name, boolean visible, String status, String message) {
    }

    private final JdbcTemplate jdbc;

    public CodingSubmissionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long record(NewSubmission s, List<NewResult> results) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("insert into coding_submissions (user_id, problem_id, problem_version_id, source_code, source_sha256, verdict, "
                    + "passed, total, compiler_output, judge_millis, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, s.userId());
            ps.setLong(2, s.problemId());
            ps.setLong(3, s.versionId());
            ps.setString(4, s.sourceCode());
            ps.setString(5, s.sourceSha256());
            ps.setString(6, s.verdict());
            ps.setInt(7, s.passed());
            ps.setInt(8, s.total());
            ps.setString(9, s.compilerOutput() == null || s.compilerOutput().isEmpty() ? null : s.compilerOutput());
            ps.setInt(10, s.judgeMillis());
            ps.setTimestamp(11, Timestamp.from(s.createdAt()));
            return ps;
        }, keys);
        long id = keys.getKey().longValue();
        for (NewResult r : results) {
            jdbc.update("insert into coding_submission_results (submission_id, test_case_id, status, message) values (?, ?, ?, ?)", id, r.testCaseId(), r.status(), r.message());
        }
        if (COUNTED.contains(s.verdict())) {
            boolean accepted = s.verdict().equals("ACCEPTED");
            // one atomic statement: the first row is inserted, every later submission adds to it; accepted_at is set once, by the first ACCEPTED
            jdbc.update("insert into coding_progress (user_id, problem_id, attempts, accepted_submissions, first_accepted_submission_id, accepted_at, "
                    + "latest_submission_id, latest_verdict, latest_at) values (?, ?, 1, ?, ?, ?, ?, ?, ?) as new "
                    + "on duplicate key update attempts = coding_progress.attempts + 1, "
                    + "accepted_submissions = coding_progress.accepted_submissions + new.accepted_submissions, "
                    + "first_accepted_submission_id = coalesce(coding_progress.first_accepted_submission_id, new.first_accepted_submission_id), "
                    + "accepted_at = coalesce(coding_progress.accepted_at, new.accepted_at), "
                    + "latest_submission_id = new.latest_submission_id, latest_verdict = new.latest_verdict, latest_at = new.latest_at",
                    s.userId(), s.problemId(), accepted ? 1 : 0, accepted ? id : null, accepted ? Timestamp.from(s.createdAt()) : null, id, s.verdict(),
                    Timestamp.from(s.createdAt()));
        }
        return id;
    }

    /** Only the owner's row; someone else's id returns empty, exactly like an id that does not exist. */
    public Optional<StoredSubmission> find(long id, long userId) {
        return jdbc.query("select s.id, s.problem_id, p.slug, v.version, s.verdict, s.passed, s.total, s.compiler_output, s.created_at, s.source_code "
                + "from coding_submissions s join coding_problems p on p.id = s.problem_id join coding_problem_versions v on v.id = s.problem_version_id "
                + "where s.id = ? and s.user_id = ?",
                (rs, i) -> new StoredSubmission(rs.getLong("id"), rs.getLong("problem_id"), rs.getString("slug"), rs.getInt("version"), rs.getString("verdict"),
                        rs.getInt("passed"), rs.getInt("total"), rs.getString("compiler_output"), rs.getTimestamp("created_at").toInstant(), rs.getString("source_code")),
                id, userId).stream().findFirst();
    }

    public List<ResultRow> results(long submissionId) {
        return jdbc.query("select t.sort_order, t.name, t.visible, r.status, r.message from coding_submission_results r "
                + "join coding_test_cases t on t.id = r.test_case_id where r.submission_id = ? order by t.sort_order",
                (rs, i) -> new ResultRow(rs.getInt("sort_order"), rs.getString("name"), rs.getBoolean("visible"), rs.getString("status"), rs.getString("message")), submissionId);
    }

    public List<CodingDtos.SubmissionSummary> history(long userId, long problemId, int limit) {
        return jdbc.query("select s.id, v.version, s.verdict, s.passed, s.total, s.created_at from coding_submissions s "
                + "join coding_problem_versions v on v.id = s.problem_version_id where s.user_id = ? and s.problem_id = ? order by s.created_at desc, s.id desc limit ?",
                (rs, i) -> new CodingDtos.SubmissionSummary(rs.getLong("id"), rs.getInt("version"), rs.getString("verdict"), rs.getInt("passed"), rs.getInt("total"),
                        rs.getTimestamp("created_at").toInstant()), userId, problemId, limit);
    }

    public List<CodingDtos.ProblemProgress> progress(long userId) {
        return jdbc.query("select p.slug, p.title, g.attempts, g.accepted_at, g.latest_verdict, g.latest_submission_id, g.latest_at from coding_progress g "
                + "join coding_problems p on p.id = g.problem_id where g.user_id = ? order by g.latest_at desc",
                (rs, i) -> new CodingDtos.ProblemProgress(rs.getString("slug"), rs.getString("title"), rs.getInt("attempts"), rs.getTimestamp("accepted_at") != null,
                        rs.getTimestamp("accepted_at") == null ? null : rs.getTimestamp("accepted_at").toInstant(), rs.getString("latest_verdict"),
                        rs.getLong("latest_submission_id"), rs.getTimestamp("latest_at").toInstant()), userId);
    }
}
