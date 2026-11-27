package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.InventoryContract.SeedStock;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.SeedProduct;
import com.springlab.lab.micro.contract.UserContract;
import com.springlab.lab.micro.contract.UserContract.SeedUser;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.run.RunContext;
import com.springlab.lab.trace.LifecycleTrace.Evidence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.springlab.lab.micro.run.MicroRecorder.ev;

/** Shared helpers of the experiments: fixtures created THROUGH the services, and read-only evidence from the lab inspector. */
final class Lab {

    /** Setup calls use a generous timeout so a What-If on the caller timeout never breaks the fixture. */
    static final CallOptions SETUP = CallOptions.timeout(2000);
    static final String INSPECTOR_NOTE = "(read by the lab inspector, not a service call)";

    private static final String STOCK = "select available from micro_lab_stock where run_key = ? and product_ref = ?";
    private static final String ORDERS_OF_PRODUCT = "select count(*) from micro_lab_orders where run_key = ? and product_ref = ? and status = ?";
    private static final String ORDERS_OF_STATUS = "select count(*) from micro_lab_orders where run_key = ? and status = ?";
    private static final String RESERVATIONS = "select count(*) from micro_lab_reservations where run_key = ?";
    private static final String NOTIFICATIONS = "select count(*) from micro_lab_notifications where run_key = ?";
    private static final String PROCESSED = "select count(*) from micro_lab_processed_events where run_key = ?";

    private Lab() {
    }

    static <T> T value(CallResult<T> result) {
        if (!result.isSuccess()) {
            throw new IllegalStateException("A fixture call failed: " + result.outcome());
        }
        return result.value();
    }

    static long seedUser(ServiceBus bus, String creditLimit) {
        return value(bus.call(null, UserContract.SEED_USER, new SeedUser("Ada", new BigDecimal(creditLimit)), SETUP)).userRef();
    }

    static long seedProduct(ServiceBus bus, String sku, String price, int stock) {
        long ref = value(bus.call(null, ProductContract.SEED_PRODUCT, new SeedProduct(sku, "Notebook " + sku, new BigDecimal(price)), SETUP)).productRef();
        value(bus.call(null, InventoryContract.SEED_STOCK, new SeedStock(ref, stock), SETUP));
        return ref;
    }

    static String outcome(CallResult<?> r) {
        return r.outcome() + (r.code() == null ? "" : " (" + r.code() + ")");
    }

    // ---- read-only evidence (the lab inspector)

    static long stock(RunContext ctx, long productRef) {
        return ctx.inspectLong(STOCK, productRef);
    }

    static long orders(RunContext ctx, String status) {
        return ctx.inspectLong(ORDERS_OF_STATUS, status);
    }

    static long ordersOf(RunContext ctx, long productRef, String status) {
        return ctx.inspectLong(ORDERS_OF_PRODUCT, productRef, status);
    }

    static long reservations(RunContext ctx) {
        return ctx.inspectLong(RESERVATIONS);
    }

    static long notifications(RunContext ctx) {
        return ctx.inspectLong(NOTIFICATIONS);
    }

    static long processed(RunContext ctx) {
        return ctx.inspectLong(PROCESSED);
    }

    static Evidence stockEv(RunContext ctx, long productRef) {
        return ev("Stock " + INSPECTOR_NOTE, stock(ctx, productRef));
    }

    static Evidence ordersEv(RunContext ctx, String status) {
        return ev("Orders " + status + " " + INSPECTOR_NOTE, orders(ctx, status));
    }

    static Evidence notificationsEv(RunContext ctx) {
        return ev("Notifications sent " + INSPECTOR_NOTE, notifications(ctx));
    }

    /** The calls the lab recorded since {@code mark}, excluding the first (the call the client itself made). */
    static long downstreamCalls(RunContext ctx, int mark) {
        List<CallRecord> since = ctx.traces().callsSince(mark);
        return Math.max(0, since.size() - 1);
    }

    static long outbox(RunContext ctx, String status) {
        return ctx.inspectLong("select count(*) from micro_lab_outbox where run_key = ? and status = ?", status);
    }

