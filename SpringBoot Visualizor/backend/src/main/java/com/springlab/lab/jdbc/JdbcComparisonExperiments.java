package com.springlab.lab.jdbc;

import com.springlab.lab.jdbc.entity.JdbcLabCustomer;
import org.hibernate.Session;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.jdbc.JdbcRunRecorder.ev;
import static com.springlab.lab.jdbc.JdbcRunRecorder.nodes;

/** Topic 7: the same logical operations through plain JDBC, Spring JDBC and JPA/Hibernate on the same table. */
@Component
public class JdbcComparisonExperiments implements JdbcExperimentSet {

    @Override
    public Map<String, JdbcExperiment> experiments() {
        return Map.of(
                "compare-read", this::read,
                "compare-insert", this::insert,
                "compare-same-session", this::sameSession);
    }

    private void read(JdbcExperimentContext c) throws Exception {
        long id = c.seedCustomer("Ada");
        c.rec.live("path-a-jdbc", "Path A: JDBC (Connection, PreparedStatement, ResultSet)", nodes("service", "jdbc", "jdbcApi", "driver", "database"), "service", "jdbc",
                "You write the SQL, the binding, the loop over the ResultSet and the closing of every resource.", "jdbc-path-plain", () -> {
                    try (Connection connection = c.open("JDBC");
                         PreparedStatement ps = connection.prepareStatement("select name from jdbc_lab_customers where id = ?")) {
                        ps.setLong(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            return List.of(ev("Name read", rs.next() ? rs.getString(1) : "(none)"));
                        }
                    }
                });
        c.rec.live("path-b-spring", "Path B: Spring JDBC (JdbcTemplate)", nodes("service", "jdbcTemplate", "jdbcApi", "driver", "database"), "service", "jdbcTemplate",
                "You still write the SQL, but connection, statement, ResultSet and exception handling are done by the template.", "jdbc-path-spring", () -> {
                    JdbcTemplate template = new JdbcTemplate(c.capturingDataSource("Spring"));
                    String name = template.queryForObject("select name from jdbc_lab_customers where id = ?", String.class, id);
                    return List.of(ev("Name read", name));
                });
        c.rec.live("path-c-jpa", "Path C: JPA / Hibernate (EntityManager.find)", nodes("service", "entityManager", "hibernate", "jdbcApi", "driver", "database"), "service", "entityManager",
                "You ask for an entity by id; Hibernate generates the SQL from the mapping, runs it through JDBC and builds the object. The SQL shown "
                        + "is the text Hibernate prepared (it is not proof of execution).",
                "jdbc-path-jpa", () -> {
                    TransactionStatus tx = c.jpaTransactions.getTransaction(new DefaultTransactionDefinition());
                    JdbcLabCustomer customer = c.em.find(JdbcLabCustomer.class, id);
                    String name = customer == null ? "(none)" : customer.getName();
                    c.jpaTransactions.commit(tx);
                    return List.of(ev("Name read", name), ev("Result type", customer == null ? "-" : customer.getClass().getSimpleName()));
                });
        c.rec.infer("same-row", "One row, three paths", nodes("jdbcApi", "driver", "database"),
                "All three paths end the same way: a SELECT text goes to the JDBC driver and on to MySQL, and the same row comes back. They differ in who "
                        + "writes the SQL (you or Hibernate), who maps the row (you, a RowMapper, or the entity mapping) and who manages the resources. "
                        + "No speed claim is made: a single read on a tiny table says nothing about performance.",
                List.of(ev("Row", c.rows("select id, name from jdbc_lab_customers where run_key = ?", c.runKey))));
    }

