package com.springlab.lab.micro.order;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.InventoryContract.Release;
import com.springlab.lab.micro.contract.InventoryContract.Reserve;
import com.springlab.lab.micro.contract.OrderContract.SagaFault;
import com.springlab.lab.micro.contract.OrderContract.SagaRequest;
import com.springlab.lab.micro.contract.OrderContract.SagaStepView;
import com.springlab.lab.micro.contract.OrderContract.SagaView;
import com.springlab.lab.micro.contract.PaymentContract;
import com.springlab.lab.micro.contract.PaymentContract.Charge;
import com.springlab.lab.micro.contract.PaymentContract.Refund;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.GetProduct;
import com.springlab.lab.micro.contract.ProductContract.ProductView;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Saga ORCHESTRATION: the Order service is the coordinator that knows the whole workflow. It performs the steps in order (create the
 * order, reserve stock, charge the payment, confirm the order), records every step and its state in its OWN saga log, and when a step
 * fails it runs the COMPENSATIONS of the steps that had completed, in reverse order.
 *
 * <p>Every step is a call to another service, which commits its own LOCAL transaction. There is no transaction spanning them, so
 * between the steps other readers can see the intermediate state. A compensation is a NEW business operation (a refund, a release)
 * that reverses the earlier effect; it is not a database rollback, and the original effect stays visible in that service history.
 * A compensation can itself fail; the saga then ends COMPENSATION_FAILED and can be resumed.
 */
@Component
class SagaOrchestrator {

    static final String RESERVE = "RESERVE_STOCK";
    static final String CHARGE = "CHARGE_PAYMENT";
    private static final CallOptions OPTIONS = CallOptions.timeout(200);

    private final OrderRepository repository;
    private final MicroDb db;

    SagaOrchestrator(OrderRepository repository, MicroDb db) {
        this.repository = repository;
        this.db = db;
    }

