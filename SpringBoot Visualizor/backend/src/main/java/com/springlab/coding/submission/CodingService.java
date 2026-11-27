package com.springlab.coding.submission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.coding.catalog.CodingProblemRepository;
import com.springlab.coding.catalog.CodingProblemRepository.ProblemRow;
import com.springlab.coding.catalog.CodingProblemRepository.TestRow;
import com.springlab.coding.catalog.CodingProblemRepository.VersionRow;
import com.springlab.coding.catalog.TestSpecs;
import com.springlab.coding.dto.CodingDtos;
import com.springlab.coding.execution.ExecutionProperties;
import com.springlab.coding.execution.ExecutionRequest;
import com.springlab.coding.execution.ExecutionResult;
import com.springlab.coding.execution.ExecutionService;
import com.springlab.coding.execution.ExecutionState;
import com.springlab.coding.execution.ExecutionStatus;
import com.springlab.coding.execution.SourceValidator;
import com.springlab.coding.execution.Verdict;
import com.springlab.entity.User;
import com.springlab.exception.InvalidRequestException;
import com.springlab.exception.ResourceNotFoundException;
import com.springlab.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The coding-practice workflow. This class is the trusted authority for which problem and which tests a run uses: both come from the
 * database, selected by the slug in the URL, and the learner supplies nothing but source code.
 *
 * <ul>
 *   <li><b>Run</b> judges the VISIBLE tests of the current version. Nothing is stored and progress is untouched.</li>
 *   <li><b>Submit</b> judges ALL tests, stores the submission with its per-test results, and updates progress, in one transaction that
 *       starts only after judging has finished (no database transaction is held while code runs).</li>
 *   <li>Anything that is not a clear, consistent answer from the worker becomes INTERNAL_ERROR. A verdict ACCEPTED is produced only when
 *       the worker said ACCEPTED, reported exactly the tests that were sent, and every one of them passed.</li>
 * </ul>
 */
@Service
public class CodingService {

    private static final Logger log = LoggerFactory.getLogger(CodingService.class);
    /** Only these two verdicts claim something about each test; the others (a timeout, a kill) may come without any per-test result. */
    private static final Set<Verdict> NEEDS_ALIGNED_TESTS = Set.of(Verdict.ACCEPTED, Verdict.WRONG_ANSWER);
    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final int HISTORY_LIMIT = 50;

    private final CodingProblemRepository problems;
    private final CodingSubmissionStore store;
    private final ExecutionService execution;
    private final ExecutionProperties executionProperties;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final TestSpecs specs;

    public CodingService(CodingProblemRepository problems, CodingSubmissionStore store, ExecutionService execution, ExecutionProperties executionProperties,
                         UserRepository users, ObjectMapper mapper) {
        this.problems = problems;
        this.store = store;
        this.execution = execution;
        this.executionProperties = executionProperties;
        this.users = users;
        this.mapper = mapper;
        this.specs = new TestSpecs(mapper);
    }

    // ------------------------------------------------------------------------------------------------ browse

    public List<CodingDtos.TopicResponse> topics() {
        return problems.topicsWithProblems().stream().map(t -> new CodingDtos.TopicResponse(t.slug(), t.name(), t.description(), t.problemCount())).toList();
    }

    public List<CodingDtos.ProblemSummary> list(String username, String topic, String difficulty) {
        if (difficulty != null && !DIFFICULTIES.contains(difficulty)) {
            throw new InvalidRequestException("Unknown difficulty '" + difficulty + "'");
        }
        if (topic != null && !problems.topicExists(topic)) {
            throw new InvalidRequestException("Unknown topic '" + topic + "'");
        }
        return problems.list(userId(username), topic, difficulty).stream()
                .map(r -> new CodingDtos.ProblemSummary(r.problem().slug(), r.problem().title(), r.problem().difficulty(), r.problem().topicSlug(), r.problem().topicName(),
                        r.problem().language(), r.summary(), r.problem().currentVersion(), r.accepted(), r.attempts()))
                .toList();
    }

