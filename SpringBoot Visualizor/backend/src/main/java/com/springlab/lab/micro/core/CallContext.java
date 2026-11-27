package com.springlab.lab.micro.core;

/** What a handler knows about the call it is serving. {@code bus} lets it call other services (creating child spans). */
public record CallContext(ServiceName service, String correlationId, String spanId, ServiceBus bus) {
}
