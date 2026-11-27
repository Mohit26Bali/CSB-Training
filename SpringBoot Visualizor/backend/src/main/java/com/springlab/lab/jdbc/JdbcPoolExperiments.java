package com.springlab.lab.jdbc;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.jdbc.JdbcRunRecorder.ev;
import static com.springlab.lab.jdbc.JdbcRunRecorder.nodes;

/**
 * Topic 5: connection pooling with HikariCP. Every experiment creates its OWN small pool with explicit limits and closes it when
 * the run ends. The application's own pool is only read (counts) and never changed or exhausted. The pool's limits come from the
 * experiment, never from the browser.
 */
@Component
public class JdbcPoolExperiments implements JdbcExperimentSet {

    @Override
    public Map<String, JdbcExperiment> experiments() {
        return Map.of(
                "pool-acquire-release", this::acquireRelease,
                "pool-multiple", this::multiple,
                "pool-exhaustion", this::exhaustion);
    }

    private void acquireRelease(JdbcExperimentContext c) throws Exception {
        HikariDataSource pool = c.labPool("jdbc-lab-pool-1", 1, 1, 1000);
        var s = new Object() { long first; Connection connection; };
        c.rec.observe("pool-created", "A dedicated lab pool with one connection", nodes("pool", "connection"),
                "HikariCP created a pool owned by this experiment: at most 1 connection. It is separate from the application's pool, whose counts "
                        + "are shown read-only for comparison.",
                List.of(ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)), ev("Application pool (read-only)", c.connections.applicationPoolSnapshot())));
        c.rec.live("acquire", "dataSource.getConnection()", nodes("application", "pool", "connection"), "application", "pool",
                "The pool lends its only connection. The thread now owns it until it calls close().",
                "jdbc-lab-pool", () -> {
                    s.connection = c.openFrom(pool, "P");
                    s.first = JdbcExperimentContext.sessionId(s.connection);
                    return List.of(ev("Object returned", c.implementationOf(s.connection)), ev("MySQL session id", s.first),
                            ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)));
                });
        c.rec.live("release", "connection.close() returns it to the pool", nodes("connection", "pool"), "connection", "pool",
                "close() on a pooled connection does NOT end the MySQL session. It marks the connection idle again so someone else can borrow it.",
                null, () -> {
                    s.connection.close();
                    long alive = c.count("select count(*) from information_schema.processlist where id = ?", s.first);
                    return List.of(ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)), ev("MySQL session still present after close()", alive == 1));
                });
        c.rec.live("reacquire", "getConnection() again", nodes("application", "pool", "connection"), "application", "pool",
                "The pool has exactly one connection, so the second call is served by the same physical session: reuse.",
                null, () -> {
                    Connection again = c.openFrom(pool, "P");
                    long second = JdbcExperimentContext.sessionId(again);
                    again.close();
                    return List.of(ev("MySQL session id", second), ev("Same physical session as the first call", second == s.first),
                            ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)));
                });
    }

    private void multiple(JdbcExperimentContext c) throws Exception {
        HikariDataSource pool = c.labPool("jdbc-lab-pool-3", 3, 1, 2000);
        var s = new Object() { Connection[] connections = new Connection[3]; long[] ids = new long[3]; };
        c.rec.observe("pool-created", "A dedicated lab pool that may grow to three", nodes("pool", "connection"),
                "minimumIdle is 1 and maximumPoolSize is 3: the pool starts small and opens more connections only when they are needed.",
                List.of(ev("Lab pool", JdbcExperimentContext.poolMetrics(pool))));
        c.rec.live("acquire-three", "Borrow three connections at the same time", nodes("application", "pool", "connection", "database"), "application", "pool",
                "Each borrowed connection is a different MySQL session, because a connection can only be lent to one caller at a time. "
                        + "The pool opens the extra ones on demand.",
                null, () -> {
                    for (int i = 0; i < 3; i++) {
                        s.connections[i] = c.openFrom(pool, "P" + (i + 1));
                        s.ids[i] = JdbcExperimentContext.sessionId(s.connections[i]);
                    }
                    return List.of(ev("MySQL session ids", s.ids[0] + ", " + s.ids[1] + ", " + s.ids[2]),
                            ev("All three are different sessions", s.ids[0] != s.ids[1] && s.ids[1] != s.ids[2] && s.ids[0] != s.ids[2]),
                            ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)));
                });
        c.rec.live("release-three", "Return all three", nodes("connection", "pool"), "connection", "pool",
                "All three go back to the pool as idle connections; the pool keeps them open for reuse.", null, () -> {
                    for (Connection connection : s.connections) {
                        connection.close();
                    }
                    return List.of(ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)));
                });
        c.rec.infer("sizing", "What the pool size means", nodes("pool"),
                "The maximum is a ceiling on how many sessions this application may hold at the same time, shared by every thread. It protects MySQL "
                        + "(each session costs memory) and also limits how many requests can talk to the database simultaneously. This is concluded from "
                        + "the metrics above; the lab does not measure performance.",
                List.of(ev("Connections this pool may ever hold", 3)));
    }

    private void exhaustion(JdbcExperimentContext c) throws Exception {
        HikariDataSource pool = c.labPool("jdbc-lab-pool-2", 2, 2, 500);
        var s = new Object() { Connection first; Connection second; long firstId; };
        c.rec.live("borrow-all", "Borrow every connection the pool has", nodes("application", "pool", "connection"), "application", "pool",
                "The pool is limited to 2 connections. The code below never returns them, which is exactly what a connection leak looks like.",
                null, () -> {
                    s.first = c.openFrom(pool, "P1");
                    s.second = c.openFrom(pool, "P2");
                    s.firstId = JdbcExperimentContext.sessionId(s.first);
                    return List.of(ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)));
                });
        c.rec.live("third-request", "Ask for a third connection", nodes("application", "pool"), "application", "pool",
                "No connection is free and the pool may not grow. The caller waits for the configured connectionTimeout (500 ms here) and then gets "
                        + "an exception: this is pool exhaustion as the application sees it.",
                null, () -> {
                    long start = System.nanoTime();
                    String outcome;
                    try {
                        c.openFrom(pool, "P3");
                        outcome = "a connection was handed out (unexpected)";
                    } catch (SQLException e) {
                        outcome = "refused: " + JdbcExperimentContext.describe(e);
                    }
                    long waited = (System.nanoTime() - start) / 1_000_000;
                    return List.of(ev("Outcome", outcome), ev("Waited about (ms)", waited), ev("Configured connectionTimeout (ms)", pool.getConnectionTimeout()),
                            ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)));
                });
        c.rec.live("release-one", "Return one connection, then ask again", nodes("connection", "pool", "application"), "connection", "pool",
                "As soon as one connection is returned, the next request is served immediately: the pool recovers without any restart.",
                null, () -> {
                    s.first.close();
                    Connection again = c.openFrom(pool, "P3");
                    long id = JdbcExperimentContext.sessionId(again);
                    return List.of(ev("Request served", true), ev("It reused the session that was returned", id == s.firstId),
                            ev("Lab pool", JdbcExperimentContext.poolMetrics(pool)));
                });
        c.rec.concept("real-life", "What this looks like in a real application", nodes("application", "pool"),
                "In a web application each request thread needs a connection while it talks to the database. When connections are leaked or held "
                        + "too long, requests queue behind the pool and finally fail with a timeout like this one. The fix is to close connections (try-with-resources, "
                        + "or let Spring do it) and to keep transactions short, not just to raise the maximum. This step is an explanation.");
    }
}
