package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.GatewayContract;
import com.springlab.lab.micro.contract.GatewayContract.Credential;
import com.springlab.lab.micro.contract.GatewayContract.HttpRequest;
import com.springlab.lab.micro.contract.GatewayContract.HttpResponse;
import com.springlab.lab.micro.contract.GatewayContract.RateLimit;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.GetProduct;
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
import java.util.List;

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/**
 * Topic 3: the API gateway. The gateway is a SIMULATED, in-process model (a route table, a credential STATE, a fixed-window counter on
 * the simulated clock). It is not a production gateway and not Spring Cloud Gateway.
 */
public final class GatewayExperiments {

    static final String NOTE = "SIMULATED in-process gateway (not a production gateway)";

    private GatewayExperiments() {
    }

    /** One request through the gateway, with the evidence every step shares. */
    static List<Evidence> through(RunContext ctx, String label, HttpRequest request) {
        int mark = ctx.traces().callCount();
        ctx.bus().startRequest();
        CallResult<HttpResponse> result = ctx.bus().call(null, GatewayContract.ROUTE, request, Lab.SETUP);
        List<Evidence> out = new ArrayList<>();
        if (result.isSuccess()) {
            HttpResponse r = result.value();
            out.add(ev(label + " status", r.status() + " " + r.reason()));
            out.add(ev("Forwarded to", r.routedTo() == null ? "nobody: the gateway answered itself" : r.routedTo()));
            if (r.downstream() != null) {
                out.add(ev("Service answered", r.downstream()));
            }
        } else {
            out.add(ev(label + " status", "no answer: the gateway call itself ended " + Lab.outcome(result)));
        }
        out.add(ev("Services called behind the gateway", Lab.downstreamCalls(ctx, mark)));
        return out;
    }

