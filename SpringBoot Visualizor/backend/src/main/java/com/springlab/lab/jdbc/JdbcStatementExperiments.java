package com.springlab.lab.jdbc;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.jdbc.JdbcRunRecorder.ev;
import static com.springlab.lab.jdbc.JdbcRunRecorder.nodes;

/** Topic 2: Statement, PreparedStatement, ResultSet, SQL injection and resource management. */
@Component
public class JdbcStatementExperiments implements JdbcExperimentSet {

    @Override
    public Map<String, JdbcExperiment> experiments() {
        return Map.of(
                "jdbc-statement-select", this::statementSelect,
                "jdbc-prepared-select", this::preparedSelect,
                "jdbc-sql-injection", this::sqlInjection,
                "jdbc-execute-methods", this::executeMethods,
                "jdbc-resource-closing", this::resourceClosing);
    }

    private void seedThree(JdbcExperimentContext c) {
        c.seedCustomer("Ada");
        c.seedCustomer("Grace");
        c.seedCustomer("Alan");
    }

    private void statementSelect(JdbcExperimentContext c) throws Exception {
        seedThree(c);
        var s = new Object() { Connection connection; Statement statement; ResultSet resultSet; java.util.Map<String, Long> before; };
        c.rec.live("create-and-execute", "createStatement() then executeQuery(sql)", nodes("connection", "statement", "database"), "connection", "statement",
                "A Statement sends exactly the SQL text it is given. Here the text is built by the lab itself from a fixed query and the run key "
                        + "(masked as <run key> in the trace); no text from the browser is ever used. executeQuery returns a ResultSet.",
                "jdbc-capture-execute", () -> {
                    s.connection = c.open("A");
                    s.before = JdbcExperimentContext.counters(s.connection);
                    s.statement = s.connection.createStatement();
                    s.resultSet = s.statement.executeQuery("select id, name from jdbc_lab_customers where run_key = '" + c.runKey + "' order by id");
                    ResultSetMetaData meta = s.resultSet.getMetaData();
                    return List.of(ev("Columns in the ResultSet", meta.getColumnCount() + ": " + meta.getColumnLabel(1) + ", " + meta.getColumnLabel(2)),
                            ev("Cursor position before the first next()", "before the first row (isBeforeFirst = " + s.resultSet.isBeforeFirst() + ")"));
                });
        c.rec.live("iterate", "Walk the ResultSet with next()", nodes("database", "resultSet"), "database", "resultSet",
                "A ResultSet is a cursor over the rows. next() moves it to the next row and returns false after the last one. Values are read "
                        + "from the CURRENT row by column label or by 1-based index.",
                null, () -> {
                    List<String> rows = new ArrayList<>();
                    while (s.resultSet.next()) {
                        rows.add(s.resultSet.getRow() + ": id " + (s.resultSet.getLong("id") > 0) + ", name " + s.resultSet.getString(2));
                    }
                    return List.of(ev("Rows read", rows.size()), ev("Rows", String.join(" | ", rows)),
                            ev("next() after the last row", s.resultSet.next()));
                });
        c.rec.live("close", "Close the ResultSet and the Statement", nodes("resultSet", "statement", "connection"), "statement", "connection",
                "Resources are closed in reverse order of creation; try-with-resources does this automatically, even when an exception is thrown. "
                        + "The counters are MySQL's own per-session statement counters, read before the query and now.",
                null, () -> {
                    Map<String, Long> after = JdbcExperimentContext.counters(s.connection);
                    s.resultSet.close();
                    s.statement.close();
                    s.connection.close();
                    return List.of(ev("ResultSet.isClosed()", s.resultSet.isClosed()), ev("Statement.isClosed()", s.statement.isClosed()),
                            ev("MySQL session counters (change during the experiment)", JdbcExperimentContext.delta(s.before, after)));
                });
    }

