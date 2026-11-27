package com.springlab.lab.trace;

/**
 * A learning module that can trace requests. Each module declares which (method, path) pairs it allows to be traced
 * and how the raw observations become that module's teaching steps. The trace filter serves every module through this
 * interface, so adding a module never touches the filter.
 */
public interface TraceModule {

    /** Tracing is honoured only for requests a module explicitly lists. */
    boolean handles(String method, String path);

    LifecycleTrace assemble(String id, RawTrace raw);
}
