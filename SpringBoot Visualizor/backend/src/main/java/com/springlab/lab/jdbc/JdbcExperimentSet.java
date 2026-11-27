package com.springlab.lab.jdbc;

import java.util.Map;

/** A group of JDBC experiments contributed by one component; {@link JdbcLabService} collects every set. */
public interface JdbcExperimentSet {

    Map<String, JdbcExperiment> experiments();
}