    private void insert(JdbcExperimentContext c) throws Exception {
        var s = new Object() { long jdbc; long spring; long jpa; };
        c.rec.live("path-a-jdbc", "Path A: JDBC with an explicit transaction", nodes("service", "jdbc", "jdbcApi", "driver", "database"), "service", "jdbc",
                "You control the transaction yourself: setAutoCommit(false), the INSERT, commit(). The generated key comes back through getGeneratedKeys().",
                null, () -> {
                    try (Connection connection = c.open("JDBC")) {
                        connection.setAutoCommit(false);
                        try (PreparedStatement ps = connection.prepareStatement("insert into jdbc_lab_customers (run_key, name) values (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                            ps.setString(1, c.runKey);
                            ps.setString(2, "Ada via JDBC");
                            ps.executeUpdate();
                            try (ResultSet keys = ps.getGeneratedKeys()) {
                                keys.next();
                                s.jdbc = keys.getLong(1);
                            }
                        }
                        connection.commit();
                    }
                    return List.of(ev("Generated key", s.jdbc > 0 ? "received" : "none"));
                });
        c.rec.live("path-b-spring", "Path B: JdbcTemplate inside a TransactionTemplate", nodes("service", "jdbcTemplate", "jdbcApi", "driver", "database"), "service", "jdbcTemplate",
                "Spring opens the transaction and commits it; the generated key is read with a KeyHolder.", null, () -> {
                    DataSource ds = c.capturingDataSource("Spring");
                    JdbcTemplate template = new JdbcTemplate(ds);
                    TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
                    tx.executeWithoutResult(status -> {
                        KeyHolder keys = new GeneratedKeyHolder();
                        template.update(connection -> {
                            PreparedStatement ps = connection.prepareStatement("insert into jdbc_lab_customers (run_key, name) values (?, ?)", Statement.RETURN_GENERATED_KEYS);
                            ps.setString(1, c.runKey);
                            ps.setString(2, "Ada via Spring");
                            return ps;
                        }, keys);
                        s.spring = keys.getKey().longValue();
                    });
                    return List.of(ev("Generated key", s.spring > 0 ? "received" : "none"));
                });
        c.rec.live("path-c-jpa", "Path C: persist() inside a JPA transaction", nodes("service", "entityManager", "hibernate", "jdbcApi", "driver", "database"), "service", "entityManager",
                "persist() makes the entity managed. Because the key is AUTO_INCREMENT, Hibernate must run the INSERT immediately to learn the id. "
                        + "Hibernate's own JDBC calls (setAutoCommit, commit) are not captured here: only the SQL text it prepared is.",
                null, () -> {
                    TransactionStatus tx = c.jpaTransactions.getTransaction(new DefaultTransactionDefinition());
                    JdbcLabCustomer customer = new JdbcLabCustomer(c.runKey, "Ada via JPA");
                    c.em.persist(customer);
                    s.jpa = customer.getId();
                    c.jpaTransactions.commit(tx);
                    return List.of(ev("Generated key", s.jpa > 0 ? "received" : "none"));
                });
        c.rec.observe("state", "The three rows MySQL now holds", nodes("database"),
                "Read on another connection: the same kind of row was created three ways.",
                List.of(ev("jdbc_lab_customers", c.rows("select id, name from jdbc_lab_customers where run_key = ? order by id", c.runKey))));
        c.rec.infer("transaction-handling", "Who controlled the transaction", nodes("jdbcApi", "driver"),
                "Path A: the code called setAutoCommit(false) and commit() itself (both observed). Path B: TransactionTemplate made the same calls "
                        + "(observed). Path C: JpaTransactionManager did, and Hibernate flushed at commit; those JDBC calls were not captured in this lab, "
                        + "so this part is stated, not observed.",
                List.of(ev("Observed at the JDBC boundary", "paths A and B"), ev("Not observed", "path C commit")));
    }

    private void sameSession(JdbcExperimentContext c) throws Exception {
        var s = new Object() { TransactionStatus tx; long hibernateSession; long templateSession; };
        c.rec.live("jpa-session", "A JPA transaction and Hibernate's MySQL session", nodes("service", "entityManager", "hibernate", "jdbcApi", "driver", "database"), "service", "entityManager",
                "Inside a JpaTransactionManager transaction, Hibernate runs on one JDBC connection. Asking MySQL which session it is gives a number.",
                null, () -> {
                    s.tx = c.jpaTransactions.getTransaction(new DefaultTransactionDefinition());
                    s.hibernateSession = ((Number) c.em.createNativeQuery("select connection_id()").getSingleResult()).longValue();
                    return List.of(ev("MySQL session used by Hibernate", s.hibernateSession > 0 ? "read" : "none"));
                });
        c.rec.live("template-session", "JdbcTemplate on the same thread, same transaction", nodes("service", "jdbcTemplate", "jdbcApi", "driver", "database"), "service", "jdbcTemplate",
                "JpaTransactionManager exposes its JDBC connection to Spring JDBC code that uses the same DataSource, so JdbcTemplate joins the "
                        + "transaction instead of opening a new session.",
                null, () -> {
                    s.templateSession = c.verify.queryForObject("select connection_id()", Long.class);
                    return List.of(ev("Same MySQL session as Hibernate", s.templateSession == s.hibernateSession));
                });
        c.rec.live("shared-uncommitted", "Visibility of an uncommitted JPA insert", nodes("entityManager", "hibernate", "jdbcTemplate", "database"), "entityManager", "hibernate",
                "Hibernate inserts a row (not committed yet). Spring JDBC in the same transaction can read it; a brand-new connection cannot.",
                null, () -> {
                    c.em.persist(new JdbcLabCustomer(c.runKey, "Ada shared"));
                    long inSession = c.verify.queryForObject("select count(*) from jdbc_lab_customers where run_key = ?", Long.class, c.runKey);
                    long fromOther;
                    try (Connection other = c.openWithDriverManager("other");
                         PreparedStatement ps = other.prepareStatement("select count(*) from jdbc_lab_customers where run_key = ?")) {
                        ps.setString(1, c.runKey);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            fromOther = rs.getLong(1);
                        }
                    }
                    return List.of(ev("Rows seen by JdbcTemplate in the same transaction", inSession), ev("Rows seen by a new connection", fromOther));
                });
        c.rec.live("commit", "Commit the JPA transaction", nodes("entityManager", "hibernate", "database"), "hibernate", "jdbcApi",
                "After the commit every session sees the row.", null, () -> {
                    c.jpaTransactions.commit(s.tx);
                    return List.of(ev("Rows seen by another connection after commit", c.count("select count(*) from jdbc_lab_customers where run_key = ?", c.runKey)));
                });
        c.rec.live("hibernate-uses-jdbc", "Where Hibernate gets its connection", nodes("hibernate", "jdbcApi", "driver"), "hibernate", "jdbcApi",
                "Session.doWork hands out the java.sql.Connection Hibernate itself is using. It is an ordinary JDBC connection from the same pool.",
                "hibernate-do-work", () -> {
                    TransactionStatus tx = c.jpaTransactions.getTransaction(new DefaultTransactionDefinition());
                    String[] seen = new String[2];
                    c.em.unwrap(Session.class).doWork(connection -> {
                        seen[0] = connection.getClass().getSimpleName();
                        seen[1] = connection.getMetaData().getDriverName();
                    });
                    c.jpaTransactions.commit(tx);
                    return List.of(ev("Connection class Hibernate works with", seen[0]), ev("Driver behind it", seen[1]));
                });
        c.rec.infer("conclusion", "Hibernate does not replace JDBC", nodes("hibernate", "jdbcApi", "driver", "database"),
                "Everything Hibernate sends to MySQL goes through the JDBC API on a pooled java.sql.Connection. JPA changes who writes the SQL and who "
                        + "maps the rows; it does not remove connections, transactions or SQL. Sharing a transaction between JPA and Spring JDBC works here "
                        + "because both use the same DataSource under one JpaTransactionManager, which needs to be configured deliberately.",
                List.of(ev("Shared MySQL session observed", true)));
    }
}
