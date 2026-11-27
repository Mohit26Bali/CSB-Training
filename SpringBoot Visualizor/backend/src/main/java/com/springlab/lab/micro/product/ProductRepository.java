package com.springlab.lab.micro.product;

import com.springlab.lab.micro.contract.ProductContract.ProductView;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.MicroDb;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/** The Product service's own data access. It touches only micro_lab_products (checked by a test). */
@Repository
class ProductRepository {

    private static final ServiceName SERVICE = ServiceName.PRODUCT;
    private final MicroDb db;

    ProductRepository(MicroDb db) {
        this.db = db;
    }

    long insert(String sku, String name, BigDecimal price) {
        JdbcTemplate template = db.template(SERVICE);
        KeyHolder keys = new GeneratedKeyHolder();
        String runKey = db.runKey();
        template.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("insert into micro_lab_products (run_key, sku, name, price) values (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, runKey);
            ps.setString(2, sku);
            ps.setString(3, name);
            ps.setBigDecimal(4, price);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    int delete(long productRef) {
        return db.template(SERVICE).update("delete from micro_lab_products where run_key = ? and id = ?", db.runKey(), productRef);
    }

    int updatePrice(long productRef, BigDecimal price) {
        return db.template(SERVICE).update("update micro_lab_products set price = ? where run_key = ? and id = ?", price, db.runKey(), productRef);
    }

    Optional<ProductView> find(long productRef) {
        List<ProductView> rows = db.template(SERVICE).query("select id, sku, name, price from micro_lab_products where run_key = ? and id = ?",
                (rs, n) -> new ProductView(rs.getLong("id"), rs.getString("sku"), rs.getString("name"), rs.getBigDecimal("price")),
                db.runKey(), productRef);
        return rows.stream().findFirst();
    }
}
