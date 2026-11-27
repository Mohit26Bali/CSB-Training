package com.springlab.lab.micro.order;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.InventoryContract.Reservation;
import com.springlab.lab.micro.contract.InventoryContract.Reserve;
import com.springlab.lab.micro.contract.NotificationContract;
import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.Injection;
import com.springlab.lab.micro.contract.OrderContract.NotifyMode;
import com.springlab.lab.micro.contract.OrderContract.OrderView;
import com.springlab.lab.micro.contract.OrderContract.OutboxOrder;
import com.springlab.lab.micro.contract.OrderContract.PlaceOrder;
import com.springlab.lab.micro.contract.OrderContract.Relay;
import com.springlab.lab.micro.contract.OrderContract.Relayed;
import com.springlab.lab.micro.contract.OrderContract.Resume;
import com.springlab.lab.micro.contract.OrderContract.SagaRequest;
import com.springlab.lab.micro.contract.OrderContract.SagaView;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.GetProduct;
import com.springlab.lab.micro.contract.ProductContract.ProductView;
import com.springlab.lab.micro.contract.UserContract;
import com.springlab.lab.micro.contract.UserContract.GetUser;
import com.springlab.lab.micro.contract.UserContract.UserView;
import com.springlab.lab.micro.core.CallContext;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Handled;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ResilientCaller;
import com.springlab.lab.micro.core.RetryPolicy;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.order.OrderRepository.OutboxRow;
import com.springlab.lab.micro.run.MicroDb;
import com.springlab.lab.micro.run.MicroEvents;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * The Order service. Placing an order takes several SYNCHRONOUS calls to other services (User, Product, Inventory) through the
 * service bus, and its own short local transactions for the order row. There is deliberately NO transaction spanning the services:
 * if this method fails after Inventory has reserved stock, the reservation stays (the problem the saga pattern addresses).
 *
 * <p>Notification is either called synchronously, or announced with an event on the SIMULATED channel AFTER the order is
 * committed. That plain publication is not a transactional outbox; {@link #placeWithOutbox} and {@link #relayOutbox} are.
 *
 * <p>The saga operations (orchestrated in {@link SagaOrchestrator}, choreographed through the event handlers here) place an order
 * across Inventory and Payment and compensate when a step fails.
 */
@Service
public class OrderService implements ServiceModule {

    private final OrderRepository repository;
    private final MicroDb db;
    private final MicroEvents events;
    private final SagaOrchestrator orchestrator;

    OrderService(OrderRepository repository, MicroDb db, MicroEvents events, SagaOrchestrator orchestrator) {
        this.repository = repository;
        this.db = db;
        this.events = events;
        this.orchestrator = orchestrator;
    }

    @Override
    public ServiceName service() {
        return ServiceName.ORDER;
    }

    @Override
    public void register(HandlerRegistry.Registrar registrar) {
        registrar.on(OrderContract.PLACE_ORDER, this::placeOrder);
        registrar.on(OrderContract.PLACE_ORDER_SAGA, this::placeOrderSaga);
        registrar.on(OrderContract.RESUME_COMPENSATION, this::resumeCompensation);
        registrar.on(OrderContract.START_CHOREOGRAPHY, this::startChoreography);
        registrar.on(OrderContract.ON_PAYMENT_COMPLETED, this::onPaymentCompleted);
        registrar.on(OrderContract.ON_STOCK_REJECTED, this::onStockRejected);
        registrar.on(OrderContract.ON_STOCK_RELEASED, this::onStockReleased);
        registrar.on(OrderContract.PLACE_WITH_OUTBOX, this::placeWithOutbox);
        registrar.on(OrderContract.PLACE_DUAL_WRITE, this::placeDualWrite);
        registrar.on(OrderContract.RELAY_OUTBOX, this::relayOutbox);
    }

    ServiceResult<OrderView> placeOrder(CallContext context, PlaceOrder request) {
        ServiceBus bus = context.bus();
        CallOptions options = CallOptions.timeout(request.callTimeoutMillis());

        CallResult<UserView> user = bus.call(ServiceName.ORDER, UserContract.GET_USER, new GetUser(request.userRef()), options);
        if (!user.isSuccess()) {
            return ServiceResult.failure(failure("USER", user));
        }
        CallResult<ProductView> product = bus.call(ServiceName.ORDER, ProductContract.GET_PRODUCT, new GetProduct(request.productRef()), options);
        if (!product.isSuccess()) {
            return ServiceResult.failure(failure("PRODUCT", product));
        }
        BigDecimal unitPrice = product.value().price();
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        if (total.compareTo(user.value().creditLimit()) > 0) {
            return ServiceResult.failure("CREDIT_LIMIT_EXCEEDED");
        }

        // The order keeps a COPY of the price it was quoted: it must not join to the Product service tables.
        long orderRef = db.local(ServiceName.ORDER, () -> repository.insertOrder(request.userRef(), request.productRef(), request.quantity(), "PENDING", unitPrice));

        Reserve reserveRequest = new Reserve(orderRef, request.productRef(), request.quantity());
        CallResult<Reservation> reserve = request.inventoryAttempts() > 1
                ? ResilientCaller.call(bus, ServiceName.ORDER, InventoryContract.RESERVE, reserveRequest, options, RetryPolicy.of(request.inventoryAttempts()),
                        null, null, true, null).last() // reserving is idempotent per order, so even a timeout may be retried
                : bus.call(ServiceName.ORDER, InventoryContract.RESERVE, reserveRequest, options);
        if (!reserve.isSuccess()) {
            if (reserve.outcome() != Outcome.TIMEOUT) {
                db.local(ServiceName.ORDER, () -> {
                    repository.updateStatus(orderRef, "REJECTED");
                    return null;
                });
            } // after a timeout the order stays PENDING: this service cannot know whether the stock was reserved
            return ServiceResult.failure(failure("INVENTORY", reserve));
        }

        if (request.injection() == Injection.AFTER_RESERVE) {
            throw new IllegalStateException("injected failure after the inventory reservation");
        }
        db.local(ServiceName.ORDER, () -> {
            repository.updateStatus(orderRef, "PLACED");
            return null;
        });

        if (request.notifyMode() == NotifyMode.SYNC) {
            CallResult<NotificationContract.Ack> sent = bus.call(ServiceName.ORDER, NotificationContract.SEND, new NotificationContract.Send(orderRef), options);
            if (!sent.isSuccess()) {
                return ServiceResult.failure(failure("NOTIFICATION", sent)); // the order IS placed, but the caller is told it failed
            }
        } else if (request.notifyMode() == NotifyMode.ASYNC) {
            events.publish("orders", "OrderPlaced", orderRef);
        }
        return ServiceResult.ok(new OrderView(orderRef, "PLACED", total));
    }

    // ---- saga: orchestration

    ServiceResult<SagaView> placeOrderSaga(CallContext context, SagaRequest request) {
        return orchestrator.run(context.bus(), request);
    }

    ServiceResult<SagaView> resumeCompensation(CallContext context, Resume request) {
        return orchestrator.resume(context.bus(), request.orderRef());
    }

    // ---- saga: choreography. Nobody coordinates; the Order service creates the order and then only REACTS to events.

    ServiceResult<OrderView> startChoreography(CallContext context, SagaRequest request) {
        CallResult<ProductView> product = context.bus().call(ServiceName.ORDER, ProductContract.GET_PRODUCT, new GetProduct(request.productRef()),
                CallOptions.timeout(200));
        if (!product.isSuccess()) {
            return ServiceResult.failure(product.outcome() == Outcome.BUSINESS_FAILURE ? "PRODUCT_NOT_FOUND" : "PRODUCT_" + product.outcome());
        }
        BigDecimal unitPrice = product.value().price();
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        long orderRef = db.local(ServiceName.ORDER, () -> {
            long id = repository.insertOrder(request.userRef(), request.productRef(), request.quantity(), "PENDING", unitPrice);
            repository.insertSagaStep(id, 1, "ORDER_CREATED", "COMPLETED", "order created PENDING; OrderCreated published next");
            return id;
        });
        events.publish("saga.OrderCreated", "OrderCreated", orderRef,
                Map.of("productRef", String.valueOf(request.productRef()), "quantity", String.valueOf(request.quantity()), "amount", amount.toPlainString()));
        return ServiceResult.ok(new OrderView(orderRef, "PENDING", amount));
    }

    ServiceResult<Handled> onPaymentCompleted(CallContext context, EventMessage message) {
        return react(message, "PLACED", "ORDER_CONFIRMED", "payment completed: order PLACED");
    }

    ServiceResult<Handled> onStockRejected(CallContext context, EventMessage message) {
        return react(message, "REJECTED", "ORDER_REJECTED", "stock rejected: " + message.data().getOrDefault("reason", "?"));
    }

    ServiceResult<Handled> onStockReleased(CallContext context, EventMessage message) {
        return react(message, "CANCELLED", "ORDER_CANCELLED", "stock released after the payment failed: order CANCELLED");
    }

    /** Idempotent: a status that is already set is not set (or logged) again. */
    private ServiceResult<Handled> react(EventMessage message, String status, String step, String detail) {
        boolean changed = db.local(ServiceName.ORDER, () -> {
            if (status.equals(repository.findStatus(message.orderRef()).orElse(""))) {
                return false;
            }
            repository.updateStatus(message.orderRef(), status);
            repository.insertSagaStep(message.orderRef(), repository.nextSagaSeq(message.orderRef()), step, "COMPLETED", detail);
            return true;
        });
        return ServiceResult.ok(new Handled(changed ? "order " + status : "already " + status));
    }

    // ---- transactional outbox versus dual write

    /**
     * The order row and the outbox row are written in ONE local transaction: they commit together or, when this method fails, roll back
     * together. Publishing happens later, by {@link #relayOutbox}.
     */
    ServiceResult<OrderView> placeWithOutbox(CallContext context, OutboxOrder request) {
        long orderRef = db.local(ServiceName.ORDER, () -> {
            long id = repository.insertOrder(request.userRef(), request.productRef(), request.quantity(), "PLACED", null);
            repository.insertOutbox("ob-" + id, "order-" + id, "OrderPlaced", "orderRef=" + id);
            if (request.failAfterWrite()) {
                throw new IllegalStateException("injected failure before the transaction commits");
            }
            return id;
        });
        return ServiceResult.ok(new OrderView(orderRef, "PLACED", BigDecimal.ZERO));
    }

    /**
     * The naive alternative: commit the order, then publish. These are two separate writes to two systems and there is a window
     * between them; a failure inside the window loses the event while the order exists.
     */
    ServiceResult<OrderView> placeDualWrite(CallContext context, OutboxOrder request) {
        long orderRef = db.local(ServiceName.ORDER, () -> repository.insertOrder(request.userRef(), request.productRef(), request.quantity(), "PLACED", null));
        if (request.failAfterWrite()) {
            throw new IllegalStateException("injected crash between the commit and the publish");
        }
        events.publish("orders", "OrderPlaced", orderRef);
        return ServiceResult.ok(new OrderView(orderRef, "PLACED", BigDecimal.ZERO));
    }

    /**
     * Publishes the committed outbox rows, then marks each PUBLISHED, as separate steps. A crash between publishing and marking leaves
     * the row PENDING, so the next run publishes it again with the SAME message id: delivery is at-least-once, never exactly-once.
     */
    ServiceResult<Relayed> relayOutbox(CallContext context, Relay request) {
        List<OutboxRow> pending = repository.pendingOutbox();
        int published = 0;
        for (OutboxRow row : pending) {
            events.publishWithId("orders", row.eventType(), Long.parseLong(row.aggregate().substring("order-".length())), row.messageId());
            published++;
            if (request.crashAfterPublish()) {
                throw new IllegalStateException("injected crash after publishing and before marking the row");
            }
            db.local(ServiceName.ORDER, () -> {
                repository.markOutboxPublished(row.id());
                return null;
            });
        }
        return ServiceResult.ok(new Relayed(published, repository.pendingOutbox().size()));
    }

    /** A business refusal keeps its own code; anything else becomes {@code <SERVICE>_<OUTCOME>}. */
    private static String failure(String service, CallResult<?> result) {
        if (result.outcome() == Outcome.BUSINESS_FAILURE) {
            return "NOT_FOUND".equals(result.code()) ? service + "_NOT_FOUND" : result.code();
        }
        return service + "_" + result.outcome();
    }
}
