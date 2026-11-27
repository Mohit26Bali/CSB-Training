package com.springlab.lab;

import com.springlab.lab.sql.CapturingStatementInspector;
import com.springlab.lab.trace.Layer;
import com.springlab.lab.trace.LifecycleStepAssembler;
import com.springlab.lab.trace.LifecycleTrace;
import com.springlab.lab.trace.Phase;
import com.springlab.lab.trace.RawTrace;
import com.springlab.lab.trace.TraceCollector;
import com.springlab.lab.trace.TraceContext;
import com.springlab.lab.trace.TraceStore;
import com.springlab.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceInfrastructureTest {

    @AfterEach
    void clearContext() {
        TraceContext.clear();
    }

    // ------------------------------------------------------------- SQL capture

    @Test
    void inspectorReturnsTheStatementUnchangedWhetherOrNotATraceIsActive() {
        CapturingStatementInspector inspector = new CapturingStatementInspector();
        String sql = "select u.id from users u where u.username=?";

        assertThat(inspector.inspect(sql)).isSameAs(sql);
        TraceContext.bind(new TraceCollector());
        assertThat(inspector.inspect(sql)).isSameAs(sql);
    }

    @Test
    void inspectorRecordsOnlyWhileATraceIsBoundToTheCurrentThread() throws Exception {
        CapturingStatementInspector inspector = new CapturingStatementInspector();
        TraceCollector collector = new TraceCollector();

        inspector.inspect("select 1 -- before binding, must not be recorded");
        TraceContext.bind(collector);
        inspector.inspect("select 2 from a where x=?");
        // Another thread (another request) has no collector bound and must not leak into this trace.
        Thread other = new Thread(() -> inspector.inspect("select 3 from other_request"));
        other.start();
        other.join();
        TraceContext.clear();
        inspector.inspect("select 4 -- after clearing, must not be recorded");

        RawTrace raw = collector.build("GET", "/x", false, List.of(), 200, null);
        assertThat(raw.sql()).extracting(RawTrace.SqlEvent::sql).containsExactly("select 2 from a where x=?");
    }

    @Test
    void sqlIsAttributedToTheInnermostRepositoryCall() {
        TraceCollector collector = new TraceCollector();
        var service = collector.enter(Layer.SERVICE, "SomeService", "doWork", "declared");
        var repo = collector.enter(Layer.REPOSITORY, "SomeRepository", "findX", null);
        collector.recordSql("select from x where id=?");
        collector.exit(repo, null);
        collector.recordSql("select from y -- issued in the service after the repository returned");
        collector.exit(service, null);

        RawTrace raw = collector.build("GET", "/x", false, List.of(), 200, null);
        assertThat(raw.sql().get(0).trigger()).isEqualTo("SomeRepository.findX");
        assertThat(raw.sql().get(0).callIndex()).isEqualTo(1);
        assertThat(raw.sql().get(1).trigger()).isEqualTo("SomeService.doWork");
    }

    @Test
    void phaseSwitchesWhenTheHandlerIsEntered() {
        TraceCollector collector = new TraceCollector();
        var before = collector.enter(Layer.REPOSITORY, "UserRepository", "findAuthInfoByUsername", null);
        collector.recordSql("select ... auth lookup");
        collector.exit(before, null);
        collector.handlerEntered(new RawTrace.HandlerInfo("C", "m", "/p"), new RawTrace.AuthInfo("u", List.of()));
        var after = collector.enter(Layer.REPOSITORY, "UserRepository", "findByUsername", null);
        collector.recordSql("select ... in handler");
        collector.exit(after, null);

        RawTrace raw = collector.build("GET", "/p", true, List.of(), 200, "application/json");
        assertThat(raw.calls()).extracting(RawTrace.CallEvent::phase).containsExactly(Phase.SECURITY, Phase.HANDLER);
        assertThat(raw.sql()).extracting(RawTrace.SqlEvent::phase).containsExactly(Phase.SECURITY, Phase.HANDLER);
        assertThat(raw.handlerInvoked()).isTrue();
    }

    @Test
    void onlyTheExceptionClassNameIsRecordedNeverItsMessage() {
        TraceCollector collector = new TraceCollector();
        var call = collector.enter(Layer.SERVICE, "S", "m", null);
        collector.exit(call, new IllegalStateException("secret detail: password=hunter2"));

        RawTrace raw = collector.build("GET", "/x", false, List.of(), 500, null);
        assertThat(raw.calls().get(0).thrown()).isEqualTo("IllegalStateException");
        assertThat(raw.toString()).doesNotContain("hunter2");
    }

    // ------------------------------------------------------------- trace store

    private static final long OWNER = 1L;

    private static TraceStore store(Clock clock, int max, Duration ttl) throws Exception {
        Constructor<TraceStore> ctor = TraceStore.class.getDeclaredConstructor(Clock.class, int.class, Duration.class);
        ctor.setAccessible(true);
        return ctor.newInstance(clock, max, ttl);
    }

    private static LifecycleTrace trace(String id) {
        RawTrace raw = new RawTrace("GET", "/api/topics", false, List.of(), null, null, List.of(), List.of(), 401, null, 1);
        return new LifecycleStepAssembler().assemble(id, raw);
    }

    @Test
    void storeIsBoundedAndEvictsTheOldestTraceFirst() throws Exception {
        TraceStore store = store(new MutableClock(), 3, Duration.ofMinutes(5));
        for (int i = 1; i <= 5; i++) {
            store.put(trace("t" + i), OWNER);
        }

        assertThat(store.findOwned("t1", OWNER)).isEmpty();
        assertThat(store.findOwned("t2", OWNER)).isEmpty();
        assertThat(store.findOwned("t3", OWNER)).isPresent();
        assertThat(store.findOwned("t5", OWNER)).isPresent();
    }

    @Test
    void storedTracesExpireAfterTheTimeToLive() throws Exception {
        MutableClock clock = new MutableClock();
        TraceStore store = store(clock, 10, Duration.ofMinutes(5));
        store.put(trace("old"), OWNER);

        clock.advance(Duration.ofMinutes(4));
        assertThat(store.findOwned("old", OWNER)).isPresent();
        clock.advance(Duration.ofMinutes(2));
        assertThat(store.findOwned("old", OWNER)).isEmpty();
    }

    @Test
    void aTraceCanOnlyBeReadByItsOwner() throws Exception {
        TraceStore store = store(new MutableClock(), 10, Duration.ofMinutes(5));
        store.put(trace("mine"), 1L);

        assertThat(store.findOwned("mine", 1L)).isPresent();
        assertThat(store.findOwned("mine", 2L)).isEmpty();   // another user
        assertThat(store.findOwned("mine", 0L)).isEmpty();
        assertThat(store.findOwned("mine", -1L)).isEmpty();
        assertThat(store.findOwned("unknown", 1L)).isEmpty();
    }

    @Test
    void capacityAndExpiryApplyWhoeverTheOwnerIs() throws Exception {
        MutableClock clock = new MutableClock();
        TraceStore store = store(clock, 3, Duration.ofMinutes(5));
        store.put(trace("a1"), 1L);
        store.put(trace("b1"), 2L);
        store.put(trace("a2"), 1L);
        store.put(trace("b2"), 2L); // pushes out the oldest (a1) even though it belongs to a different user

        assertThat(store.findOwned("a1", 1L)).isEmpty();
        assertThat(store.findOwned("b1", 2L)).isPresent();
        clock.advance(Duration.ofMinutes(6));
        assertThat(store.findOwned("b1", 2L)).isEmpty();
        assertThat(store.findOwned("b2", 2L)).isEmpty();
    }
}
