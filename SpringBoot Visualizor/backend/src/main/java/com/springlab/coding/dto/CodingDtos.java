package com.springlab.coding.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Everything a learner can receive from the coding API. None of these records has a field for a hidden test's name, input, expected
 * value or result detail, for the reference solution, for a worker path, or for process and sandbox information: what is not a field
 * cannot be serialised by mistake. Hidden tests appear only as counts.
 */
public final class CodingDtos {

    private CodingDtos() {
    }

    public record TopicResponse(String slug, String name, String description, int problemCount) {
    }

    public record ProblemSummary(String slug, String title, String difficulty, String topicSlug, String topicName, String language,
                                 String summary, int version, boolean accepted, int attempts) {
    }

    public record Example(String title, String input, String output, String explanation) {
    }

    /** A visible test, described in words. Hidden tests have no counterpart here. */
    public record SampleTest(String name, String input, String expected) {
    }

    public record ProblemDetail(String slug, String title, String difficulty, String topicSlug, String topicName, String language, int version,
                                String summary, String statement, List<String> objectives, String constraints, List<Example> examples,
                                String starterCode, List<SampleTest> sampleTests, int hiddenTestCount) {
    }

    /** The source is the only thing a learner sends. The problem comes from the URL slug and the tests from the database. */
    public record SourceRequest(@NotNull @Size(min = 1, max = 70_000) String sourceCode) {
        @Override
        public String toString() {
            return "SourceRequest[sourceChars=" + (sourceCode == null ? 0 : sourceCode.length()) + "]";
        }
    }

    /** message is present only for a visible test. */
    public record TestOutcomeView(String name, String status, String message) {
    }

    /** The answer to a sample run: visible tests only, never stored, never counted. */
    public record RunResponse(String verdict, int passed, int total, List<TestOutcomeView> tests, String compilerOutput, String feedback) {
    }

    public record SubmissionResponse(long id, String problemSlug, int problemVersion, String verdict, int passed, int total,
                                     List<TestOutcomeView> visibleTests, int hiddenTotal, int hiddenPassed, String compilerOutput,
                                     String feedback, Instant submittedAt) {
    }

    /** The owner's own submission in full, including the source they sent. */
    public record SubmissionDetail(long id, String problemSlug, int problemVersion, String verdict, int passed, int total,
                                   List<TestOutcomeView> visibleTests, int hiddenTotal, int hiddenPassed, String compilerOutput,
                                   String feedback, Instant submittedAt, String sourceCode) {
    }

    public record SubmissionSummary(long id, int problemVersion, String verdict, int passed, int total, Instant submittedAt) {
    }

    public record ProblemProgress(String slug, String title, int attempts, boolean accepted, Instant acceptedAt, String latestVerdict,
                                  long latestSubmissionId, Instant latestAt) {
    }

    /** Computed only from stored FULL submissions; sample runs are not in it. */
    public record ProgressResponse(int problemsAttempted, int problemsAccepted, int totalFullSubmissions, List<ProblemProgress> problems) {
    }
}