    ServiceResult<SagaView> run(ServiceBus bus, SagaRequest request) {
        CallResult<ProductView> product = bus.call(ServiceName.ORDER, ProductContract.GET_PRODUCT, new GetProduct(request.productRef()), OPTIONS);
        if (!product.isSuccess()) {
            return ServiceResult.failure(product.outcome() == Outcome.BUSINESS_FAILURE ? "PRODUCT_NOT_FOUND" : "PRODUCT_" + product.outcome());
        }
        BigDecimal unitPrice = product.value().price();
        BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));
        long orderRef = db.local(ServiceName.ORDER, () -> {
            long id = repository.insertOrder(request.userRef(), request.productRef(), request.quantity(), "PENDING", unitPrice);
            repository.insertSagaStep(id, 1, "CREATE_ORDER", "COMPLETED", "order created PENDING");
            return id;
        });
        List<String> completed = new ArrayList<>();

        int seq = begin(orderRef, RESERVE);
        CallResult<?> reserve = bus.call(ServiceName.ORDER, InventoryContract.RESERVE, new Reserve(orderRef, request.productRef(), request.quantity()), OPTIONS);
        if (!reserve.isSuccess()) {
            finish(orderRef, seq, "FAILED", describe(reserve));
            return ServiceResult.ok(end(orderRef, "REJECTED"));
        }
        finish(orderRef, seq, "COMPLETED", "stock reserved");
        completed.add(RESERVE);

        seq = begin(orderRef, CHARGE);
        CallResult<?> charge = bus.call(ServiceName.ORDER, PaymentContract.CHARGE, new Charge(orderRef, amount, "order-" + orderRef), OPTIONS);
        if (!charge.isSuccess()) {
            finish(orderRef, seq, "FAILED", describe(charge));
            return ServiceResult.ok(compensate(bus, orderRef, request.productRef(), completed));
        }
        finish(orderRef, seq, "COMPLETED", "payment charged");
        completed.add(CHARGE);

        seq = begin(orderRef, "CONFIRM_ORDER");
        if (request.fault() == SagaFault.CONFIRM_FAILS) {
            finish(orderRef, seq, "FAILED", "injected failure while confirming the order");
            return ServiceResult.ok(compensate(bus, orderRef, request.productRef(), completed));
        }
        int confirmSeq = seq;
        db.local(ServiceName.ORDER, () -> {
            repository.updateStatus(orderRef, "PLACED");
            repository.updateSagaStep(orderRef, confirmSeq, "COMPLETED", "order PLACED");
            return null;
        });
        return ServiceResult.ok(view(orderRef));
    }

    /** Retries the compensations that failed. Business refusal: NOT_WAITING when the saga is not in COMPENSATION_FAILED. */
    ServiceResult<SagaView> resume(ServiceBus bus, long orderRef) {
        if (!"COMPENSATION_FAILED".equals(repository.findStatus(orderRef).orElse(""))) {
            return ServiceResult.failure("NOT_WAITING");
        }
        boolean allDone = true;
        for (SagaStepView step : repository.sagaSteps(orderRef)) {
            if ("COMPENSATION_FAILED".equals(step.state())) {
                CallResult<?> result = compensation(bus, orderRef, step.step());
                boolean ok = result.isSuccess();
                allDone &= ok;
                db.local(ServiceName.ORDER, () -> {
                    repository.updateSagaStep(orderRef, step.seq(), ok ? "COMPENSATED" : "COMPENSATION_FAILED", ok ? "compensation succeeded on retry" : describe(result));
                    return null;
                });
            }
        }
        if (allDone) {
            db.local(ServiceName.ORDER, () -> {
                repository.updateStatus(orderRef, "CANCELLED");
                return null;
            });
        }
        return ServiceResult.ok(view(orderRef));
    }

    private SagaView compensate(ServiceBus bus, long orderRef, long productRef, List<String> completed) {
        boolean failed = false;
        for (int i = completed.size() - 1; i >= 0; i--) { // reverse order
            String step = "COMPENSATE_" + completed.get(i);
            int seq = begin(orderRef, step);
            CallResult<?> result = compensation(bus, orderRef, step);
            failed |= !result.isSuccess();
            finish(orderRef, seq, result.isSuccess() ? "COMPENSATED" : "COMPENSATION_FAILED", result.isSuccess() ? "reversed by a new business operation" : describe(result));
        }
        return end(orderRef, failed ? "COMPENSATION_FAILED" : "CANCELLED");
    }

    private CallResult<?> compensation(ServiceBus bus, long orderRef, String step) {
        if (step.equals("COMPENSATE_" + CHARGE)) {
            return bus.call(ServiceName.ORDER, PaymentContract.REFUND, new Refund(orderRef), OPTIONS);
        }
        return bus.call(ServiceName.ORDER, InventoryContract.RELEASE, new Release(orderRef), OPTIONS);
    }

    private int begin(long orderRef, String step) {
        return db.local(ServiceName.ORDER, () -> {
            int seq = repository.nextSagaSeq(orderRef);
            repository.insertSagaStep(orderRef, seq, step, "STARTED", null);
            return seq;
        });
    }

    private void finish(long orderRef, int seq, String state, String detail) {
        db.local(ServiceName.ORDER, () -> {
            repository.updateSagaStep(orderRef, seq, state, detail);
            return null;
        });
    }

    private SagaView end(long orderRef, String status) {
        db.local(ServiceName.ORDER, () -> {
            repository.updateStatus(orderRef, status);
            return null;
        });
        return view(orderRef);
    }

    private SagaView view(long orderRef) {
        return new SagaView(orderRef, repository.findStatus(orderRef).orElse("UNKNOWN"), repository.sagaSteps(orderRef));
    }

    private static String describe(CallResult<?> r) {
        return r.outcome() + (r.code() == null ? "" : " (" + r.code() + ")");
    }
}
