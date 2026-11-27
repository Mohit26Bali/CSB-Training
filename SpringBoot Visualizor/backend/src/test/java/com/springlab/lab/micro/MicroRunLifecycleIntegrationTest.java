package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.run.ExperimentMeta;
import com.springlab.lab.micro.run.MicroExperiment;
import com.springlab.lab.micro.run.MicroRunService;
import com.springlab.lab.micro.run.RunContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The run lifecycle with deterministic helper experiments: execute-once, conflicts, reset, ownership, expiry, failure and recovery.
 * Nothing here sleeps: the blocking experiment waits on latches that the test controls.
 */
@Import(MicroRunLifecycleIntegrationTest.Helpers.class)
class MicroRunLifecycleIntegrationTest extends AbstractMicroLabTest {

    static final AtomicInteger EXECUTIONS = new AtomicInteger();
    static final java.util.List<String> TTL_RESULTS = new java.util.concurrent.CopyOnWriteArrayList<>();
    static volatile CountDownLatch started = new CountDownLatch(1);
    static volatile CountDownLatch release = new CountDownLatch(1);

    /** Each helper writes a real row through a real service so cleanup and "business writes happen once" are observable. */
    @TestConfiguration
    static class Helpers {
        @Bean
        MicroExperiment counting() {
            return new Helper("test-counting", ctx -> {
                EXECUTIONS.incrementAndGet();
                seedProduct(ctx);
            });
        }

        @Bean
        MicroExperiment blocking() {
            return new Helper("test-blocking", ctx -> {
                EXECUTIONS.incrementAndGet();
                seedProduct(ctx);
                started.countDown();
                if (!release.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("never released");
                }
            });
        }

        @Bean
        MicroExperiment failing() {
            return new Helper("test-failing", ctx -> {
                EXECUTIONS.incrementAndGet();
                seedProduct(ctx);
                throw new IllegalStateException("secret-message-that-must-not-leak");
            });
        }

        /**
         * Probes the registry expiry rule at EXACT ages. The heartbeat stamp is the simulated time at which the register call STARTED; every
         * probe is a single call made at stamp + age, so the age the registry sees is exactly the one being tested.
         */
        @Bean
        MicroExperiment ttlBoundary() {
            return new Helper("test-ttl", ctx -> {
                var bus = ctx.bus();
                for (long age : new long[] {2999, 3000, 3001}) {
                    bus.startRequest();
                    long stamp = bus.clock().now();
                    bus.call(null, com.springlab.lab.micro.contract.RegistryContract.REGISTER, new com.springlab.lab.micro.contract.RegistryContract.Register("list-" + age, "a", "sim://a"));
                    bus.clock().advanceTo(stamp + age);
                    var listed = bus.call(null, com.springlab.lab.micro.contract.RegistryContract.LIST, new com.springlab.lab.micro.contract.RegistryContract.Query("list-" + age));
                    var view = listed.value().instances().get(0);
                    TTL_RESULTS.add("list@" + age + ":healthy=" + view.healthy() + ":age=" + view.ageMillis());

                    long stamp2 = bus.clock().now();
                    bus.call(null, com.springlab.lab.micro.contract.RegistryContract.REGISTER, new com.springlab.lab.micro.contract.RegistryContract.Register("res-" + age, "a", "sim://a"));
                    bus.clock().advanceTo(stamp2 + age);
                    var resolved = bus.call(null, com.springlab.lab.micro.contract.RegistryContract.RESOLVE, new com.springlab.lab.micro.contract.RegistryContract.Query("res-" + age));
                    TTL_RESULTS.add("resolve@" + age + ":" + resolved.outcome());
                }
            });
        }

        /** The reaper uses the same rule: at exactly the TTL it removes nothing; later it removes the instance. */
        @Bean
        MicroExperiment ttlEvict() {
            return new Helper("test-ttl-evict", ctx -> {
                var bus = ctx.bus();
                bus.startRequest();
                long stamp = bus.clock().now();
                bus.call(null, com.springlab.lab.micro.contract.RegistryContract.REGISTER, new com.springlab.lab.micro.contract.RegistryContract.Register("ev", "a", "sim://a"));
                bus.clock().advanceTo(stamp + 3000);
                TTL_RESULTS.add("evict@3000=" + bus.call(null, com.springlab.lab.micro.contract.RegistryContract.EVICT_EXPIRED, new com.springlab.lab.micro.contract.RegistryContract.Evict()).value().count());
                TTL_RESULTS.add("evict-later=" + bus.call(null, com.springlab.lab.micro.contract.RegistryContract.EVICT_EXPIRED, new com.springlab.lab.micro.contract.RegistryContract.Evict()).value().count());
            });
        }

