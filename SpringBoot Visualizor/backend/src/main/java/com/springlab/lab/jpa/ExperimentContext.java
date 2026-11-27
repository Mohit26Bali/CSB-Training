package com.springlab.lab.jpa;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What one experiment run may use. The JdbcTemplate reads and seeds through plain JDBC, which Hibernate never sees: seeding
 * therefore leaves the persistence context empty, and reading rows shows the database itself rather than Hibernate caches.
 * Inside an open transaction the JdbcTemplate shares the transaction's connection (so it sees flushed, uncommitted rows);
 * {@link #inOtherTransaction} uses a second connection (so it sees committed rows only).
 */
public final class ExperimentContext {

    public final EntityManager em;
    public final JdbcTemplate jdbc;
    public final String runKey;
    public final ExperimentRecorder rec;
    private final PlatformTransactionManager transactionManager;

    ExperimentContext(EntityManager em, JdbcTemplate jdbc, PlatformTransactionManager transactionManager, String runKey,
                      ExperimentRecorder rec) {
        this.em = em;
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.runKey = runKey;
        this.rec = rec;
    }

    public TransactionStatus begin() {
        return transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    public void commit(TransactionStatus status) {
        transactionManager.commit(status);
    }

    public void rollback(TransactionStatus status) {
        if (!status.isCompleted()) {
            transactionManager.rollback(status);
        }
    }

    /** Runs a read on a second connection while the caller keeps its own transaction open. */
    public <T> T inOtherTransaction(Supplier<T> work) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setReadOnly(true);
        return new TransactionTemplate(transactionManager, definition).execute(status -> work.get());
    }

    /** Seeds one row with plain JDBC and returns its generated id. */
    public long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    public long seedDepartment(String name) {
        return insert("insert into jpa_lab_department (run_key, name) values (?, ?)", runKey, name);
    }

    public long seedEmployee(String name, Long departmentId) {
        return insert("insert into jpa_lab_employee (run_key, name, department_id) values (?, ?, ?)", runKey, name, departmentId);
    }

    public long seedRoom(String label, Long departmentId) {
        return insert("insert into jpa_lab_room (run_key, label, department_id) values (?, ?, ?)", runKey, label, departmentId);
    }

    public long seedProject(String name) {
        return insert("insert into jpa_lab_project (run_key, name) values (?, ?)", runKey, name);
    }

    public long seedTask(String title, Long projectId) {
        return insert("insert into jpa_lab_task (run_key, title, project_id) values (?, ?, ?)", runKey, title, projectId);
    }

    public long seedTeam(String name) {
        return insert("insert into jpa_lab_team (run_key, name) values (?, ?)", runKey, name);
    }

    public long seedMember(String name, Long teamId) {
        return insert("insert into jpa_lab_member (run_key, name, team_id) values (?, ?, ?)", runKey, name, teamId);
    }

    /** Rows rendered as {@code col=value col=value | col=value ...} (NULL shown as NULL). The query must be a fixed lab query. */
    public String rows(String sql, Object... args) {
        return render(jdbc.queryForList(sql, args));
    }

    public long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    static String render(List<Map<String, Object>> rows) {
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

    /** Simple class names of an exception and its causes, plus the MySQL error code when one is in the chain. */
    public static String describe(Throwable failure) {
        List<String> names = new ArrayList<>();
        String vendor = "";
        for (Throwable t = failure; t != null && names.size() < 6; t = t.getCause()) {
            names.add(t.getClass().getSimpleName());
            if (t instanceof SQLException sql) {
                vendor = " (MySQL error " + sql.getErrorCode() + ", SQLState " + sql.getSQLState() + ")";
            }
        }
        return String.join(" <- ", names) + vendor;
    }
}
