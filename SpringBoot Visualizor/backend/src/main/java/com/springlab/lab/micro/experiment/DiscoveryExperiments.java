package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.InventoryContract.Reserve;
import com.springlab.lab.micro.contract.RegistryContract;
import com.springlab.lab.micro.contract.RegistryContract.InstanceKey;
import com.springlab.lab.micro.contract.RegistryContract.InstanceView;
import com.springlab.lab.micro.contract.RegistryContract.Instances;
import com.springlab.lab.micro.contract.RegistryContract.Query;
import com.springlab.lab.micro.contract.RegistryContract.Register;
import com.springlab.lab.micro.contract.RegistryContract.Resolved;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.ServiceBehaviours.Behaviour;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.ExperimentMeta;
import com.springlab.lab.micro.run.MicroExperiment;
import com.springlab.lab.micro.run.MicroRecorder;
import com.springlab.lab.micro.run.RunContext;
import com.springlab.lab.micro.run.WhatIfOption;
import com.springlab.lab.trace.CodeRefIds;
import com.springlab.lab.trace.LifecycleTrace.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/**
 * Topic 4: service discovery. The registry is a SIMULATED model (a table plus code), not Eureka or Consul. The "addresses" are labels;
 * nothing listens on them. What is real: the registry rows and SQL, the heartbeat/TTL rules on the simulated clock, and the fact that a
 * call aimed at a crashed instance fails.
 */
public final class DiscoveryExperiments {

    static final String NOTE = "SIMULATED registry (a table and code, not Eureka); addresses are labels";

    private DiscoveryExperiments() {
    }

    static void register(ServiceBus bus, String service, String instance) {
        bus.startRequest();
        Lab.value(bus.call(null, RegistryContract.REGISTER, new Register(service, instance, "sim://" + service + "/" + instance), Lab.SETUP));
    }

    static CallResult<Resolved> resolve(ServiceBus bus, String service) {
        bus.startRequest();
        return bus.call(null, RegistryContract.RESOLVE, new Query(service), Lab.SETUP);
    }

    static String describe(Instances instances) {
        List<String> parts = new ArrayList<>();
        for (InstanceView v : instances.instances()) {
            parts.add(v.instance() + " " + v.status() + (v.healthy() ? " healthy" : " NOT healthy") + " (last heartbeat " + v.ageMillis() + " ms ago)");
        }
        return parts.isEmpty() ? "none" : String.join("; ", parts);
    }

    static Instances list(ServiceBus bus, String service) {
        bus.startRequest();
        return Lab.value(bus.call(null, RegistryContract.LIST, new Query(service), Lab.SETUP));
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class RegisterAndLookup implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("disc-register-lookup", "discovery", "Register instances and look a service up by name",
                    "How does a client find a service without a hard-coded address, and what does it get for a service nobody registered?",
                    "Instances register under a service name; clients ask the registry for a healthy instance by name. An unknown name is a clear business "
                            + "refusal, not a connection error.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();

            rec.live("register", "Three instances register with the registry", nodes("inventory", "product", "registry", "registry-db"), "inventory", "registry",
                    "Each instance announces its service name, instance id and address. The registry stores them in its own table.", CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        register(bus, "inventory", "inventory-1");
                        register(bus, "inventory", "inventory-2");
                        register(bus, "product", "product-1");
                        return List.of(ev("Registry", NOTE), ev("Registered", "inventory-1, inventory-2, product-1"));
                    });

            rec.live("list", "The registry lists the inventory instances", nodes("client", "registry", "registry-db"), "client", "registry",
                    "An instance is healthy when its last health report was UP and its heartbeat is newer than the TTL (" + RegistryContract.TTL_MILLIS
                            + " simulated ms).", CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> List.of(ev("Instances of inventory", describe(list(bus, "inventory")))));

            rec.live("resolve", "Resolve 'inventory' three times", nodes("client", "registry"), "client", "registry",
                    "Client-side discovery: the client asks for an instance and then calls it. The registry hands out healthy instances in turn.",
                    CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        List<String> picked = new ArrayList<>();
                        for (int i = 0; i < 3; i++) {
                            picked.add(resolve(bus, "inventory").value().instance());
                        }
                        return List.of(ev("Instances returned, in order", String.join(", ", picked)));
                    });

