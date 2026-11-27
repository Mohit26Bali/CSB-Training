package com.springlab.lab.jpa;

import java.util.Map;

/** A group of experiments contributed by one component; {@link JpaLabService} collects every set. */
public interface ExperimentSet {

    Map<String, Experiment> experiments();
}
