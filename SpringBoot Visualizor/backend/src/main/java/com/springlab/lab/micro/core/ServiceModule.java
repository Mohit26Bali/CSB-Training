package com.springlab.lab.micro.core;

/** A simulated service. It registers handlers for ITS OWN operations only; {@link HandlerRegistry} enforces that. */
public interface ServiceModule {

    ServiceName service();

    void register(HandlerRegistry.Registrar registrar);
}
