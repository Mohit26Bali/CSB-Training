package com.springlab.lab.micro.contract;

import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceName;

/** The public contract of the Notification service: a direct call, and two ways of consuming the OrderPlaced event. */
public final class NotificationContract {

    private NotificationContract() {
    }

    /** Synchronous: send the confirmation now. */
    public static final Operation<Send, Ack> SEND = new Operation<>(ServiceName.NOTIFICATION, "send");
    /** Event consumer that remembers the message ids it has processed, so a duplicate delivery changes nothing. */
    public static final Operation<EventMessage, Delivery> ON_ORDER_PLACED = new Operation<>(ServiceName.NOTIFICATION, "onOrderPlaced");
    /** Event consumer WITHOUT deduplication: what a naive consumer does. A duplicate delivery sends a second notification. */
    public static final Operation<EventMessage, Delivery> ON_ORDER_PLACED_NAIVE = new Operation<>(ServiceName.NOTIFICATION, "onOrderPlacedNaive");

    public record Send(long orderRef) {
    }

    public record Ack(boolean done) {
    }

    /** {@code processed}: this delivery created a notification. {@code duplicate}: the message had been processed before and was ignored. */
    public record Delivery(boolean processed, boolean duplicate) {
    }
}
