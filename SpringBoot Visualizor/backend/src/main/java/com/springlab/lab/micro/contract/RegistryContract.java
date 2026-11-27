package com.springlab.lab.micro.contract;

import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceName;

import java.util.List;

/**
 * The public contract of the (simulated) Service Registry. It is a table plus code inside SpringLab, not Eureka or Consul, and the
 * "addresses" are labels: nothing listens on them. An instance is healthy when its last health report said UP and it sent a
 * heartbeat within {@link #TTL_MILLIS} simulated milliseconds. Business refusal codes: UNKNOWN_SERVICE, NO_HEALTHY_INSTANCE,
 * NOT_REGISTERED.
 */
public final class RegistryContract {

    private RegistryContract() {
    }

    public static final int TTL_MILLIS = 3000;

    /**
     * The explicit expiry rule. An instance heartbeat is FRESH while its age is at most {@link #TTL_MILLIS} simulated milliseconds, so an
     * instance is still healthy at exactly the TTL and is stale from TTL + 1 ms. The reaper uses the same rule (it removes only age greater
     * than the TTL). Ages are measured on the simulated clock, which only moves when a call or a wait moves it.
     */
    public static boolean isFresh(long ageMillis) {
        return ageMillis <= TTL_MILLIS;
    }

    public static final Operation<Register, Ack> REGISTER = new Operation<>(ServiceName.REGISTRY, "register");
    public static final Operation<InstanceKey, Ack> HEARTBEAT = new Operation<>(ServiceName.REGISTRY, "heartbeat");
    public static final Operation<InstanceKey, Ack> DEREGISTER = new Operation<>(ServiceName.REGISTRY, "deregister");
    public static final Operation<HealthReport, Ack> REPORT_HEALTH = new Operation<>(ServiceName.REGISTRY, "reportHealth");
    /** Client-side discovery: one healthy instance, chosen round-robin. */
    public static final Operation<Query, Resolved> RESOLVE = new Operation<>(ServiceName.REGISTRY, "resolve");
    public static final Operation<Query, Instances> LIST = new Operation<>(ServiceName.REGISTRY, "list");
    /** The registry reaper: removes instances whose heartbeat is older than the TTL. */
    public static final Operation<Evict, Evicted> EVICT_EXPIRED = new Operation<>(ServiceName.REGISTRY, "evictExpired");

    public record Register(String service, String instance, String address) {
    }

    public record InstanceKey(String service, String instance) {
    }

    public record HealthReport(String service, String instance, boolean up) {
    }

    public record Query(String service) {
    }

    public record Resolved(String instance, String address) {
    }

    public record InstanceView(String instance, String address, String status, long ageMillis, boolean healthy) {
    }

    public record Instances(List<InstanceView> instances) {
    }

    public record Evict() {
    }

    public record Evicted(int count) {
    }

    public record Ack(boolean done) {
    }
}
