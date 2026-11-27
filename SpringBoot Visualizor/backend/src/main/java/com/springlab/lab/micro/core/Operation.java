package com.springlab.lab.micro.core;

/**
 * A typed service contract entry: one operation of one service. {@code Q} is the request type and {@code R} the response type, so
 * a caller cannot send the wrong payload to the wrong operation. Contracts are declared in {@code micro.contract}; callers import
 * the contract, never the implementing service.
 */
public record Operation<Q, R>(ServiceName service, String name) {

    @Override
    public String toString() {
        return service.id() + "." + name;
    }
}
