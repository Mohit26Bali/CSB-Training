package com.springlab.lab.micro.run;

import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** Per-run state for stateless services (a rate-limit counter, a round-robin position). Never shared between runs or users. */
@Component
public class MicroRunState {

    public <T> T of(String key, Supplier<T> initial) {
        return MicroRunContext.require().state(key, initial);
    }
}
