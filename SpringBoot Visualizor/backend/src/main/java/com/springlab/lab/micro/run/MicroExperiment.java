package com.springlab.lab.micro.run;

/**
 * A predefined experiment. It talks to services only through {@code context.bus()} and may read the run's own rows through the
 * lab inspector; it never receives SQL, ids or any other free input from the browser.
 */
public interface MicroExperiment {

    ExperimentMeta meta();

    void run(RunContext context) throws Exception;
}
