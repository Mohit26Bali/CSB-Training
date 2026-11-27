package com.springlab.lab.micro.contract;

import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Handled;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceName;

/**
 * The public contract of the Inventory service: the ONLY way other code may use it. Callers depend on this class, never on
 * {@code micro.inventory}. Business refusals are the codes OUT_OF_STOCK, UNKNOWN_PRODUCT and INVALID_QUANTITY.
 */
public final class InventoryContract {

    private InventoryContract() {
    }

    /** Lab fixture, not part of a real service API: creates the stock row of a product. */
    public static final Operation<SeedStock, Ack> SEED_STOCK = new Operation<>(ServiceName.INVENTORY, "seedStock");
    /** Reserves stock for an order. Idempotent per orderRef: asking again returns the existing reservation. */
    public static final Operation<Reserve, Reservation> RESERVE = new Operation<>(ServiceName.INVENTORY, "reserve");

    /** The compensation of {@link #RESERVE}: a new business operation that gives the stock back. It is not a database rollback. Idempotent. */
    public static final Operation<Release, Released> RELEASE = new Operation<>(ServiceName.INVENTORY, "release");
    public static final Operation<Level, StockLevel> STOCK_LEVEL = new Operation<>(ServiceName.INVENTORY, "stockLevel");
    /** Inventory adds stock by itself (a delivery arrives). Nobody else is told. */
    public static final Operation<Restock, Ack> RESTOCK = new Operation<>(ServiceName.INVENTORY, "restock");
    /** Lab fixture: deletes a stock row. MySQL refuses while a reservation still points at it (a foreign key INSIDE this service). */
    public static final Operation<DeleteStock, Ack> DELETE_STOCK = new Operation<>(ServiceName.INVENTORY, "deleteStock");
    /** Choreography: reacts to OrderCreated by reserving, and publishes StockReserved or StockRejected. */
    public static final Operation<EventMessage, Handled> ON_ORDER_CREATED = new Operation<>(ServiceName.INVENTORY, "onOrderCreated");
    /** Choreography: reacts to PaymentFailed by releasing the reservation (the compensation) and publishes StockReleased. */
    public static final Operation<EventMessage, Handled> ON_PAYMENT_FAILED = new Operation<>(ServiceName.INVENTORY, "onPaymentFailed");

    public record Release(long orderRef) {
    }

    public record Released(boolean alreadyReleased, int available) {
    }

    public record Level(long productRef) {
    }

    public record StockLevel(int available) {
    }

    public record Restock(long productRef, int quantity) {
    }

    public record DeleteStock(long productRef) {
    }

    public record SeedStock(long productRef, int quantity) {
    }

    public record Reserve(long orderRef, long productRef, int quantity) {
    }

    public record Reservation(long reservationId, int remaining, boolean alreadyReserved) {
    }

    public record Ack(boolean done) {
    }
}
