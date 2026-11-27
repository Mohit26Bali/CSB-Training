package com.springlab.lab.jdbc;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.jdbc.JdbcRunRecorder.ev;
import static com.springlab.lab.jdbc.JdbcRunRecorder.nodes;

/** Topic 3: INSERT, SELECT, UPDATE, DELETE, generated keys, update counts and batches. */
@Component
public class JdbcCrudExperiments implements JdbcExperimentSet {

    @Override
    public Map<String, JdbcExperiment> experiments() {
        return Map.of(
                "jdbc-crud-single", this::crudSingle,
                "jdbc-read-many", this::readMany,
                "jdbc-batch-insert", this::batchInsert);
    }

    private void crudSingle(JdbcExperimentContext c) throws Exception {
        var s = new Object() { Connection connection; long id; Map<String, Long> before; };
        String state = "select id, name from jdbc_lab_customers where run_key = ? order by id";
        c.rec.live("insert", "INSERT with a generated key", nodes("connection", "statement", "database"), "statement", "database",
                "executeUpdate returns the number of rows inserted. Statement.RETURN_GENERATED_KEYS asks the driver to give back the AUTO_INCREMENT id "
                        + "MySQL assigned, which getGeneratedKeys() exposes as a ResultSet.",
                null, () -> {
                    s.connection = c.open("A");
                    s.before = JdbcExperimentContext.counters(s.connection);
                    try (PreparedStatement ps = s.connection.prepareStatement("insert into jdbc_lab_customers (run_key, name) values (?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, c.runKey);
                        ps.setString(2, "Ada");
                        int affected = ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            keys.next();
                            s.id = keys.getLong(1);
                        }
                        return List.of(ev("Rows affected", affected), ev("Generated key received", s.id > 0));
                    }
                });
        c.rec.observe("state-after-insert", "Database state after the INSERT (read on another connection)", nodes("database"),
                "Autocommit is on, so the INSERT was committed when it ran and another connection can see it.",
                List.of(ev("jdbc_lab_customers", c.rows(state, c.runKey))));
        c.rec.live("select-one", "SELECT one row by primary key", nodes("connection", "statement", "database", "resultSet"), "statement", "database",
                "A query by primary key returns zero or one row; reading the ResultSet is how the row becomes Java values.", null, () -> {
                    try (PreparedStatement ps = s.connection.prepareStatement("select id, name from jdbc_lab_customers where id = ?")) {
                        ps.setLong(1, s.id);
                        try (ResultSet rs = ps.executeQuery()) {
                            boolean found = rs.next();
                            return List.of(ev("Row found", found), ev("name read from the row", found ? rs.getString("name") : "-"));
                        }
                    }
                });
        c.rec.live("update", "UPDATE: rows affected", nodes("connection", "statement", "database"), "statement", "database",
                "executeUpdate returns how many rows the statement changed. An UPDATE that matches no row is not an error: it simply returns 0.",
                null, () -> {
                    try (PreparedStatement ps = s.connection.prepareStatement("update jdbc_lab_customers set name = ? where id = ?")) {
                        ps.setString(1, "Ada Lovelace");
                        ps.setLong(2, s.id);
                        int existing = ps.executeUpdate();
                        ps.setString(1, "Nobody");
                        ps.setLong(2, -1);
                        int missing = ps.executeUpdate();
                        return List.of(ev("Rows affected for an existing id", existing), ev("Rows affected for an id that does not exist", missing));
                    }
                });
        c.rec.live("delete", "DELETE: rows affected", nodes("connection", "statement", "database"), "statement", "database",
                "The second DELETE finds nothing and returns 0: checking the count is how code learns whether anything happened.", null, () -> {
                    try (PreparedStatement ps = s.connection.prepareStatement("delete from jdbc_lab_customers where id = ?")) {
                        ps.setLong(1, s.id);
                        int first = ps.executeUpdate();
                        int second = ps.executeUpdate();
                        return List.of(ev("Rows affected the first time", first), ev("Rows affected the second time", second));
                    }
                });
        Map<String, Long> after = JdbcExperimentContext.counters(s.connection);
        c.rec.observe("state-after-delete", "Database state at the end, and what MySQL says it received", nodes("database"),
                "The row is gone. The counters are MySQL's own per-session statement counters, taken before the first statement and now.",
                List.of(ev("jdbc_lab_customers", c.rows(state, c.runKey)),
                        ev("MySQL session counters (change during the experiment)", JdbcExperimentContext.delta(s.before, after))));
        s.connection.close();
    }

