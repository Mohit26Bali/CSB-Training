package com.springlab.lab.micro.run;

import com.springlab.lab.jdbc.CapturingDataSource;
import com.springlab.lab.jdbc.JdbcCapture;
import com.springlab.lab.jdbc.JdbcEventLog;
import com.springlab.lab.micro.core.Bulkheads;
import com.springlab.lab.micro.core.EventChannel;
import java.util.function.Supplier;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.ServiceBehaviours;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.SimClock;
import com.springlab.lab.micro.core.TraceBook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything ONE run owns: its key, its resolved What-If values, its clock, service behaviours, bulkheads, trace, captured
 * database access and recorder. Nothing in here is shared with another run, so runs cannot interfere with each other.
 */
public final class RunContext {

    static final String INSPECTOR = "lab-inspector";

    private final String runKey;
    private final String owner;
    private final String experimentId;
    private final Map<String, String> whatIf;
    private final HandlerRegistry handlers;
    private final DataSource applicationDataSource;

    private final SimClock clock = new SimClock();
    private final ServiceBehaviours behaviours = new ServiceBehaviours();
    private final TraceBook traces = new TraceBook();
    private final JdbcEventLog events = new JdbcEventLog();
    private final JdbcCapture capture;
    private final MicroRecorder recorder;
    private final Map<String, JdbcTemplate> templates = new HashMap<>();
    private final Map<String, TransactionTemplate> transactions = new HashMap<>();
    private final Map<String, CapturingDataSource> sources = new HashMap<>();
    private final Map<String, javax.sql.DataSource> guardedSources = new HashMap<>();
    private final Map<String, Object> state = new HashMap<>();
    private EventChannel channel;
    private JdbcTemplate inspector;
    private Bulkheads bulkheads = new Bulkheads(Bulkheads.Policy.perService(4));
    private boolean propagateCorrelation = true;
    private ServiceBus bus;

    RunContext(String runKey, String owner, String experimentId, Map<String, String> whatIf, HandlerRegistry handlers,
               DataSource applicationDataSource) {
        this.runKey = runKey;
        this.owner = owner;
        this.experimentId = experimentId;
        this.whatIf = Map.copyOf(whatIf);
        this.handlers = handlers;
        this.applicationDataSource = applicationDataSource;
        this.capture = new JdbcCapture(events, runKey);
        this.recorder = new MicroRecorder(events, traces);
    }

    public String runKey() {
        return runKey;
    }

    public String experimentId() {
        return experimentId;
    }

    String owner() {
        return owner;
    }

    public MicroRecorder recorder() {
        return recorder;
    }

    public ServiceBehaviours behaviours() {
        return behaviours;
    }

    public TraceBook traces() {
        return traces;
    }

    JdbcEventLog events() {
        return events;
    }

    // ---- What-If values (already validated against the experiment's options; defaults filled in)

    public boolean flag(String key) {
        return Boolean.parseBoolean(value(key));
    }

    public int number(String key) {
        return Integer.parseInt(value(key));
    }

    public String choice(String key) {
        return value(key);
    }

    private String value(String key) {
        String v = whatIf.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Unknown what-if option " + key);
        }
        return v;
    }

    // ---- configuration that must happen before the first call

    public void useBulkhead(Bulkheads.Policy policy) {
        requireNoBus();
        this.bulkheads = new Bulkheads(policy);
    }

    public void propagateCorrelation(boolean propagate) {
        requireNoBus();
        this.propagateCorrelation = propagate;
    }

    private void requireNoBus() {
        if (bus != null) {
            throw new IllegalStateException("Configure the bus before the first call");
        }
    }

    public ServiceBus bus() {
        if (bus == null) {
            bus = new ServiceBus(handlers, clock, behaviours, bulkheads, traces, propagateCorrelation);
        }
        return bus;
    }

    // ---- database access of the services (used through MicroDb)

    /** {@code label} is the service id (or "monolith" for the comparison workflow); it names the connection in the captured SQL. */
    synchronized JdbcTemplate jdbc(String label) {
        return templates.computeIfAbsent(label, s -> new JdbcTemplate(guarded(s)));
    }

    synchronized TransactionTemplate transaction(String label) {
        return transactions.computeIfAbsent(label, s -> new TransactionTemplate(new DataSourceTransactionManager(guarded(s))));
    }

    private CapturingDataSource source(String label) {
        if (applicationDataSource == null) {
            throw new IllegalStateException("This run has no database");
        }
        return sources.computeIfAbsent(label, s -> new CapturingDataSource(applicationDataSource, capture, s));
    }

    /** A service connection is the captured connection wrapped by the ownership guard; any other label (the monolith, the lab inspector) is not guarded. */
    private javax.sql.DataSource guarded(String label) {
        // ONE DataSource instance per label: Spring binds a transaction to a DataSource instance, so the template and the transaction manager
        // of a service must share it, otherwise the template would not take part in the transaction.
        return guardedSources.computeIfAbsent(label, l -> {
            CapturingDataSource captured = source(l);
            return OwnershipGuardedDataSource.serviceOf(l).<javax.sql.DataSource>map(s -> new OwnershipGuardedDataSource(captured, s)).orElse(captured);
        });
    }

    // ---- per-run state for the stateless services (rate-limit counters, round-robin positions): never shared between runs

    @SuppressWarnings("unchecked")
    synchronized <T> T state(String key, Supplier<T> initial) {
        return (T) state.computeIfAbsent(key, k -> initial.get());
    }

    /** The run's simulated message channel (created with the bus). */
    public synchronized EventChannel channel() {
        if (channel == null) {
            channel = new EventChannel(bus());
        }
        return channel;
    }

    // ---- the lab inspector: the lab's own read access to the run's rows. It is NOT a service call and is labelled as such.

    /** Runs a read-only, fixed query. The FIRST placeholder must be the run key (it is bound automatically). */
    public long inspectLong(String sql, Object... args) {
        if (!sql.toLowerCase().contains("run_key = ?")) {
            throw new IllegalArgumentException("Inspector queries must be scoped by run_key");
        }
        List<Object> params = new ArrayList<>();
        params.add(runKey);
        params.addAll(List.of(args));
        Long value = inspector().queryForObject(sql, Long.class, params.toArray());
        return value == null ? -1 : value;
    }

    /** Like {@link #inspectLong} for a single text value (for example a price). */
    public String inspectString(String sql, Object... args) {
        if (!sql.toLowerCase().contains("run_key = ?")) {
            throw new IllegalArgumentException("Inspector queries must be scoped by run_key");
        }
        List<Object> params = new ArrayList<>();
        params.add(runKey);
        params.addAll(List.of(args));
        List<String> rows = inspector().queryForList(sql, String.class, params.toArray());
        return rows.isEmpty() ? "none" : String.valueOf(rows.get(0));
    }

    private synchronized JdbcTemplate inspector() {
        if (inspector == null) {
            if (applicationDataSource == null) {
                throw new IllegalStateException("This run has no database");
            }
            inspector = new JdbcTemplate(new CapturingDataSource(applicationDataSource, capture, INSPECTOR));
        }
        return inspector;
    }
}
