package com.springlab.lab.jwt;

import com.springlab.lab.trace.JwtStepAssembler;
import com.springlab.lab.trace.LifecycleTrace;
import com.springlab.lab.trace.RawTrace;
import com.springlab.lab.trace.TraceModule;
import org.springframework.stereotype.Component;

/** The JWT Security Simulator's tracing rules: only its catalogued lab-secure endpoints, assembled by {@link JwtStepAssembler}. */
@Component
public class JwtTraceModule implements TraceModule {

    private final JwtSimulatorCatalog catalog;
    private final JwtStepAssembler assembler;

    public JwtTraceModule(JwtSimulatorCatalog catalog, JwtStepAssembler assembler) {
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
