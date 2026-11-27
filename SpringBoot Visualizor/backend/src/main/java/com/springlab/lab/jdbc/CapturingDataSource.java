package com.springlab.lab.jdbc;

import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** A DataSource whose connections are captured (see JdbcCapture). Spring JDBC and Spring transactions work on it unchanged. */
public final class CapturingDataSource extends DelegatingDataSource {

    private final JdbcCapture capture;
    private final String label;

    public CapturingDataSource(DataSource target, JdbcCapture capture, String label) {
        super(target);
        this.capture = capture;
        this.label = label;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return capture.wrap(super.getConnection(), label);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return capture.wrap(super.getConnection(username, password), label);
    }
}
