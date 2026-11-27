package com.springlab.lab.trace;

import com.springlab.lab.catalog.LifecycleCatalog;
import org.springframework.stereotype.Component;

/** The REST Request Lifecycle module's tracing rules (unchanged from Phase 5). */
@Component
public class LifecycleTraceModule implements TraceModule {

    private final LifecycleCatalog catalog;
    private final LifecycleStepAssembler assembler;

    public LifecycleTraceModule(LifecycleCatalog catalog, LifecycleStepAssembler assembler) {
        this.catalog = catalog;
        this.assembler = assembler;
    }

    @Override
    public boolean handles(String method, String path) {
        return catalog.isTraceable(method, path);
    }

    @Override
    public LifecycleTrace assemble(String id, RawTrace raw) {
        return assembler.assemble(id, raw);
    }
}
