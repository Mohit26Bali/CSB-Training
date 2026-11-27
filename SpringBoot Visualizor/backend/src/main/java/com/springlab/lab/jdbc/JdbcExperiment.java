package com.springlab.lab.jdbc;

/** One predefined, parameter-free JDBC experiment. It receives everything it may touch through the context. */
@FunctionalInterface
public interface JdbcExperiment {

    void run(JdbcExperimentContext context) throws Exception;
}
