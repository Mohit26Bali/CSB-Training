package com.springlab.lab.jpa;

import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.exception.ResourceNotFoundException;
import com.springlab.lab.jpa.LabJpaDtos.ExperimentRun;
import com.springlab.lab.trace.TraceCollector;
import com.springlab.lab.trace.TraceContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs the predefined JPA experiments. Safety properties, each covered by a test:
 * <ul>
 *   <li>The client chooses only an experiment id from a fixed catalogue; no SQL, entity name or id is ever accepted.</li>
 *   <li>Every run works on rows tagged with its own random run key and removes exactly those rows afterwards, so runs
 *       cannot see each other and no application table is touched.</li>
 *   <li>SQL is observed on the running thread only (the same StatementInspector as the other modules), so concurrent
 *       runs and ordinary requests are never mixed up.</li>
 *   <li>At most {@value #MAX_CONCURRENT_RUNS} runs execute at once; others wait briefly and are then refused.</li>
 * </ul>
 */
@Service
public class JpaLabService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JpaLabService.class);
    static final int MAX_CONCURRENT_RUNS = 4;

    /** Child tables first, so foreign keys never block the cleanup. */
    private static final List<String> CLEANUP = List.of(
            "delete from jpa_lab_enrollment where student_id in (select id from jpa_lab_student where run_key = ?)",
            "delete from jpa_lab_member where run_key = ?",
            "delete from jpa_lab_team where run_key = ?",
            "delete from jpa_lab_task where run_key = ?",
            "delete from jpa_lab_project where run_key = ?",
            "delete from jpa_lab_room where run_key = ?",
            "delete from jpa_lab_employee where run_key = ?",
            "delete from jpa_lab_department where run_key = ?",
            "delete from jpa_lab_profile where run_key = ?",
            "delete from jpa_lab_account where run_key = ?",
            "delete from jpa_lab_student where run_key = ?",
            "delete from jpa_lab_course where run_key = ?");

    @PersistenceContext
    private EntityManager entityManager;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final LabJpaCatalog catalog;
    private final Map<String, Experiment> experiments = new HashMap<>();
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_RUNS, true);

    public JpaLabService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, LabJpaCatalog catalog,
                         List<ExperimentSet> sets) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.catalog = catalog;
        for (ExperimentSet set : sets) {
            for (Map.Entry<String, Experiment> entry : set.experiments().entrySet()) {
                if (experiments.put(entry.getKey(), entry.getValue()) != null) {
                    throw new IllegalStateException("Duplicate experiment id " + entry.getKey());
                }
            }
        }
    }

    public java.util.Set<String> experimentIds() {
        return java.util.Set.copyOf(experiments.keySet());
    }

    public ExperimentRun run(String experimentId) {
        Experiment experiment = experiments.get(experimentId);
        if (experiment == null || catalog.find(experimentId).isEmpty()) {
            throw new ResourceNotFoundException("Unknown experiment");
        }
        try {
            if (!slots.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new BusinessRuleViolationException("Too many experiments are running right now. Try again in a moment.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleViolationException("The experiment was interrupted.");
        }
        String runKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        TraceCollector collector = new TraceCollector();
        ExperimentRecorder recorder = new ExperimentRecorder(collector);
        ExperimentContext context = new ExperimentContext(entityManager, jdbc, transactionManager, runKey, recorder);
        long started = System.nanoTime();
        TraceContext.bind(collector);
        try {
            experiment.run(context);
        } catch (Exception e) {
            log.warn("JPA lab experiment {} failed: {}", experimentId, e.getClass().getSimpleName());
            throw new IllegalStateException("The experiment could not be completed", e);
        } finally {
            TraceContext.clear();
            try {
                cleanup(runKey);
            } finally {
                slots.release();
            }
        }
        return new ExperimentRun(experimentId, "LIVE", Instant.now(), (System.nanoTime() - started) / 1_000_000,
                recorder.sql().size(), recorder.sql(), recorder.steps(), catalog.limitations());
    }

    /** Removes everything a run created, whatever happened during it. */
    void cleanup(String runKey) {
        for (String sql : CLEANUP) {
            jdbc.update(sql, runKey);
        }
    }

    /** Rows left behind by a crash are removed at startup: the lab tables contain no application data. */
    @Override
    public void run(ApplicationArguments args) {
        for (String table : List.of("jpa_lab_enrollment", "jpa_lab_member", "jpa_lab_team", "jpa_lab_task", "jpa_lab_project",
                "jpa_lab_room", "jpa_lab_employee", "jpa_lab_department", "jpa_lab_profile", "jpa_lab_account",
                "jpa_lab_student", "jpa_lab_course")) {
            jdbc.update("delete from " + table);
        }
    }
}
