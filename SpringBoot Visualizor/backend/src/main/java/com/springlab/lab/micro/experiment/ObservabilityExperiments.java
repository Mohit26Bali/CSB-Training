package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.NotificationContract;
import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.NotifyMode;
import com.springlab.lab.micro.contract.OrderContract.OrderView;
import com.springlab.lab.micro.contract.OrderContract.PlaceOrder;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.EventChannel;
import com.springlab.lab.micro.core.ServiceBehaviours.Behaviour;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.Span;
import com.springlab.lab.micro.run.ExperimentMeta;
import com.springlab.lab.micro.run.MicroExperiment;
import com.springlab.lab.micro.run.MicroRecorder;
import com.springlab.lab.micro.run.RunContext;
import com.springlab.lab.micro.run.WhatIfOption;
import com.springlab.lab.trace.CodeRefIds;
import com.springlab.lab.trace.LifecycleTrace.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/**
 * Topic 8: observability. The spans, correlation ids and parent/child links are an in-process SIMULATION of distributed tracing concepts.
 * It is not OpenTelemetry and nothing travels over a network. Durations in the evidence are SIMULATED (a logical clock); every span also
 * carries the real elapsed time of the in-process call (measuredMicros), which differs on each run and is therefore only in the span data.
 */
public final class ObservabilityExperiments {

    static final String SIMULATED_TRACING = "an in-process SIMULATION of tracing (not OpenTelemetry); durations are SIMULATED, measured microseconds are in the span data";

    private ObservabilityExperiments() {
    }

    private static long[] seed(ServiceBus bus) {
        bus.startRequest();
        return new long[] {Lab.seedUser(bus, "100.00"), Lab.seedProduct(bus, "SKU-1", "4.50", 20)};
    }

