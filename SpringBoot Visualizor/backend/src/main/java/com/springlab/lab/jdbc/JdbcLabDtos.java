package com.springlab.lab.jdbc;

import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;

import java.time.Instant;
import java.util.List;

/** Response shapes of the JDBC Lab. Only these records leave the server: never a connection, statement or result set. */
public final class JdbcLabDtos {

    private JdbcLabDtos() {
    }

    /** The outcome of one LIVE run, with what the run removed again when it finished. */
    public record ExperimentRun(
            String experimentId,
            String mode,
            Instant ranAt,
            long durationMillis,
            int sqlCount,
            List<SqlStatement> sql,
            List<TraceStep> steps,
            int cleanupRowsRemoved,
            List<String> limitations) {
    }

    public record TableInfo(String id, List<ColumnInfo> columns, List<ForeignKeyInfo> foreignKeys) {
    }

    public record ColumnInfo(String name, String type, boolean nullable, boolean primaryKey, boolean unique) {
    }

    public record ForeignKeyInfo(String constraint, String column, String referencedTable, String referencedColumn) {
    }

    public record Mapping(List<TableInfo> tables, String database, String driver, String isolationDefault) {
    }
}
