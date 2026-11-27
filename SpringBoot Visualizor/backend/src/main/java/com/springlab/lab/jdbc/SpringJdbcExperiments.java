package com.springlab.lab.jdbc;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.jdbc.JdbcRunRecorder.ev;
import static com.springlab.lab.jdbc.JdbcRunRecorder.nodes;

/**
 * Topic 6: Spring JDBC. These experiments use the real JdbcTemplate, NamedParameterJdbcTemplate and TransactionTemplate on the
 * application's own DataSource, wrapped only so the JDBC calls they make can be observed (the calls are forwarded unchanged).
 */
@Component
public class SpringJdbcExperiments implements JdbcExperimentSet {

    /** A row mapped by hand or by a RowMapper. */
    record CustomerRow(long id, String name) {
    }

    @Override
    public Map<String, JdbcExperiment> experiments() {
        return Map.of(
                "spring-jdbc-query", this::query,
                "spring-jdbc-named-update", this::namedUpdate,
                "spring-jdbc-exception-translation", this::exceptionTranslation,
                "spring-jdbc-transaction", this::transaction);
    }

    private void seedThree(JdbcExperimentContext c) {
        c.seedCustomer("Ada");
        c.seedCustomer("Grace");
        c.seedCustomer("Alan");
    }

    private void query(JdbcExperimentContext c) throws Exception {
        seedThree(c);
        String sql = "select id, name from jdbc_lab_customers where run_key = ? order by id";
        var s = new Object() { List<String> manual; };
        c.rec.live("manual-jdbc", "Plain JDBC: you manage every resource", nodes("service", "dataSource", "driver", "database"), "dataSource", "driver",
                "Connection, PreparedStatement and ResultSet are opened, used and closed by the programmer, and the SQLException must be handled. "
                        + "This is the reference the next step is compared with.",
                null, () -> {
                    s.manual = new ArrayList<>();
                    try (Connection connection = c.open("manual");
                         PreparedStatement ps = connection.prepareStatement(sql)) {
                        ps.setString(1, c.runKey);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                s.manual.add(rs.getLong("id") + ":" + rs.getString("name"));
                            }
                        }
                    }
                    return List.of(ev("Rows mapped by hand", s.manual.size()));
                });
        JdbcTemplate template = new JdbcTemplate(c.capturingDataSource("template"));
        RowMapper<CustomerRow> mapper = (rs, rowNum) -> new CustomerRow(rs.getLong("id"), rs.getString("name"));
        c.rec.live("template-query", "JdbcTemplate.query(sql, rowMapper, args)", nodes("service", "jdbcTemplate", "dataSource", "driver", "database"),
                "service", "jdbcTemplate",
                "The template gets a connection from the DataSource, prepares the statement, binds the arguments, executes it, calls the RowMapper "
                        + "once per row, and then closes the ResultSet, the statement and the connection. The JDBC calls observed below are the ones it "
                        + "made for you.",
                "spring-jdbc-template", () -> {
                    List<CustomerRow> rows = template.query(sql, mapper, c.runKey);
                    List<String> asText = rows.stream().map(r -> r.id() + ":" + r.name()).toList();
                    return List.of(ev("Rows returned as Java objects", rows.size()), ev("Same rows as the plain JDBC version", asText.equals(s.manual)));
                });
        c.rec.live("extractor", "ResultSetExtractor: you receive the whole ResultSet", nodes("jdbcTemplate", "dataSource", "driver", "database"), "jdbcTemplate", "dataSource",
                "A RowMapper is called per row. A ResultSetExtractor is called once with the ResultSet and decides how to read it: here it builds a "
                        + "Map from the rows. Resource handling is still done by the template.",
                null, () -> {
                    Map<String, Long> byName = template.query(sql, rs -> {
                        Map<String, Long> map = new LinkedHashMap<>();
                        while (rs.next()) {
                            map.put(rs.getString("name"), rs.getLong("id"));
                        }
                        return map;
                    }, c.runKey);
                    return List.of(ev("Entries in the Map", byName.size()), ev("Names", String.join(", ", byName.keySet())));
                });
        c.rec.live("scalar", "queryForObject for a single value", nodes("jdbcTemplate", "dataSource", "database"), "jdbcTemplate", "service",
                "For one value the template converts the single column of the single row to the requested type.", null, () -> {
                    Long count = template.queryForObject("select count(*) from jdbc_lab_customers where run_key = ?", Long.class, c.runKey);
                    return List.of(ev("count(*)", count));
                });
        c.rec.infer("what-it-did", "What JdbcTemplate removed from your code", nodes("jdbcTemplate", "dataSource"),
                "From the observed calls: it acquired and closed the connection, prepared the SQL, bound the arguments, executed it, iterated the "
                        + "ResultSet and closed it. What it did NOT remove: you still wrote the SQL, chose the columns, and must understand transactions "
                        + "and what the statement costs.",
                List.of(ev("Written by the programmer", "the SQL, the arguments and the mapping"), ev("Handled by JdbcTemplate", "acquire, prepare, bind, execute, iterate, close, translate exceptions")));
    }

    private void namedUpdate(JdbcExperimentContext c) throws Exception {
        NamedParameterJdbcTemplate named = new NamedParameterJdbcTemplate(c.capturingDataSource("named"));
        c.rec.live("named-update", "update with :names instead of ?", nodes("service", "jdbcTemplate", "dataSource", "driver", "database"), "service", "jdbcTemplate",
                "Parameters are written as :runKey and :name and supplied by name. Before the statement reaches JDBC, Spring rewrites the names to "
                        + "positional ? placeholders: the SQL in the trace is what the driver actually received.",
                null, () -> {
                    SqlParameterSource params = new MapSqlParameterSource().addValue("runKey", c.runKey).addValue("name", "Ada");
                    int rows = named.update("insert into jdbc_lab_customers (run_key, name) values (:runKey, :name)", params);
                    return List.of(ev("Rows affected", rows));
                });
        c.rec.live("repeated-name", "The same name used twice", nodes("jdbcTemplate", "dataSource", "driver", "database"), "jdbcTemplate", "dataSource",
                "A named parameter can appear several times in the SQL and is supplied once. Spring expands it into one ? per occurrence.",
                null, () -> {
                    Long count = named.queryForObject("select count(*) from jdbc_lab_customers where run_key = :runKey and (name = :n or name = :n)",
                            new MapSqlParameterSource().addValue("runKey", c.runKey).addValue("n", "Ada"), Long.class);
                    return List.of(ev("Rows matched", count));
                });
        c.rec.live("batch-update", "batchUpdate with one parameter source per row", nodes("jdbcTemplate", "dataSource", "driver", "database"), "jdbcTemplate", "dataSource",
                "batchUpdate prepares the statement once and adds each parameter set to a JDBC batch, then executes the batch.",
                null, () -> {
                    SqlParameterSource[] batch = new SqlParameterSource[4];
                    for (int i = 0; i < batch.length; i++) {
                        batch[i] = new MapSqlParameterSource().addValue("runKey", c.runKey).addValue("name", "Customer " + (i + 1));
                    }
                    int[] counts = named.batchUpdate("insert into jdbc_lab_customers (run_key, name) values (:runKey, :name)", batch);
                    return List.of(ev("Update counts", Arrays.toString(counts)));
                });
        c.rec.observe("state", "Database state", nodes("database"),
                "Read on another connection after the statements finished.",
                List.of(ev("Customers in the table for this run", c.count("select count(*) from jdbc_lab_customers where run_key = ?", c.runKey))));
    }

    private void exceptionTranslation(JdbcExperimentContext c) throws Exception {
        c.seedCustomer("Ada");
        JdbcTemplate template = new JdbcTemplate(c.capturingDataSource("template"));
        c.rec.live("duplicate", "A duplicate unique key", nodes("service", "jdbcTemplate", "dataSource", "driver", "database"), "jdbcTemplate", "service",
                "MySQL answers with a vendor error code (1062). JdbcTemplate translates the SQLException into a Spring DataAccessException subclass, so "
                        + "code can catch DuplicateKeyException without knowing about MySQL.",
                null, () -> List.of(ev("Outcome", failure(() -> template.update("insert into jdbc_lab_customers (run_key, name) values (?, ?)", c.runKey, "Ada")))));
        c.rec.live("foreign-key", "A foreign key violation", nodes("jdbcTemplate", "dataSource", "driver", "database"), "jdbcTemplate", "service",
                "The order refers to a customer and a product that do not exist (MySQL error 1452).", null, () -> List.of(ev("Outcome",
                        failure(() -> template.update("insert into jdbc_lab_orders (run_key, customer_id, product_id, quantity) values (?, ?, ?, ?)", c.runKey, -1, -1, 1)))));
        c.rec.live("bad-sql", "SQL that is not valid", nodes("jdbcTemplate", "dataSource", "driver", "database"), "jdbcTemplate", "service",
                "A statement on a table that does not exist (a fixed text, not client input) is translated to BadSqlGrammarException.", null,
                () -> List.of(ev("Outcome", failure(() -> template.queryForList("select * from jdbc_lab_table_that_does_not_exist")))));
        c.rec.infer("hierarchy", "Why translation matters", nodes("jdbcTemplate", "service"),
                "The translated exceptions are unchecked and form a hierarchy (for example DuplicateKeyException extends DataIntegrityViolationException "
                        + "extends NonTransientDataAccessException extends DataAccessException). The original SQLException is kept as the cause, so the vendor "
                        + "code is still available when needed. Concluded from the classes observed above.",
                List.of(ev("Base class of all of them", DataAccessException.class.getName())));
    }

    private static String failure(Runnable action) {
        try {
            action.run();
            return "succeeded (unexpected)";
        } catch (DataAccessException e) {
            List<String> chain = new ArrayList<>();
            for (Class<?> type = e.getClass(); type != null && !type.getSimpleName().equals("RuntimeException"); type = type.getSuperclass()) {
                chain.add(type.getSimpleName());
            }
            return "Spring threw " + String.join(" extends ", chain) + "; cause " + JdbcExperimentContext.describe(e.getCause());
        }
    }

    private void transaction(JdbcExperimentContext c) throws Exception {
        c.seedAccount("Alice", "100.00");
        c.seedAccount("Bob", "50.00");
        DataSource ds = c.capturingDataSource("S");
        JdbcTemplate template = new JdbcTemplate(ds);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        c.rec.live("commit", "TransactionTemplate: two updates, normal return", nodes("service", "jdbcTemplate", "dataSource", "driver", "database"), "service", "jdbcTemplate",
                "The transaction manager takes ONE connection from the DataSource, turns auto-commit off, and binds the connection to the thread. Both "
                        + "JdbcTemplate calls find it there and reuse it. When the callback returns normally the manager commits.",
                "spring-tx-template", () -> {
                    boolean[] active = new boolean[1];
                    tx.executeWithoutResult(status -> {
                        active[0] = TransactionSynchronizationManager.isActualTransactionActive();
                        template.update("update jdbc_lab_accounts set balance = balance - 30 where run_key = ? and owner = ?", c.runKey, "Alice");
                        template.update("update jdbc_lab_accounts set balance = balance + 30 where run_key = ? and owner = ?", c.runKey, "Bob");
                    });
                    return List.of(ev("A transaction was active inside the callback", active[0]),
                            ev("Seen by another connection after the commit", c.balances()));
                });
        c.rec.live("rollback", "TransactionTemplate: an exception inside the callback", nodes("service", "jdbcTemplate", "dataSource", "driver", "database"), "service", "jdbcTemplate",
                "A RuntimeException leaving the callback makes the manager roll the transaction back, so the first UPDATE is undone. No try/catch or "
                        + "rollback() call appears in the application code.",
                null, () -> {
                    String outcome;
                    try {
                        tx.executeWithoutResult(status -> {
                            template.update("update jdbc_lab_accounts set balance = balance - 50 where run_key = ? and owner = ?", c.runKey, "Alice");
                            throw new IllegalStateException("simulated failure");
                        });
                        outcome = "no exception (unexpected)";
                    } catch (IllegalStateException e) {
                        outcome = "the callback threw " + e.getClass().getSimpleName() + " and the transaction was rolled back";
                    }
                    return List.of(ev("Outcome", outcome), ev("Seen by another connection after the rollback", c.balances()));
                });
        c.rec.infer("comparison", "Compared with the JDBC transaction experiments", nodes("jdbcTemplate", "dataSource"),
                "The JDBC calls observed are the same ones written by hand in the transaction topic: setAutoCommit(false), the statements, then commit() "
                        + "or rollback(), and finally the connection is closed. Spring decides when to make them. Concluded from the calls listed in the two steps above.",
                List.of(ev("Transaction scope", "one connection for the whole callback")));
    }
}
