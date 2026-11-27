package com.springlab.lab.micro.contract;

import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Handled;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceName;

import java.math.BigDecimal;
import java.util.List;

/**
 * The public contract of the Order service. {@code placeOrder} works with other services only through their contracts:
 * User, Product and Inventory (synchronously) and Notification (synchronously or through the simulated event channel).
 *
 * <p>Business refusal codes: USER_NOT_FOUND, PRODUCT_NOT_FOUND, CREDIT_LIMIT_EXCEEDED, OUT_OF_STOCK, and
 * {@code <SERVICE>_<OUTCOME>} (for example INVENTORY_TIMEOUT) when a dependency could not be used.
 *
 * <p>The saga operations place an order by coordinating Inventory and Payment, either from one place (orchestration) or by services
 * reacting to each other's events (choreography). The outbox operations show how an order and its event are written together.
 */
public final class OrderContract {

    private OrderContract() {
    }

    public static final Operation<PlaceOrder, OrderView> PLACE_ORDER = new Operation<>(ServiceName.ORDER, "placeOrder");

    /** Orchestration: the Order service drives reserve, charge and confirm, and compensates in reverse order when a step fails. */
    public static final Operation<SagaRequest, SagaView> PLACE_ORDER_SAGA = new Operation<>(ServiceName.ORDER, "placeOrderSaga");
    /** Retries the compensations of a saga that ended COMPENSATION_FAILED (for example once the failed service is back). */
    public static final Operation<Resume, SagaView> RESUME_COMPENSATION = new Operation<>(ServiceName.ORDER, "resumeCompensation");
    /** Choreography: creates the order and publishes OrderCreated. Nothing coordinates the rest; services react to events. */
    public static final Operation<SagaRequest, OrderView> START_CHOREOGRAPHY = new Operation<>(ServiceName.ORDER, "startChoreography");
    public static final Operation<EventMessage, Handled> ON_PAYMENT_COMPLETED = new Operation<>(ServiceName.ORDER, "onPaymentCompleted");
    public static final Operation<EventMessage, Handled> ON_STOCK_REJECTED = new Operation<>(ServiceName.ORDER, "onStockRejected");
    public static final Operation<EventMessage, Handled> ON_STOCK_RELEASED = new Operation<>(ServiceName.ORDER, "onStockReleased");

    /** Transactional outbox: the order row and the outbox row are written in ONE local transaction. */
    public static final Operation<OutboxOrder, OrderView> PLACE_WITH_OUTBOX = new Operation<>(ServiceName.ORDER, "placeWithOutbox");
    /** The naive alternative: commit the order, THEN publish the event as a separate action (a dual write). */
    public static final Operation<OutboxOrder, OrderView> PLACE_DUAL_WRITE = new Operation<>(ServiceName.ORDER, "placeDualWrite");
    /** The outbox relay: publishes committed outbox rows to the channel, then marks them PUBLISHED, as separate steps. */
    public static final Operation<Relay, Relayed> RELAY_OUTBOX = new Operation<>(ServiceName.ORDER, "relayOutbox");

    /** How the customer is told about the order: not at all, by a synchronous call, or by an event (consumed later). */
    public enum NotifyMode { NONE, SYNC, ASYNC }

    /** Lab fault injection: fail inside the Order service AFTER the Inventory reservation succeeded. */
    public enum Injection { NONE, AFTER_RESERVE }

    /** Lab fault injection for the saga: the Order service fails to CONFIRM the order after the payment was taken. */
    public enum SagaFault { NONE, CONFIRM_FAILS }

    public record PlaceOrder(long userRef, long productRef, int quantity, NotifyMode notifyMode, Injection injection, int callTimeoutMillis,
                             int inventoryAttempts) {
        public static PlaceOrder simple(long userRef, long productRef, int quantity) {
            return new PlaceOrder(userRef, productRef, quantity, NotifyMode.NONE, Injection.NONE, 200, 1);
        }

        public PlaceOrder withNotify(NotifyMode mode) {
            return new PlaceOrder(userRef, productRef, quantity, mode, injection, callTimeoutMillis, inventoryAttempts);
        }

        public PlaceOrder withInjection(Injection i) {
            return new PlaceOrder(userRef, productRef, quantity, notifyMode, i, callTimeoutMillis, inventoryAttempts);
        }

        public PlaceOrder withTimeout(int millis) {
            return new PlaceOrder(userRef, productRef, quantity, notifyMode, injection, millis, inventoryAttempts);
        }

        /** The Order service retries the Inventory call up to this many times (with backoff) when it is unavailable or fails. */
        public PlaceOrder withInventoryAttempts(int attempts) {
            return new PlaceOrder(userRef, productRef, quantity, notifyMode, injection, callTimeoutMillis, attempts);
        }
    }

    public record OrderView(long orderRef, String status, BigDecimal total) {
    }

    public record SagaRequest(long userRef, long productRef, int quantity, SagaFault fault) {
    }

    public record Resume(long orderRef) {
    }

    /** One row of the saga log kept by the Order service. */
    public record SagaStepView(int seq, String step, String state, String detail) {
    }

    public record SagaView(long orderRef, String status, List<SagaStepView> steps) {
    }

    /** {@code failAfterWrite}: the Order service fails right after its write (outbox: before commit; dual write: before publishing). */
    public record OutboxOrder(long userRef, long productRef, int quantity, boolean failAfterWrite) {
    }

    /** {@code crashAfterPublish}: the relay stops after publishing the first row and before marking it PUBLISHED. */
    public record Relay(boolean crashAfterPublish) {
    }

    public record Relayed(int published, int stillPending) {
    }
}
