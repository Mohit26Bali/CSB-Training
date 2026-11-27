package com.springlab.lab.jdbc;

/**
 * One observation made at the JDBC API boundary. kind is OPEN, PREPARE, EXECUTE or CALL. Only EXECUTE events carry SQL
 * that was handed to the driver for execution; the SQL text never contains a bound parameter value (placeholders only).
 */
public record JdbcEvent(int seq, String connection, String kind, String sql, String detail) {

    public boolean isExecution() {
        return "EXECUTE".equals(kind);
    }
}
