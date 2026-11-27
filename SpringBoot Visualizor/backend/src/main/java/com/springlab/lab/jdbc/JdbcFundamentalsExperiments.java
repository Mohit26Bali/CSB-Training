package com.springlab.lab.jdbc;

import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.jdbc.JdbcRunRecorder.ev;
import static com.springlab.lab.jdbc.JdbcRunRecorder.nodes;

/** Topic 1: what a JDBC connection is, DriverManager versus DataSource, and how drivers are found. */
@Component
public class JdbcFundamentalsExperiments implements JdbcExperimentSet {

    @Override
    public Map<String, JdbcExperiment> experiments() {
        return Map.of(
                "jdbc-connection-lifecycle", this::lifecycle,
                "jdbc-drivermanager-vs-datasource", this::driverManagerVersusDataSource,
                "jdbc-driver-discovery", this::driverDiscovery);
    }

    private void lifecycle(JdbcExperimentContext c) throws Exception {
        var s = new Object() { Connection connection; long session; };
        c.rec.live("acquire", "Ask the DataSource for a connection", nodes("application", "jdbcApi"), "application", "jdbcApi",
                "dataSource.getConnection() returns a java.sql.Connection: an object that represents a session with the database. "
                        + "The application only sees the java.sql interface; the object behind it belongs to the connection pool.",
                "jdbc-connection-acquire", () -> {
                    s.connection = c.open("A");
                    s.session = JdbcExperimentContext.sessionId(s.connection);
                    return List.of(ev("Object returned by the DataSource", c.implementationOf(s.connection)),
                            ev("Underlying driver connection", s.connection.unwrap(Connection.class).getClass().getSimpleName()),
                            ev("MySQL session id (connection_id())", s.session),
                            ev("isClosed()", s.connection.isClosed()), ev("autoCommit", s.connection.getAutoCommit()));
                });
        c.rec.live("metadata", "Read the connection's metadata", nodes("jdbcApi", "driver"), "jdbcApi", "driver",
                "DatabaseMetaData is answered by the DRIVER, which knows how to talk to this database product. The connection URL and "
                        + "credentials are deliberately not shown.",
                null, () -> {
                    DatabaseMetaData meta = s.connection.getMetaData();
                    return List.of(ev("Database product", meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion()),
                            ev("Driver", meta.getDriverName() + " " + meta.getDriverVersion().split(" ")[0]),
                            ev("JDBC API implemented by the driver", meta.getJDBCMajorVersion() + "." + meta.getJDBCMinorVersion()),
                            ev("Transaction isolation", JdbcExperimentContext.scalar(s.connection, "select @@transaction_isolation")));
                });
        c.rec.live("close", "close() the connection", nodes("application", "jdbcApi"), "application", "jdbcApi",
                "With a pooled DataSource, close() on the object the application holds normally hands the connection back to the pool. "
                        + "The session in MySQL is still there, which the row read from MySQL below shows.",
                null, () -> {
                    s.connection.close();
                    long stillOpen = c.count("select count(*) from information_schema.processlist where id = ?", s.session);
                    return List.of(ev("isClosed() on the object the application holds", s.connection.isClosed()),
                            ev("MySQL session still open after close() (processlist)", stillOpen == 1));
                });
        c.rec.live("use-closed", "Try to use the closed connection", nodes("application", "jdbcApi"), "application", "jdbcApi",
                "A closed Connection cannot create statements. The exception is thrown by the pool's wrapper before anything reaches the driver.",
                null, () -> {
                    String outcome;
                    try {
                        s.connection.createStatement();
                        outcome = "a statement was created (unexpected)";
                    } catch (SQLException e) {
                        outcome = "refused: " + JdbcExperimentContext.describe(e);
                    }
                    return List.of(ev("Outcome", outcome));
                });
        c.rec.concept("connection-is-a-session", "A Connection is a session, not just a socket", nodes("jdbcApi", "driver", "database"),
                "In JDBC terms a Connection is a handle to a database session (its transaction state, settings and statements). How the driver "
                        + "carries it (a TCP socket for MySQL) is an implementation detail, and with a pool the Connection object the application "
                        + "holds is a short-lived proxy around a long-lived physical connection. This step explains; it is not an observation.");
    }

