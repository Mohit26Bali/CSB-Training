package com.springlab.lab.jdbc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wraps a real java.sql.Connection (and the statements it creates) in a dynamic proxy that records what the application asks
 * the JDBC API to do: which SQL is executed, which transaction calls are made, when the connection is closed. The calls are
 * forwarded unchanged to the real objects; nothing is faked, delayed or altered.
 *
 * What this proves and what it does not: it proves the application called the driver with this SQL text (placeholders, never
 * bound values). It does not prove how MySQL executed the statement; the experiments confirm that separately with MySQL's own
 * per-session counters.
 */
public final class JdbcCapture {

    private final JdbcEventLog log;
    private final String runKey;

    public JdbcCapture(JdbcEventLog log, String runKey) {
        this.log = log;
        this.runKey = runKey;
    }

    /** SQL text for display: the run key (the only literal the lab ever concatenates) is masked. */
    String clean(String sql) {
        return sql == null ? null : sql.replace(runKey, "<run key>").replaceAll("\\s+", " ").trim();
    }

    public Connection wrap(Connection target, String label) {
        log.add(label, "OPEN", null, "connection obtained");
        return (Connection) Proxy.newProxyInstance(JdbcCapture.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> connectionCall(target, label, method, args));
    }

    private Object connectionCall(Connection target, String label, Method method, Object[] args) throws Throwable {
        Object result = invoke(target, method, args);
        switch (method.getName()) {
            case "prepareStatement" -> {
                String sql = (String) args[0];
                log.add(label, "PREPARE", clean(sql), "prepareStatement");
                return wrapPrepared((PreparedStatement) result, sql, label);
            }
            case "createStatement" -> {
                return wrapStatement((Statement) result, label);
            }
            case "setAutoCommit" -> log.add(label, "CALL", null, "setAutoCommit(" + args[0] + ")");
            case "commit" -> log.add(label, "CALL", null, "commit()");
            case "rollback" -> log.add(label, "CALL", null, args == null || args.length == 0 ? "rollback()" : "rollback(to savepoint)");
            case "setSavepoint" -> log.add(label, "CALL", null, "setSavepoint(" + (args == null || args.length == 0 ? "" : args[0]) + ")");
            case "releaseSavepoint" -> log.add(label, "CALL", null, "releaseSavepoint()");
            case "setTransactionIsolation" -> log.add(label, "CALL", null, "setTransactionIsolation(" + isolation((Integer) args[0]) + ")");
            case "close" -> log.add(label, "CALL", null, "close()");
            default -> { /* not interesting for the lesson */ }
        }
        return result;
    }

    private static String isolation(int level) {
        return switch (level) {
            case Connection.TRANSACTION_READ_UNCOMMITTED -> "READ_UNCOMMITTED";
            case Connection.TRANSACTION_READ_COMMITTED -> "READ_COMMITTED";
            case Connection.TRANSACTION_REPEATABLE_READ -> "REPEATABLE_READ";
            case Connection.TRANSACTION_SERIALIZABLE -> "SERIALIZABLE";
            default -> "NONE";
        };
    }

    private Statement wrapStatement(Statement target, String label) {
        State state = new State();
        return (Statement) Proxy.newProxyInstance(JdbcCapture.class.getClassLoader(), new Class<?>[] {Statement.class},
                (proxy, method, args) -> statementCall(target, label, null, state, method, args));
    }

    private PreparedStatement wrapPrepared(PreparedStatement target, String sql, String label) {
        State state = new State();
        return (PreparedStatement) Proxy.newProxyInstance(JdbcCapture.class.getClassLoader(), new Class<?>[] {PreparedStatement.class},
                (proxy, method, args) -> statementCall(target, label, sql, state, method, args));
    }

    private static final class State {
        int batched;
        final List<String> batchSql = new ArrayList<>();
    }

    private Object statementCall(Statement target, String label, String preparedSql, State state, Method method, Object[] args)
            throws Throwable {
        String name = method.getName();
        if (name.equals("addBatch")) {
            state.batched++;
            if (preparedSql == null && args != null && args.length > 0) {
                state.batchSql.add((String) args[0]);
            }
            return invoke(target, method, args);
        }
        boolean execution = name.equals("executeQuery") || name.equals("executeUpdate") || name.equals("execute")
                || name.equals("executeBatch") || name.equals("executeLargeUpdate") || name.equals("executeLargeBatch");
        if (!execution) {
            if (name.equals("close")) {
                log.add(label, "CALL", null, (preparedSql == null ? "Statement" : "PreparedStatement") + ".close()");
            }
            return invoke(target, method, args);
        }
        String sql = preparedSql != null ? preparedSql
                : args != null && args.length > 0 && args[0] instanceof String s ? s
                : state.batchSql.isEmpty() ? "(batch)" : state.batchSql.get(0);
        String kind = (preparedSql == null ? "Statement." : "PreparedStatement.") + name;
        Object result;
        try {
            result = method.invoke(target, args);
        } catch (InvocationTargetException e) {
            log.add(label, "EXECUTE", clean(sql), kind + " failed: " + describe(e.getCause()));
            throw e.getCause();
        }
        String detail;
        if (name.equals("executeQuery")) {
            detail = kind + " returned a ResultSet";
        } else if (name.equals("execute")) {
            detail = kind + " returned " + result + (Boolean.TRUE.equals(result) ? " (a ResultSet is available)" : " (an update count is available)");
        } else if (name.contains("Batch")) {
            int[] counts = result instanceof int[] ints ? ints : Arrays.stream((long[]) result).mapToInt(l -> (int) l).toArray();
            detail = kind + " sent " + state.batched + " statements, update counts " + Arrays.toString(counts);
            state.batched = 0;
            state.batchSql.clear();
        } else {
            detail = kind + " affected " + result + " row(s)";
        }
        log.add(label, "EXECUTE", clean(sql), detail);
        return result;
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Class names of an exception chain plus the MySQL error code and SQLState; never the message (it can contain values). */
    public static String describe(Throwable failure) {
        List<String> names = new ArrayList<>();
        String vendor = "";
        for (Throwable t = failure; t != null && names.size() < 5; t = t.getCause()) {
            names.add(t.getClass().getSimpleName());
            if (t instanceof SQLException sql && vendor.isEmpty()) {
                vendor = " (MySQL error " + sql.getErrorCode() + ", SQLState " + sql.getSQLState() + ")";
            }
        }
        return String.join(" <- ", names) + vendor;
    }
}
