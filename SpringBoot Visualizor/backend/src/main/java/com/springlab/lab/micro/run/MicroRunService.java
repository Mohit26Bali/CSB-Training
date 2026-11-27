package com.springlab.lab.micro.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.exception.InvalidRequestException;
import com.springlab.exception.ResourceNotFoundException;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.MicroDtos.CreateRunRequest;
import com.springlab.lab.micro.run.MicroDtos.Limits;
import com.springlab.lab.micro.run.MicroDtos.Meta;
import com.springlab.lab.micro.run.MicroDtos.MicroResult;
import com.springlab.lab.micro.run.MicroDtos.RunView;
import com.springlab.lab.micro.run.MicroDtos.ServiceInfo;
import com.springlab.lab.micro.run.MicroRunStore.RunRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Creates, executes, reads and resets Microservices Lab runs. Safety properties, each covered by a test:
 * <ul>
 *   <li>The client chooses an experiment id and What-If values from a fixed catalogue: never SQL, a table, an id or a URL.</li>
 *   <li>A run belongs to the user who created it. Anyone else gets the same 404 as for an unknown or expired run.</li>
 *   <li>Execution is guarded by a database compare-and-set CREATED to EXECUTING, so a run's business operations run at most once:
 *       a repeated execute returns the stored result, and a concurrent one gets 409. Only an explicit reset allows a second execution.</li>
 *   <li>Every run removes exactly its own rows (by its random key) when it ends, whether it succeeded or failed, and reports how many.</li>
 *   <li>Runs expire after {@value #RUN_LIFETIME_MINUTES} minutes; rows left by a crash and runs stuck in EXECUTING are cleaned up at
 *       startup, but only when older than {@value #STALE_MINUTES} minutes, so a run that is still active is never touched.</li>
 *   <li>At most {@value #MAX_CONCURRENT_EXECUTIONS} runs execute at once and a user may hold {@value #MAX_RUNS_PER_USER} live runs.</li>
 *   <li>The workflow of a run is NOT wrapped in one database transaction: each service commits its own short local transaction.</li>
 * </ul>
 */
@Service
public class MicroRunService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MicroRunService.class);

    static final int MAX_CONCURRENT_EXECUTIONS = 4;
    static final int MAX_RUNS_PER_USER = 20;
    static final int RUN_LIFETIME_MINUTES = 30;
    static final int STALE_MINUTES = 10;

    static final String ENVIRONMENT = "Simulated in-process services: one Java process, no network, no message broker, no separate deployments.";
    static final List<String> LIMITATIONS = List.of(
            "All services run in the SpringLab JVM. Calls are method calls through a typed service bus: nothing is sent over a network or serialized.",
            "Latency, timeouts, unavailability and lost requests are SIMULATED with a logical clock and injected behaviour. Measured times are real elapsed times of in-process calls.",
            "Each service owns its own tables, but all tables live in one MySQL schema: logical isolation, not separate database servers.",
            "Spans and correlation ids illustrate distributed tracing inside one process. This is not OpenTelemetry and nothing is propagated over a network.",
            "Resilience patterns are SpringLab's own minimal state machines, not a resilience library.");

    private final MicroRunStore store;
    private final HandlerRegistry handlers;
    private final DataSource dataSource;
    private final ObjectMapper mapper;
    private final Map<String, MicroExperiment> experiments = new LinkedHashMap<>();
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_EXECUTIONS, true);

    public MicroRunService(MicroRunStore store, HandlerRegistry handlers, DataSource dataSource, ObjectMapper mapper,
                           List<MicroExperiment> experimentBeans) {
        this.store = store;
        this.handlers = handlers;
        this.dataSource = dataSource;
        this.mapper = mapper;
        for (MicroExperiment experiment : experimentBeans) {
            ExperimentMeta meta = experiment.meta();
            if (!MicroTopics.exists(meta.topic())) {
                throw new IllegalStateException("Experiment " + meta.id() + " names an unknown topic " + meta.topic());
            }
            if (experiments.put(meta.id(), experiment) != null) {
                throw new IllegalStateException("Duplicate microservices experiment id " + meta.id());
            }
        }
    }

    public Meta meta() {
        return new Meta(ENVIRONMENT, Arrays.stream(ServiceName.values()).map(ServiceInfo::of).toList(),
                new Limits(MAX_CONCURRENT_EXECUTIONS, MAX_RUNS_PER_USER, RUN_LIFETIME_MINUTES, ServiceBus.MAX_CALLS), LIMITATIONS);
    }

    public List<ExperimentMeta> catalog() {
        return experiments.values().stream().map(MicroExperiment::meta).toList();
    }

    public RunView create(String owner, CreateRunRequest request) {
        MicroExperiment experiment = experiments.get(request.experimentId());
        if (experiment == null) {
            throw new ResourceNotFoundException("Unknown experiment");
        }
        Map<String, String> resolved = resolveWhatIf(experiment.meta(), request.whatIf() == null ? Map.of() : request.whatIf());
        int live = store.countRecent(owner, RUN_LIFETIME_MINUTES);
        if (live >= MAX_RUNS_PER_USER) {
            // Finished runs only hold a stored result, so the oldest of them make room. Runs that are not finished are never discarded.
            live -= store.deleteOldestFinished(owner, live - MAX_RUNS_PER_USER + 1);
        }
        if (live >= MAX_RUNS_PER_USER) {
            throw new BusinessRuleViolationException("You already have " + MAX_RUNS_PER_USER + " runs that are not finished. Execute or reset some, or wait for them to expire.");
        }
        String runKey = UUID.randomUUID().toString().replace("-", "");
        store.insert(runKey, owner, request.experimentId(), json(resolved));
        return view(require(owner, runKey));
    }

    public RunView get(String owner, String runId) {
        return view(require(owner, runId));
    }

    /**
     * Executes a CREATED run exactly once. A run that already finished returns its stored result without executing anything; a run
     * that is executing right now is a conflict.
     */
    public RunView execute(String owner, String runId) {
        RunRow row = require(owner, runId);
        if (row.state() == RunState.EXECUTING) {
            throw new BusinessRuleViolationException("This run is already executing.");
        }
        if (row.state().isFinished()) {
            return view(row);
        }
        MicroExperiment experiment = experiments.get(row.experimentId());
        if (experiment == null) {
            throw new ResourceNotFoundException("Unknown experiment");
        }
        acquireSlot();
        try {
            if (!store.transition(row.runKey(), owner, RunState.CREATED, RunState.EXECUTING)) {
                RunRow now = require(owner, runId); // somebody else moved it first
                if (now.state().isFinished()) {
                    return view(now);
                }
                throw new BusinessRuleViolationException("This run is already executing.");
            }
            runExperiment(row, experiment);
        } finally {
            slots.release();
        }
        return view(require(owner, runId));
    }

    public RunView reset(String owner, String runId) {
        RunRow row = require(owner, runId);
        if (row.state() == RunState.EXECUTING) {
            throw new BusinessRuleViolationException("This run is executing and cannot be reset right now.");
        }
        if (row.state().isFinished() && !store.reset(row.runKey(), owner, row.state())) {
            throw new BusinessRuleViolationException("This run changed while it was being reset. Try again.");
        }
        store.deleteRows(row.runKey()); // idempotent safety net: a finished run has none left
        return view(require(owner, runId));
    }

    private void runExperiment(RunRow row, MicroExperiment experiment) {
        Map<String, String> whatIf = readMap(row.whatIfJson());
        RunContext context = new RunContext(row.runKey(), row.owner(), row.experimentId(), whatIf, handlers, dataSource);
        long started = System.nanoTime();
        RunState end = RunState.COMPLETED;
        String errorCode = null;
        MicroRunContext.bind(context);
        try {
            experiment.run(context);
        } catch (Exception e) {
            end = RunState.FAILED;
            errorCode = e.getClass().getSimpleName(); // the class only: a message could contain data
            log.warn("Microservices lab experiment {} failed: {}", row.experimentId(), errorCode);
        } finally {
            MicroRunContext.clear();
        }
        int removed = 0;
        try {
            removed = store.deleteRows(row.runKey());
        } catch (RuntimeException e) {
            log.warn("Cleanup of microservices lab run failed: {}", e.getClass().getSimpleName());
            end = RunState.FAILED;
            errorCode = errorCode == null ? "CleanupFailed" : errorCode;
        }
        String resultJson = null;
        try {
            MicroResult result = new MicroResult(ENVIRONMENT, errorCode, (System.nanoTime() - started) / 1_000_000, context.recorder().sql().size(),
                    context.recorder().sql(), context.recorder().steps(), context.traces().spans(), context.traces().calls(), removed, LIMITATIONS);
            resultJson = mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            end = RunState.FAILED;
        }
        if (!store.finish(row.runKey(), end, resultJson)) {
            log.warn("Microservices lab run {} was not EXECUTING when it finished", row.experimentId());
        }
    }

    // ---- helpers

    private RunRow require(String owner, String runId) {
        if (runId == null || !runId.matches("[0-9a-f]{32}")) {
            throw new ResourceNotFoundException("Unknown or expired run");
        }
        return store.find(runId, owner, RUN_LIFETIME_MINUTES).orElseThrow(() -> new ResourceNotFoundException("Unknown or expired run"));
    }

    private Map<String, String> resolveWhatIf(ExperimentMeta meta, Map<String, String> requested) {
        Map<String, String> resolved = new TreeMap<>();
        for (String key : requested.keySet()) {
            if (meta.whatIfs().stream().noneMatch(o -> o.key().equals(key))) {
                throw new InvalidRequestException("This experiment has no what-if option '" + key + "'");
            }
        }
        for (WhatIfOption option : meta.whatIfs()) {
            String raw = requested.get(option.key());
            resolved.put(option.key(), raw == null ? option.defaultValue() : option.validate(raw));
        }
        return resolved;
    }

    private RunView view(RunRow row) {
        MicroResult result = null;
        if (row.resultJson() != null) {
            try {
                result = mapper.readValue(row.resultJson(), MicroResult.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Stored run result could not be read", e);
            }
        }
        return new RunView(row.runKey(), row.experimentId(), row.state().name(), readMap(row.whatIfJson()), row.createdAt(), row.updatedAt(), result);
    }

    private String json(Map<String, String> map) {
        try {
            return mapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> readMap(String json) {
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored what-if values could not be read", e);
        }
    }

    private void acquireSlot() {
        try {
            if (!slots.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new BusinessRuleViolationException("Too many experiments are running right now. Try again in a moment.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleViolationException("The execution was interrupted.");
        }
    }

    /** Startup recovery and the same logic for tests: interrupted runs fail, expired runs and stale service rows disappear. */
    public int recover() {
        int interrupted = store.failInterrupted(STALE_MINUTES);
        int removed = store.deleteExpired(RUN_LIFETIME_MINUTES) + store.deleteStaleServiceRows(STALE_MINUTES);
        if (interrupted > 0 || removed > 0) {
            log.info("Microservices lab recovery: {} interrupted run(s) marked FAILED, {} stale row(s) removed", interrupted, removed);
        }
        return removed;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }
}