    private static Span rootOf(List<Span> spans) {
        return spans.stream().filter(s -> s.service().equals("order")).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class TraceAnOrder implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("obs-trace-order", "observability", "Trace one order through four services",
                    "Which services did one order request touch, in what order, and where did the time go?",
                    "Every call is a span with a service, an operation, a status and a duration, linked to its parent by an id, all under one correlation id. Self time "
                            + "(a span's time minus its children) shows which service is responsible for the latency.",
                    "services", List.of(WhatIfOption.choice("slowService", "A service that is slow", "product", List.of("none", "user", "product", "inventory")),
                            WhatIfOption.integer("delayMillis", "How slow (extra simulated ms)", 80, 0, 150)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            String slow = ctx.choice("slowService");
            int delay = ctx.number("delayMillis");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            int[] mark = new int[1];

            rec.live("seed", "Fixtures: a customer and a product", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them. Tracing here is " + SIMULATED_TRACING + ".", null, () -> {
                        long[] r = seed(bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        return List.of(ev("Tracing", SIMULATED_TRACING));
                    });

            rec.live("place-order", "One order request", nodes("client", "order", "user", "product", "inventory"), "client", "order",
                    "The client sends one request. The Order service calls three services; every call becomes a span.", CodeRefIds.MICRO_CORRELATION, () -> {
                        if (!slow.equals("none") && delay > 0) {
                            ctx.behaviours().set(ServiceName.valueOf(slow.toUpperCase()), Behaviour.delayed(delay));
                        }
                        mark[0] = ctx.traces().callCount();
                        bus.startRequest();
                        CallResult<OrderView> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Correlation id of this request", Lab.spansSince(ctx, mark[0]).get(0).correlationId()));
                    });

            List<Span> spans = Lab.spansSince(ctx, mark[0]);
            List<Evidence> tree = new ArrayList<>(Lab.spanTree(spans));
            Set<String> ids = new HashSet<>();
            spans.forEach(s -> ids.add(s.spanId()));
            boolean linksValid = spans.stream().allMatch(s -> s.parentSpanId() == null || ids.contains(s.parentSpanId()));
            tree.add(ev("Spans in the trace", spans.size()));
            tree.add(ev("Distinct correlation ids", spans.stream().map(Span::correlationId).distinct().count()));
            tree.add(ev("Every parent link points at a span of this trace", linksValid));
            tree.add(ev("Timing basis", "simulated milliseconds from the logical clock; the real elapsed time of each call is in the span's measuredMicros"));
            rec.infer("span-tree", "The trace as a tree", nodes("client", "order", "user", "product", "inventory"),
                    "Rebuilt from the span ids and parent ids recorded during the request. Indentation shows who called whom.", tree);

            Map<String, Long> self = new LinkedHashMap<>();
            for (Span s : spans) {
                long children = spans.stream().filter(c -> s.spanId().equals(c.parentSpanId())).mapToLong(Span::simulatedMillis).sum();
                self.merge(s.service(), Math.max(0, s.simulatedMillis() - children), Long::sum);
            }
            long total = rootOf(spans).simulatedMillis();
            List<Evidence> attribution = new ArrayList<>();
            self.forEach((service, millis) -> attribution.add(ev("Self time of " + service, millis + " ms of " + total + " ms (" + (total == 0 ? 0 : millis * 100 / total) + "%), simulated")));
            String largest = self.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
            attribution.add(ev("Service responsible for most of the time", largest));
            rec.infer("latency-attribution", "Where did the time go?", nodes("order", "user", "product", "inventory"),
                    "Self time is a span's duration minus the time of its children. It attributes latency to the service that spent it, not to the one that waited.", attribution);

            rec.concept("not-opentelemetry", "What this is and is not", nodes("client", "order"),
                    "Real distributed tracing (for example with OpenTelemetry) generates ids in each process, sends them in request headers across the network, and collects "
                            + "spans from many processes. Here everything runs in one process, so the 'propagation' is a field passed between method calls. The concepts (span, "
                            + "parent, correlation id, self time) are the same; the network and the collector are not simulated.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class FailurePropagation implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("obs-failure-propagation", "observability", "A downstream failure, seen in the trace",
                    "One dependency fails. How does the failure look at each level, and how do you find where it started?",
                    "The failure changes form as it travels up: the failing service reports a technical status, the Order service turns it into a business refusal "
                            + "naming the dependency, and the user sees one error. The trace lets you walk back to the span where it started.",
                    "services", List.of(WhatIfOption.choice("failingService", "The service that fails", "inventory", List.of("user", "product", "inventory")),
                            WhatIfOption.choice("fault", "How it fails", "error", List.of("error", "down", "slow"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceName failing = ServiceName.valueOf(ctx.choice("failingService").toUpperCase());
            String fault = ctx.choice("fault");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            int[] mark = new int[1];

            rec.live("seed", "Fixtures: a customer and a product", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        long[] r = seed(bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        return List.of(ev("Tracing", SIMULATED_TRACING));
                    });

            rec.live("failing-request", "The " + failing.id() + " service fails during an order", nodes("client", "order", failing.id()), "client", "order",
                    "The same request as before, with one dependency failing. The Order service waits at most 100 simulated ms for each dependency.", CodeRefIds.MICRO_CORRELATION, () -> {
                        ctx.behaviours().set(failing, switch (fault) {
                            case "down" -> Behaviour.down();
                            case "slow" -> Behaviour.delayed(300);
                            default -> Behaviour.error();
                        });
                        mark[0] = ctx.traces().callCount();
                        bus.startRequest();
                        CallResult<OrderView> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withTimeout(100), Lab.SETUP);
                        ctx.behaviours().reset(failing);
                        return List.of(ev("User sees", Lab.outcome(r)));
                    });

            List<Span> spans = Lab.spansSince(ctx, mark[0]);
            List<CallRecord> calls = ctx.traces().callsSince(mark[0]);
            List<Evidence> chain = new ArrayList<>(Lab.spanTree(spans));
            Span root = rootOf(spans);
            Span cause = spans.stream().filter(s -> !s.status().equals("SUCCESS") && !s.service().equals("order")).findFirst().orElse(null);
            CallRecord orderCall = calls.stream().filter(c -> c.to().equals("order")).findFirst().orElseThrow();
            chain.add(ev("Span where the failure started", cause == null ? "none" : cause.service() + "." + cause.operation() + " " + cause.status()));
            chain.add(ev("What the Order service reported upward", orderCall.outcome() + (orderCall.code() == null ? "" : " (" + orderCall.code() + ")")));
            chain.add(ev("Spans that report a failure", spans.stream().filter(s -> !s.status().equals("SUCCESS")).count() + " of " + spans.size()));
            chain.add(ev("Failure found by following parent links from the root", cause != null && root.spanId().equals(cause.parentSpanId())));
            rec.infer("find-the-cause", "Walking back to where it started", nodes("order", failing.id()),
                    "Calculated from the span statuses and parent ids. The root (the Order span) shows the symptom; the child span with the failure status shows the cause.", chain);

            rec.concept("traces-vs-logs", "Traces answer 'where', logs answer 'why'", nodes("order", failing.id()),
                    "A trace shows which call failed and how the failure travelled. It usually does not say why: that is in the failing service's logs, found by the same "
                            + "correlation id. Never put credentials, tokens or request bodies into spans or logs; the lab records identifiers, operation names and statuses only.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class MissingCorrelation implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("obs-missing-correlation", "observability", "What is lost when correlation is not passed on",
                    "What happens to a trace when one service forgets to pass the correlation id to the calls it makes?",
                    "The chain breaks: each downstream span starts a new, unrelated correlation id and has no link to its parent, so the request cannot be reassembled from the data.",
                    "services", List.of(WhatIfOption.bool("propagate", "Correlation is passed on", false)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            boolean propagate = ctx.flag("propagate");
            ctx.propagateCorrelation(propagate);
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            int[] mark = new int[1];

            rec.live("seed", "Fixtures: a customer and a product", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Correlation propagation is " + (propagate ? "ON" : "OFF") + " for this run.", null, () -> {
                        long[] r = seed(bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        return List.of(ev("Correlation passed to downstream calls", propagate), ev("Tracing", SIMULATED_TRACING));
                    });

            rec.live("place-order", "One order request", nodes("client", "order", "user", "product", "inventory"), "client", "order",
                    "The Order service makes three calls. Whether they can be tied back to the request depends on the id being passed.", CodeRefIds.MICRO_CORRELATION, () -> {
                        mark[0] = ctx.traces().callCount();
                        bus.startRequest();
                        CallResult<OrderView> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)));
                    });

            List<Span> spans = Lab.spansSince(ctx, mark[0]);
            List<Evidence> evidence = new ArrayList<>(Lab.spanTree(spans));
            long orphans = spans.stream().filter(s -> s.parentSpanId() == null).count() - 1;
            long correlations = spans.stream().map(Span::correlationId).distinct().count();
            evidence.add(ev("Spans with no parent besides the request itself", orphans));
            evidence.add(ev("Distinct correlation ids for ONE request", correlations));
            evidence.add(ev("The request can be reassembled from the recorded data", orphans == 0 && correlations == 1));
            rec.infer("what-is-lost", "Can the request be reassembled?", nodes("order", "user", "product", "inventory"),
                    "Calculated from the recorded spans: orphan spans and extra correlation ids are what you see in a real system when a hop drops the trace headers.", evidence);

            rec.concept("propagation", "Why propagation is a convention everyone must follow", nodes("order"),
                    "Tracing only works if every service passes the trace context on, including through message queues and background jobs. One service that forgets "
                            + "leaves a hole that no collector can repair afterwards, which is why frameworks and gateways add the headers automatically.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class AsyncCorrelation implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("obs-async-correlation", "observability", "Correlation across an event",
                    "Can the consumer's work be tied back to the request that caused it, when the two are separated by an event?",
                    "Only if the correlation id travels inside the event. Without it the consumer's span starts a new, unrelated trace.",
                    "services", List.of(WhatIfOption.bool("propagateInEvent", "The event carries the correlation id", true)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            boolean propagate = ctx.flag("propagateInEvent");
            ServiceBus bus = ctx.bus();
            ctx.channel().propagateCorrelation(propagate);
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            String[] correlation = new String[2];

            rec.live("seed", "Fixtures, and the Notification consumer subscribes", nodes("client", "user", "product", "inventory", "broker", "notification"), "client", "user",
                    "The channel is SIMULATED (not Kafka or RabbitMQ). The correlation id travels in the event: " + propagate + ".", null, () -> {
                        long[] r = seed(bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        ctx.channel().subscribe("orders", "notification", NotificationContract.ON_ORDER_PLACED);
                        return List.of(ev("Event mechanism", CommunicationExperiments.MECHANISM), ev("Correlation id carried in the event", propagate));
                    });

            rec.live("publish", "The order request publishes an event", nodes("client", "order", "broker"), "order", "broker",
                    "The Order service publishes OrderPlaced after the commit. The event is stamped with the correlation id of the request that is running.", CodeRefIds.MICRO_CORRELATION, () -> {
                        int mark = ctx.traces().callCount();
                        bus.startRequest();
                        CallResult<OrderView> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withNotify(NotifyMode.ASYNC), Lab.SETUP);
                        correlation[0] = rootOf(Lab.spansSince(ctx, mark)).correlationId();
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Correlation id of the order request", correlation[0]));
                    });

            rec.live("consume", "Later, the consumer handles the event in a different request", nodes("broker", "notification", "notification-db"), "broker", "notification",
                    "Delivery is a separate request, possibly much later. Whether its span joins the original trace depends on the id in the event.", CodeRefIds.MICRO_CORRELATION, () -> {
                        int mark = ctx.traces().callCount();
                        bus.startRequest();
                        List<EventChannel.Attempt> attempts = ctx.channel().deliverPending("notification", CallOptions.timeout(200));
                        Span consumer = Lab.spansSince(ctx, mark).stream().filter(s -> s.service().equals("notification")).findFirst().orElseThrow();
                        correlation[1] = consumer.correlationId();
                        return List.of(ev("Delivery", attempts.get(0).outcome()), ev("Correlation id of the consumer span", correlation[1]));
                    });

            rec.infer("same-trace", "Are the two spans in the same trace?", nodes("order", "notification"),
                    "Calculated from the two correlation ids. The consumer span has no parent span (the sender did not wait for it): only the correlation id can link them.",
                    List.of(ev("Order request correlation id", correlation[0]), ev("Consumer correlation id", correlation[1]),
                            ev("The consumer work can be tied to the request", correlation[0].equals(correlation[1])),
                            ev("Parent span of the consumer span", "none: an event decouples the two, so the link is the correlation id only")));

            rec.concept("span-links", "How real systems link asynchronous work", nodes("broker"),
                    "Real tracing systems put the trace context in message headers and record a span link from the consumer to the producer span. This lab shows the "
                            + "correlation id only, and it is passed in the SIMULATED channel, not through a real broker.");
        }
    }
}
