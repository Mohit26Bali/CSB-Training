package com.springlab.lab.jdbc;

import com.springlab.lab.trace.LifecycleTrace.Evidence;
import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import com.springlab.lab.trace.Provenance;
import com.springlab.lab.trace.RawTrace;
import com.springlab.lab.trace.TraceCollector;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what a JDBC experiment does into teaching steps with the shared provenance model.
 *
 * <ul>
 *   <li>{@link #live}: an action really ran; the JDBC executions (and, for the JPA path, the statements Hibernate prepared)
 *       observed while it ran are attached to the step, and the other JDBC calls (prepare, setAutoCommit, commit, rollback,
 *       close, ...) are listed as evidence. CAPTURED.</li>
 *   <li>{@link #observe}: facts the experiment read (rows, MySQL counters, pool metrics). CAPTURED.</li>
 *   <li>{@link #infer}: a conclusion from captured facts. DERIVED.</li>
 *   <li>{@link #concept}: an explanation; nothing about it was observed. CONCEPTUAL.</li>
 * </ul>
 */
public final class JdbcRunRecorder {

    @FunctionalInterface
    public interface Action {
        List<Evidence> run() throws Exception;
    }

    public static Evidence ev(String label, Object value) {
        return new Evidence(label, String.valueOf(value));
    }

    public static List<String> nodes(String... ids) {
        return List.of(ids);
    }

    private final JdbcEventLog log;
    private final TraceCollector hibernate;
    private final List<TraceStep> steps = new ArrayList<>();
    private final List<SqlStatement> allSql = new ArrayList<>();
    private int mark;
    private int hibernateMark;

    JdbcRunRecorder(JdbcEventLog log, TraceCollector hibernate) {
        this.log = log;
        this.hibernate = hibernate;
    }

    public void live(String id, String title, List<String> nodes, String edgeFrom, String edgeTo, String explanation, String codeRefId,
                     Action action) throws Exception {
        mark = log.size();
        hibernateMark = hibernate.sqlCount();
        List<Evidence> evidence = new ArrayList<>(action.run());
        add(id, title, nodes, edgeFrom, edgeTo, Provenance.CAPTURED, explanation, evidence, codeRefId);
    }

    public void observe(String id, String title, List<String> nodes, String explanation, List<Evidence> evidence) {
        add(id, title, nodes, null, null, Provenance.CAPTURED, explanation, new ArrayList<>(evidence), null);
    }

    public void infer(String id, String title, List<String> nodes, String explanation, List<Evidence> evidence) {
        add(id, title, nodes, null, null, Provenance.DERIVED, explanation, new ArrayList<>(evidence), null);
    }

    public void concept(String id, String title, List<String> nodes, String explanation) {
        add(id, title, nodes, null, null, Provenance.CONCEPTUAL, explanation, new ArrayList<>(), null);
    }

    private void add(String id, String title, List<String> nodes, String edgeFrom, String edgeTo, Provenance provenance,
                     String explanation, List<Evidence> evidence, String codeRefId) {
        List<SqlStatement> sql = new ArrayList<>();
        if (provenance == Provenance.CAPTURED) {
            List<String> calls = new ArrayList<>();
            for (JdbcEvent e : log.since(mark)) {
                if (e.isExecution()) {
                    sql.add(new SqlStatement(allSql.size() + sql.size() + 1, e.sql(), "JDBC", e.connection() + ": " + e.detail()));
                } else {
                    calls.add(e.connection() + ": " + (e.detail() == null ? e.kind() : e.detail()));
                }
            }
            for (RawTrace.SqlEvent h : hibernate.sqlSince(hibernateMark)) {
                sql.add(new SqlStatement(allSql.size() + sql.size() + 1, h.sql(), "HIBERNATE",
                        "Hibernate prepared this text for JDBC (not proof of execution)"));
            }
            if (!calls.isEmpty()) {
                evidence.add(ev("Other JDBC calls observed", String.join(" → ", calls.size() > 14 ? calls.subList(0, 14) : calls)
                        + (calls.size() > 14 ? " → …" : "")));
            }
        }
        mark = log.size();
        hibernateMark = hibernate.sqlCount();
        allSql.addAll(sql);
        steps.add(new TraceStep(id, title, nodes, edgeFrom, edgeTo, "REQUEST", provenance, explanation, evidence, sql, codeRefId));
    }

    public int sqlCountOfStep(String stepId) {
        return steps.stream().filter(s -> s.id().equals(stepId)).findFirst().orElseThrow().sql().size();
    }

    List<TraceStep> steps() {
        return List.copyOf(steps);
    }

    List<SqlStatement> sql() {
        return List.copyOf(allSql);
    }
}
