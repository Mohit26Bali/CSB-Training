package com.springlab.coding.execution;

/**
 * One test as the backend hands it to the worker. The backend reads tests from its database and is the only source of them. The test's
 * input goes into the sandbox; its expected values stay in the worker's trusted driver, outside it.
 */
public interface WorkerTest {

    String name();

    boolean visible();

    /** The worker's line format for this test. Everything in it is restricted to a small alphabet, so it cannot be misread. */
    String toWireLine(int index);
}