    private void readMany(JdbcExperimentContext c) throws Exception {
        c.seedProduct("Keyboard", "49.90");
        c.seedProduct("Mouse", "19.50");
        c.seedProduct("Monitor", "189.00");
        c.seedProduct("Cable", "4.25");
        c.seedProduct("Dock", "99.99");
        c.rec.live("select-all", "SELECT several rows and map them", nodes("connection", "statement", "database", "resultSet"), "database", "resultSet",
                "One query, one ResultSet, a loop. Mapping a row to a Java object is manual in plain JDBC: the code reads each column and builds the "
                        + "object itself.",
                null, () -> {
                    try (Connection connection = c.open("A");
                         PreparedStatement ps = connection.prepareStatement("select id, name, price from jdbc_lab_products where run_key = ? order by price")) {
                        ps.setString(1, c.runKey);
                        List<String> names = new ArrayList<>();
                        BigDecimal total = BigDecimal.ZERO;
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                names.add(rs.getString("name"));
                                total = total.add(rs.getBigDecimal("price"));
                            }
                        }
                        return List.of(ev("Rows read", names.size()), ev("Names in price order", String.join(", ", names)),
                                ev("Sum of the price column (BigDecimal)", total));
                    }
                });
        c.rec.live("select-count", "COUNT(*) is one row", nodes("connection", "statement", "database", "resultSet"), "database", "resultSet",
                "When only a number is needed, ask the database for it: it returns one row instead of five.", null, () -> {
                    try (Connection connection = c.open("B");
                         PreparedStatement ps = connection.prepareStatement("select count(*) from jdbc_lab_products where run_key = ?")) {
                        ps.setString(1, c.runKey);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            return List.of(ev("count(*)", rs.getLong(1)));
                        }
                    }
                });
        c.rec.infer("mapping-by-hand", "Who turned rows into Java values", nodes("resultSet", "statement"),
                "In the first step the loop, the column names, the types (getString, getBigDecimal) and the summing were all written by the programmer. "
                        + "JDBC returns rows; turning them into objects is the application's job, which is exactly what RowMapper (Spring JDBC) and entity "
                        + "mapping (JPA) take over in the later topics. Concluded from the code of this experiment.",
                List.of(ev("Mapping written by the programmer", "yes"), ev("Closing of ResultSet, statement and connection", "try-with-resources")));
    }

    private void batchInsert(JdbcExperimentContext c) throws Exception {
        var s = new Object() { Connection connection; Map<String, Long> before; int[] counts; };
        c.rec.live("add-batch", "addBatch() five times", nodes("connection", "statement"), "connection", "statement",
                "addBatch stores a parameter set in the statement. Nothing is sent to the database yet.", "jdbc-batch", () -> {
                    s.connection = c.open("A");
                    s.connection.setAutoCommit(false);
                    s.before = JdbcExperimentContext.counters(s.connection);
                    PreparedStatement ps = s.connection.prepareStatement("insert into jdbc_lab_batch_records (run_key, label) values (?, ?)");
                    for (int i = 1; i <= 5; i++) {
                        ps.setString(1, c.runKey);
                        ps.setString(2, "record " + i);
                        ps.addBatch();
                    }
                    s.counts = ps.executeBatch();
                    ps.close();
                    return List.of(ev("Update counts returned by executeBatch", Arrays.toString(s.counts)));
                });
        c.rec.observe("before-commit", "Visible to another connection before commit?", nodes("database"),
                "The batch ran inside a transaction (autocommit off). Another connection sees nothing until commit.",
                List.of(ev("Rows visible to another connection", c.count("select count(*) from jdbc_lab_batch_records where run_key = ?", c.runKey))));
        c.rec.live("commit", "commit()", nodes("connection", "database"), "statement", "database",
                "commit makes the five rows permanent and visible.", null, () -> {
                    s.connection.commit();
                    return List.of();
                });
        Map<String, Long> after = JdbcExperimentContext.counters(s.connection);
        c.rec.observe("server-confirmation", "After the commit: what MySQL says it received", nodes("database"),
                "The session counters show how many INSERT statements actually reached MySQL for one executeBatch call.",
                List.of(ev("Rows visible to another connection", c.count("select count(*) from jdbc_lab_batch_records where run_key = ?", c.runKey)),
                        ev("MySQL session counters (change during the experiment)", JdbcExperimentContext.delta(s.before, after))));
        c.rec.infer("batching-note", "What batching saved here", nodes("statement", "database"),
                "One executeBatch call carried five parameter sets, but MySQL counted five separate INSERT statements: with MySQL Connector/J's "
                        + "default settings the driver still sends them one by one. Only the option rewriteBatchedStatements=true lets it combine them. "
                        + "This is concluded from the counter above; the lab does not change driver settings.",
                List.of(ev("Statements received by MySQL", after.get("Com_insert") - s.before.get("Com_insert"))));
        s.connection.close();
    }
}
