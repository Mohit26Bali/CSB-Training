package com.springlab.lab.sql;

import com.springlab.lab.trace.TraceCollector;
import com.springlab.lab.trace.TraceContext;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Hibernate calls {@link #inspect(String)} with the SQL text of every statement it is about to prepare. When the
 * current thread is running a traced request the text is recorded; otherwise this is a no-op, and the statement
 * is ALWAYS returned unchanged so application behaviour is never altered.
 *
 * <p>What this does and does not show: the text still contains {@code ?} placeholders (bound values are not
 * available here), and it says nothing about what the database returned or how it executed the statement.
 * It sees statements issued through Hibernate only (not Flyway or plain JDBC).
 *
 * <p>Registered with {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}. Hibernate
 * instantiates it by reflection, so it cannot receive Spring beans; state lives in {@link TraceContext}.
 */
public class CapturingStatementInspector implements StatementInspector {

    private static final long serialVersionUID = 1L;

    @Override
    public String inspect(String sql) {
        TraceCollector collector = TraceContext.current();
        if (collector != null) {
            collector.recordSql(sql);
        }
        return sql;
    }
}
