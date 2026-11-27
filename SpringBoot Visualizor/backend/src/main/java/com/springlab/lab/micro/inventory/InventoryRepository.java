package com.springlab.lab.micro.inventory;

import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** The Inventory service's own data access. It touches only micro_lab_stock and micro_lab_reservations (checked by a test). */
@Repository
class InventoryRepository {

    record ReservationRow(long id, long stockId, int quantity, String status, long productRef) {
    }

    private static final ServiceName SERVICE = ServiceName.INVENTORY;
    private final MicroDb db;

    InventoryRepository(MicroDb db) {
        this.db = db;
    }

    void insertStock(long productRef, int quantity) {
        db.template(SERVICE).update("insert into micro_lab_stock (run_key, product_ref, available) values (?, ?, ?)", db.runKey(), productRef, quantity);
    }

    boolean stockExists(long productRef) {
        Integer n = db.template(SERVICE).queryForObject("select count(*) from micro_lab_stock where run_key = ? and product_ref = ?", Integer.class,
                db.runKey(), productRef);
        return n != null && n > 0;
    }

    /** Atomically takes stock if enough is left. Returns the number of rows changed (0 = not enough, or no such product). */
    int decrement(long productRef, int quantity) {
        return db.template(SERVICE).update(
                "update micro_lab_stock set available = available - ? where run_key = ? and product_ref = ? and available >= ?",
                quantity, db.runKey(), productRef, quantity);
    }

    /** Gives stock back to the row a reservation took it from. */
    void addToStock(long stockId, int quantity) {
        db.template(SERVICE).update("update micro_lab_stock set available = available + ? where run_key = ? and id = ?", quantity, db.runKey(), stockId);
    }

    int addToProduct(long productRef, int quantity) {
        return db.template(SERVICE).update("update micro_lab_stock set available = available + ? where run_key = ? and product_ref = ?", quantity,
                db.runKey(), productRef);
    }

    int deleteStock(long productRef) {
        return db.template(SERVICE).update("delete from micro_lab_stock where run_key = ? and product_ref = ?", db.runKey(), productRef);
    }

    void insertReservation(long orderRef, long productRef, int quantity) {
        db.template(SERVICE).update("insert into micro_lab_reservations (run_key, order_ref, stock_id, quantity, status) "
                + "select ?, ?, id, ?, ? from micro_lab_stock where run_key = ? and product_ref = ?",
                db.runKey(), orderRef, quantity, "RESERVED", db.runKey(), productRef);
    }

    Optional<ReservationRow> findReservation(long orderRef) {
        List<ReservationRow> rows = db.template(SERVICE).query(
                "select r.id, r.stock_id, r.quantity, r.status, s.product_ref from micro_lab_reservations r join micro_lab_stock s on s.id = r.stock_id "
                        + "where r.run_key = ? and r.order_ref = ?",
                (rs, n) -> new ReservationRow(rs.getLong("id"), rs.getLong("stock_id"), rs.getInt("quantity"), rs.getString("status"), rs.getLong("product_ref")),
                db.runKey(), orderRef);
        return rows.stream().findFirst();
    }

    /** RESERVED to RELEASED. Returns 0 when the reservation was not RESERVED (already released). */
    int markReleased(long reservationId) {
        return db.template(SERVICE).update("update micro_lab_reservations set status = ? where run_key = ? and id = ? and status = ?", "RELEASED",
                db.runKey(), reservationId, "RESERVED");
    }

    int available(long productRef) {
        Integer n = db.template(SERVICE).queryForObject("select available from micro_lab_stock where run_key = ? and product_ref = ?", Integer.class,
                db.runKey(), productRef);
        return n == null ? 0 : n;
    }
}
