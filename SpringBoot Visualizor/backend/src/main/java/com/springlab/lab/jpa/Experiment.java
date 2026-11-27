package com.springlab.lab.jpa;

/** One predefined, parameter-free experiment. It receives everything it may touch through the context. */
@FunctionalInterface
public interface Experiment {

    void run(ExperimentContext context) throws Exception;
}
