package com.springlab.coding.execution;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Admission, queueing, cancellation and failure handling of {@link ExecutionService}, against a backend the test controls. */
class ExecutionServiceTest {

    private final List<ExecutionService> services = new ArrayList<>();

    @AfterEach
    void stop() {
        services.forEach(ExecutionService::shutdown);
    }

    private ExecutionService service(ExecutionBackend backend, int workers, int queue) {
        ExecutionService s = new ExecutionService(backend, new ExecutionProperties(ExecutionProperties.Backend.DISABLED, workers, queue, 60,
                new ExecutionProperties.Wsl("wsl.exe", null, null)));
        services.add(s);
        return s;
    }

    private static ExecutionRequest request() {
        return new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.SUBMIT, "public final class RetryExecutor {}".getBytes(StandardCharsets.UTF_8));
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("condition not reached in time");
            }
            Thread.sleep(10);
        }
    }

    private static ExecutionStatus awaitFinished(ExecutionService s, String user, String id) throws InterruptedException {
        await(() -> s.status(user, id).map(st -> st.state() == ExecutionState.FINISHED || st.state() == ExecutionState.CANCELLED).orElse(false));
        return s.status(user, id).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ defaults and refusals

    @Test
    void theDisabledBackendRefusesEverythingAndStartsNothing() {
        ExecutionService s = service(new DisabledBackend(), 4, 8);
        assertThat(s.availability().available()).isFalse();
        assertThatThrownBy(() -> s.submit("ann", request())).isInstanceOf(ExecutionUnavailableException.class)
                .extracting(e -> ((ExecutionRejectedException) e).status().value()).isEqualTo(503);
        assertThat(s.inFlightCount()).isZero();
        assertThat(s.peakRunning()).isZero();
    }

    @Test
    void aBackendThatIsDownIsRefusedBeforeAnySlotIsTaken() {
        FakeBackend backend = new FakeBackend();
        backend.availability = Availability.down("fake", "wsl stopped");
        ExecutionService s = service(backend, 2, 2);
        assertThatThrownBy(() -> s.submit("ann", request())).isInstanceOf(ExecutionUnavailableException.class);
        assertThat(s.inFlightCount()).isZero();
        assertThat(backend.started.get()).isZero();
    }

    @Test
    void anUnavailableBackendIsLoggedForTheOperatorOncePerReasonWithoutLearnerData() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExecutionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            FakeBackend backend = new FakeBackend();
            backend.availability = Availability.down("fake", "base-dir is not acceptable");
            ExecutionService s = service(backend, 2, 2);
            ExecutionRequest r = new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.RUN, "SECRET-SOURCE-TEXT".getBytes(StandardCharsets.UTF_8));
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> s.submit("ann", r)).isInstanceOf(ExecutionUnavailableException.class).hasMessageNotContaining("base-dir");
            }
            List<String> warnings = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(warnings).hasSize(1);
            assertThat(warnings.get(0)).contains("base-dir is not acceptable").doesNotContain("SECRET-SOURCE-TEXT");
            backend.availability = Availability.down("fake", "wsl is stopped");
            assertThatThrownBy(() -> s.submit("ann", r)).isInstanceOf(ExecutionUnavailableException.class);
            assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count()).as("a new reason is logged again").isEqualTo(2);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void anInvalidSourceIsRefusedBeforeQueueingAndCostsNothing() {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 2, 2);
        byte[][] bad = {
                new byte[0],
                new byte[SourceValidator.MAX_SOURCE_BYTES + 1],
                new byte[]{(byte) 0xC3, (byte) 0x28},                    // not UTF-8
                "class A {}\0".getBytes(StandardCharsets.UTF_8),         // NUL
        };
        for (byte[] source : bad) {
            ExecutionRequest r = new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.RUN, source);
            assertThatThrownBy(() -> s.submit("ann", r)).isInstanceOf(InvalidSourceException.class)
                    .extracting(e -> ((ExecutionRejectedException) e).status().value()).isEqualTo(400);
        }
        assertThat(s.inFlightCount()).isZero();
        assertThat(backend.started.get()).isZero();
        // and the user is not locked out afterwards
        backend.open();
        assertThat(s.submit("ann", request())).hasSize(16);
    }

    // ------------------------------------------------------------------------------------------------ per-user and global limits

    @Test
    void aUserCanHaveOneJobInFlightAndGetsItsSlotBackWhenItEnds() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 2, 2);
        String first = s.submit("ann", request());
        assertThatThrownBy(() -> s.submit("ann", request())).isInstanceOf(ExecutionBusyException.class)
                .extracting(e -> ((ExecutionRejectedException) e).status().value()).isEqualTo(409);
        assertThat(s.submit("bob", request())).isNotEqualTo(first); // someone else is not affected
        backend.open();
        awaitFinished(s, "ann", first);
        assertThat(s.submit("ann", request())).isNotNull();
    }

    @Test
    void whenWorkersAndQueueAreFullTheNextSubmissionIs429AndLeavesNothingBehind() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 2, 3);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(s.submit("user" + i, request()));
        }
        await(() -> s.runningCount() == 2);
        assertThatThrownBy(() -> s.submit("overflow", request())).isInstanceOf(ExecutionCapacityException.class)
                .extracting(e -> ((ExecutionRejectedException) e).status().value()).isEqualTo(429);
        assertThat(s.inFlightCount()).isEqualTo(5);
        assertThat(s.status("overflow", ids.get(0))).isEmpty();
        backend.open();
        for (int i = 0; i < 5; i++) {
            assertThat(awaitFinished(s, "user" + i, ids.get(i)).result().verdict()).isEqualTo(Verdict.ACCEPTED);
        }
        assertThat(s.inFlightCount()).isZero();
        assertThat(backend.maxConcurrent.get()).as("never more than the configured workers at once").isLessThanOrEqualTo(2);
        assertThat(s.submit("overflow", request())).isNotNull(); // capacity is back
    }

    @Test
    void admissionIsAtomicUnderContentionNeverMoreThanWorkersPlusQueue() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 4, 6);
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<String>> outcomes = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String user = "u" + i;
            outcomes.add(pool.submit((Callable<String>) () -> {
                barrier.await();
                try {
                    return s.submit(user, request());
                } catch (ExecutionCapacityException e) {
                    return "429";
                }
            }));
        }
        int accepted = 0;
        int rejected = 0;
        for (Future<String> f : outcomes) {
            if (f.get().equals("429")) {
                rejected++;
            } else {
                accepted++;
            }
        }
        pool.shutdown();
        assertThat(accepted).isEqualTo(10);
        assertThat(rejected).isEqualTo(90);
        await(() -> s.runningCount() == 4);
        backend.open();
        await(() -> s.inFlightCount() == 0);
        assertThat(backend.maxConcurrent.get()).isLessThanOrEqualTo(4);
        assertThat(s.peakRunning()).isLessThanOrEqualTo(4);
        assertThat(backend.started.get()).isEqualTo(10);
    }

    @Test
    void aUserRacingWithItselfGetsExactlyOneJob() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 4, 20);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger busy = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                barrier.await();
                try {
                    s.submit("same-user", request());
                    accepted.incrementAndGet();
                } catch (ExecutionBusyException e) {
                    busy.incrementAndGet();
                }
                return null;
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(accepted.get()).isEqualTo(1);
        assertThat(busy.get()).isEqualTo(threads - 1);
        backend.open();
    }

    // ------------------------------------------------------------------------------------------------ ownership

    @Test
    void anotherUsersJobBehavesLikeAnUnknownId() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 2, 2);
        String id = s.submit("ann", request());
        assertThat(s.status("bob", id)).isEmpty();
        assertThat(s.cancel("bob", id)).isEmpty();
        assertThat(s.status("ann", "0000000000000000")).isEmpty();
        assertThat(s.status("ann", id)).isPresent();
        backend.open();
    }

    @Test
    void theResultIsAbsentWhileTheJobIsNotFinished() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 1, 1);
        String id = s.submit("ann", request());
        await(() -> s.status("ann", id).orElseThrow().state() == ExecutionState.RUNNING);
        assertThat(s.status("ann", id).orElseThrow().result()).isNull();
        backend.open();
        assertThat(awaitFinished(s, "ann", id).result().verdict()).isEqualTo(Verdict.ACCEPTED);
    }

    // ------------------------------------------------------------------------------------------------ cancellation

    @Test
    void cancellingAQueuedJobRemovesItWithoutEverRunningIt() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 1, 3);
        s.submit("runner", request());
        await(() -> s.runningCount() == 1);
        String queued = s.submit("ann", request());
        assertThat(s.status("ann", queued).orElseThrow().state()).isEqualTo(ExecutionState.QUEUED);
        ExecutionStatus cancelled = s.cancel("ann", queued).orElseThrow();
        assertThat(cancelled.state()).isEqualTo(ExecutionState.CANCELLED);
        assertThat(cancelled.result().verdict()).isEqualTo(Verdict.CANCELLED);
        assertThat(s.inFlightCount()).isEqualTo(1);
        assertThat(s.submit("ann", request())).isNotNull(); // its slot is free again
        backend.open();
        await(() -> s.inFlightCount() == 0);
        assertThat(backend.executed).doesNotContain(queued);
    }

    @Test
    void cancellingARunningJobKillsItsSandboxAndNeverReportsAnAcceptance() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.ignoreInterrupt = true; // the worst case: the backend keeps going and later returns ACCEPTED
        ExecutionService s = service(backend, 2, 2);
        String id = s.submit("ann", request());
        await(() -> s.status("ann", id).orElseThrow().state() == ExecutionState.RUNNING);
        s.cancel("ann", id);
        assertThat(backend.cancelled).contains(id);
        backend.open(); // now the backend returns ACCEPTED
        ExecutionStatus done = awaitFinished(s, "ann", id);
        assertThat(done.state()).isEqualTo(ExecutionState.CANCELLED);
        assertThat(done.result().verdict()).isEqualTo(Verdict.CANCELLED);
        assertThat(done.result().tests()).isEmpty();
        assertThat(s.inFlightCount()).isZero();
        assertThat(s.submit("ann", request())).isNotNull();
    }

    @Test
    void cancellingAFinishedJobChangesNothing() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.open();
        ExecutionService s = service(backend, 2, 2);
        String id = s.submit("ann", request());
        awaitFinished(s, "ann", id);
        ExecutionStatus after = s.cancel("ann", id).orElseThrow();
        assertThat(after.state()).isEqualTo(ExecutionState.FINISHED);
        assertThat(after.result().verdict()).isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void shuttingDownCancelsEveryUnfinishedJobAndNothingIsReportedAsAccepted() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService s = service(backend, 1, 2);
        String running = s.submit("a", request());
        await(() -> s.runningCount() == 1);
        String queued = s.submit("b", request());
        s.shutdown();
        assertThat(backend.cancelled).contains(running, queued);
        assertThatThrownBy(() -> s.submit("c", request())).isInstanceOf(ExecutionUnavailableException.class);
        for (String[] pair : new String[][]{{"a", running}, {"b", queued}}) {
            Optional<ExecutionStatus> st = s.status(pair[0], pair[1]);
            assertThat(st.orElseThrow().result() == null || st.get().result().verdict() != Verdict.ACCEPTED).isTrue();
        }
    }

    @Test
    void afterARestartNoPreviousJobIsKnownSoNothingCanBeReportedAsAccepted() throws Exception {
        FakeBackend backend = new FakeBackend();
        ExecutionService before = service(backend, 2, 2);
        String id = before.submit("ann", request());
        await(() -> before.runningCount() == 1);
        ExecutionService after = service(new FakeBackend(), 2, 2); // a new process: new state
        assertThat(after.status("ann", id)).isEmpty();
        assertThat(after.cancel("ann", id)).isEmpty();
        backend.open();
    }

    // ------------------------------------------------------------------------------------------------ failures

    @Test
    void aBackendExceptionIsAnInternalErrorAndTheSlotIsReleased() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.failWith = new IllegalStateException("worker crashed with secret detail");
        backend.open();
        ExecutionService s = service(backend, 2, 2);
        String id = s.submit("ann", request());
        ExecutionStatus st = awaitFinished(s, "ann", id);
        assertThat(st.state()).isEqualTo(ExecutionState.FINISHED);
        assertThat(st.result().verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
        assertThat(st.result().toString()).doesNotContain("secret detail");
        assertThat(s.inFlightCount()).isZero();
    }

    @Test
    void aNullResultIsAnInternalError() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.result = () -> null;
        backend.open();
        ExecutionService s = service(backend, 2, 2);
        String id = s.submit("ann", request());
        assertThat(awaitFinished(s, "ann", id).result().verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @Test
    void verdictsFromTheBackendPassThroughUnchanged() throws Exception {
        for (Verdict verdict : List.of(Verdict.TIME_LIMIT, Verdict.COMPILE_ERROR, Verdict.WRONG_ANSWER, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT, Verdict.RUNTIME_ERROR)) {
            FakeBackend backend = new FakeBackend();
            backend.result = () -> ExecutionResult.of(verdict, 12);
            backend.open();
            ExecutionService s = service(backend, 1, 1);
            String id = s.submit("ann", request());
            assertThat(awaitFinished(s, "ann", id).result().verdict()).isEqualTo(verdict);
        }
    }

    // ------------------------------------------------------------------------------------------------ logging

    @Test
    void learnerSourceIsNeverWrittenToTheLog() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(ExecutionService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previous = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        try {
            FakeBackend backend = new FakeBackend();
            backend.failWith = new IllegalStateException("boom");
            backend.open();
            ExecutionService s = service(backend, 1, 1);
            String marker = "UNIQUE-LEARNER-MARKER-9f2c";
            ExecutionRequest r = new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.SUBMIT,
                    ("public final class RetryExecutor { String s = \"" + marker + "\"; }").getBytes(StandardCharsets.UTF_8));
            String id = s.submit("ann", r);
            awaitFinished(s, "ann", id);
            assertThat(r.toString()).doesNotContain(marker);
            assertThat(s.toString()).doesNotContain(marker);
            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list).noneMatch(e -> e.getFormattedMessage().contains(marker));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
        }
    }

    @Test
    void jobIdsAreSixteenHexCharactersAndUnique() throws Exception {
        FakeBackend backend = new FakeBackend();
        backend.open();
        ExecutionService s = service(backend, 4, 50);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < 40; i++) {
            String id = s.submit("u" + i, request());
            assertThat(id).matches("[0-9a-f]{16}");
            ids.add(id);
        }
        assertThat(ids).hasSize(40);
        CountDownLatch done = new CountDownLatch(1);
        done.countDown();
        await(() -> s.inFlightCount() == 0);
    }
}
