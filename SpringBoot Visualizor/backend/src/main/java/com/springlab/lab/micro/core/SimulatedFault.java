package com.springlab.lab.micro.core;

/** The unexpected exception injected by {@link ServiceBehaviours.Mode#ERROR}: a stand-in for a bug or a broken dependency. */
public final class SimulatedFault extends RuntimeException {

    public SimulatedFault(String message) {
        super(message);
    }
}
