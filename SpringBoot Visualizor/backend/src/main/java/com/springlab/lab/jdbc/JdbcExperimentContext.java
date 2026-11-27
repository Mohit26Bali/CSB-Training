package com.springlab.lab.jdbc;

import com.springlab.lab.trace.TraceCollector;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one JDBC experiment run may use. `verify` is a plain JdbcTemplate on the application pool that is NOT captured: it seeds
 * data and reads rows back so what the experiment shows comes from the database itself. Everything the lesson is about goes
 * through captured connections (see JdbcCapture).
 */
public final class JdbcExperimentContext {

    public final String runKey;
    public final JdbcRunRecorder rec;
    public final JdbcCapture capture;
    public final JdbcConnections connections;
    public final JdbcTemplate verify;
    public final EntityManager em;
    public final PlatformTransactionManager jpaTransactions;
    public final TraceCollector hibernate;
    private final List<Connection> opened = new ArrayList<>();
    private final Map<Connection, String> implementations = new java.util.IdentityHashMap<>();
    private final List<HikariDataSource> pools = new ArrayList<>();

    JdbcExperimentContext(String runKey, JdbcRunRecorder rec, JdbcCapture capture, JdbcConnections connections, EntityManager em,
                          PlatformTransactionManager jpaTransactions, TraceCollector hibernate) {
        this.runKey = runKey;
        this.rec = rec;
        this.capture = capture;
        this.connections = connections;
        this.verify = new JdbcTemplate(connections.applicationDataSource());
        this.em = em;
        this.jpaTransactions = jpaTransactions;
        this.hibernate = hibernate;
    }

    /** A captured connection from the application's pooled DataSource. */
    public Connection open(String label) throws SQLException {
        Connection real = connections.applicationDataSource().getConnection();
        Connection connection = capture.wrap(real, label);
        implementations.put(connection, real.getClass().getSimpleName());
        opened.add(connection);
        return connection;
    }

    /** A captured connection from any DataSource (for example a lab pool). */
    public Connection openFrom(DataSource dataSource, String label) throws SQLException {
        Connection real = dataSource.getConnection();
        Connection connection = capture.wrap(real, label);
        implementations.put(connection, real.getClass().getSimpleName());
        opened.add(connection);
        return connection;
    }

    /** Pool counts of a lab pool, read from HikariCP's own MXBean. */
    public static String poolMetrics(HikariDataSource pool) {
        var bean = pool.getHikariPoolMXBean();
        return "active=" + bean.getActiveConnections() + " idle=" + bean.getIdleConnections() + " total=" + bean.getTotalConnections()
                + " waiting=" + bean.getThreadsAwaitingConnection() + " (maximum " + pool.getMaximumPoolSize() + ")";
    }

    /** A captured, brand-new physical connection from DriverManager. */
    public Connection openWithDriverManager(String label) throws SQLException {
        Connection real = connections.driverManagerConnection();
        Connection connection = capture.wrap(real, label);
        implementations.put(connection, real.getClass().getSimpleName());
        opened.add(connection);
        return connection;
    }

    /** Simple class name of the object the DataSource or DriverManager actually returned (before capture wrapped it). */
    public String implementationOf(Connection connection) {
        return implementations.getOrDefault(connection, "unknown");
    }

    public DataSource capturingDataSource(String label) {
        return new CapturingDataSource(connections.applicationDataSource(), capture, label);
    }

    /** A dedicated bounded pool for this run; closed automatically when the run ends. */
    public HikariDataSource labPool(String name, int max, int minIdle, long timeoutMillis) {
        HikariDataSource pool = connections.newLabPool(name, max, minIdle, timeoutMillis);
        pools.add(pool);
        return pool;
    }

    /** Closes one of the connections this run opened: index 0 is the first, -1 the most recent. */
    public void closeOpened(int index) throws SQLException {
        Connection connection = opened.get(index < 0 ? opened.size() + index : index);
        connection.close();
    }

    /** Called by the service when the run ends, whatever happened. */
    void closeEverything() {
        for (Connection connection : opened) {
            try {
                if (!connection.isClosed()) {
                    if (!connection.getAutoCommit()) {
                        connection.rollback(); // never leave an open transaction on a connection that goes back to the pool
                    }
                    connection.close();
                }
            } catch (SQLException | RuntimeException ignored) {
                // already closed or broken: nothing more to release
            }
        }
        for (HikariDataSource pool : pools) {
            pool.close();
        }
    }

    // ----------------------------------------------------------------------------------------------- seeding and reading

    public long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        verify.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public long seedCustomer(String name) {
        return insert("insert into jdbc_lab_customers (run_key, name) values (?, ?)", runKey, name);
    }

    public long seedProduct(String name, String price) {
        return insert("insert into jdbc_lab_products (run_key, name, price) values (?, ?, ?)", runKey, name, new java.math.BigDecimal(price));
    }

    public long seedAccount(String owner, String balance) {
        return insert("insert into jdbc_lab_accounts (run_key, owner, balance) values (?, ?, ?)", runKey, owner, new java.math.BigDecimal(balance));
    }

    /** Rows rendered as {@code col=value col=value | ...}. The query must be a fixed lab query. */
    public String rows(String sql, Object... args) {
        List<Map<String, Object>> rows = verify.queryForList(sql, args);
        if (rows.isEmpty()) {
            return "(no rows)";
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<String> cells = new ArrayList<>();
            row.forEach((key, value) -> cells.add(key + "=" + (value == null ? "NULL" : value)));
            lines.add(String.join(" ", cells));
        }
        return String.join(" | ", lines);
    }

    public long count(String sql, Object... args) {
        Long value = verify.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    public String balances() {
        return rows("select owner, balance from jdbc_lab_accounts where run_key = ? order by id", runKey);
    }

    // -------------------------------------------------------------------------------------------- MySQL's own observations

    /** The connection's MySQL session id, read with an uncaptured statement on the same physical session. */
    public static long sessionId(Connection connection) throws SQLException {
        Connection raw = connection.unwrap(Connection.class);
        try (Statement st = raw.createStatement(); ResultSet rs = st.executeQuery("select connection_id()")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** MySQL's per-SESSION statement counters (Com_*). They count what the server itself received on this connection. */
    public static Map<String, Long> counters(Connection connection) throws SQLException {
        Connection raw = connection.unwrap(Connection.class);
        Map<String, Long> values = new LinkedHashMap<>();
        try (Statement st = raw.createStatement();
             ResultSet rs = st.executeQuery("show session status where Variable_name in ('Com_select','Com_insert','Com_update','Com_delete',"
                     + "'Com_commit','Com_rollback','Com_savepoint','Com_rollback_to_savepoint')")) {
            while (rs.next()) {
                values.put(rs.getString(1), rs.getLong(2));
            }
        }
        return values;
    }

    /** The counters that changed between two readings, for example {@code Com_insert +5, Com_commit +1}. */
    public static String delta(Map<String, Long> before, Map<String, Long> after) {
        List<String> changes = new ArrayList<>();
        after.forEach((name, value) -> {
            long d = value - before.getOrDefault(name, 0L);
            if (d != 0) {
                changes.add(name + " +" + d);
            }
        });
        return changes.isEmpty() ? "no statement counter changed" : String.join(", ", changes);
    }

    public static String describe(Throwable failure) {
        return JdbcCapture.describe(failure);
    }

    /** Value of a fixed single-value query on a captured or raw connection, without recording it. */
    public static String scalar(Connection connection, String sql) throws SQLException {
        Connection raw = connection.unwrap(Connection.class);
        try (Statement st = raw.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
