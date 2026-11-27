package com.springlab.lab.micro.notification;

import com.springlab.lab.micro.contract.NotificationContract;
import com.springlab.lab.micro.contract.NotificationContract.Ack;
import com.springlab.lab.micro.contract.NotificationContract.Delivery;
import com.springlab.lab.micro.contract.NotificationContract.Send;
import com.springlab.lab.micro.core.CallContext;
import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.stereotype.Service;

/**
 * The Notification service. It confirms orders, either when called directly or when it consumes the OrderPlaced event. Event
 * channels can deliver a message more than once (at-least-once), so the consumer comes in two versions: one that remembers the
 * message ids it has handled and one that does not.
 */
@Service
public class NotificationService implements ServiceModule {

    static final String CONSUMER = "notification";

    private final NotificationRepository repository;
    private final MicroDb db;

    NotificationService(NotificationRepository repository, MicroDb db) {
        this.repository = repository;
        this.db = db;
    }

    @Override
    public ServiceName service() {
        return ServiceName.NOTIFICATION;
    }

    @Override
    public void register(HandlerRegistry.Registrar registrar) {
        registrar.on(NotificationContract.SEND, this::send);
        registrar.on(NotificationContract.ON_ORDER_PLACED, this::onOrderPlaced);
        registrar.on(NotificationContract.ON_ORDER_PLACED_NAIVE, this::onOrderPlacedNaive);
    }

    ServiceResult<Ack> send(CallContext context, Send request) {
        db.local(ServiceName.NOTIFICATION, () -> {
            repository.insertNotification(request.orderRef(), "ORDER_CONFIRMATION");
            return null;
        });
        return ServiceResult.ok(new Ack(true));
    }

    /** Idempotent consumer: the message id and the notification are recorded in ONE local transaction, so a duplicate changes nothing. */
    ServiceResult<Delivery> onOrderPlaced(CallContext context, EventMessage message) {
        return ServiceResult.ok(db.local(ServiceName.NOTIFICATION, () -> {
            if (repository.alreadyProcessed(CONSUMER, message.messageId())) {
                return new Delivery(false, true);
            }
            repository.markProcessed(CONSUMER, message.messageId());
            repository.insertNotification(message.orderRef(), "ORDER_CONFIRMATION");
            return new Delivery(true, false);
        }));
    }

    /** Naive consumer: no deduplication. */
    ServiceResult<Delivery> onOrderPlacedNaive(CallContext context, EventMessage message) {
        db.local(ServiceName.NOTIFICATION, () -> {
            repository.insertNotification(message.orderRef(), "ORDER_CONFIRMATION");
            return null;
        });
        return ServiceResult.ok(new Delivery(true, false));
    }
}
