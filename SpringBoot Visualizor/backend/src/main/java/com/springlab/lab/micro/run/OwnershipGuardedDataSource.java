package com.springlab.lab.micro.run;

import com.springlab.lab.micro.core.ServiceName;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces data ownership AT RUNTIME for the connections of one service: any statement that mentions a micro_lab_* table the service
 * does not own is refused with {@link OwnershipViolation} before it reaches the JDBC driver.
 *
 * <p>This is LOGICAL isolation, enforced by lab code inside one JVM against one MySQL schema. With separate physical databases the
 * database server itself would refuse (different credentials, different servers); here nothing in MySQL stops a statement, which is
 * why the lab checks it. The source-level boundary test already guarantees the real services never trip this guard.
 */
final class OwnershipGuardedDataSource extends DelegatingDataSource {

    private static final Pattern TABLE = Pattern.compile("micro_lab_[a-z_]+");

    private final ServiceName owner;

    OwnershipGuardedDataSource(DataSource target, ServiceName owner) {
        super(target);
        this.owner = owner;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return guard(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return guard(super.getConnection(username, password));
    }

    private Connection guard(Connection target) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, args) -> {
            if ((method.getName().equals("prepareStatement") || method.getName().equals("prepareCall")) && args != null && args[0] instanceof String sql) {
                check(sql);
            }
            Object result = call(target, method, args);
            if (method.getName().equals("createStatement") && result instanceof Statement statement) {
                return guard(statement);
            }
            return result;
        });
    }

    private Statement guard(Statement target) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ((method.getName().startsWith("execute") || method.getName().equals("addBatch")) && args != null && args.length > 0 && args[0] instanceof String sql) {
                check(sql);
            }
            return call(target, method, args);
        };
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[] {Statement.class}, handler);
    }

    private void check(String sql) {
        Matcher m = TABLE.matcher(sql);
        while (m.find()) {
            String table = m.group();
            if (!owner.ownedTables().contains(table)) {
                String owningService = Arrays.stream(ServiceName.values()).filter(s -> s.ownedTables().contains(table)).map(ServiceName::id).findFirst().orElse("nobody");
                throw new OwnershipViolation("The " + owner.id() + " service may not touch " + table + ", which belongs to " + owningService);
            }
        }
    }

    private static Object call(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    static Optional<ServiceName> serviceOf(String label) {
        return Arrays.stream(ServiceName.values()).filter(s -> s.id().equals(label)).findFirst();
    }
}
