package com.springlab.lab.jdbc;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.jdbc.JdbcRunRecorder.ev;
import static com.springlab.lab.jdbc.JdbcRunRecorder.nodes;

/**
 * Topic 4: transactions, observed with TWO separate connections. Connection A does the work; connection B only looks. Every step
 * says which connection did what, and whether the transaction is open, committed or rolled back. Nothing is timed: the two
 * connections are used one after the other in a fixed order, so the outcome never depends on scheduling.
 */
@Component
public class JdbcTransactionExperiments implements JdbcExperimentSet {

    static final String AUTO = "AUTO-COMMIT: each statement commits by itself";
    static final String OPEN = "OPEN: changes exist only inside this transaction (not committed)";
    static final String COMMITTED = "COMMITTED: the changes are permanent";
    static final String ROLLED_BACK = "ROLLED BACK: the changes were discarded";

    @Override
    public Map<String, JdbcExperiment> experiments() {
        return Map.of(
                "jdbc-autocommit", this::autocommit,
                "jdbc-commit", c -> transfer(c, true),
                "jdbc-rollback", c -> transfer(c, false),
                "jdbc-rollback-on-failure", this::rollbackOnFailure,
                "jdbc-savepoint", this::savepoint,
                "jdbc-isolation", this::isolation);
    }

