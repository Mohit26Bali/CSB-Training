package com.springlab.lab.micro.run;

import com.springlab.lab.jdbc.JdbcCapture;
import com.springlab.lab.micro.core.ServiceName;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Lab tool for the Database per Service topic. It runs a FIXED lab query on behalf of a service, to show what happens when a service
 * reaches into data it does not own. The tables and statements are constants chosen by the lab: nothing here comes from the browser.
 *
 * <p>{@link #guardedRead} uses the service own connection, which refuses foreign tables (logical isolation enforced by lab code).
 * {@link #sharedDatabaseRead} uses a connection that belongs to no service and therefore has no guard: it is the shared-database
 * counter-example, where nothing stops one team from depending on another team table layout.
 */
@Component
public class BoundaryProbe {

    /** The outcome of a probe: whether it worked, and what happened. {@code detail} holds class names and codes only, never data. */
    public record ProbeResult(boolean succeeded, String detail) {
    }

    /** Fixed statements. CURRENT_COLUMN matches the real stock table; OLD_COLUMN uses a column name the table does not have. */
    public enum SharedQuery {
        CURRENT_COLUMN("select available from micro_lab_stock where run_key = ? and product_ref = ?"),
        OLD_COLUMN("select qty from micro_lab_stock where run_key = ? and product_ref = ?");

        private final String sql;

        SharedQuery(String sql) {
            this.sql = sql;
        }
    }

    /** Label of the connection that belongs to no service (the shared-database counter-example). */
    public static final String SHARED_LABEL = "shared-db";

    /** The service tries to count the rows of {@code table} through its own connection. */
    public ProbeResult guardedRead(ServiceName as, String table) {
        if (Arrays.stream(ServiceName.values()).noneMatch(s -> s.ownedTables().contains(table))) {
            throw new IllegalArgumentException("Not a lab table");
        }
        RunContext run = MicroRunContext.require();
        try {
            Long rows = run.jdbc(as.id()).queryForObject("select count(*) from " + table + " where run_key = ?", Long.class, run.runKey());
            return new ProbeResult(true, "read " + rows + " row(s) of " + table);
        } catch (OwnershipViolation e) {
            return new ProbeResult(false, "OwnershipViolation: " + e.getMessage());
        }
    }

    /** The same data read through a connection that is not owned by any service. */
    public ProbeResult sharedDatabaseRead(SharedQuery query, long productRef) {
        RunContext run = MicroRunContext.require();
        JdbcTemplate template = run.jdbc(SHARED_LABEL);
        try {
            Integer value = template.queryForObject(query.sql, Integer.class, run.runKey(), productRef);
            return new ProbeResult(true, "read " + value);
        } catch (DataAccessException e) {
            return new ProbeResult(false, JdbcCapture.describe(e));
        }
    }
}