    private static long[] seed(RunContext ctx, ServiceBus bus) {
        bus.startRequest();
        return new long[] {Lab.seedUser(bus, "100.00"), Lab.seedProduct(bus, "SKU-1", "4.50", 5)};
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Routing implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("gw-routing", "gateway", "Routing: one entry point, several services",
                    "How does the gateway send different requests to different services, and what happens to a path that has no route?",
                    "The gateway forwards each public path to the service that owns it. Internal services and unknown paths have no route and are answered by the "
                            + "gateway itself with 404, without reaching any service.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];

            rec.live("seed", "Fixtures: a customer and a product with 5 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services. The gateway then exposes these routes: " + String.join("; ", GatewayContract.ROUTES) + ".", null, () -> {
                        long[] r = seed(ctx, bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        return List.of(ev("Gateway", NOTE), ev("Public routes", String.join("; ", GatewayContract.ROUTES)));
                    });

            rec.live("route-user", "GET /users/{id}", nodes("client", "gateway", "user", "user-db"), "client", "gateway",
                    "The gateway matches the path to the User service and forwards the call through the service bus.", CodeRefIds.MICRO_GATEWAY_ROUTE,
                    () -> through(ctx, "Response", HttpRequest.get("/users/" + ref[0], "browser")));

            rec.live("route-product", "GET /products/{id}", nodes("client", "gateway", "product", "product-db"), "gateway", "product",
                    "The same entry point, a different service.", CodeRefIds.MICRO_GATEWAY_ROUTE,
                    () -> through(ctx, "Response", HttpRequest.get("/products/" + ref[1], "browser")));

            rec.live("route-order", "POST /orders", nodes("client", "gateway", "order", "order-db"), "gateway", "order",
                    "Placing an order is routed to the Order service, which calls its own dependencies. The client sees one request.", CodeRefIds.MICRO_GATEWAY_ROUTE,
                    () -> through(ctx, "Response", new HttpRequest("POST", "/orders", Credential.VALID, "browser", ref[0], ref[1], 1)));

            rec.live("route-internal", "GET /inventory/stock: an internal service has no route", nodes("client", "gateway"), "client", "gateway",
                    "Inventory is an internal service. It is not in the route table, so the gateway answers 404 itself and nothing behind it is called. "
                            + "Not exposing internal services is part of what a gateway is for.", CodeRefIds.MICRO_GATEWAY_ROUTE,
                    () -> through(ctx, "Response", HttpRequest.get("/inventory/stock", "browser")));

            rec.live("route-unknown", "GET /nope: an invalid route", nodes("client", "gateway"), "client", "gateway",
                    "A path that matches nothing is rejected at the edge with 404 NO_ROUTE.", CodeRefIds.MICRO_GATEWAY_ROUTE,
                    () -> through(ctx, "Response", HttpRequest.get("/nope", "browser")));

            rec.concept("real-gateways", "What a production gateway adds", nodes("gateway"),
                    "Real gateways (Spring Cloud Gateway, Kong, NGINX, cloud gateways) add TLS termination, token validation, request transformation, "
                            + "caching, canary routing and observability, and they are deployed and scaled as their own component. This lab shows the "
                            + "routing decision only. A gateway is also not service discovery: discovery answers where a service is, the gateway answers "
                            + "which service a request is for.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Authentication implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("gw-auth", "gateway", "Authentication at the edge",
                    "What happens to a request with a missing or expired credential, and does it ever reach a service?",
                    "The gateway rejects an unauthenticated request with 401 before routing, so no service is called.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] downstream = new long[3];

            rec.live("seed", "Fixtures: a customer and a product", nodes("client", "user", "product", "inventory"), "client", "user",
                    "The credential in this lab is a STATE (valid, missing or expired). No real credential is read, stored or shown.", null, () -> {
                        long[] r = seed(ctx, bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        return List.of(ev("Credential model", "a lab state, not a real token"), ev("Gateway", NOTE));
                    });

            rec.live("valid", "A valid credential", nodes("client", "gateway", "product"), "client", "gateway",
                    "Authenticated, within limits, and routed.", CodeRefIds.MICRO_GATEWAY_ROUTE, () -> {
                        List<Evidence> e = through(ctx, "Response", HttpRequest.get("/products/" + ref[1], "browser").with(Credential.VALID));
                        downstream[0] = Long.parseLong(e.get(e.size() - 1).value());
                        return e;
                    });

            rec.live("missing", "No credential", nodes("client", "gateway"), "client", "gateway",
                    "The gateway answers 401 itself. The Product service is never called.", CodeRefIds.MICRO_GATEWAY_ROUTE, () -> {
                        List<Evidence> e = through(ctx, "Response", HttpRequest.get("/products/" + ref[1], "browser").with(Credential.MISSING));
                        downstream[1] = Long.parseLong(e.get(e.size() - 1).value());
                        return e;
                    });

            rec.live("expired", "An expired credential", nodes("client", "gateway"), "client", "gateway",
                    "Also 401, with a different reason. Again nothing behind the gateway is called.", CodeRefIds.MICRO_GATEWAY_ROUTE, () -> {
                        List<Evidence> e = through(ctx, "Response", HttpRequest.get("/products/" + ref[1], "browser").with(Credential.EXPIRED));
                        downstream[2] = Long.parseLong(e.get(e.size() - 1).value());
                        return e;
                    });

            rec.infer("services-protected", "Did the rejected requests reach a service?", nodes("gateway", "product"),
                    "Calculated from the calls recorded behind the gateway for each request.",
                    List.of(ev("Services called for the valid request", downstream[0]), ev("Services called for the missing credential", downstream[1]),
                            ev("Services called for the expired credential", downstream[2]),
                            ev("Rejected requests reached no service", downstream[1] == 0 && downstream[2] == 0)));

            rec.concept("defence-in-depth", "The gateway is not the only guard", nodes("gateway", "product"),
                    "Authenticating once at the edge is efficient, but services still need to authorise what each caller may do, and must not be reachable "
                            + "around the gateway. If the gateway is the only check, one misconfigured route exposes everything behind it.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class RateLimiting implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("gw-rate-limit", "gateway", "Rate limiting at the edge",
                    "How many requests from one client does the gateway accept in a window, and what happens to the rest?",
                    "A fixed-window limiter accepts up to the limit per client per window, answers 429 to the rest, resets in the next window, and does not "
                            + "affect other clients.",
                    "services", List.of(WhatIfOption.integer("limit", "Requests allowed per window", 3, 1, 10),
                            WhatIfOption.integer("requests", "Requests sent in the burst", 5, 1, 12)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            int limit = ctx.number("limit");
            int requests = ctx.number("requests");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            int[] counts = new int[2];

            rec.live("seed", "Fixtures, and the limit is set to " + limit + " per " + GatewayContract.WINDOW_MILLIS + " ms", nodes("client", "gateway", "user", "product", "inventory"),
                    "client", "gateway", "The window is measured on the SIMULATED clock.", CodeRefIds.MICRO_RATE_LIMITER, () -> {
                        long[] r = seed(ctx, bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        bus.startRequest();
                        Lab.value(bus.call(null, GatewayContract.CONFIGURE_RATE_LIMIT, new RateLimit(limit), Lab.SETUP));
                        return List.of(ev("Limit", limit + " requests per client per " + GatewayContract.WINDOW_MILLIS + " ms (simulated)"), ev("Gateway", NOTE));
                    });

            rec.live("burst", "A burst of " + requests + " requests from one client", nodes("client", "gateway", "product"), "client", "gateway",
                    "The requests are sent 20 simulated milliseconds apart, so they fall in the same window.", CodeRefIds.MICRO_RATE_LIMITER, () -> {
                        StringBuilder statuses = new StringBuilder();
                        for (int i = 0; i < requests; i++) {
                            bus.startRequest();
                            CallResult<HttpResponse> r = bus.call(null, GatewayContract.ROUTE, HttpRequest.get("/products/" + ref[1], "browser"), Lab.SETUP);
                            int status = r.value().status();
                            counts[status == 200 ? 0 : 1]++;
                            statuses.append(i == 0 ? "" : ", ").append(status);
                            bus.clock().advanceTo(bus.clock().now() + 20);
                        }
                        return List.of(ev("Statuses in order", statuses.toString()), ev("Accepted (200)", counts[0]), ev("Rejected (429)", counts[1]));
                    });

            rec.live("other-client", "A different client in the same window", nodes("client", "gateway", "product"), "client", "gateway",
                    "The counter is per client, so one noisy client does not use up another one's allowance.", CodeRefIds.MICRO_RATE_LIMITER,
                    () -> through(ctx, "Other client", HttpRequest.get("/products/" + ref[1], "another-browser")));

            rec.live("next-window", "After the window has passed", nodes("client", "gateway", "product"), "client", "gateway",
                    "The simulated clock moves past the end of the window, the counter resets, and the first client is accepted again.", CodeRefIds.MICRO_RATE_LIMITER, () -> {
                        bus.clock().advanceTo(bus.clock().now() + GatewayContract.WINDOW_MILLIS);
                        return through(ctx, "First client again", HttpRequest.get("/products/" + ref[1], "browser"));
                    });

            rec.infer("check", "Was the limit applied as configured?", nodes("gateway"),
                    "Calculated from the statuses above: at most the limit may be accepted from one client in one window.",
                    List.of(ev("Expected accepted", Math.min(limit, requests)), ev("Observed accepted", counts[0]),
                            ev("Limit applied correctly", counts[0] == Math.min(limit, requests) && counts[1] == requests - Math.min(limit, requests))));

            rec.concept("limiter-caveats", "What this limiter is and is not", nodes("gateway"),
                    "A fixed window is the simplest limiter: a burst at the boundary of two windows can briefly allow twice the limit. Production limiters "
                            + "use sliding windows or token buckets, usually backed by a shared store so that every gateway instance counts together.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Failure implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("gw-failure", "gateway", "When the gateway or a service behind it fails",
                    "How does the gateway report a failing service, and what if the gateway itself is the thing that is down?",
                    "The gateway turns a service failure into 502, 503 or 504. If the gateway itself is down, every service is unreachable for clients even though "
                            + "they are healthy: it is a single point of failure unless it is replicated.",
                    "services", List.of(WhatIfOption.choice("fault", "What fails", "service-down",
                            List.of("service-down", "service-slow", "service-error", "gateway-down"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            String fault = ctx.choice("fault");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];

            rec.live("seed", "Fixtures: a customer and a product", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        long[] r = seed(ctx, bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        return List.of(ev("Gateway", NOTE));
                    });

            rec.live("healthy", "Baseline: GET /products/{id} works", nodes("client", "gateway", "product", "product-db"), "client", "gateway",
                    "Everything is up, so the request is routed and answered.", CodeRefIds.MICRO_GATEWAY_ROUTE, () -> through(ctx, "Response", HttpRequest.get("/products/" + ref[1], "browser")));

            rec.live("faulted", "Fault: " + fault, nodes("client", "gateway", "product"), "client", "gateway",
                    "The gateway forwards the request and translates what comes back. Its own downstream timeout is 200 simulated ms.", CodeRefIds.MICRO_GATEWAY_ROUTE, () -> {
                        switch (fault) {
                            case "service-down" -> ctx.behaviours().set(ServiceName.PRODUCT, Behaviour.down());
                            case "service-slow" -> ctx.behaviours().set(ServiceName.PRODUCT, Behaviour.delayed(400));
                            case "service-error" -> ctx.behaviours().set(ServiceName.PRODUCT, Behaviour.error());
                            default -> ctx.behaviours().set(ServiceName.GATEWAY, Behaviour.down());
                        }
                        List<Evidence> out = new ArrayList<>(through(ctx, "Response", HttpRequest.get("/products/" + ref[1], "browser")));
                        if (fault.equals("gateway-down")) {
                            ctx.behaviours().reset(ServiceName.GATEWAY);
                            bus.startRequest();
                            CallResult<?> direct = bus.call(null, ProductContract.GET_PRODUCT, new GetProduct(ref[1]), Lab.SETUP);
                            out.add(ev("Product service, called directly by the lab (a real client cannot bypass the gateway)", Lab.outcome(direct)));
                        } else {
                            ctx.behaviours().reset(ServiceName.PRODUCT);
                        }
                        return out;
                    });

            rec.infer("translation", "How the gateway translates failures", nodes("gateway"),
                    "These are the mappings the gateway code applies. They are derived from its implementation, not measured.",
                    List.of(ev("Service refused (business)", "404 or 422"), ev("Service unavailable", "503"), ev("Service too slow", "504"),
                            ev("Service raised an unexpected error", "502"),
                            ev("This run", fault.equals("gateway-down") ? "the client got no gateway response at all"
                                    : "see the status in the previous step")));

            rec.concept("single-point", "The gateway as a single point of failure", nodes("gateway"),
                    "Every client request passes through the gateway, so its availability caps the availability of everything behind it. Real deployments "
                            + "run several gateway instances behind a load balancer and monitor them; none of that is simulated here.");
        }
    }
}