    /** Alice=balance, Bob=balance as seen through the given connection (a captured SELECT). */
    private static String seenBy(JdbcExperimentContext c, Connection connection) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("select owner, balance from jdbc_lab_accounts where run_key = ? order by id")) {
            ps.setString(1, c.runKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    values.add(rs.getString("owner") + "=" + rs.getBigDecimal("balance"));
                }
            }
        }
        return String.join(", ", values);
    }

    private static void update(Connection connection, String runKey, String owner, String delta) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("update jdbc_lab_accounts set balance = balance + ? where run_key = ? and owner = ?")) {
            ps.setBigDecimal(1, new java.math.BigDecimal(delta));
            ps.setString(2, runKey);
            ps.setString(3, owner);
            ps.executeUpdate();
        }
    }

    private static void ledger(Connection connection, String runKey, long account, String delta, String note) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("insert into jdbc_lab_transactions (run_key, account_id, delta, note) values (?, ?, ?, ?)")) {
            ps.setString(1, runKey);
            ps.setLong(2, account);
            ps.setBigDecimal(3, new java.math.BigDecimal(delta));
            ps.setString(4, note);
            ps.executeUpdate();
        }
    }

    private void autocommit(JdbcExperimentContext c) throws Exception {
        c.seedAccount("Alice", "100.00");
        c.seedAccount("Bob", "50.00");
        var s = new Object() { Connection a; Connection b; };
        c.rec.live("default", "A new connection starts in auto-commit mode", nodes("connectionA", "connectionB"), "connectionA", "transaction",
                "JDBC's default is auto-commit = true: every statement is its own transaction and is committed as soon as it finishes. Two "
                        + "connections are opened: A will change data, B will only look.",
                null, () -> {
                    s.a = c.open("A");
                    s.b = c.open("B");
                    return List.of(ev("Connection A autoCommit", s.a.getAutoCommit()),
                            ev("Isolation level of A", JdbcExperimentContext.scalar(s.a, "select @@transaction_isolation")),
                            ev("Transaction state of connection A", AUTO));
                });
        c.rec.live("update", "Connection A: one UPDATE, no commit() call", nodes("connectionA", "transaction", "database"), "connectionA", "transaction",
                "The program never calls commit(), yet the change is permanent: with auto-commit the driver and server commit the statement for you.",
                null, () -> {
                    update(s.a, c.runKey, "Alice", "-10.00");
                    return List.of(ev("Transaction state of connection A", AUTO), ev("Seen by connection A", seenBy(c, s.a)));
                });
        c.rec.live("other-connection", "Connection B reads", nodes("connectionB", "database"), "connectionB", "database",
                "B sees the change straight away because it was committed. Compare with the next experiments, where the change is invisible to B "
                        + "until commit().",
                null, () -> {
                    String seen = seenBy(c, s.b);
                    s.a.close();
                    s.b.close();
                    return List.of(ev("Seen by connection B", seen));
                });
    }

    private void transfer(JdbcExperimentContext c, boolean commit) throws Exception {
        long alice = c.seedAccount("Alice", "100.00");
        long bob = c.seedAccount("Bob", "50.00");
        var s = new Object() { Connection a; Connection b; Map<String, Long> before; };
        c.rec.live("begin", "Connection A: setAutoCommit(false)", nodes("connectionA", "transaction"), "connectionA", "transaction",
                "Turning auto-commit off starts a transaction scope: from now on statements are grouped until commit() or rollback(). "
                        + "No SQL has been sent yet.",
                null, () -> {
                    s.a = c.open("A");
                    s.b = c.open("B");
                    s.a.setAutoCommit(false);
                    s.before = JdbcExperimentContext.counters(s.a);
                    return List.of(ev("Connection A autoCommit", s.a.getAutoCommit()), ev("Transaction state of connection A", OPEN));
                });
        c.rec.live("operations", "Connection A: move 30.00 from Alice to Bob and write two ledger rows", nodes("connectionA", "transaction", "database"),
                "connectionA", "transaction",
                "Four statements form ONE unit of work. executeUpdate returned normally for each, but that only means MySQL applied them inside "
                        + "the open transaction: nothing is committed.",
                null, () -> {
                    update(s.a, c.runKey, "Alice", "-30.00");
                    update(s.a, c.runKey, "Bob", "30.00");
                    ledger(s.a, c.runKey, alice, "-30.00", "transfer out");
                    ledger(s.a, c.runKey, bob, "30.00", "transfer in");
                    return List.of(ev("Transaction state of connection A", OPEN), ev("Seen by connection A (inside its own transaction)", seenBy(c, s.a)));
                });
        c.rec.live("other-before", "Connection B reads while A's transaction is open", nodes("connectionB", "database"), "connectionB", "database",
                "B is a different session. Under MySQL's default REPEATABLE READ it sees only COMMITTED data, so A's uncommitted changes are invisible.",
                null, () -> List.of(ev("Seen by connection B", seenBy(c, s.b)),
                        ev("Ledger rows visible to B", c.count("select count(*) from jdbc_lab_transactions where run_key = ?", c.runKey))));
        String verb = commit ? "commit()" : "rollback()";
        c.rec.live(commit ? "commit" : "rollback", "Connection A: " + verb, nodes("connectionA", "transaction", "database"),
                commit ? "transaction" : "transaction", commit ? "database" : "connectionA",
                commit ? "commit() ends the transaction and makes ALL four statements permanent together." :
                        "rollback() ends the transaction and discards ALL four statements together, including the ones that had succeeded.",
                "jdbc-commit-rollback", () -> {
                    if (commit) {
                        s.a.commit();
                    } else {
                        s.a.rollback();
                    }
                    Map<String, Long> after = JdbcExperimentContext.counters(s.a);
                    return List.of(ev("Transaction state of connection A", commit ? COMMITTED : ROLLED_BACK),
                            ev("MySQL session counters (change during the experiment)", JdbcExperimentContext.delta(s.before, after)));
                });
        c.rec.live("other-after", "Connection B reads again", nodes("connectionB", "database"), "connectionB", "database",
                commit ? "Now B sees the transfer: commit made it visible to every session." : "B sees the original balances: the rollback left no trace.",
                null, () -> {
                    String seenB = seenBy(c, s.b);
                    String seenA = seenBy(c, s.a);
                    long rows = c.count("select count(*) from jdbc_lab_transactions where run_key = ?", c.runKey);
                    s.a.close();
                    s.b.close();
                    return List.of(ev("Seen by connection B", seenB), ev("Seen by connection A", seenA), ev("Ledger rows now", rows));
                });
    }

    private void rollbackOnFailure(JdbcExperimentContext c) throws Exception {
        c.seedAccount("Alice", "100.00");
        c.seedAccount("Bob", "50.00");
        var s = new Object() { Connection a; Connection b; };
        c.rec.live("first-operation", "Connection A: the first operation succeeds", nodes("connectionA", "transaction", "database"), "connectionA", "transaction",
                "Auto-commit is off, so this UPDATE is pending in A's transaction.", null, () -> {
                    s.a = c.open("A");
                    s.b = c.open("B");
                    s.a.setAutoCommit(false);
                    update(s.a, c.runKey, "Alice", "-30.00");
                    return List.of(ev("Transaction state of connection A", OPEN), ev("Seen by connection A", seenBy(c, s.a)));
                });
        c.rec.live("failing-operation", "Connection A: the second operation violates a foreign key", nodes("connectionA", "transaction", "database"),
                "connectionA", "transaction",
                "The INSERT refers to a customer and a product that do not exist, so MySQL refuses it (MySQL enforces the foreign key). "
                        + "Only that statement failed: the first UPDATE is still pending. Nothing rolled the transaction back for you.",
                null, () -> {
                    String outcome;
                    try (PreparedStatement ps = s.a.prepareStatement("insert into jdbc_lab_orders (run_key, customer_id, product_id, quantity) values (?, ?, ?, ?)")) {
                        ps.setString(1, c.runKey);
                        ps.setLong(2, -1);
                        ps.setLong(3, -1);
                        ps.setInt(4, 1);
                        ps.executeUpdate();
                        outcome = "accepted (unexpected)";
                    } catch (SQLException e) {
                        outcome = "refused: " + JdbcExperimentContext.describe(e);
                    }
                    return List.of(ev("Second statement", outcome), ev("Transaction state of connection A", OPEN + " (the first UPDATE is still pending)"),
                            ev("Seen by connection A right after the failure", seenBy(c, s.a)));
                });
        c.rec.live("rollback", "Connection A: rollback() in the error handler", nodes("connectionA", "transaction"), "transaction", "connectionA",
                "Code must roll back explicitly when a step of a unit of work fails (try/catch, or Spring's @Transactional which does it for you). "
                        + "Now the first UPDATE is undone too.",
                null, () -> {
                    s.a.rollback();
                    return List.of(ev("Transaction state of connection A", ROLLED_BACK), ev("Seen by connection A", seenBy(c, s.a)),
                            ev("Seen by connection B", seenBy(c, s.b)));
                });
        s.a.close();
        s.b.close();
    }

    private void savepoint(JdbcExperimentContext c) throws Exception {
        c.seedAccount("Alice", "100.00");
        c.seedAccount("Bob", "50.00");
        var s = new Object() { Connection a; Connection b; Savepoint savepoint; Map<String, Long> before; };
        c.rec.live("first-operation", "Connection A: first UPDATE, then a savepoint", nodes("connectionA", "transaction", "database"), "connectionA", "transaction",
                "A savepoint is a named point INSIDE a transaction that you can roll back to without discarding the work before it.",
                "jdbc-savepoint-set", () -> {
                    s.a = c.open("A");
                    s.b = c.open("B");
                    s.a.setAutoCommit(false);
                    s.before = JdbcExperimentContext.counters(s.a);
                    update(s.a, c.runKey, "Alice", "-10.00");
                    s.savepoint = s.a.setSavepoint("after_first_update");
                    return List.of(ev("Transaction state of connection A", OPEN), ev("Seen by connection A", seenBy(c, s.a)));
                });
        c.rec.live("second-operation", "Connection A: a second UPDATE after the savepoint", nodes("connectionA", "transaction", "database"), "connectionA", "transaction",
                "This change is also pending in the same transaction.", null, () -> {
                    update(s.a, c.runKey, "Bob", "10.00");
                    return List.of(ev("Seen by connection A", seenBy(c, s.a)));
                });
        c.rec.live("rollback-to-savepoint", "Connection A: rollback(savepoint)", nodes("connectionA", "transaction"), "transaction", "connectionA",
                "Only the work done after the savepoint is undone. The transaction is STILL OPEN and the first UPDATE is still pending.",
                "jdbc-savepoint-rollback", () -> {
                    s.a.rollback(s.savepoint);
                    return List.of(ev("Transaction state of connection A", OPEN + " (still open after rollback to the savepoint)"),
                            ev("Seen by connection A", seenBy(c, s.a)), ev("Seen by connection B", seenBy(c, s.b)));
                });
        c.rec.live("commit", "Connection A: commit()", nodes("connectionA", "transaction", "database"), "transaction", "database",
                "Committing makes what is left permanent: the first UPDATE only.", null, () -> {
                    s.a.commit();
                    Map<String, Long> after = JdbcExperimentContext.counters(s.a);
                    return List.of(ev("Transaction state of connection A", COMMITTED), ev("Seen by connection B", seenBy(c, s.b)),
                            ev("MySQL session counters (change during the experiment)", JdbcExperimentContext.delta(s.before, after)));
                });
        s.a.close();
        s.b.close();
    }

    private void isolation(JdbcExperimentContext c) throws Exception {
        long alice = c.seedAccount("Alice", "100.00");
        var s = new Object() { Connection a; Connection b; };
        String read = "select balance from jdbc_lab_accounts where id = ?";
        c.rec.live("setup", "Open a reader (A) and a writer (B)", nodes("connectionA", "connectionB"), "connectionA", "transaction",
                "The experiment sets the isolation level of A explicitly, one level at a time, and records the level MySQL reports. B changes "
                        + "data between two reads by A. The order of operations is fixed, so the result does not depend on timing.",
                null, () -> {
                    s.a = c.open("A");
                    s.b = c.open("B");
                    return List.of(ev("MySQL default isolation for a new connection", JdbcExperimentContext.scalar(s.a, "select @@transaction_isolation")),
                            ev("MySQL version", JdbcExperimentContext.scalar(s.a, "select version()")));
                });

        stage(c, s.a, s.b, alice, read, Connection.TRANSACTION_REPEATABLE_READ, "repeatable-read", "REPEATABLE READ (MySQL's default)",
                "60.00", false,
                "A reads inside its transaction, B updates and COMMITS, A reads again. A keeps seeing the snapshot taken at its first read, so "
                        + "the same query gives the same answer (a repeatable read). After A ends its transaction it sees the new value.");
        stage(c, s.a, s.b, alice, read, Connection.TRANSACTION_READ_COMMITTED, "read-committed", "READ COMMITTED",
                "40.00", false,
                "Each statement sees the data committed at the moment it runs, so A's second read sees B's committed change inside the same "
                        + "transaction: a non-repeatable read.");
        stage(c, s.a, s.b, alice, read, Connection.TRANSACTION_READ_UNCOMMITTED, "read-uncommitted", "READ UNCOMMITTED",
                "20.00", true,
                "B changes the row but does NOT commit. At this level A can still see it: a dirty read. B then rolls back, so A has read a value "
                        + "that never officially existed.");
        c.rec.live("restore", "Put the connections back to normal", nodes("connectionA", "connectionB"), "connectionA", "transaction",
                "The isolation level is restored before the connections go back to the pool.", null, () -> {
                    s.a.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                    s.a.setAutoCommit(true);
                    s.b.setAutoCommit(true);
                    s.a.close();
                    s.b.close();
                    return List.of();
                });
        c.rec.infer("summary", "What each level allowed in this run", nodes("transaction", "database"),
                "Concluded from the readings above (this run, MySQL InnoDB): REPEATABLE READ kept A's view stable; READ COMMITTED let A see B's committed "
                        + "change inside one transaction; READ UNCOMMITTED let A see B's uncommitted change. SERIALIZABLE and the finer points of phantom "
                        + "reads and locking are not run here.",
                List.of(ev("Not run", "SERIALIZABLE, phantom reads, locking behaviour")));
    }

    private void stage(JdbcExperimentContext c, Connection a, Connection b, long account, String read, int level, String id, String name,
                       String newBalance, boolean uncommitted, String explanation) throws Exception {
        c.rec.live(id, "Isolation level " + name, nodes("connectionA", "connectionB", "transaction", "database"), "connectionA", "transaction", explanation,
                null, () -> {
                    List<String> log = new ArrayList<>();
                    a.setTransactionIsolation(level);
                    a.setAutoCommit(false);
                    log.add("A isolation reported by MySQL: " + JdbcExperimentContext.scalar(a, "select @@transaction_isolation"));
                    String first = value(a, read, account);
                    b.setAutoCommit(!uncommitted);
                    try (PreparedStatement ps = b.prepareStatement("update jdbc_lab_accounts set balance = ? where id = ?")) {
                        ps.setBigDecimal(1, new java.math.BigDecimal(newBalance));
                        ps.setLong(2, account);
                        ps.executeUpdate();
                    }
                    String second = value(a, read, account);
                    if (uncommitted) {
                        b.rollback();
                        b.setAutoCommit(true);
                    }
                    a.commit(); // end A's first transaction; the next read starts a new one
                    String third = value(a, read, account);
                    a.commit(); // end that one too, so the next stage's isolation level applies to a fresh transaction
                    String after = uncommitted ? "B rolled back its change" : "B committed its change";
                    return List.of(ev("A reads first", first), ev("B updates the row (" + after + ")", "to " + newBalance + (uncommitted ? " (uncommitted at the moment A reads)" : "")),
                            ev("A reads again, still in the same transaction", second), ev("A reads after finishing its transaction", third),
                            ev("Observed", observation(first, second, uncommitted)), ev("Isolation level", log.get(0)));
                });
    }

    private static String observation(String first, String second, boolean uncommittedWrite) {
        if (first.equals(second)) {
            return "A's two reads in one transaction were identical (a stable snapshot)";
        }
        return uncommittedWrite ? "A saw a change B had not committed (a dirty read)" : "A saw a change B had committed during A's transaction (a non-repeatable read)";
    }

    private static String value(Connection connection, String sql, long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1).toPlainString();
            }
        }
    }
}
