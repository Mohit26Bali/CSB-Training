package com.springlab.lab.jdbc;

import com.springlab.exception.BusinessRuleViolationException;
import com.springlab.exception.ResourceNotFoundException;
import com.springlab.lab.jdbc.JdbcLabDtos.ExperimentRun;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs the predefined JDBC experiments. Safety properties, each covered by a test:
 * <ul>
 *   <li>The client chooses only an experiment id from a fixed catalogue: never SQL, a table, a column, a URL or credentials.</li>
 *   <li>Every run works on rows tagged with its own random run key and removes exactly those rows afterwards (child tables
 *       first), whether it succeeded or failed. The number removed is reported in the result.</li>
 *   <li>Rows left behind by a crash are removed at startup, but only rows older than {@value #STALE_MINUTES} minutes, so a run
 *       that is still active (for example on another node) is never touched.</li>
 *   <li>At most {@value #MAX_CONCURRENT_RUNS} runs execute at once.</li>
 * </ul>
 */
@Service
public class JdbcLabService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JdbcLabService.class);
    static final int MAX_CONCURRENT_RUNS = 4;
    static final int STALE_MINUTES = 10;

    /** Child tables first, so foreign keys never block the cleanup. Fixed list: nothing here comes from a client. */
    private static final List<String> TABLES = List.of("jdbc_lab_orders", "jdbc_lab_transactions", "jdbc_lab_batch_records",
            "jdbc_lab_accounts", "jdbc_lab_products", "jdbc_lab_customers");

    @PersistenceContext
    private EntityManager entityManager;
    private final JdbcConnections connections;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager jpaTransactions;
    private final JdbcLabCatalog catalog;
    private final Map<String, JdbcExperiment> experiments = new HashMap<>();
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_RUNS, true);

    public JdbcLabService(JdbcConnections connections, PlatformTransactionManager jpaTransactions, JdbcLabCatalog catalog,
                          List<JdbcExperimentSet> sets) {
        this.connections = connections;
        this.jdbc = new JdbcTemplate(connections.applicationDataSource());
        this.jpaTransactions = jpaTransactions;
        this.catalog = catalog;
        for (JdbcExperimentSet set : sets) {
            for (Map.Entry<String, JdbcExperiment> entry : set.experiments().entrySet()) {
                if (experiments.put(entry.getKey(), entry.getValue()) != null) {
                    throw new IllegalStateException("Duplicate JDBC experiment id " + entry.getKey());
                }
            }
        }
    }

    public Set<String> experimentIds() {
        return Set.copyOf(experiments.keySet());
    }

    public ExperimentRun run(String experimentId) {
        JdbcExperiment experiment = experiments.get(experimentId);
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
        JdbcEventLog events = new JdbcEventLog();
        TraceCollector hibernate = new TraceCollector();
        JdbcRunRecorder recorder = new JdbcRunRecorder(events, hibernate);
        JdbcExperimentContext context = new JdbcExperimentContext(runKey, recorder, new JdbcCapture(events, runKey), connections,
                entityManager, jpaTransactions, hibernate);
        long started = System.nanoTime();
        int removed;
        TraceContext.bind(hibernate); // the JPA path of the comparison experiments is observed with the existing inspector
        try {
            experiment.run(context);
        } catch (Exception e) {
            log.warn("JDBC lab experiment {} failed: {}", experimentId, e.getClass().getSimpleName());
            throw new IllegalStateException("The experiment could not be completed", e);
        } finally {
            TraceContext.clear();
            try {
                context.closeEverything();
            } finally {
                try {
                    removed = cleanup(runKey);
                } finally {
                    slots.release();
                }
            }
        }
        return new ExperimentRun(experimentId, "LIVE", Instant.now(), (System.nanoTime() - started) / 1_000_000, recorder.sql().size(),
                recorder.sql(), recorder.steps(), removed, catalog.catalog().limitations());
    }

    /** Removes everything one run created. Returns the number of rows removed. */
    int cleanup(String runKey) {
        int removed = 0;
        for (String table : TABLES) {
            removed += jdbc.update("delete from " + table + " where run_key = ?", runKey);
        }
        return removed;
    }

    /** Removes lab rows older than the given age (left by a crashed run). Active runs are younger and are not touched. */
    public int cleanupStale(Duration olderThan) {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(olderThan));
        int removed = 0;
        for (String table : TABLES) {
            removed += jdbc.update("delete from " + table + " where created_at < ?", cutoff);
        }
        return removed;
    }

    @Override
    public void run(ApplicationArguments args) {
        int removed = cleanupStale(Duration.ofMinutes(STALE_MINUTES));
        if (removed > 0) {
            log.info("Removed {} stale JDBC lab row(s) left by an earlier run", removed);
        }
    }
}
