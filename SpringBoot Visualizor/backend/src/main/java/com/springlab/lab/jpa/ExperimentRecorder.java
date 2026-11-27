package com.springlab.lab.jpa;

import com.springlab.lab.trace.LifecycleTrace.Evidence;
import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import com.springlab.lab.trace.Provenance;
import com.springlab.lab.trace.RawTrace;
import com.springlab.lab.trace.TraceCollector;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what an experiment does into teaching steps with honest provenance.
 *
 * <ul>
 *   <li>{@link #live}: the experiment really runs an action; every statement Hibernate prepared WHILE that action ran
 *       is attached to the step (CAPTURED).</li>
 *   <li>{@link #observe}: facts the experiment read (rows, object state); CAPTURED, and any statement prepared meanwhile
 *       would be attached as well.</li>
 *   <li>{@link #infer}: a conclusion drawn from captured facts (DERIVED).</li>
 *   <li>{@link #concept}: an explanation; nothing about it was observed (CONCEPTUAL).</li>
 * </ul>
 * SQL text is what Hibernate handed to JDBC (placeholders, no values); it is never invented and never copied from a table of
 * expected statements.
 */
public final class ExperimentRecorder {

    /** The action of a live step; returns the facts to show for it. */
    @FunctionalInterface
    public interface Action {
        List<Evidence> run() throws Exception;
    }

    private static final String PHASE = "EXPERIMENT";

    private final TraceCollector collector;
    private final List<TraceStep> steps = new ArrayList<>();
    private final List<SqlStatement> allSql = new ArrayList<>();
    private int mark;

    ExperimentRecorder(TraceCollector collector) {
        this.collector = collector;
    }

    public static Evidence ev(String label, Object value) {
        return new Evidence(label, String.valueOf(value));
    }

    public static List<String> nodes(String... ids) {
        return List.of(ids);
    }

    public void live(String id, String title, List<String> nodes, String edgeFrom, String edgeTo, String explanation,
                     String codeRefId, Action action) throws Exception {
        mark = collector.sqlCount();
        List<Evidence> evidence = action.run();
        add(id, title, nodes, edgeFrom, edgeTo, Provenance.CAPTURED, explanation, evidence, codeRefId);
    }

    public void observe(String id, String title, List<String> nodes, String explanation, List<Evidence> evidence) {
        add(id, title, nodes, null, null, Provenance.CAPTURED, explanation, evidence, null);
    }

    public void infer(String id, String title, List<String> nodes, String explanation, List<Evidence> evidence) {
        add(id, title, nodes, null, null, Provenance.DERIVED, explanation, evidence, null);
    }

    public void concept(String id, String title, List<String> nodes, String explanation) {
        add(id, title, nodes, null, null, Provenance.CONCEPTUAL, explanation, List.of(), null);
    }

    private void add(String id, String title, List<String> nodes, String edgeFrom, String edgeTo, Provenance provenance,
                     String explanation, List<Evidence> evidence, String codeRefId) {
        List<SqlStatement> sql = new ArrayList<>();
        if (provenance == Provenance.CAPTURED) {
            for (RawTrace.SqlEvent event : collector.sqlSince(mark)) {
                sql.add(new SqlStatement(allSql.size() + sql.size() + 1, event.sql(), PHASE, event.trigger()));
            }
        }
        mark = collector.sqlCount();
        allSql.addAll(sql);
        steps.add(new TraceStep(id, title, nodes, edgeFrom, edgeTo, "REQUEST", provenance, explanation, evidence, sql,
                codeRefId));
    }

    /** The number of statements attached to an already recorded step (used for measured summaries). */
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
