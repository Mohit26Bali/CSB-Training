package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.InventoryContract.Reserve;
import com.springlab.lab.micro.contract.InventoryContract.SeedStock;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.GetProduct;
import com.springlab.lab.micro.contract.ProductContract.SeedProduct;
import com.springlab.lab.micro.core.Bulkheads;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.Outcome;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/**
 * Service communication, topic 2: the same call to the Inventory service ends in six different ways, and a caller must treat
 * them differently. The decisive case is the timeout in which the work WAS done.
 */
@Component
public class FailureSemanticsExperiment implements MicroExperiment {

    public static final String ID = "comm-failure-semantics";
    private static final String STOCK = "select available from micro_lab_stock where run_key = ? and product_ref = ?";
    private static final String RESERVATIONS = "select count(*) from micro_lab_reservations where run_key = ?";
    private static final String INSPECTOR_NOTE = "read by the lab inspector (not a service call)";

    @Override
    public ExperimentMeta meta() {
        return new ExperimentMeta(ID, "communication", "Six ways a service call can end",
                "What can the caller conclude when a call to the Inventory service succeeds, is refused, cannot connect, times out, "
                        + "fails unexpectedly, or is rejected locally?",
                "These are six different outcomes. A refused or unreachable call changed nothing, but a timeout can hide completed work, "
                        + "so retrying it is only safe for idempotent operations. A bulkhead keeps one saturated service from using another's capacity.",
                "services",
                List.of(WhatIfOption.integer("callTimeoutMillis", "Caller's timeout (simulated ms)", 100, 50, 500),
                        WhatIfOption.choice("bulkheadPolicy", "Concurrency permits", "per-service", List.of("per-service", "shared"))));
    }

    @Override
    public void run(RunContext ctx) throws Exception {
        int timeout = ctx.number("callTimeoutMillis");
        boolean shared = "shared".equals(ctx.choice("bulkheadPolicy"));
        ctx.useBulkhead(shared ? Bulkheads.Policy.shared(2) : Bulkheads.Policy.perService(2));
        ServiceBus bus = ctx.bus();
        MicroRecorder rec = ctx.recorder();
        CallOptions options = CallOptions.timeout(timeout);
        long[] product = new long[1];
        long[] order = {0};

        rec.live("seed", "Lab fixture: one product with 5 units in stock", nodes("client", "product", "product-db", "inventory", "inventory-db"),
                "client", "product",
                "The lab creates a product through the Product service and its stock through the Inventory service, so the next steps have "
                        + "something to reserve. These are real calls with real local transactions in each service's own tables.",
                null, () -> {
                    bus.startRequest();
                    CallResult<ProductContract.ProductRef> p = bus.call(null, ProductContract.SEED_PRODUCT,
                            new SeedProduct("SKU-1", "Notebook", new BigDecimal("4.50")), options);
                    product[0] = p.value().productRef();
                    bus.call(null, InventoryContract.SEED_STOCK, new SeedStock(product[0], 5), options);
                    return List.of(ev("Stock " + INSPECTOR_NOTE, ctx.inspectLong(STOCK, product[0])));
                });

        rec.live("success", "SUCCESS: reserve 2 units", nodes("client", "inventory", "inventory-db"), "client", "inventory",
                "The request reaches the Inventory service, which reserves the stock in one local transaction and answers. The caller can "
                        + "conclude that the work was done.",
                CodeRefIds.MICRO_BUS_CALL, () -> {
                    bus.startRequest();
                    CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(++order[0], product[0], 2), options);
                    return outcomeEvidence(r, ctx, product[0]);
                });

