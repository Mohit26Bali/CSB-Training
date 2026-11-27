package com.springlab.lab.micro.payment;

import com.springlab.lab.micro.contract.PaymentContract;
import com.springlab.lab.micro.contract.PaymentContract.Ack;
import com.springlab.lab.micro.contract.PaymentContract.Charge;
import com.springlab.lab.micro.contract.PaymentContract.PaymentView;
import com.springlab.lab.micro.contract.PaymentContract.Policy;
import com.springlab.lab.micro.contract.PaymentContract.Refund;
import com.springlab.lab.micro.core.CallContext;
import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Handled;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.payment.PaymentRepository.Row;
import com.springlab.lab.micro.run.MicroDb;
import com.springlab.lab.micro.run.MicroEvents;
import com.springlab.lab.micro.run.MicroRunState;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Payment service. It owns payments and nothing else: it knows an order reference and an amount, not the order, the product or
 * the customer. No card data exists anywhere in the lab.
 *
 * <p>{@link #charge} is idempotent when the caller supplies an idempotency key: the key is unique in the Payment service own table,
 * so a repeated request returns the original payment instead of charging again. {@link #refund} is the COMPENSATION of a charge: a
 * new business operation that marks the payment REFUNDED. The payment stays visible; it is not a database rollback.
 */
@Service
public class PaymentService implements ServiceModule {

    private final PaymentRepository repository;
    private final MicroDb db;
    private final MicroEvents events;
    private final MicroRunState state;

    PaymentService(PaymentRepository repository, MicroDb db, MicroEvents events, MicroRunState state) {
        this.repository = repository;
        this.db = db;
        this.events = events;
        this.state = state;
    }

    @Override
    public ServiceName service() {
        return ServiceName.PAYMENT;
    }

    @Override
    public void register(HandlerRegistry.Registrar registrar) {
        registrar.on(PaymentContract.SET_POLICY, this::setPolicy);
        registrar.on(PaymentContract.CHARGE, this::charge);
        registrar.on(PaymentContract.REFUND, this::refund);
        registrar.on(PaymentContract.ON_STOCK_RESERVED, this::onStockReserved);
    }

    ServiceResult<Ack> setPolicy(CallContext context, Policy request) {
        state.of("payment.decline", AtomicBoolean::new).set(request.declineAll());
        return ServiceResult.ok(new Ack(true));
    }

    ServiceResult<PaymentView> charge(CallContext context, Charge request) {
        if (state.of("payment.decline", AtomicBoolean::new).get()) {
            return ServiceResult.failure("DECLINED");
        }
        try {
            return ServiceResult.ok(db.local(ServiceName.PAYMENT, () -> {
                if (request.idempotencyKey() != null) {
                    Optional<Row> existing = repository.findByKey(request.idempotencyKey());
                    if (existing.isPresent()) {
                        return new PaymentView(existing.get().id(), existing.get().status(), true);
                    }
                }
                return new PaymentView(repository.insert(request.orderRef(), request.amount(), "CHARGED", request.idempotencyKey()), "CHARGED", false);
            }));
        } catch (DuplicateKeyException race) {
            // Two requests with the same key arrived together: the unique key let exactly one insert through.
            Row existing = repository.findByKey(request.idempotencyKey()).orElseThrow();
            return ServiceResult.ok(new PaymentView(existing.id(), existing.status(), true));
        }
    }

    /** Compensation of charge. Idempotent. Business refusal: NOT_CHARGED. */
    ServiceResult<PaymentView> refund(CallContext context, Refund request) {
        return db.local(ServiceName.PAYMENT, () -> {
            Optional<Row> payment = repository.findByOrder(request.orderRef());
            if (payment.isEmpty()) {
                return ServiceResult.failure("NOT_CHARGED");
            }
            Row row = payment.get();
            if (repository.markRefunded(row.id()) == 0) {
                return ServiceResult.ok(new PaymentView(row.id(), row.status(), true));
            }
            return ServiceResult.ok(new PaymentView(row.id(), "REFUNDED", false));
        });
    }

    // ---- choreography: Payment reacts to StockReserved, nothing calls it

    ServiceResult<Handled> onStockReserved(CallContext context, EventMessage message) {
        BigDecimal amount = new BigDecimal(message.data().get("amount"));
        ServiceResult<PaymentView> result = charge(context, new Charge(message.orderRef(), amount, "order-" + message.orderRef()));
        if (result.ok()) {
            events.publish("saga.PaymentCompleted", "PaymentCompleted", message.orderRef(), message.data());
            return ServiceResult.ok(new Handled("charged"));
        }
        events.publish("saga.PaymentFailed", "PaymentFailed", message.orderRef(), Map.of("reason", result.code()));
        return ServiceResult.ok(new Handled("failed: " + result.code()));
    }
}