    public CodingDtos.ProblemDetail detail(String slug) {
        ProblemRow problem = activeProblem(slug);
        VersionRow version = currentVersion(problem);
        List<TestRow> tests = problems.tests(version.id());
        List<CodingDtos.SampleTest> samples = tests.stream().filter(TestRow::visible).map(t -> specs.describeVisible(problem.harnessKind(), t)).toList();
        int hidden = (int) tests.stream().filter(t -> !t.visible()).count();
        try {
            List<String> objectives = new ArrayList<>();
            mapper.readTree(version.objectivesJson()).forEach(n -> objectives.add(n.asText()));
            List<CodingDtos.Example> examples = new ArrayList<>();
            for (JsonNode e : mapper.readTree(version.examplesJson())) {
                examples.add(new CodingDtos.Example(e.get("title").asText(), e.get("input").asText(), e.get("output").asText(), e.get("explanation").asText()));
            }
            return new CodingDtos.ProblemDetail(problem.slug(), problem.title(), problem.difficulty(), problem.topicSlug(), problem.topicName(), problem.language(),
                    version.version(), version.summary(), version.statementMd(), objectives, version.constraintsMd(), examples, version.starterCode(), samples, hidden);
        } catch (Exception e) {
            throw new IllegalStateException("problem " + slug + " has unreadable content", e);
        }
    }

    // ------------------------------------------------------------------------------------------------ run and submit

    /** Judges the visible tests only. Stores nothing, changes no progress. */
    public CodingDtos.RunResponse run(String username, String slug, String sourceCode) {
        ProblemRow problem = activeProblem(slug);
        byte[] source = sourceBytes(sourceCode);
        List<TestRow> visible = problems.tests(currentVersion(problem).id()).stream().filter(TestRow::visible).toList();
        Judged judged = judge(username, problem, ExecutionRequest.Mode.RUN, source, visible);
        log.info("coding run user={} problem={} verdict={} passed={}/{}", username, slug, judged.verdict, judged.passed, judged.total);
        return new CodingDtos.RunResponse(judged.verdict, judged.passed, judged.total, judged.visibleViews(visible), judged.compilerOutput, feedback(judged, false));
    }

