package com.springlab.lab.micro.inventory;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.InventoryContract.Ack;
import com.springlab.lab.micro.contract.InventoryContract.DeleteStock;
import com.springlab.lab.micro.contract.InventoryContract.Level;
import com.springlab.lab.micro.contract.InventoryContract.Release;
import com.springlab.lab.micro.contract.InventoryContract.Released;
import com.springlab.lab.micro.contract.InventoryContract.Reservation;
import com.springlab.lab.micro.contract.InventoryContract.Reserve;
import com.springlab.lab.micro.contract.InventoryContract.Restock;
import com.springlab.lab.micro.contract.InventoryContract.SeedStock;
import com.springlab.lab.micro.contract.InventoryContract.StockLevel;
import com.springlab.lab.micro.core.CallContext;
import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Handled;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.inventory.InventoryRepository.ReservationRow;
import com.springlab.lab.micro.run.MicroDb;
import com.springlab.lab.micro.run.MicroEvents;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * The Inventory service. It owns stock and reservations and decides, in ONE short local transaction, whether stock can be
 * reserved. It knows nothing about orders or payments beyond the order reference it is given.
 *
 * <p>Releasing a reservation is the COMPENSATION of reserving: a new business operation that gives the stock back and leaves the
 * reservation visible as RELEASED. It is not a database rollback; the reservation was committed and other readers could see it.
 */
@Service
public class InventoryService implements ServiceModule {

    private final InventoryRepository repository;
    private final MicroDb db;
    private final MicroEvents events;

    InventoryService(InventoryRepository repository, MicroDb db, MicroEvents events) {
        this.repository = repository;
        this.db = db;
        this.events = events;
    }

    @Override
    public ServiceName service() {
        return ServiceName.INVENTORY;
    }

    @Override
    public void register(HandlerRegistry.Registrar registrar) {
        registrar.on(InventoryContract.SEED_STOCK, this::seedStock);
        registrar.on(InventoryContract.RESERVE, this::reserve);
        registrar.on(InventoryContract.RELEASE, this::release);
        registrar.on(InventoryContract.STOCK_LEVEL, this::stockLevel);
        registrar.on(InventoryContract.RESTOCK, this::restock);
        registrar.on(InventoryContract.DELETE_STOCK, this::deleteStock);
        registrar.on(InventoryContract.ON_ORDER_CREATED, this::onOrderCreated);
        registrar.on(InventoryContract.ON_PAYMENT_FAILED, this::onPaymentFailed);
    }

    ServiceResult<Ack> seedStock(CallContext context, SeedStock request) {
        db.local(ServiceName.INVENTORY, () -> {
            repository.insertStock(request.productRef(), request.quantity());
            return null;
        });
        return ServiceResult.ok(new Ack(true));
    }

    ServiceResult<Reservation> reserve(CallContext context, Reserve request) {
        if (request.quantity() <= 0) {
            return ServiceResult.failure("INVALID_QUANTITY");
        }
        return db.local(ServiceName.INVENTORY, () -> {
            Optional<ReservationRow> existing = repository.findReservation(request.orderRef());
            if (existing.isPresent()) {
                if ("RELEASED".equals(existing.get().status())) {
                    return ServiceResult.failure("RESERVATION_RELEASED");
                }
                return ServiceResult.ok(new Reservation(existing.get().id(), repository.available(request.productRef()), true));
            }
            if (repository.decrement(request.productRef(), request.quantity()) == 0) {
                return ServiceResult.failure(repository.stockExists(request.productRef()) ? "OUT_OF_STOCK" : "UNKNOWN_PRODUCT");
            }
            repository.insertReservation(request.orderRef(), request.productRef(), request.quantity());
            long id = repository.findReservation(request.orderRef()).orElseThrow().id();
            return ServiceResult.ok(new Reservation(id, repository.available(request.productRef()), false));
        });
    }

    /** Compensation of reserve. Idempotent: releasing twice gives the stock back once. Business refusal: NOT_RESERVED. */
    ServiceResult<Released> release(CallContext context, Release request) {
        return db.local(ServiceName.INVENTORY, () -> {
            Optional<ReservationRow> reservation = repository.findReservation(request.orderRef());
            if (reservation.isEmpty()) {
                return ServiceResult.failure("NOT_RESERVED");
            }
            ReservationRow row = reservation.get();
            if (repository.markReleased(row.id()) == 0) {
                return ServiceResult.ok(new Released(true, repository.available(row.productRef())));
            }
            repository.addToStock(row.stockId(), row.quantity());
            return ServiceResult.ok(new Released(false, repository.available(row.productRef())));
        });
    }

    ServiceResult<StockLevel> stockLevel(CallContext context, Level request) {
        if (!repository.stockExists(request.productRef())) {
            return ServiceResult.failure("UNKNOWN_PRODUCT");
        }
        return ServiceResult.ok(new StockLevel(repository.available(request.productRef())));
    }

    ServiceResult<Ack> restock(CallContext context, Restock request) {
        int changed = db.local(ServiceName.INVENTORY, () -> repository.addToProduct(request.productRef(), request.quantity()));
        return changed == 0 ? ServiceResult.failure("UNKNOWN_PRODUCT") : ServiceResult.ok(new Ack(true));
    }

    ServiceResult<Ack> deleteStock(CallContext context, DeleteStock request) {
        db.local(ServiceName.INVENTORY, () -> repository.deleteStock(request.productRef()));
        return ServiceResult.ok(new Ack(true));
    }

    // ---- choreography: Inventory reacts to events, nothing calls it

    ServiceResult<Handled> onOrderCreated(CallContext context, EventMessage message) {
        ServiceResult<Reservation> result = reserve(context, new Reserve(message.orderRef(), message.number("productRef"), (int) message.number("quantity")));
        if (result.ok()) {
            events.publish("saga.StockReserved", "StockReserved", message.orderRef(), message.data());
            return ServiceResult.ok(new Handled("reserved"));
        }
        events.publish("saga.StockRejected", "StockRejected", message.orderRef(), Map.of("reason", result.code()));
        return ServiceResult.ok(new Handled("rejected: " + result.code()));
    }

    ServiceResult<Handled> onPaymentFailed(CallContext context, EventMessage message) {
        ServiceResult<Released> result = release(context, new Release(message.orderRef()));
        if (!result.ok()) {
            return ServiceResult.ok(new Handled("nothing to release: " + result.code()));
        }
        events.publish("saga.StockReleased", "StockReleased", message.orderRef(), Map.of());
        return ServiceResult.ok(new Handled(result.value().alreadyReleased() ? "already released" : "released"));
    }
}
