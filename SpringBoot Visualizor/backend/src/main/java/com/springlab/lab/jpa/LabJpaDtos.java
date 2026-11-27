package com.springlab.lab.jpa;

import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;

import java.time.Instant;
import java.util.List;

/** Response shapes of the JPA Relationship Lab. Entities are never serialized: only these records leave the server. */
public final class LabJpaDtos {

    private LabJpaDtos() {
    }

    /** The outcome of one LIVE run: the same step shape as every other module, plus the statements Hibernate prepared. */
    public record ExperimentRun(
            String experimentId,
            String mode,
            Instant ranAt,
            long durationMillis,
            int sqlCount,
            List<SqlStatement> sql,
            List<TraceStep> steps,
            List<String> limitations) {
    }

    public record Mapping(List<EntityInfo> entities, List<TableInfo> tables, List<EdgeInfo> edges, Facts facts) {
    }

    public record Facts(String hibernateVersion, boolean openInView, String database) {
    }

    public record EntityInfo(String id, String javaClass, String table, List<AttributeInfo> attributes) {
    }

    /** {@code annotation} lists the attribute values that differ from the annotation defaults (what the source writes). */
    public record AttributeInfo(
            String name,
            String javaType,
            String column,
            boolean id,
            String association,
            String target,
            boolean owning,
            String mappedBy,
            String fetch,
            List<String> cascade,
            boolean orphanRemoval,
            String joinColumn,
            JoinTableInfo joinTable,
            String annotation) {
    }

    public record JoinTableInfo(String name, String joinColumn, String inverseJoinColumn) {
    }

    public record TableInfo(String id, boolean joinTable, List<ColumnInfo> columns, List<ForeignKeyInfo> foreignKeys) {
    }

    public record ColumnInfo(String name, String type, boolean nullable, boolean primaryKey, boolean unique) {
    }

    public record ForeignKeyInfo(String constraint, String column, String referencedTable, String referencedColumn) {
    }

    /** kind: MAPS_TO (entity to table), ASSOCIATION (entity to entity) or FOREIGN_KEY (table to table). */
    public record EdgeInfo(String id, String source, String target, String kind, String label) {
    }
}
