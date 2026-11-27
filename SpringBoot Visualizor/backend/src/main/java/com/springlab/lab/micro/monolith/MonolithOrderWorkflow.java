package com.springlab.lab.micro.monolith;

import com.springlab.lab.micro.run.MicroDb;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * The COMPARISON "monolith": the same order workflow as the Order service, written the way a single application with one shared
 * database writes it. Users, products, stock, reservations and orders are all read and written inside ONE database transaction,
 * so either everything happens or nothing does.
 *
 * <p>This class is the only place in the lab that reaches across what are otherwise separate services' tables. That is the whole
 * point: it is what you give up (and gain) by splitting. No service may use it (checked by a test).
 */
@Component
public class MonolithOrderWorkflow {

    static final String LABEL = "monolith";

    public record Placed(long orderRef, BigDecimal total) {
    }

    private final MicroDb db;

    MonolithOrderWorkflow(MicroDb db) {
        this.db = db;
    }

    /** Places an order in one transaction. An exception anywhere rolls EVERYTHING back, including the stock that was already taken. */
    public Placed place(long userRef, long productRef, int quantity, boolean failAfterReserve) {
        return db.localFor(LABEL, () -> {
            JdbcTemplate t = db.templateFor(LABEL);
            String run = db.runKey();
            BigDecimal credit = one(t, "select credit_limit from micro_lab_users where run_key = ? and id = ?", run, userRef);
            BigDecimal price = one(t, "select price from micro_lab_products where run_key = ? and id = ?", run, productRef);
            BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
            if (total.compareTo(credit) > 0) {
                throw new IllegalStateException("CREDIT_LIMIT_EXCEEDED");
            }
            KeyHolder keys = new GeneratedKeyHolder();
            t.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "insert into micro_lab_orders (run_key, user_ref, product_ref, quantity, status) values (?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, run);
                ps.setLong(2, userRef);
                ps.setLong(3, productRef);
                ps.setInt(4, quantity);
                ps.setString(5, "PENDING");
                return ps;
            }, keys);
            long orderRef = keys.getKey().longValue();
            int taken = t.update("update micro_lab_stock set available = available - ? where run_key = ? and product_ref = ? and available >= ?",
                    quantity, run, productRef, quantity);
            if (taken == 0) {
                throw new IllegalStateException("OUT_OF_STOCK");
            }
            t.update("insert into micro_lab_reservations (run_key, order_ref, stock_id, quantity, status) select ?, ?, id, ?, ? from micro_lab_stock "
                    + "where run_key = ? and product_ref = ?", run, orderRef, quantity, "RESERVED", run, productRef);
            if (failAfterReserve) {
                throw new IllegalStateException("injected failure after the stock reservation");
            }
            t.update("update micro_lab_orders set status = ? where run_key = ? and id = ?", "PLACED", run, orderRef);
            return new Placed(orderRef, total);
        });
    }

    private static BigDecimal one(JdbcTemplate t, String sql, Object... args) {
        List<BigDecimal> rows = t.queryForList(sql, BigDecimal.class, args);
        if (rows.isEmpty()) {
            throw new IllegalStateException("NOT_FOUND");
        }
        return rows.get(0);
    }
}
