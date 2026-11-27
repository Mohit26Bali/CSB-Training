package com.springlab.lab.jdbc;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Where lab experiments get connections. The URL and credentials are read from the application configuration and never leave
 * this class: they are not logged, returned or shown. The dedicated pools are separate HikariCP instances, so an experiment can
 * never exhaust or reconfigure the application's own pool.
 */
@Component
public class JdbcConnections {

    private final DataSource dataSource;
    private final String url;
    private final String username;
    private final String password;

    public JdbcConnections(DataSource dataSource, @Value("${spring.datasource.url}") String url,
                           @Value("${spring.datasource.username}") String username,
                           @Value("${spring.datasource.password:}") String password) {
        this.dataSource = dataSource;
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public DataSource applicationDataSource() {
        return dataSource;
    }

    /** A brand-new physical connection through DriverManager (no pool). */
    public Connection driverManagerConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    /** A small, short-lived pool owned by one experiment run (the caller closes it). */
    public HikariDataSource newLabPool(String name, int maximumPoolSize, int minimumIdle, long connectionTimeoutMillis) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(name);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setConnectionTimeout(connectionTimeoutMillis);
        config.setRegisterMbeans(false);
        return new HikariDataSource(config);
    }

    /** Read-only view of the application's own pool (counts only). Never changes it. */
    public String applicationPoolSnapshot() {
        if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
            var pool = hikari.getHikariPoolMXBean();
            return "active=" + pool.getActiveConnections() + " idle=" + pool.getIdleConnections() + " total=" + pool.getTotalConnections()
                    + " waiting=" + pool.getThreadsAwaitingConnection() + " (maximum " + hikari.getMaximumPoolSize() + ")";
        }
        return "not a HikariCP pool";
    }
}