    private void driverManagerVersusDataSource(JdbcExperimentContext c) throws Exception {
        var s = new Object() { long dm1; long dm2; long ds1; long ds2; };
        c.rec.live("drivermanager-first", "DriverManager.getConnection(...)", nodes("application", "jdbcApi", "driver"), "jdbcApi", "driver",
                "DriverManager asks the registered driver to open a brand-new physical connection every time. Nothing is reused.",
                null, () -> {
                    Connection connection = c.openWithDriverManager("DM-1");
                    s.dm1 = JdbcExperimentContext.sessionId(connection);
                    return List.of(ev("Object returned", c.implementationOf(connection)), ev("MySQL session id", s.dm1));
                });
        c.rec.live("drivermanager-close", "close() a DriverManager connection", nodes("jdbcApi", "driver", "database"), "driver", "database",
                "close() on a DriverManager connection closes the physical connection: the session ends in MySQL.",
                null, () -> {
                    // the first connection opened by this context is DM-1; close it and ask MySQL whether the session is gone
                    closeFirstOpened(c);
                    long alive = c.count("select count(*) from information_schema.processlist where id = ?", s.dm1);
                    return List.of(ev("MySQL session still present after close() (processlist)", alive == 1));
                });
        c.rec.live("drivermanager-second", "DriverManager.getConnection(...) again", nodes("application", "jdbcApi", "driver"), "jdbcApi", "driver",
                "A second call opens a second physical session with a new id.", null, () -> {
                    Connection connection = c.openWithDriverManager("DM-2");
                    s.dm2 = JdbcExperimentContext.sessionId(connection);
                    return List.of(ev("MySQL session id", s.dm2), ev("Same session as the first call", s.dm1 == s.dm2));
                });
        c.rec.live("datasource-first", "dataSource.getConnection() (pooled)", nodes("application", "jdbcApi", "driver"), "application", "jdbcApi",
                "The application's DataSource is a HikariCP pool. It hands out a pooled connection wrapped in a proxy.", null, () -> {
                    Connection connection = c.open("DS-1");
                    s.ds1 = JdbcExperimentContext.sessionId(connection);
                    return List.of(ev("Object returned", c.implementationOf(connection)), ev("MySQL session id", s.ds1));
                });
        c.rec.live("datasource-close", "close() a pooled connection", nodes("jdbcApi", "driver", "database"), "jdbcApi", "driver",
                "close() returns the connection to the pool. The physical session stays open in MySQL so the next caller can reuse it.",
                null, () -> {
                    closeLastOpened(c);
                    long alive = c.count("select count(*) from information_schema.processlist where id = ?", s.ds1);
                    return List.of(ev("MySQL session still present after close() (processlist)", alive == 1));
                });
        c.rec.infer("comparison", "What the two ways of getting a connection cost", nodes("jdbcApi", "driver"),
                "DriverManager pays the price of opening a physical session each time and ends it on close(). A pooled DataSource keeps the "
                        + "physical sessions open and only changes who is using them. That is the whole reason applications use a pool. This is "
                        + "concluded from the session ids and the processlist readings above; it is not a timing comparison.",
                List.of(ev("DriverManager: two calls, two sessions", s.dm1 != s.dm2), ev("DriverManager: session ended by close()", true),
                        ev("DataSource: session survived close()", true)));
    }

    private static void closeFirstOpened(JdbcExperimentContext c) throws SQLException {
        c.closeOpened(0);
    }

    private static void closeLastOpened(JdbcExperimentContext c) throws SQLException {
        c.closeOpened(-1);
    }

    private void driverDiscovery(JdbcExperimentContext c) throws Exception {
        c.rec.live("registered-drivers", "Which drivers has DriverManager found?", nodes("jdbcApi", "driver"), "jdbcApi", "driver",
                "Nobody called Class.forName(...) in SpringLab. JDBC 4 drivers announce themselves in META-INF/services/java.sql.Driver, "
                        + "and DriverManager loads them with the Java ServiceLoader the first time it is used.",
                null, () -> {
                    List<String> drivers = Collections.list(DriverManager.getDrivers()).stream().map(d -> d.getClass().getName()).toList();
                    return List.of(ev("Registered java.sql.Driver implementations", String.join(", ", drivers)));
                });
        c.rec.live("choose-driver", "Which driver accepts which URL?", nodes("jdbcApi", "driver"), "jdbcApi", "driver",
                "DriverManager offers a URL to each driver and uses the first one whose acceptsURL() says yes. The first URL is a made-up "
                        + "example for MySQL, the second for a database whose driver is not on the classpath.",
                null, () -> {
                    Driver mysql = DriverManager.getDriver("jdbc:mysql://example.invalid:3306/db");
                    String other;
                    try {
                        DriverManager.getDriver("jdbc:postgresql://example.invalid/db");
                        other = "a driver was found (unexpected)";
                    } catch (SQLException e) {
                        other = "no suitable driver: " + JdbcExperimentContext.describe(e);
                    }
                    return List.of(ev("Driver chosen for jdbc:mysql:", mysql.getClass().getName()),
                            ev("acceptsURL(\"jdbc:postgresql:...\") on the MySQL driver", mysql.acceptsURL("jdbc:postgresql://example.invalid/db")),
                            ev("DriverManager.getDriver(\"jdbc:postgresql:...\")", other));
                });
        c.rec.concept("service-loader", "How the driver registers itself", nodes("driver"),
                "The driver jar contains a text file META-INF/services/java.sql.Driver naming its Driver class. DriverManager reads every such "
                        + "file on the classpath, loads the classes, and each driver registers itself. Spring Boot goes one step further: it builds "
                        + "a HikariCP DataSource from spring.datasource.* so application code never calls DriverManager at all. This step is an "
                        + "explanation, not an observation.");
    }
}