    private void preparedSelect(JdbcExperimentContext c) throws Exception {
        seedThree(c);
        var s = new Object() { Connection connection; PreparedStatement statement; java.util.Map<String, Long> before; };
        String sql = "select id, name from jdbc_lab_customers where run_key = ? and name = ?";
        c.rec.live("prepare", "prepareStatement(sql) with ? placeholders", nodes("connection", "statement"), "connection", "statement",
                "The SQL text has placeholders and no values. The same text can be executed many times with different parameters.",
                "jdbc-prepared-statement", () -> {
                    s.connection = c.open("A");
                    s.before = JdbcExperimentContext.counters(s.connection);
                    s.statement = s.connection.prepareStatement(sql);
                    return List.of(ev("Placeholders in the statement", s.statement.getParameterMetaData().getParameterCount()));
                });
        c.rec.live("first-execution", "Bind parameters, executeQuery()", nodes("statement", "database", "resultSet"), "statement", "database",
                "setString(1, ...) and setString(2, ...) bind values to the placeholders (1-based). The values are not shown anywhere in the lab's trace.",
                "jdbc-prepared-statement", () -> {
                    s.statement.setString(1, c.runKey);
                    s.statement.setString(2, "Ada");
                    int rows = 0;
                    try (ResultSet rs = s.statement.executeQuery()) {
                        while (rs.next()) {
                            rows++;
                        }
                    }
                    return List.of(ev("Rows returned", rows));
                });
        c.rec.live("second-execution", "Reuse the same PreparedStatement with another value", nodes("statement", "database", "resultSet"), "statement", "database",
                "The statement object is reused: only the bound value changes. The SQL text (with its placeholders) is the same one again.",
                null, () -> {
                    s.statement.setString(2, "Grace");
                    int rows = 0;
                    try (ResultSet rs = s.statement.executeQuery()) {
                        while (rs.next()) {
                            rows++;
                        }
                    }
                    s.statement.close();
                    s.connection.close();
                    return List.of(ev("Rows returned", rows));
                });
        c.rec.concept("client-side-preparation", "Where the statement is really prepared", nodes("statement", "database"),
                "MySQL Connector/J by default prepares statements on the CLIENT (useServerPrepStmts=false): the driver builds the final text, "
                        + "escaping the bound values, before sending it. The statement is still safe against injection because the values are escaped "
                        + "as data, but the JDBC call 'prepareStatement' does not by itself mean MySQL parsed the text in advance. This step explains; "
                        + "it is not an observation of this run.");
    }

    private void sqlInjection(JdbcExperimentContext c) throws Exception {
        seedThree(c);
        String payload = "' OR '1'='1";
        c.rec.concept("unsafe-construction", "The unsafe way (illustration only, never executed)", nodes("connection", "statement"),
                "Building SQL by concatenating input produces text such as: select id, name from jdbc_lab_customers where run_key = '<run key>' and name = '"
                        + payload + "'. The quote in the input ends the string, and OR '1'='1' makes the condition true for every row: the input has "
                        + "become SQL. The lab does NOT run this text. It is shown only so the difference with the next step is visible.");
        var s = new Object() { Connection connection; };
        c.rec.live("safe-binding", "The safe way: the same text as a bound parameter", nodes("connection", "statement", "database", "resultSet"), "statement", "database",
                "With a PreparedStatement the SQL is fixed (name = ?) and the text is passed separately as data. The same characters that would "
                        + "have changed the query are just a name that no row has.",
                null, () -> {
                    s.connection = c.open("A");
                    int hostile;
                    int ordinary;
                    try (PreparedStatement ps = s.connection.prepareStatement("select id from jdbc_lab_customers where run_key = ? and name = ?")) {
                        ps.setString(1, c.runKey);
                        ps.setString(2, payload);
                        hostile = count(ps);
                        ps.setString(2, "Ada");
                        ordinary = count(ps);
                    }
                    s.connection.close();
                    return List.of(ev("Rows returned when quote-and-OR text is bound as the name", hostile),
                            ev("Rows returned for the ordinary name 'Ada'", ordinary),
                            ev("Customers in the table for this run", 3));
                });
        c.rec.infer("why-it-is-safe", "Why the input stays data", nodes("statement", "database"),
                "The query structure is decided before any value is bound, and the driver treats a bound value only as a value (it escapes or "
                        + "transmits it separately). That is a property of parameter binding in general, concluded here from the observed result "
                        + "(0 rows for the hostile text although the unsafe construction would match all 3). Never build SQL from input by concatenation, "
                        + "even for values that look harmless.",
                List.of(ev("Hostile text matched a row", false), ev("Unsafe construction would have matched", "all 3 rows (explained, not run)")));
    }

