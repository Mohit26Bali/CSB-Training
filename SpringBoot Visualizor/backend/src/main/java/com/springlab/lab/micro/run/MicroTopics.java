package com.springlab.lab.micro.run;

import com.springlab.lab.micro.run.MicroDtos.TopicInfo;

import java.util.List;

/** The eight topics of the Microservices Lab, in teaching order. The ids are what each experiment's {@code topic} refers to. */
public final class MicroTopics {

    private MicroTopics() {
    }

    public static final List<TopicInfo> ALL = List.of(
            new TopicInfo("monolith", "Monolith vs microservices", "Deployment boundaries, data ownership, coupling, and when NOT to split."),
            new TopicInfo("communication", "Service communication", "Synchronous calls, events, timeouts, retries and the different ways a call can end."),
            new TopicInfo("gateway", "API gateway", "Routing, authentication, rate limiting and what a gateway is not."),
            new TopicInfo("discovery", "Service discovery", "Registration, health, lookup, stale entries and load balancing."),
            new TopicInfo("database-per-service", "Database per service", "Data ownership, API-based access, duplication and the cost of sharing tables."),
            new TopicInfo("saga", "Distributed transactions and sagas", "Why one transaction cannot span services, orchestration, choreography and compensation."),
            new TopicInfo("resilience", "Resilience and fault tolerance", "Timeouts, retries, circuit breakers, fallbacks, bulkheads and idempotency."),
            new TopicInfo("observability", "Observability and tracing", "Correlation ids, spans, latency attribution and what is lost without them."));

    public static boolean exists(String id) {
        return ALL.stream().anyMatch(t -> t.id().equals(id));
    }
}