    static long payments(RunContext ctx, String status) {
        return ctx.inspectLong("select count(*) from micro_lab_payments where run_key = ? and status = ?", status);
    }

    /** Reservation rows (any status) for one product, joining Inventory's own two tables. */
    static long reservationsOf(RunContext ctx, long productRef) {
        return ctx.inspectLong("select count(*) from micro_lab_reservations r join micro_lab_stock s on s.id = r.stock_id where r.run_key = ? and s.product_ref = ?", productRef);
    }

    static long reservationsWith(RunContext ctx, String status) {
        return ctx.inspectLong("select count(*) from micro_lab_reservations where run_key = ? and status = ?", status);
    }

    static String orderStatus(RunContext ctx, long orderRef) {
        return ctx.inspectString("select status from micro_lab_orders where run_key = ? and id = ?", orderRef);
    }

    /** The spans recorded since {@code mark} (a call count taken before the request). */
    static List<com.springlab.lab.micro.core.Span> spansSince(RunContext ctx, int mark) {
        List<com.springlab.lab.micro.core.Span> all = ctx.traces().spans();
        return all.subList(Math.min(mark, all.size()), all.size());
    }

    /** The spans as an indented tree (children under their parent, in the order they started). Each line also shows self time. */
    static List<Evidence> spanTree(List<com.springlab.lab.micro.core.Span> spans) {
        java.util.Map<String, List<com.springlab.lab.micro.core.Span>> children = new java.util.LinkedHashMap<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        spans.forEach(sp -> ids.add(sp.spanId()));
        List<com.springlab.lab.micro.core.Span> roots = new ArrayList<>();
        for (com.springlab.lab.micro.core.Span sp : spans) {
            if (sp.parentSpanId() == null || !ids.contains(sp.parentSpanId())) {
                roots.add(sp);
            } else {
                children.computeIfAbsent(sp.parentSpanId(), k -> new ArrayList<>()).add(sp);
            }
        }
        java.util.Comparator<com.springlab.lab.micro.core.Span> byStart = java.util.Comparator
                .comparingLong(com.springlab.lab.micro.core.Span::startMillis).thenComparing(sp -> Integer.parseInt(sp.spanId().substring(5)));
        List<Evidence> out = new ArrayList<>();
        roots.sort(byStart);
        for (com.springlab.lab.micro.core.Span root : roots) {
            walk(root, children, byStart, 0, out);
        }
        return out;
    }

    private static void walk(com.springlab.lab.micro.core.Span span, java.util.Map<String, List<com.springlab.lab.micro.core.Span>> children,
                             java.util.Comparator<com.springlab.lab.micro.core.Span> byStart, int depth, List<Evidence> out) {
        List<com.springlab.lab.micro.core.Span> kids = new ArrayList<>(children.getOrDefault(span.spanId(), List.of()));
        kids.sort(byStart);
        long childTotal = kids.stream().mapToLong(com.springlab.lab.micro.core.Span::simulatedMillis).sum();
        out.add(ev("Span " + span.spanId(), "  ".repeat(depth) + span.service() + "." + span.operation() + " " + span.status() + ", " + span.simulatedMillis()
                + " ms simulated, self " + Math.max(0, span.simulatedMillis() - childTotal) + " ms" + (span.parentSpanId() == null ? "" : ", child of " + span.parentSpanId())
                + ", " + span.correlationId()));
        for (com.springlab.lab.micro.core.Span kid : kids) {
            walk(kid, children, byStart, depth + 1, out);
        }
    }

    /** What a caller may conclude from an outcome (the same wording everywhere in the lab). */
    static String meaning(Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> "the work was done";
            case BUSINESS_FAILURE -> "the service refused; nothing changed; retrying gives the same answer";
            case UNAVAILABLE -> "the request was never processed; a retry is safe once the service is back";
            case TIMEOUT -> "unknown: the work may or may not have been done; retry only if the operation is idempotent";
            case TECHNICAL_ERROR -> "an unexpected failure, not a business decision; whether anything changed depends on the service";
            case BULKHEAD_REJECTED -> "refused locally; the service was never reached";
            case CIRCUIT_OPEN -> "refused locally by the open circuit breaker; the service was never reached";
        };
    }
}
