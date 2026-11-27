package com.springlab.lab.micro.order;

import com.springlab.lab.micro.contract.OrderContract.SagaStepView;
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

/** The Order service's own data access. It touches only micro_lab_orders, micro_lab_saga_steps and micro_lab_outbox (checked by a test). */
@Repository
class OrderRepository {

    record OutboxRow(long id, String messageId, String aggregate, String eventType) {
    }

    private static final ServiceName SERVICE = ServiceName.ORDER;
    private final MicroDb db;

    OrderRepository(MicroDb db) {
        this.db = db;
    }

    long insertOrder(long userRef, long productRef, int quantity, String status, BigDecimal unitPrice) {
        KeyHolder keys = new GeneratedKeyHolder();
        String runKey = db.runKey();
        db.template(SERVICE).update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "insert into micro_lab_orders (run_key, user_ref, product_ref, quantity, status, unit_price) values (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, runKey);
            ps.setLong(2, userRef);
            ps.setLong(3, productRef);
            ps.setInt(4, quantity);
            ps.setString(5, status);
            ps.setBigDecimal(6, unitPrice);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    void updateStatus(long orderRef, String status) {
        db.template(SERVICE).update("update micro_lab_orders set status = ? where run_key = ? and id = ?", status, db.runKey(), orderRef);
    }

    Optional<String> findStatus(long orderRef) {
        return db.template(SERVICE).queryForList("select status from micro_lab_orders where run_key = ? and id = ?", String.class, db.runKey(), orderRef)
                .stream().findFirst();
    }

    // ---- the saga log

    void insertSagaStep(long orderRef, int seq, String step, String state, String detail) {
        db.template(SERVICE).update("insert into micro_lab_saga_steps (run_key, order_id, seq, step, state, detail) values (?, ?, ?, ?, ?, ?)",
                db.runKey(), orderRef, seq, step, state, detail);
    }

    void updateSagaStep(long orderRef, int seq, String state, String detail) {
        db.template(SERVICE).update("update micro_lab_saga_steps set state = ?, detail = ? where run_key = ? and order_id = ? and seq = ?", state, detail,
                db.runKey(), orderRef, seq);
    }

    List<SagaStepView> sagaSteps(long orderRef) {
        return db.template(SERVICE).query("select seq, step, state, detail from micro_lab_saga_steps where run_key = ? and order_id = ? order by seq",
                (rs, n) -> new SagaStepView(rs.getInt("seq"), rs.getString("step"), rs.getString("state"), rs.getString("detail")), db.runKey(), orderRef);
    }

    int nextSagaSeq(long orderRef) {
        Integer max = db.template(SERVICE).queryForObject("select coalesce(max(seq), 0) from micro_lab_saga_steps where run_key = ? and order_id = ?",
                Integer.class, db.runKey(), orderRef);
        return (max == null ? 0 : max) + 1;
    }

    // ---- the transactional outbox

    void insertOutbox(String messageId, String aggregate, String eventType, String payload) {
        db.template(SERVICE).update("insert into micro_lab_outbox (run_key, message_id, aggregate, event_type, payload, status) values (?, ?, ?, ?, ?, ?)",
                db.runKey(), messageId, aggregate, eventType, payload, "PENDING");
    }

    List<OutboxRow> pendingOutbox() {
        return db.template(SERVICE).query("select id, message_id, aggregate, event_type from micro_lab_outbox where run_key = ? and status = ? order by id",
                (rs, n) -> new OutboxRow(rs.getLong("id"), rs.getString("message_id"), rs.getString("aggregate"), rs.getString("event_type")), db.runKey(), "PENDING");
    }

    void markOutboxPublished(long id) {
        db.template(SERVICE).update("update micro_lab_outbox set status = ? where run_key = ? and id = ?", "PUBLISHED", db.runKey(), id);
    }
}
