package com.springlab.lab.micro.core;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the handlers of every service at startup. A module can register only operations that belong to its own service, so a
 * service cannot quietly implement (or hijack) another service's contract. Handlers are stateless: all per-run state lives in the
 * run (its run key, behaviours, database connections).
 */
@Component
public final class HandlerRegistry {

    private final Map<Operation<?, ?>, ServiceHandler<?, ?>> handlers = new HashMap<>();

    public HandlerRegistry(List<ServiceModule> modules) {
        for (ServiceModule module : modules) {
            module.register(new Registrar(module.service()));
        }
    }

    public final class Registrar {
        private final ServiceName owner;

        private Registrar(ServiceName owner) {
            this.owner = owner;
        }

        public <Q, R> void on(Operation<Q, R> operation, ServiceHandler<Q, R> handler) {
            if (operation.service() != owner) {
                throw new IllegalStateException(owner.id() + " may not implement " + operation + ", which belongs to " + operation.service().id());
            }
            if (handlers.put(operation, handler) != null) {
                throw new IllegalStateException("Operation " + operation + " has two handlers");
            }
        }
    }

    @SuppressWarnings("unchecked")
    <Q, R> ServiceHandler<Q, R> find(Operation<Q, R> operation) {
        ServiceHandler<?, ?> handler = handlers.get(operation);
        if (handler == null) {
            throw new IllegalArgumentException("No handler is registered for " + operation);
        }
        return (ServiceHandler<Q, R>) handler;
    }

    public int size() {
        return handlers.size();
    }
}
