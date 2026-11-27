package com.springlab.lab.micro.run;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence of run records. State changes are compare-and-set updates ({@code where state = :expected}), so the database, not
 * application memory, decides which of several concurrent requests wins a transition. This store uses the plain application
 * DataSource (the run's bookkeeping is not part of what the learner observes).
 */
@Repository
class MicroRunStore {

    record RunRow(String runKey, String owner, String experimentId, String whatIfJson, RunState state, String resultJson, Instant createdAt,
                  Instant updatedAt) {
    }

    /** Child tables first, so foreign keys never block the cleanup. A fixed list: nothing here comes from a client. */
    static final List<String> LAB_TABLES = List.of("micro_lab_saga_steps", "micro_lab_reservations", "micro_lab_outbox", "micro_lab_processed_events",
            "micro_lab_payments", "micro_lab_notifications", "micro_lab_orders", "micro_lab_stock", "micro_lab_registry", "micro_lab_products",
            "micro_lab_users");

    private final JdbcTemplate jdbc;

    MicroRunStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    void insert(String runKey, String owner, String experimentId, String whatIfJson) {
        jdbc.update("insert into micro_lab_runs (run_key, owner, experiment_id, what_if, state) values (?, ?, ?, ?, 'CREATED')",
                runKey, owner, experimentId, whatIfJson);
    }

    /** The run if it exists, belongs to {@code owner} and has not expired. A foreign or unknown run is indistinguishable. */
    Optional<RunRow> find(String runKey, String owner, int lifetimeMinutes) {
        List<RunRow> rows = jdbc.query(
                "select run_key, owner, experiment_id, what_if, state, result_json, created_at, updated_at from micro_lab_runs "
                        + "where run_key = ? and owner = ? and created_at > (now(6) - interval ? minute)",
                (rs, n) -> new RunRow(rs.getString("run_key"), rs.getString("owner"), rs.getString("experiment_id"), rs.getString("what_if"),
                        RunState.valueOf(rs.getString("state")), rs.getString("result_json"), rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                runKey, owner, lifetimeMinutes);
        return rows.stream().findFirst();
    }

    /** Moves a run from {@code from} to {@code to}; false when it was no longer in {@code from} (somebody else moved it first). */
    boolean transition(String runKey, String owner, RunState from, RunState to) {
        if (!from.canMoveTo(to)) {
            throw new IllegalStateException("Illegal run transition " + from + " -> " + to);
        }
        return jdbc.update("update micro_lab_runs set state = ? where run_key = ? and owner = ? and state = ?", to.name(), runKey, owner, from.name()) == 1;
    }

    /** EXECUTING -> COMPLETED/FAILED together with the stored result, in one statement. */
    boolean finish(String runKey, RunState to, String resultJson) {
        if (!RunState.EXECUTING.canMoveTo(to)) {
            throw new IllegalStateException("Illegal run transition EXECUTING -> " + to);
        }
        return jdbc.update("update micro_lab_runs set state = ?, result_json = ? where run_key = ? and state = 'EXECUTING'", to.name(), resultJson, runKey) == 1;
    }

    /** COMPLETED/FAILED -> CREATED, discarding the stored result. */
    boolean reset(String runKey, String owner, RunState from) {
        if (!from.canMoveTo(RunState.CREATED)) {
            throw new IllegalStateException("Illegal run transition " + from + " -> CREATED");
        }
        return jdbc.update("update micro_lab_runs set state = 'CREATED', result_json = null where run_key = ? and owner = ? and state = ?",
                runKey, owner, from.name()) == 1;
    }

    /** Deletes this owner's {@code count} oldest FINISHED runs (a finished run holds only its stored result). Unfinished runs are never touched. */
    int deleteOldestFinished(String owner, int count) {
        List<String> keys = jdbc.queryForList("select run_key from micro_lab_runs where owner = ? and state in ('COMPLETED', 'FAILED') order by created_at limit ?",
                String.class, owner, count);
        for (String key : keys) {
            deleteRows(key);
            jdbc.update("delete from micro_lab_runs where run_key = ?", key);
        }
        return keys.size();
    }

    int countRecent(String owner, int lifetimeMinutes) {
        Integer n = jdbc.queryForObject("select count(*) from micro_lab_runs where owner = ? and created_at > (now(6) - interval ? minute)", Integer.class,
                owner, lifetimeMinutes);
        return n == null ? 0 : n;
    }

    int deleteRows(String runKey) {
        int removed = 0;
        for (String table : LAB_TABLES) {
            removed += jdbc.update("delete from " + table + " where run_key = ?", runKey);
        }
        return removed;
    }

    /** Deletes expired runs and everything they own. Returns the number of rows removed. */
    int deleteExpired(int lifetimeMinutes) {
        List<String> keys = jdbc.queryForList("select run_key from micro_lab_runs where created_at <= (now(6) - interval ? minute)", String.class, lifetimeMinutes);
        int removed = 0;
        for (String key : keys) {
            removed += deleteRows(key);
            removed += jdbc.update("delete from micro_lab_runs where run_key = ?", key);
        }
        return removed;
    }

    /** Rows of the service tables older than {@code minutes}, left by a crashed run (active runs are younger). */
    int deleteStaleServiceRows(int minutes) {
        int removed = 0;
        for (String table : LAB_TABLES) {
            removed += jdbc.update("delete from " + table + " where created_at < (now(6) - interval ? minute)", minutes);
        }
        return removed;
    }

    /** A run still EXECUTING after {@code minutes} was interrupted (for example by a crash): mark it FAILED. */
    int failInterrupted(int minutes) {
        return jdbc.update("update micro_lab_runs set state = 'FAILED', result_json = null where state = 'EXECUTING' and updated_at < (now(6) - interval ? minute)",
                minutes);
    }
}