        static void seedProduct(RunContext ctx) {
            ctx.bus().startRequest();
            ctx.bus().call(null, ProductContract.SEED_PRODUCT, new ProductContract.SeedProduct("SKU-T", "Thing", new BigDecimal("1.00")));
        }
    }

    interface Body {
        void run(RunContext ctx) throws Exception;
    }

    record Helper(String id, Body body) implements MicroExperiment {
        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta(id, "resilience", "Helper " + id, "q", "c", "services", List.of());
        }

        @Override
        public void run(RunContext context) throws Exception {
            body.run(context);
        }
    }

    @Autowired
    MicroRunService service;

    @BeforeEach
    void resetCounters() {
        EXECUTIONS.set(0);
        started = new CountDownLatch(1);
        release = new CountDownLatch(1);
    }

    // ---- authentication and input validation

    @Test
    void everyRouteRequiresAToken() throws Exception {
        for (String path : List.of("/api/lab/microservices/meta", "/api/lab/microservices/topics", "/api/lab/microservices/experiments",
                "/api/lab/microservices/runs/" + "0".repeat(32))) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isUnauthorized());
        }
        for (String path : List.of("/api/lab/microservices/runs", "/api/lab/microservices/runs/" + "0".repeat(32) + "/execute",
                "/api/lab/microservices/runs/" + "0".repeat(32) + "/reset")) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void invalidCreateRequestsAreRefused() throws Exception {
        postJson("/api/lab/microservices/runs", student, "{\"experimentId\":\"nope\"}").andExpect(status().isNotFound());
        postJson("/api/lab/microservices/runs", student, "{\"experimentId\":\"comm-failure-semantics\",\"whatIf\":{\"unknownOption\":\"1\"}}")
                .andExpect(status().isBadRequest());
        postJson("/api/lab/microservices/runs", student, "{\"experimentId\":\"comm-failure-semantics\",\"whatIf\":{\"callTimeoutMillis\":\"5\"}}")
                .andExpect(status().isBadRequest());
        postJson("/api/lab/microservices/runs", student, "{\"experimentId\":\"comm-failure-semantics\",\"whatIf\":{\"callTimeoutMillis\":\"1 or 1=1\"}}")
                .andExpect(status().isBadRequest());
        postJson("/api/lab/microservices/runs", student, "{\"experimentId\":\"comm-failure-semantics\",\"whatIf\":{\"bulkheadPolicy\":\"everything\"}}")
                .andExpect(status().isBadRequest());
        postJson("/api/lab/microservices/runs", student, "{\"experimentId\":\"Robert'); drop table users;--\"}").andExpect(status().isBadRequest());
        postJson("/api/lab/microservices/runs", student, "{\"experimentId\":\"\"}").andExpect(status().isBadRequest());
        postJson("/api/lab/microservices/runs", student, "{}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from micro_lab_runs where owner = ?", Long.class, student.getUsername())).isZero();
    }

    @Test
    void malformedRunIdsAreAllJustNotFound() throws Exception {
        for (String id : List.of("nope", "0".repeat(31), "0".repeat(33), "0".repeat(32), "G".repeat(32), "1_or_1")) {
            getAs("/api/lab/microservices/runs/" + id, student).andExpect(status().isNotFound());
            postJson("/api/lab/microservices/runs/" + id + "/execute", student, null).andExpect(status().isNotFound());
            postJson("/api/lab/microservices/runs/" + id + "/reset", student, null).andExpect(status().isNotFound());
        }
        // An encoded slash is refused even earlier, by the servlet container's HTTP firewall: never a 200 or a 500.
        for (String tricky : List.of("..%2F..%2Fadmin", "1%20or%201=1")) {
            assertThat(getAs("/api/lab/microservices/runs/" + tricky, student).andReturn().getResponse().getStatus()).isIn(400, 404);
        }
    }

    // ---- lifecycle

    @Test
    void aNewRunIsCreatedWithoutExecutingAnything() throws Exception {
        JsonNode run = createRun(student, "test-counting");
        assertThat(run.get("state").asText()).isEqualTo("CREATED");
        assertThat(run.get("runId").asText()).matches("[0-9a-f]{32}");
        assertThat(run.has("result")).isFalse();
        assertThat(EXECUTIONS).hasValue(0);
        assertThat(json(getAs("/api/lab/microservices/runs/" + run.get("runId").asText(), student).andExpect(status().isOk())).get("state").asText())
                .isEqualTo("CREATED");
    }

    @Test
    void executingRunsTheExperimentOnceAndCleansUp() throws Exception {
        JsonNode created = createRun(student, "test-counting");
        JsonNode done = execute(student, created.get("runId").asText());
        assertThat(done.get("state").asText()).isEqualTo("COMPLETED");
        assertThat(EXECUTIONS).hasValue(1);
        assertThat(done.get("result").get("cleanupRowsRemoved").asInt()).isEqualTo(1);
        assertThat(labRows()).isZero();
        assertThat(done.get("result").get("steps")).isEmpty();
    }

    @Test
    void aRepeatedExecuteReturnsTheStoredResultAndNeverRunsAgain() throws Exception {
        String id = createRun(student, "test-counting").get("runId").asText();
        JsonNode first = execute(student, id);
        JsonNode second = execute(student, id);
        JsonNode third = execute(student, id);
        assertThat(EXECUTIONS).as("business operations ran exactly once").hasValue(1);
        assertThat(second.get("result")).isEqualTo(first.get("result"));
        assertThat(third.get("result")).isEqualTo(first.get("result"));
        assertThat(second.get("state").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void resetAllowsAnExplicitSecondExecutionOnly() throws Exception {
        String id = createRun(student, "test-counting").get("runId").asText();
        execute(student, id);
        JsonNode reset = json(postJson("/api/lab/microservices/runs/" + id + "/reset", student, null).andExpect(status().isOk()));
        assertThat(reset.get("state").asText()).isEqualTo("CREATED");
        assertThat(reset.has("result")).isFalse();
        assertThat(json(postJson("/api/lab/microservices/runs/" + id + "/reset", student, null).andExpect(status().isOk())).get("state").asText())
                .as("resetting a CREATED run is harmless").isEqualTo("CREATED");
        execute(student, id);
        assertThat(EXECUTIONS).hasValue(2);
        assertThat(labRows()).isZero();
    }

    @Test
    void eightSimultaneousExecutesOfOneRunExecuteItExactlyOnce() throws Exception {
        String id = createRun(student, "test-counting").get("runId").asText();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> postJson("/api/lab/microservices/runs/" + id + "/execute", student, null)
                        .andReturn().getResponse().getStatus()));
            }
            int ok = 0;
            for (Future<Integer> f : futures) {
                int code = f.get();
                assertThat(code).isIn(200, 409);
                if (code == 200) {
                    ok++;
                }
            }
            assertThat(ok).isGreaterThanOrEqualTo(1);
        } finally {
            pool.shutdown();
        }
        assertThat(EXECUTIONS).as("no matter how requests interleave, the work happens once").hasValue(1);
        assertThat(json(getAs("/api/lab/microservices/runs/" + id, student).andExpect(status().isOk())).get("state").asText()).isEqualTo("COMPLETED");
        assertThat(labRows()).isZero();
    }

    @Test
    void whileARunIsExecutingItCanBeReadButNotExecutedOrResetAndFinishesNormally() throws Exception {
        String id = createRun(student, "test-blocking").get("runId").asText();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> running = pool.submit(() -> postJson("/api/lab/microservices/runs/" + id + "/execute", student, null)
                    .andReturn().getResponse().getStatus());
            assertThat(started.await(20, TimeUnit.SECONDS)).isTrue();

            assertThat(json(getAs("/api/lab/microservices/runs/" + id, student).andExpect(status().isOk())).get("state").asText()).isEqualTo("EXECUTING");
            postJson("/api/lab/microservices/runs/" + id + "/execute", student, null).andExpect(status().isConflict());
            postJson("/api/lab/microservices/runs/" + id + "/reset", student, null).andExpect(status().isConflict());
            assertThat(labRows()).as("the run's rows exist while it executes").isGreaterThan(0);

            release.countDown();
            assertThat(running.get()).isEqualTo(200);
        } finally {
            release.countDown();
            pool.shutdown();
        }
        assertThat(EXECUTIONS).hasValue(1);
        assertThat(json(getAs("/api/lab/microservices/runs/" + id, student).andExpect(status().isOk())).get("state").asText()).isEqualTo("COMPLETED");
        assertThat(labRows()).isZero();
    }

    @Test
    void aFailingExperimentEndsFailedWithoutLeakingTheMessageAndStillCleansUp() throws Exception {
        String id = createRun(student, "test-failing").get("runId").asText();
        JsonNode failed = execute(student, id);
        assertThat(failed.get("state").asText()).isEqualTo("FAILED");
        assertThat(failed.get("result").get("errorCode").asText()).isEqualTo("IllegalStateException");
        assertThat(failed.toString()).doesNotContain("secret-message");
        assertThat(failed.get("result").get("cleanupRowsRemoved").asInt()).isEqualTo(1);
        assertThat(labRows()).isZero();
        assertThat(json(postJson("/api/lab/microservices/runs/" + id + "/execute", student, null).andExpect(status().isOk())).get("state").asText())
                .as("a failed run is not silently retried").isEqualTo("FAILED");
        assertThat(EXECUTIONS).hasValue(1);
    }

    // ---- ownership

    @Test
    void anotherUserCannotReadExecuteOrResetARunAndLearnsNothingAboutIt() throws Exception {
        User other = newUser("micro_lab_other_" + System.nanoTime(), Role.STUDENT);
        User teacher = newUser("micro_lab_teach_" + System.nanoTime(), Role.TEACHER);
        String id = createRun(student, "test-counting").get("runId").asText();
        for (User intruder : List.of(other, teacher)) {
            getAs("/api/lab/microservices/runs/" + id, intruder).andExpect(status().isNotFound());
            postJson("/api/lab/microservices/runs/" + id + "/execute", intruder, null).andExpect(status().isNotFound());
            postJson("/api/lab/microservices/runs/" + id + "/reset", intruder, null).andExpect(status().isNotFound());
        }
        assertThat(EXECUTIONS).hasValue(0);
        assertThat(json(getAs("/api/lab/microservices/runs/" + id, student).andExpect(status().isOk())).get("state").asText()).isEqualTo("CREATED");
        execute(student, id);
        getAs("/api/lab/microservices/runs/" + id, other).andExpect(status().isNotFound());
    }

    @Test
    void theNotFoundForAForeignRunIsIdenticalToTheNotFoundForAnUnknownRun() throws Exception {
        User other = newUser("micro_lab_other2_" + System.nanoTime(), Role.STUDENT);
        String id = createRun(student, "test-counting").get("runId").asText();
        String foreign = getAs("/api/lab/microservices/runs/" + id, other).andReturn().getResponse().getContentAsString();
        String unknown = getAs("/api/lab/microservices/runs/" + "a".repeat(32), other).andReturn().getResponse().getContentAsString();
        // Everything except the echoed request path (which contains the id the caller itself sent) must be identical.
        com.fasterxml.jackson.databind.node.ObjectNode a = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(foreign);
        com.fasterxml.jackson.databind.node.ObjectNode b = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(unknown);
        a.remove("instance");
        b.remove("instance");
        assertThat(a.toString()).isEqualTo(b.toString());
    }

    // ---- limits, expiry and recovery

    @Test
    void aUserCannotHoldMoreThanTheLiveRunLimit() throws Exception {
        for (int i = 0; i < 20; i++) {
            createRun(student, "test-counting");
        }
        postJson("/api/lab/microservices/runs", student, "{\"experimentId\":\"test-counting\"}").andExpect(status().isConflict());
        User other = newUser("micro_lab_other3_" + System.nanoTime(), Role.STUDENT);
        createRun(other, "test-counting");
    }

    @Test
    void anExpiredRunDisappearsAndRecoveryDeletesIt() throws Exception {
        String id = createRun(student, "test-counting").get("runId").asText();
        jdbc.update("update micro_lab_runs set created_at = now(6) - interval 31 minute where run_key = ?", id);
        getAs("/api/lab/microservices/runs/" + id, student).andExpect(status().isNotFound());
        postJson("/api/lab/microservices/runs/" + id + "/execute", student, null).andExpect(status().isNotFound());
        service.recover();
        assertThat(jdbc.queryForObject("select count(*) from micro_lab_runs where run_key = ?", Long.class, id)).isZero();
    }

    @Test
    void recoveryMarksAnInterruptedRunFailedAndRemovesOnlyStaleRows() throws Exception {
        String stuck = createRun(student, "test-counting").get("runId").asText();
        jdbc.update("update micro_lab_runs set state = 'EXECUTING', updated_at = now(6) - interval 11 minute where run_key = ?", stuck);
        String active = createRun(student, "test-counting").get("runId").asText();
        jdbc.update("update micro_lab_runs set state = 'EXECUTING' where run_key = ?", active);
        jdbc.update("insert into micro_lab_products (run_key, sku, name, price, created_at) values (?, 'OLD', 'old', 1, now(6) - interval 11 minute)", stuck);
        jdbc.update("insert into micro_lab_products (run_key, sku, name, price) values (?, 'NEW', 'new', 1)", active);

        service.recover();

        assertThat(json(getAs("/api/lab/microservices/runs/" + stuck, student)).get("state").asText()).isEqualTo("FAILED");
        assertThat(json(getAs("/api/lab/microservices/runs/" + active, student)).get("state").asText()).as("an active run is never touched").isEqualTo("EXECUTING");
        assertThat(jdbc.queryForObject("select count(*) from micro_lab_products where run_key = ?", Long.class, stuck)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from micro_lab_products where run_key = ?", Long.class, active)).isEqualTo(1);
        jdbc.update("delete from micro_lab_products where run_key = ?", active);
    }

    @Test
    void theRegistryExpiryRuleIsInclusiveAtTheTtlAndStaleOneMillisecondLater() throws Exception {
        TTL_RESULTS.clear();
        assertThat(execute(student, createRun(student, "test-ttl").get("runId").asText()).get("state").asText()).isEqualTo("COMPLETED");
        assertThat(TTL_RESULTS).containsExactly(
                "list@2999:healthy=true:age=2999", "resolve@2999:SUCCESS",
                "list@3000:healthy=true:age=3000", "resolve@3000:SUCCESS",
                "list@3001:healthy=false:age=3001", "resolve@3001:BUSINESS_FAILURE");
    }

    @Test
    void theReaperAppliesTheSameRule() throws Exception {
        TTL_RESULTS.clear();
        assertThat(execute(student, createRun(student, "test-ttl-evict").get("runId").asText()).get("state").asText()).isEqualTo("COMPLETED");
        assertThat(TTL_RESULTS).containsExactly("evict@3000=0", "evict-later=1");
    }

    @Test
    void finishedRunsMakeRoomWhenTheLimitIsReachedButUnfinishedOnesNeverDo() throws Exception {
        for (int i = 0; i < 25; i++) {
            execute(student, createRun(student, "test-counting").get("runId").asText());
        }
        assertThat(EXECUTIONS).hasValue(25);
        assertThat(jdbc.queryForObject("select count(*) from micro_lab_runs where owner = ?", Long.class, student.getUsername())).isLessThanOrEqualTo(20);

        User other = newUser("micro_lab_other4_" + System.nanoTime(), Role.STUDENT);
        List<String> unfinished = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            unfinished.add(createRun(other, "test-counting").get("runId").asText());
        }
        postJson("/api/lab/microservices/runs", other, "{\"experimentId\":\"test-counting\"}").andExpect(status().isConflict());
        for (String id : unfinished) {
            assertThat(json(getAs("/api/lab/microservices/runs/" + id, other).andExpect(status().isOk())).get("state").asText()).isEqualTo("CREATED");
        }
    }
}
