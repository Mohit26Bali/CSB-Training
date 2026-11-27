package com.springlab.lab.micro.contract;

import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Handled;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceName;

import java.math.BigDecimal;

/**
 * The public contract of the Payment service. It carries an amount and an opaque order reference only: no card data, name or
 * credential exists anywhere in the lab. Business refusal codes: DECLINED, NOT_CHARGED.
 */
public final class PaymentContract {

    private PaymentContract() {
    }

    /** Lab fixture: makes the Payment service decline every charge (for example the customer card is refused). */
    public static final Operation<Policy, Ack> SET_POLICY = new Operation<>(ServiceName.PAYMENT, "setPolicy");
    /**
     * Charges an order. With an idempotency key the call is idempotent: repeating it with the same key returns the original payment
     * ({@code replayed = true}) instead of charging again. Without a key every call charges.
     */
    public static final Operation<Charge, PaymentView> CHARGE = new Operation<>(ServiceName.PAYMENT, "charge");
    /** The compensation of {@link #CHARGE}: a new business operation that reverses it. It is not a database rollback. */
    public static final Operation<Refund, PaymentView> REFUND = new Operation<>(ServiceName.PAYMENT, "refund");
    /** Choreography: reacts to StockReserved by charging, and publishes PaymentCompleted or PaymentFailed. */
    public static final Operation<EventMessage, Handled> ON_STOCK_RESERVED = new Operation<>(ServiceName.PAYMENT, "onStockReserved");

    public record Policy(boolean declineAll) {
    }

    public record Charge(long orderRef, BigDecimal amount, String idempotencyKey) {
    }

    public record Refund(long orderRef) {
    }

    public record PaymentView(long paymentId, String status, boolean replayed) {
    }

    public record Ack(boolean done) {
    }
}
