package com.springlab.lab.micro.run;

import com.springlab.lab.micro.core.ServiceName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The runtime ownership guard, against fake JDBC objects: a refused statement must never reach the driver. */
class OwnershipGuardTest {

    final List<String> reachedTheDriver = new ArrayList<>();

    DataSource fakeDataSource() {
        Statement statement = (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Statement.class}, (p, m, a) -> {
            if (m.getName().startsWith("execute") && a != null && a.length > 0 && a[0] instanceof String sql) {
                reachedTheDriver.add("statement:" + sql);
            }
            return defaultFor(m.getReturnType());
        });
        Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class}, (p, m, a) -> {
            if (m.getName().equals("prepareStatement")) {
                reachedTheDriver.add("prepare:" + a[0]);
                return null;
            }
            if (m.getName().equals("createStatement")) {
                return statement;
            }
            return defaultFor(m.getReturnType());
        });
        return (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class}, (p, m, a) -> {
            if (m.getName().equals("getConnection")) {
                return connection;
            }
            return defaultFor(m.getReturnType());
        });
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    OwnershipGuardedDataSource guardFor(ServiceName owner) {
        return new OwnershipGuardedDataSource(fakeDataSource(), owner);
    }

    @Test
    void aServiceMayPrepareStatementsOnItsOwnTables() throws Exception {
        guardFor(ServiceName.ORDER).getConnection().prepareStatement("select status from micro_lab_orders where run_key = ?");
        guardFor(ServiceName.ORDER).getConnection().prepareStatement("insert into micro_lab_outbox (run_key) values (?)");
        assertThat(reachedTheDriver).hasSize(2);
    }

    @Test
    void aStatementOnAnotherServicesTableIsRefusedBeforeTheDriverSeesIt() {
        assertThatThrownBy(() -> guardFor(ServiceName.ORDER).getConnection().prepareStatement("select available from micro_lab_stock where run_key = ?"))
                .isInstanceOf(OwnershipViolation.class).hasMessageContaining("order service may not touch micro_lab_stock").hasMessageContaining("belongs to inventory");
        assertThat(reachedTheDriver).as("nothing reached the driver").isEmpty();
    }

    @Test
    void aJoinThatIncludesAForeignTableIsRefusedToo() {
        assertThatThrownBy(() -> guardFor(ServiceName.INVENTORY).getConnection()
                .prepareStatement("select * from micro_lab_stock s join micro_lab_orders o on o.product_ref = s.product_ref"))
                .isInstanceOf(OwnershipViolation.class).hasMessageContaining("micro_lab_orders");
        assertThat(reachedTheDriver).isEmpty();
    }

    @Test
    void plainStatementsAreGuardedToo() throws Exception {
        Statement statement = guardFor(ServiceName.USER).getConnection().createStatement();
        assertThatThrownBy(() -> statement.executeQuery("select * from micro_lab_payments")).isInstanceOf(OwnershipViolation.class);
        statement.executeQuery("select * from micro_lab_users");
        assertThat(reachedTheDriver).containsExactly("statement:select * from micro_lab_users");
    }

    @Test
    void theGatewayOwnsNoTablesSoItMayTouchNone() {
        assertThatThrownBy(() -> guardFor(ServiceName.GATEWAY).getConnection().prepareStatement("select 1 from micro_lab_users")).isInstanceOf(OwnershipViolation.class);
    }

    @Test
    void statementsWithoutLabTablesAreLeftAlone() throws Exception {
        guardFor(ServiceName.PRODUCT).getConnection().prepareStatement("select 1");
        assertThat(reachedTheDriver).containsExactly("prepare:select 1");
    }

    @Test
    void onlyServiceLabelsAreGuardedNotTheMonolithOrTheInspector() {
        assertThat(OwnershipGuardedDataSource.serviceOf("order")).isPresent();
        assertThat(OwnershipGuardedDataSource.serviceOf("monolith")).isEmpty();
        assertThat(OwnershipGuardedDataSource.serviceOf("lab-inspector")).isEmpty();
        assertThat(OwnershipGuardedDataSource.serviceOf("shared-db")).isEmpty();
    }
}
