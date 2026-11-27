package com.springlab.lab.micro.run;

import com.springlab.lab.jdbc.JdbcEvent;
import com.springlab.lab.jdbc.JdbcEventLog;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.TraceBook;
import com.springlab.lab.trace.LifecycleTrace.Evidence;
import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import com.springlab.lab.trace.Provenance;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns what an experiment does into teaching steps, with the shared provenance model:
 * <ul>
 *   <li>{@link #live}: an action really ran. The service calls it made (outcome, simulated duration, whether the handler ran) and
 *       the SQL the services handed to the JDBC driver while it ran are attached. CAPTURED.</li>
 *   <li>{@link #observe}: facts the lab read from the run's own rows. CAPTURED.</li>
 *   <li>{@link #infer}: a conclusion calculated from captured facts. DERIVED.</li>
 *   <li>{@link #concept}: an explanation; nothing about it was observed. CONCEPTUAL.</li>
 * </ul>
 * Simulated durations are labelled "simulated" wherever they appear; real elapsed times are labelled "measured".
 */
public final class MicroRecorder {

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

    private final JdbcEventLog events;
    private final TraceBook traces;
    private final List<TraceStep> steps = new ArrayList<>();
    private final List<SqlStatement> allSql = new ArrayList<>();
    private int eventMark;
    private int callMark;

    MicroRecorder(JdbcEventLog events, TraceBook traces) {
        this.events = events;
        this.traces = traces;
    }

    public void live(String id, String title, List<String> nodes, String edgeFrom, String edgeTo, String explanation, String codeRefId,
                     Action action) throws Exception {
        eventMark = events.size();
        callMark = traces.callCount();
        List<Evidence> actionEvidence = action.run();
        add(id, title, nodes, edgeFrom, edgeTo, Provenance.CAPTURED, explanation, new ArrayList<>(actionEvidence), codeRefId, true);
    }

    public void observe(String id, String title, List<String> nodes, String explanation, List<Evidence> evidence) {
        add(id, title, nodes, null, null, Provenance.CAPTURED, explanation, new ArrayList<>(evidence), null, false);
    }

    public void infer(String id, String title, List<String> nodes, String explanation, List<Evidence> evidence) {
        add(id, title, nodes, null, null, Provenance.DERIVED, explanation, new ArrayList<>(evidence), null, false);
    }

    public void concept(String id, String title, List<String> nodes, String explanation) {
        add(id, title, nodes, null, null, Provenance.CONCEPTUAL, explanation, new ArrayList<>(), null, false);
    }

    private void add(String id, String title, List<String> nodes, String edgeFrom, String edgeTo, Provenance provenance,
                     String explanation, List<Evidence> evidence, String codeRefId, boolean attachObservations) {
        List<SqlStatement> sql = new ArrayList<>();
        if (attachObservations) {
            List<Evidence> head = new ArrayList<>();
            for (CallRecord call : traces.callsSince(callMark)) {
                head.add(ev("Service call " + call.seq(), call.describe()));
            }
            List<String> other = new ArrayList<>();
            for (JdbcEvent e : events.since(eventMark)) {
                if (e.isExecution()) {
                    sql.add(new SqlStatement(allSql.size() + sql.size() + 1, e.sql(), "SERVICE DB", e.connection() + ": " + e.detail()));
                } else if (!RunContext.INSPECTOR.equals(e.connection()) && !isStatementNoise(e)) {
                    // The lab inspector's own connection and the per-statement prepare/close calls are left out: the SQL is listed separately,
                    // and what matters here is the connection and transaction calls (setAutoCommit, commit, rollback, close).
                    other.add(e.connection() + ": " + (e.detail() == null ? e.kind() : e.detail()));
                }
            }
            if (!other.isEmpty()) {
                // Long lists keep the first and the LAST calls: the last one is usually the commit or the rollback, which must stay visible.
                List<String> shown = new ArrayList<>(other.size() > 14 ? other.subList(0, 7) : other);
                if (other.size() > 14) {
                    shown.add("… (" + (other.size() - 14) + " more) …");
                    shown.addAll(other.subList(other.size() - 7, other.size()));
                }
                head.add(ev("Other database calls observed", String.join(" → ", shown)));
            }
            head.addAll(evidence);
            evidence = head;
        }
        eventMark = events.size();
        callMark = traces.callCount();
        allSql.addAll(sql);
        steps.add(new TraceStep(id, title, nodes, edgeFrom, edgeTo, "REQUEST", provenance, explanation, evidence, sql, codeRefId));
    }

    /** How many times the connection call {@code name}() (for example "commit" or "rollback") was recorded so far, over the whole run. */
    public long countDatabaseCalls(String name) {
        String wanted = name.toLowerCase() + "()";
        return events.since(0).stream().filter(e -> !e.isExecution() && e.detail() != null && e.detail().toLowerCase().equals(wanted)).count();
    }

    private static boolean isStatementNoise(JdbcEvent e) {
        String detail = e.detail() == null ? "" : e.detail();
        return detail.contains("prepareStatement") || detail.contains("Statement.close()");
    }

    /** The micro_lab_* tables each connection label (a service id) handed to the JDBC driver, from the captured SQL. The lab inspector is excluded. */
    public java.util.Map<String, java.util.Set<String>> tablesTouchedByService() {
        java.util.Map<String, java.util.Set<String>> out = new java.util.TreeMap<>();
        java.util.regex.Pattern table = java.util.regex.Pattern.compile("micro_lab_[a-z_]+");
        for (SqlStatement s : allSql) {
            String label = s.trigger() == null ? "?" : s.trigger().split(":", 2)[0];
            if (label.equals(RunContext.INSPECTOR)) {
                continue;
            }
            java.util.regex.Matcher m = table.matcher(s.sql());
            while (m.find()) {
                out.computeIfAbsent(label, k -> new java.util.TreeSet<>()).add(m.group());
            }
        }
        return out;
    }

    List<TraceStep> steps() {
        return List.copyOf(steps);
    }

    List<SqlStatement> sql() {
        return List.copyOf(allSql);
    }
}
