package com.springlab.lab.micro.payment;

import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/** The Payment service's own data access. It touches only micro_lab_payments (checked by a test). */
@Repository
class PaymentRepository {

    record Row(long id, String status) {
    }

    private static final ServiceName SERVICE = ServiceName.PAYMENT;
    private final MicroDb db;

    PaymentRepository(MicroDb db) {
        this.db = db;
    }

    Optional<Row> findByKey(String idempotencyKey) {
        return one(db.template(SERVICE).query("select id, status from micro_lab_payments where run_key = ? and idempotency_key = ?",
                (rs, n) -> new Row(rs.getLong("id"), rs.getString("status")), db.runKey(), idempotencyKey));
    }

    Optional<Row> findByOrder(long orderRef) {
        return one(db.template(SERVICE).query("select id, status from micro_lab_payments where run_key = ? and order_ref = ? order by id desc",
                (rs, n) -> new Row(rs.getLong("id"), rs.getString("status")), db.runKey(), orderRef));
    }

    long insert(long orderRef, BigDecimal amount, String status, String idempotencyKey) {
        KeyHolder keys = new GeneratedKeyHolder();
        String runKey = db.runKey();
        db.template(SERVICE).update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into micro_lab_payments (run_key, order_ref, amount, status, idempotency_key) values (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, runKey);
            ps.setLong(2, orderRef);
            ps.setBigDecimal(3, amount);
            ps.setString(4, status);
            ps.setString(5, idempotencyKey);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    /** CHARGED to REFUNDED. Returns 0 when the payment was not CHARGED. */
    int markRefunded(long paymentId) {
        return db.template(SERVICE).update("update micro_lab_payments set status = ? where run_key = ? and id = ? and status = ?", "REFUNDED", db.runKey(),
                paymentId, "CHARGED");
    }

    private static Optional<Row> one(List<Row> rows) {
        return rows.stream().findFirst();
    }
}