        rec.live("business-failure", "BUSINESS_FAILURE: reserve 99 units", nodes("client", "inventory", "inventory-db"), "client", "inventory",
                "The service was reached and understood the request, and refused it: there is not enough stock. This is a normal answer, "
                        + "not a fault. Nothing changed, and retrying the same request will get the same answer.",
                CodeRefIds.MICRO_INVENTORY_RESERVE, () -> {
                    bus.startRequest();
                    CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(++order[0], product[0], 99), options);
                    return outcomeEvidence(r, ctx, product[0]);
                });

        rec.live("unavailable", "UNAVAILABLE: the Inventory service is down", nodes("client", "inventory"), "client", "inventory",
                "The connection is refused, so the request was never processed. The caller knows nothing happened, which makes a retry "
                        + "safe (once the service is back).",
                null, () -> {
                    ctx.behaviours().set(ServiceName.INVENTORY, Behaviour.down());
                    bus.startRequest();
                    CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(++order[0], product[0], 1), options);
                    ctx.behaviours().reset(ServiceName.INVENTORY);
                    return outcomeEvidence(r, ctx, product[0]);
                });

        rec.live("timeout-work-done", "TIMEOUT: slow response, but the work WAS done", nodes("client", "inventory", "inventory-db"), "client",
                "inventory",
                "The service processes the request and commits its local transaction, but answers after the caller's timeout. The caller "
                        + "sees TIMEOUT and cannot tell the stock was reserved. Retrying a non-idempotent operation here would reserve twice.",
                CodeRefIds.MICRO_BUS_CALL, () -> {
                    ctx.behaviours().set(ServiceName.INVENTORY, Behaviour.delayed(timeout + 300));
                    bus.startRequest();
                    CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(++order[0], product[0], 1), options);
                    ctx.behaviours().reset(ServiceName.INVENTORY);
                    return outcomeEvidence(r, ctx, product[0]);
                });

        rec.live("timeout-request-lost", "TIMEOUT: the request was lost, nothing was done", nodes("client", "inventory", "inventory-db"), "client",
                "inventory",
                "The caller sees exactly the same TIMEOUT as in the previous step, but this time the request never reached the service. "
                        + "From the caller's side the two cases are indistinguishable. Only the lab can see the difference.",
                null, () -> {
                    ctx.behaviours().set(ServiceName.INVENTORY, Behaviour.requestLost());
                    bus.startRequest();
                    CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(++order[0], product[0], 1), options);
                    ctx.behaviours().reset(ServiceName.INVENTORY);
                    return outcomeEvidence(r, ctx, product[0]);
                });

        rec.live("technical-error", "TECHNICAL_ERROR: the service fails unexpectedly", nodes("client", "inventory"), "client", "inventory",
                "The service throws an unexpected exception instead of answering. This is neither a business decision nor an outage: it is "
                        + "a bug or a broken dependency. Only the exception class is recorded, never its message.",
                null, () -> {
                    ctx.behaviours().set(ServiceName.INVENTORY, Behaviour.error());
                    bus.startRequest();
                    CallResult<?> r = bus.call(null, InventoryContract.RESERVE, new Reserve(++order[0], product[0], 1), options);
                    ctx.behaviours().reset(ServiceName.INVENTORY);
                    return outcomeEvidence(r, ctx, product[0]);
                });

        rec.live("bulkhead", "BULKHEAD_REJECTED: Inventory is saturated; is Product affected?", nodes("client", "inventory", "product"), "client",
                "inventory",
                "Two requests are still being processed by the Inventory service and hold both of its permits. A third Inventory call is "
                        + "refused locally. Whether a call to the Product service still works depends on the permit policy: with separate "
                        + "permits it does, with one shared pool it does not.",
                CodeRefIds.MICRO_BULKHEAD, () -> {
                    List<Evidence> out = new ArrayList<>();
                    try (Bulkheads.Permit a = bus.holdPermit(ServiceName.INVENTORY); Bulkheads.Permit b = bus.holdPermit(ServiceName.INVENTORY)) {
                        bus.startRequest();
                        CallResult<?> inventory = bus.call(null, InventoryContract.RESERVE, new Reserve(++order[0], product[0], 1), options);
                        CallResult<?> catalogue = bus.call(null, ProductContract.GET_PRODUCT, new GetProduct(product[0]), options);
                        out.add(ev("Permit policy", shared ? "one pool shared by all services (2 permits)" : "separate permits per service (2 each)"));
                        out.add(ev("Inventory call", inventory.outcome()));
                        out.add(ev("Product call", catalogue.outcome()));
                        out.add(ev("Permits left for Product", bus.bulkheads().available(ServiceName.PRODUCT)));
                    }
                    out.add(ev("Permits left for Inventory after the held requests finish", bus.bulkheads().available(ServiceName.INVENTORY)));
                    return out;
                });

        rec.infer("what-the-caller-knows", "What the caller could and could not conclude",
                nodes("client", "inventory"),
                "Calculated from the calls recorded above. The same code path produced different outcomes, and only some of them tell the "
                        + "caller what happened to the data.",
                derivedEvidence(ctx));

        rec.concept("what-this-cannot-show", "What this simulation cannot show", nodes("client", "inventory"),
                "All services run in one Java process, so no packets are sent, nothing is serialized and the timing is simulated. A real "
                        + "network can also duplicate or reorder messages, drop a response after the work was done, or split into partitions. "
                        + "Those effects are explained here, not run.");
    }

    private static List<Evidence> outcomeEvidence(CallResult<?> r, RunContext ctx, long productRef) {
        List<Evidence> out = new ArrayList<>();
        out.add(ev("Caller sees", r.outcome() + (r.code() == null ? "" : " (" + r.code() + ")")));
        out.add(ev("Handler ran (known only to the lab)", r.handlerInvoked()));
        out.add(ev("Stock " + INSPECTOR_NOTE, ctx.inspectLong(STOCK, productRef)));
        out.add(ev("Reservations " + INSPECTOR_NOTE, ctx.inspectLong(RESERVATIONS)));
        return out;
    }

    private static List<Evidence> derivedEvidence(RunContext ctx) {
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        long timeoutsWithWork = 0;
        for (CallRecord call : ctx.traces().calls()) {
            counts.merge(call.outcome(), 1, Integer::sum);
            if (call.outcome() == Outcome.TIMEOUT && call.handlerInvoked()) {
                timeoutsWithWork++;
            }
        }
        List<Evidence> out = new ArrayList<>();
        for (Outcome o : Outcome.values()) {
            if (counts.containsKey(o)) {
                out.add(ev(o + " (" + counts.get(o) + " call(s))", Lab.meaning(o)));
            }
        }
        out.add(ev("Timeouts where the work was nevertheless done", timeoutsWithWork));
        return out;
    }
}