    private static int count(PreparedStatement ps) throws SQLException {
        int rows = 0;
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows++;
            }
        }
        return rows;
    }

    private void executeMethods(JdbcExperimentContext c) throws Exception {
        seedThree(c);
        var s = new Object() { Connection connection; };
        String where = " where run_key = '" + c.runKey + "'";
        c.rec.live("execute-query", "executeQuery: returns a ResultSet", nodes("connection", "statement", "database", "resultSet"), "statement", "database",
                "executeQuery is for statements that produce rows (SELECT). It returns a ResultSet and never null.",
                null, () -> {
                    s.connection = c.open("A");
                    try (Statement st = s.connection.createStatement(); ResultSet rs = st.executeQuery("select id from jdbc_lab_customers" + where)) {
                        int rows = 0;
                        while (rs.next()) {
                            rows++;
                        }
                        return List.of(ev("Return value", "a ResultSet with " + rows + " rows"));
                    }
                });
        c.rec.live("execute-update", "executeUpdate: returns a row count", nodes("connection", "statement", "database"), "statement", "database",
                "executeUpdate is for INSERT, UPDATE, DELETE and DDL. It returns the number of rows affected as an int.", null, () -> {
                    try (Statement st = s.connection.createStatement()) {
                        int affected = st.executeUpdate("update jdbc_lab_customers set name = concat(name, '*')" + where);
                        return List.of(ev("Return value", affected + " (rows affected)"));
                    }
                });
        c.rec.live("execute-generic", "execute: you ask what came back", nodes("connection", "statement", "database", "resultSet"), "statement", "database",
                "execute works for anything, so it returns a boolean: true means the first result is a ResultSet (read it with getResultSet()), false "
                        + "means it is an update count (getUpdateCount()).",
                null, () -> {
                    try (Statement st = s.connection.createStatement()) {
                        boolean query = st.execute("select id from jdbc_lab_customers" + where);
                        String first = "returned " + query + "; getResultSet() is " + (st.getResultSet() != null ? "a ResultSet" : "null")
                                + "; getUpdateCount() = " + st.getUpdateCount();
                        boolean update = st.execute("update jdbc_lab_customers set name = concat(name, '+')" + where);
                        String second = "returned " + update + "; getResultSet() is " + (st.getResultSet() != null ? "a ResultSet" : "null")
                                + "; getUpdateCount() = " + st.getUpdateCount();
                        return List.of(ev("execute(SELECT)", first), ev("execute(UPDATE)", second));
                    }
                });
        c.rec.live("wrong-method", "Using the wrong method", nodes("connection", "statement"), "connection", "statement",
                "The driver checks that the method fits the statement. executeQuery on an UPDATE, or executeUpdate on a SELECT, is an error.",
                null, () -> {
                    String queryOnUpdate;
                    String updateOnQuery;
                    try (Statement st = s.connection.createStatement()) {
                        try {
                            st.executeQuery("update jdbc_lab_customers set name = name" + where);
                            queryOnUpdate = "accepted (unexpected)";
                        } catch (SQLException e) {
                            queryOnUpdate = "refused: " + JdbcExperimentContext.describe(e);
                        }
                        try {
                            st.executeUpdate("select id from jdbc_lab_customers" + where);
                            updateOnQuery = "accepted (unexpected)";
                        } catch (SQLException e) {
                            updateOnQuery = "refused: " + JdbcExperimentContext.describe(e);
                        }
                    }
                    s.connection.close();
                    return List.of(ev("executeQuery(UPDATE)", queryOnUpdate), ev("executeUpdate(SELECT)", updateOnQuery));
                });
    }

    private void resourceClosing(JdbcExperimentContext c) throws Exception {
        seedThree(c);
        var s = new Object() { Connection connection; Statement statement; ResultSet resultSet; };
        c.rec.live("open-resources", "Open a connection, a statement and a result set", nodes("connection", "statement", "resultSet"), "connection", "statement",
                "Each of the three is a separate resource that holds something on the client and often on the server.", null, () -> {
                    s.connection = c.open("A");
                    s.statement = s.connection.createStatement();
                    s.resultSet = s.statement.executeQuery("select id from jdbc_lab_customers where run_key = '" + c.runKey + "'");
                    return List.of(ev("ResultSet.isClosed()", s.resultSet.isClosed()), ev("Statement.isClosed()", s.statement.isClosed()));
                });
        c.rec.live("close-statement", "Close only the Statement", nodes("statement", "resultSet"), "statement", "resultSet",
                "Closing a Statement also closes the ResultSet it produced, but you should not rely on that: close what you open, in reverse order.",
                null, () -> {
                    s.statement.close();
                    return List.of(ev("ResultSet.isClosed()", s.resultSet.isClosed()), ev("Statement.isClosed()", s.statement.isClosed()),
                            ev("Connection.isClosed()", s.connection.isClosed()));
                });
        c.rec.live("close-connection", "Close the Connection", nodes("connection"), "connection", "statement",
                "A closed connection cannot be used again. With try-with-resources all three are closed in the right order automatically.",
                null, () -> {
                    s.connection.close();
                    String again;
                    try {
                        s.statement.executeQuery("select 1");
                        again = "executed (unexpected)";
                    } catch (SQLException e) {
                        again = "refused: " + JdbcExperimentContext.describe(e);
                    }
                    return List.of(ev("Connection.isClosed()", s.connection.isClosed()), ev("Using the closed Statement", again));
                });
        c.rec.concept("leaks", "What happens when you forget", nodes("connection"),
                "A connection that is never closed is never returned to the pool. A few of those and every later request waits for a connection that "
                        + "will not come back: this is the most common cause of pool exhaustion (see the pooling topic). This step is an explanation.");
    }
}