    /** Judges every test, stores the submission and its results, and updates progress. */
    public CodingDtos.SubmissionResponse submit(String username, String slug, String sourceCode) {
        ProblemRow problem = activeProblem(slug);
        VersionRow version = currentVersion(problem);
        byte[] source = sourceBytes(sourceCode);
        List<TestRow> all = problems.tests(version.id());
        long userId = userId(username);
        Judged judged = judge(username, problem, ExecutionRequest.Mode.SUBMIT, source, all);
        Instant now = Instant.now();
        List<CodingSubmissionStore.NewResult> results = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            TestRow t = all.get(i);
            ExecutionResult.TestOutcome o = judged.aligned ? judged.result.tests().get(i) : null;
            // a message is stored only for a visible test, whatever the worker sent
            results.add(new CodingSubmissionStore.NewResult(t.id(), o == null ? "NOT_RUN" : o.status(), o != null && t.visible() ? o.message() : null));
        }
        long id = store.record(new CodingSubmissionStore.NewSubmission(userId, problem.id(), version.id(), sourceCode, sha256(source), judged.verdict, judged.passed,
                judged.total, judged.compilerOutput, (int) Math.min(judged.result.wallMillis(), Integer.MAX_VALUE), now), results);
        log.info("coding submission id={} user={} problem={} version={} verdict={} passed={}/{}", id, username, slug, version.version(), judged.verdict, judged.passed, judged.total);
        List<CodingDtos.TestOutcomeView> visibleViews = judged.visibleViews(all);
        return new CodingDtos.SubmissionResponse(id, slug, version.version(), judged.verdict, judged.passed, judged.total, visibleViews, judged.hiddenTotal(all),
                judged.hiddenPassed(all), judged.compilerOutput, feedback(judged, true), now);
    }

    // ------------------------------------------------------------------------------------------------ history and progress

    /** The caller's own submission. Someone else's id and a missing id give the same answer. */
    public CodingDtos.SubmissionDetail submission(String username, long id) {
        CodingSubmissionStore.StoredSubmission s = store.find(id, userId(username)).orElseThrow(() -> new ResourceNotFoundException("Submission not found"));
        List<CodingSubmissionStore.ResultRow> rows = store.results(s.id());
        List<CodingDtos.TestOutcomeView> visible = rows.stream().filter(CodingSubmissionStore.ResultRow::visible)
                .map(r -> new CodingDtos.TestOutcomeView(r.name(), r.status(), r.message())).toList();
        int hiddenTotal = (int) rows.stream().filter(r -> !r.visible()).count();
        int hiddenPassed = (int) rows.stream().filter(r -> !r.visible() && r.status().equals("PASS")).count();
        boolean anyVisibleFailed = visible.stream().anyMatch(v -> !v.status().equals("PASS"));
        return new CodingDtos.SubmissionDetail(s.id(), s.problemSlug(), s.version(), s.verdict(), s.passed(), s.total(), visible, hiddenTotal, hiddenPassed,
                s.compilerOutput() == null ? "" : s.compilerOutput(), feedbackFor(s.verdict(), anyVisibleFailed, hiddenTotal - hiddenPassed, true), s.createdAt(), s.sourceCode());
    }

    public List<CodingDtos.SubmissionSummary> history(String username, String slug) {
        ProblemRow problem = activeProblem(slug);
        return store.history(userId(username), problem.id(), HISTORY_LIMIT);
    }

    public CodingDtos.ProgressResponse progress(String username) {
        List<CodingDtos.ProblemProgress> rows = store.progress(userId(username));
        return new CodingDtos.ProgressResponse(rows.size(), (int) rows.stream().filter(CodingDtos.ProblemProgress::accepted).count(),
                rows.stream().mapToInt(CodingDtos.ProblemProgress::attempts).sum(), rows);
    }

    // ------------------------------------------------------------------------------------------------ judging

    /** The outcome of one execution, already reconciled with the tests that were sent. */
    private static final class Judged {
        final ExecutionResult result;
        final String verdict;
        final boolean aligned;
        final int passed;
        final int total;
        final String compilerOutput;

        Judged(ExecutionResult result, String verdict, boolean aligned, int passed, int total, String compilerOutput) {
            this.result = result;
            this.verdict = verdict;
            this.aligned = aligned;
            this.passed = passed;
            this.total = total;
            this.compilerOutput = compilerOutput;
        }

        List<CodingDtos.TestOutcomeView> visibleViews(List<TestRow> sent) {
            List<CodingDtos.TestOutcomeView> views = new ArrayList<>();
            for (int i = 0; i < sent.size(); i++) {
                if (sent.get(i).visible()) {
                    ExecutionResult.TestOutcome o = aligned ? result.tests().get(i) : null;
                    views.add(new CodingDtos.TestOutcomeView(sent.get(i).name(), o == null ? "NOT_RUN" : o.status(), o == null ? null : o.message()));
                }
            }
            return views;
        }

        int hiddenTotal(List<TestRow> sent) {
            return (int) sent.stream().filter(t -> !t.visible()).count();
        }

        int hiddenPassed(List<TestRow> sent) {
            int n = 0;
            for (int i = 0; aligned && i < sent.size(); i++) {
                if (!sent.get(i).visible() && result.tests().get(i).status().equals("PASS")) {
                    n++;
                }
            }
            return n;
        }
    }

    private Judged judge(String username, ProblemRow problem, ExecutionRequest.Mode mode, byte[] source, List<TestRow> tests) {
        ExecutionRequest.Problem harness;
        List<com.springlab.coding.execution.WorkerTest> specList;
        try {
            harness = ExecutionRequest.Problem.valueOf(problem.harnessKind());
            specList = tests.stream().map(t -> specs.toWorkerTest(problem.harnessKind(), t)).toList();
        } catch (RuntimeException e) {
            log.error("problem {} cannot be judged: its tests are not usable", problem.slug());
            return new Judged(ExecutionResult.of(Verdict.INTERNAL_ERROR), Verdict.INTERNAL_ERROR.name(), false, 0, tests.size(), "");
        }
        ExecutionResult result = awaitResult(username, new ExecutionRequest(harness, mode, source, specList));
        return reconcile(result, tests);
    }

    /** Checks that the worker judged exactly the tests that were sent, in order, and derives the counts ourselves. */
    private Judged reconcile(ExecutionResult r, List<TestRow> sent) {
        Verdict v = r.verdict();
        if (v == Verdict.INVALID_SUBMISSION) {
            v = Verdict.INTERNAL_ERROR; // the source was validated before queueing; a worker that disagrees is a platform problem
        }
        boolean aligned = r.tests().size() == sent.size();
        for (int i = 0; aligned && i < sent.size(); i++) {
            aligned = r.tests().get(i).visible() == sent.get(i).visible();
        }
        if (!aligned && NEEDS_ALIGNED_TESTS.contains(v)) {
            log.warn("the worker's tests do not match the tests that were sent (verdict {}); treated as an internal error", v);
            return new Judged(ExecutionResult.of(Verdict.INTERNAL_ERROR), Verdict.INTERNAL_ERROR.name(), false, 0, sent.size(), "");
        }
        int passed = 0;
        for (int i = 0; aligned && i < sent.size(); i++) {
            if (r.tests().get(i).status().equals("PASS")) {
                passed++;
            }
        }
        if (v == Verdict.ACCEPTED && (!aligned || sent.isEmpty() || passed != sent.size())) {
            log.warn("ACCEPTED reported but not every test passed; treated as an internal error");
            return new Judged(ExecutionResult.of(Verdict.INTERNAL_ERROR), Verdict.INTERNAL_ERROR.name(), false, 0, sent.size(), "");
        }
        if (v == Verdict.WRONG_ANSWER && passed == sent.size()) {
            return new Judged(ExecutionResult.of(Verdict.INTERNAL_ERROR), Verdict.INTERNAL_ERROR.name(), false, 0, sent.size(), "");
        }
        String compiler = v == Verdict.COMPILE_ERROR ? r.compilerOutput() : "";
        return new Judged(r, v.name(), aligned, passed, sent.size(), compiler);
    }

    /** Submits to the execution queue and waits for the end of the job. Admission refusals (400, 409, 429, 503) propagate unchanged. */
    private ExecutionResult awaitResult(String username, ExecutionRequest request) {
        String jobId = execution.submit(username, request);
        long deadline = System.nanoTime() + Duration.ofSeconds(executionProperties.hardTimeoutSeconds() + 30L).toNanos();
        try {
            while (true) {
                Optional<ExecutionStatus> status = execution.status(username, jobId);
                if (status.isEmpty()) {
                    return ExecutionResult.of(Verdict.INTERNAL_ERROR);
                }
                ExecutionStatus st = status.get();
                if (st.state() == ExecutionState.FINISHED || st.state() == ExecutionState.CANCELLED) {
                    return st.result() == null ? ExecutionResult.of(Verdict.INTERNAL_ERROR) : st.result();
                }
                if (System.nanoTime() > deadline) {
                    execution.cancel(username, jobId);
                    return ExecutionResult.of(Verdict.INTERNAL_ERROR);
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            execution.cancel(username, jobId);
            Thread.currentThread().interrupt();
            return ExecutionResult.of(Verdict.CANCELLED);
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private String feedback(Judged j, boolean submit) {
        return feedbackFor(j.verdict, visibleFailed(j), hiddenFailedCount(j), submit);
    }

    private static boolean visibleFailed(Judged j) {
        return j.aligned && j.result.tests().stream().anyMatch(t -> t.visible() && !t.status().equals("PASS"));
    }

    private static int hiddenFailedCount(Judged j) {
        return j.aligned ? (int) j.result.tests().stream().filter(t -> !t.visible() && !t.status().equals("PASS")).count() : 0;
    }

    static String feedbackFor(String verdict, boolean visibleFailed, int hiddenFailed, boolean submit) {
        return switch (verdict) {
            case "ACCEPTED" -> submit ? "All tests passed, including the hidden ones." : "All sample tests passed. Submit to run the hidden tests too.";
            case "WRONG_ANSWER" -> visibleFailed
                    ? "Some sample tests fail. Read the message of each failing test and compare it with the rules in the statement."
                    : "All sample tests pass, but " + hiddenFailed + " hidden test(s) fail. Hidden tests check the rules in the statement, including limits and unusual inputs. "
                      + "Re-read each numbered rule and check it against your code.";
            case "COMPILE_ERROR" -> "Your code did not compile. See the compiler output; the line numbers refer to your source.";
            case "RUNTIME_ERROR" -> "Your program ended with an error or stopped before the tests finished (for example System.exit, or an error the runner could not recover from).";
            case "TIME_LIMIT" -> "Your code did not finish in time. Look for a loop that never ends, a call that waits for something that never happens, and any real waiting such as Thread.sleep: the exercise supplies its own clock or sleeper.";
            case "MEMORY_LIMIT" -> "Your code used more memory than allowed. Look for a collection that grows without bound.";
            case "OUTPUT_LIMIT" -> "Your code printed too much. Remove debugging output.";
            case "CANCELLED" -> "The run was cancelled. It does not count as an attempt.";
            default -> "The code runner had a problem. This is not your fault and it does not count as an attempt. Try again in a moment.";
        };
    }

    private ProblemRow activeProblem(String slug) {
        return problems.findActiveBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Problem not found"));
    }

    private VersionRow currentVersion(ProblemRow problem) {
        return problems.version(problem.id(), problem.currentVersion()).orElseThrow(() -> new IllegalStateException("problem " + problem.slug() + " has no current version"));
    }

    private static byte[] sourceBytes(String sourceCode) {
        byte[] bytes = sourceCode.getBytes(StandardCharsets.UTF_8);
        SourceValidator.validate(bytes);
        return bytes;
    }

    private long userId(String username) {
        return users.findByUsername(username).map(User::getId).orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
