package com.springlab.lab.micro.user;

import com.springlab.lab.micro.contract.UserContract.UserView;
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

/** The User service's own data access. It touches only micro_lab_users (checked by a test). */
@Repository
class UserAccountRepository {

    private static final ServiceName SERVICE = ServiceName.USER;
    private final MicroDb db;

    UserAccountRepository(MicroDb db) {
        this.db = db;
    }

    long insert(String name, BigDecimal creditLimit) {
        KeyHolder keys = new GeneratedKeyHolder();
        String runKey = db.runKey();
        db.template(SERVICE).update(connection -> {
            PreparedStatement ps = connection.prepareStatement("insert into micro_lab_users (run_key, name, credit_limit) values (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, runKey);
            ps.setString(2, name);
            ps.setBigDecimal(3, creditLimit);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    Optional<UserView> find(long userRef) {
        List<UserView> rows = db.template(SERVICE).query("select id, name, credit_limit from micro_lab_users where run_key = ? and id = ?",
                (rs, n) -> new UserView(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("credit_limit")), db.runKey(), userRef);
        return rows.stream().findFirst();
    }
}