            rec.live("unknown-service", "Resolve a service nobody registered", nodes("client", "registry"), "client", "registry",
                    "The registry answers with a business refusal, UNKNOWN_SERVICE. The caller can tell this from the registry being down.",
                    CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        CallResult<Resolved> r = resolve(bus, "payment");
                        return List.of(ev("Caller sees", Lab.outcome(r)));
                    });

            rec.concept("static-vs-dynamic", "Why not a fixed address?", nodes("client", "registry"),
                    "A hard-coded address breaks when an instance moves, scales out or is replaced. Registration and lookup let instances come and go. "
                            + "A gateway is not discovery: discovery answers where a service is, the gateway answers which service a request is for.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class LoadBalancing implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("disc-load-balancing", "discovery", "Several instances and basic load balancing",
                    "How are requests spread over several instances of one service?",
                    "Round-robin resolution sends consecutive requests to different healthy instances, so work is spread about evenly.",
                    "services", List.of(WhatIfOption.integer("instances", "Instances of the Inventory service", 3, 1, 4),
                            WhatIfOption.integer("requests", "Requests", 6, 1, 9)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            int instances = ctx.number("instances");
            int requests = ctx.number("requests");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] product = new long[1];
            Map<String, Integer> served = new LinkedHashMap<>();

            rec.live("register", instances + " instance(s) of Inventory register", nodes("inventory", "registry", "registry-db"), "inventory", "registry",
                    "Fixtures create a product with " + requests + " units, then the instances register.", CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        bus.startRequest();
                        product[0] = Lab.seedProduct(bus, "SKU-1", "4.50", requests);
                        for (int i = 1; i <= instances; i++) {
                            register(bus, "inventory", "inventory-" + i);
                        }
                        return List.of(ev("Registry", NOTE), ev("Instances", describe(list(bus, "inventory"))));
                    });

            rec.live("dispatch", requests + " reservations, each sent to the instance the registry returns", nodes("client", "registry", "inventory", "inventory-db"),
                    "client", "inventory",
                    "For every request the client resolves a name to an instance, then calls that instance. Each call really reserves one unit.",
                    CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        for (int i = 1; i <= requests; i++) {
                            String instance = resolve(bus, "inventory").value().instance();
                            bus.startRequest();
                            CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(i, product[0], 1), CallOptions.timeout(200).onInstance(instance));
                            if (r.isSuccess()) {
                                served.merge(instance, 1, Integer::sum);
                            }
                        }
                        List<Evidence> out = new ArrayList<>();
                        served.forEach((k, v) -> out.add(ev("Requests served by " + k, v)));
                        out.add(Lab.stockEv(ctx, product[0]));
                        return out;
                    });

            int max = served.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = served.size() < instances ? 0 : served.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            rec.infer("balance", "Was the work spread evenly?", nodes("registry", "inventory"),
                    "Calculated from the number of requests each instance served. Round-robin gives every instance a turn, so the counts differ by at most one.",
                    List.of(ev("Busiest instance served", max), ev("Least busy instance served", min), ev("Spread evenly (difference at most 1)", max - min <= 1)));

            rec.concept("real-balancers", "What real load balancing considers", nodes("registry", "inventory"),
                    "Round-robin ignores how busy each instance is. Real balancers also weigh active connections, latency and instance size, and may run in "
                            + "the client, in a gateway or in the platform. This lab shows only the rotation.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Unavailable implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("disc-unavailable", "discovery", "An instance reports unhealthy",
                    "What does discovery do when an instance reports DOWN, and when no healthy instance is left?",
                    "Unhealthy instances are skipped. When none is healthy the registry answers NO_HEALTHY_INSTANCE, which the caller must handle.",
                    "services", List.of(WhatIfOption.integer("instances", "Instances of the Inventory service", 2, 1, 3),
                            WhatIfOption.integer("unhealthy", "Instances that report DOWN", 1, 0, 3)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            int instances = ctx.number("instances");
            int down = Math.min(ctx.number("unhealthy"), instances);
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] product = new long[1];
            String[] after = new String[1];

            rec.live("register", instances + " instance(s) register and are healthy", nodes("inventory", "registry", "registry-db"), "inventory", "registry",
                    "Fixtures create a product, then the instances register.", CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        bus.startRequest();
                        product[0] = Lab.seedProduct(bus, "SKU-1", "4.50", 10);
                        for (int i = 1; i <= instances; i++) {
                            register(bus, "inventory", "inventory-" + i);
                        }
                        return List.of(ev("Registry", NOTE), ev("Instances", describe(list(bus, "inventory"))));
                    });

            rec.live("report-down", down + " instance(s) report DOWN", nodes("inventory", "registry", "registry-db"), "inventory", "registry",
                    "A health check fails and the instance reports DOWN to the registry. In the lab the instance really is down: calls aimed at it fail.",
                    CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        for (int i = 1; i <= down; i++) {
                            bus.startRequest();
                            Lab.value(bus.call(null, RegistryContract.REPORT_HEALTH, new RegistryContract.HealthReport("inventory", "inventory-" + i, false), Lab.SETUP));
                            ctx.behaviours().setInstance(ServiceName.INVENTORY, "inventory-" + i, Behaviour.down());
                        }
                        return List.of(ev("Instances", describe(list(bus, "inventory"))));
                    });

            rec.live("resolve", "Resolve 'inventory' " + (instances + 1) + " times", nodes("client", "registry", "inventory"), "client", "registry",
                    "Unhealthy instances are skipped. If every instance is unhealthy the registry has nothing to return.", CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        List<String> picked = new ArrayList<>();
                        for (int i = 0; i < instances + 1; i++) {
                            CallResult<Resolved> r = resolve(bus, "inventory");
                            picked.add(r.isSuccess() ? r.value().instance() : Lab.outcome(r));
                        }
                        after[0] = String.join(", ", picked);
                        return List.of(ev("Results, in order", after[0]));
                    });

            rec.infer("conclusion", "What the caller must be ready for", nodes("registry", "client"),
                    "Calculated from the number of healthy instances left.",
                    List.of(ev("Healthy instances left", instances - down),
                            ev("Registry answer when none is healthy", instances - down == 0 ? "NO_HEALTHY_INSTANCE (a business refusal the caller must handle)" : "not reached in this run")));

            rec.concept("health-checks", "Where health comes from", nodes("registry"),
                    "Real systems learn health from instance heartbeats, from active health checks by the registry or load balancer, or from both. A health "
                            + "check usually reports whether the process is up, not whether it can do useful work, which is why it can be wrong.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class StaleRegistration implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("disc-stale-registration", "discovery", "A stale registration: the registry hands out a dead instance",
                    "What happens when an instance crashes without deregistering, and how long does the registry keep recommending it?",
                    "The registry only knows what instances tell it. A crashed instance stays listed as healthy until its heartbeat is older than the TTL, "
                            + "and clients that resolve it in the meantime fail.",
                    "services", List.of(WhatIfOption.integer("waitMillis", "Simulated time the client waits before looking again", 4000, 0, 6000)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            int wait = ctx.number("waitMillis");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] product = new long[1];
            int[] failures = new int[2];

            rec.live("register", "Two instances register", nodes("inventory", "registry", "registry-db"), "inventory", "registry",
                    "inventory-1 and inventory-2 register and are healthy.", CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        bus.startRequest();
                        product[0] = Lab.seedProduct(bus, "SKU-1", "4.50", 20);
                        register(bus, "inventory", "inventory-1");
                        register(bus, "inventory", "inventory-2");
                        return List.of(ev("Registry", NOTE), ev("Instances", describe(list(bus, "inventory"))));
                    });

            rec.live("crash", "inventory-2 crashes without deregistering", nodes("inventory", "registry"), "inventory", "registry",
                    "Nothing tells the registry. The process is gone, but its row says UP and its last heartbeat is recent, so the registry still lists it "
                            + "as healthy.", CodeRefIds.MICRO_INSTANCE_BEHAVIOUR, () -> {
                        ctx.behaviours().setInstance(ServiceName.INVENTORY, "inventory-2", Behaviour.down());
                        return List.of(ev("Instances as the registry sees them", describe(list(bus, "inventory"))));
                    });

            rec.live("stale-lookup", "Clients resolve and call while the entry is stale", nodes("client", "registry", "inventory"), "client", "inventory",
                    "Round-robin eventually returns the dead instance. The call to it fails with UNAVAILABLE, although the registry reported it healthy.",
                    CodeRefIds.MICRO_INSTANCE_BEHAVIOUR, () -> {
                        List<Evidence> out = new ArrayList<>();
                        for (int i = 1; i <= 4; i++) {
                            String instance = resolve(bus, "inventory").value().instance();
                            bus.startRequest();
                            CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(i, product[0], 1), CallOptions.timeout(200).onInstance(instance));
                            if (!r.isSuccess()) {
                                failures[0]++;
                            }
                            out.add(ev("Request " + i + " sent to " + instance, Lab.outcome(r)));
                        }
                        out.add(ev("Failures", failures[0]));
                        return out;
                    });

            rec.live("wait-and-heartbeat", "Time passes: inventory-1 keeps its heartbeat, inventory-2 cannot", nodes("inventory", "registry", "registry-db"), "inventory", "registry",
                    "The simulated clock moves forward by the chosen wait. The living instance sends a heartbeat; the dead one cannot. The expiry rule is explicit: "
                            + "an instance is healthy while its heartbeat is at most " + RegistryContract.TTL_MILLIS + " ms old (exactly " + RegistryContract.TTL_MILLIS
                            + " ms is still healthy) and stale from " + (RegistryContract.TTL_MILLIS + 1) + " ms.", CodeRefIds.MICRO_REGISTRY_TTL, () -> {
                        bus.clock().advanceTo(bus.clock().now() + wait);
                        bus.startRequest();
                        Lab.value(bus.call(null, RegistryContract.HEARTBEAT, new InstanceKey("inventory", "inventory-1"), Lab.SETUP));
                        return List.of(ev("Waited (simulated)", wait + " ms"), ev("Instances as the registry sees them", describe(list(bus, "inventory"))));
                    });

            rec.live("lookup-again", "Clients resolve and call again", nodes("client", "registry", "inventory"), "client", "inventory",
                    "If the wait exceeded the TTL the dead instance is no longer healthy and is not returned. Otherwise it is still recommended.",
                    CodeRefIds.MICRO_REGISTRY_RESOLVE, () -> {
                        List<Evidence> out = new ArrayList<>();
                        for (int i = 5; i <= 8; i++) {
                            String instance = resolve(bus, "inventory").value().instance();
                            bus.startRequest();
                            CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(i, product[0], 1), CallOptions.timeout(200).onInstance(instance));
                            if (!r.isSuccess()) {
                                failures[1]++;
                            }
                            out.add(ev("Request " + i + " sent to " + instance, Lab.outcome(r)));
                        }
                        out.add(ev("Failures", failures[1]));
                        return out;
                    });

            rec.infer("conclusion", "How long was the registry wrong?", nodes("registry", "client"),
                    "Calculated from the wait and the TTL: the registry cannot know about a silent crash until the heartbeat is older than the TTL.",
                    List.of(ev("TTL", RegistryContract.TTL_MILLIS + " ms (simulated)"), ev("Waited", wait + " ms"),
                            ev("Dead instance still recommended after the wait", failures[1] > 0),
                            ev("Failures before / after the wait", failures[0] + " / " + failures[1])));

            rec.concept("client-retries", "What clients do about it", nodes("client", "registry"),
                    "A shorter TTL detects crashes sooner but makes healthy instances look dead when a heartbeat is delayed. Clients therefore also retry on "
                            + "another instance after a failed call, and use health checks and circuit breakers. Those are the Resilience topic.");
        }
    }
}
