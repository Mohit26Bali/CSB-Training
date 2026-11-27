package com.springlab.lab.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Topics 1 to 3 of the JDBC lab, run against the local MySQL test schema. */
class JdbcFundamentalsStatementsCrudIntegrationTest extends AbstractJdbcLabTest {

    // --------------------------------------------------------------------------------------------- fundamentals

    @Test
    void aConnectionFromTheDataSourceIsAPoolProxyOverADriverConnectionAndCloseKeepsTheSessionOpen() throws Exception {
        JsonNode run = run("jdbc-connection-lifecycle");
        assertThat(evidence(run, "acquire", "Object returned by the DataSource")).contains("HikariProxyConnection");
        assertThat(evidence(run, "acquire", "Underlying driver connection")).contains("ConnectionImpl");
        assertThat(Long.parseLong(evidence(run, "acquire", "MySQL session id (connection_id())"))).isPositive();
        assertThat(evidence(run, "metadata", "Database product")).startsWith("MySQL 8");
        assertThat(evidence(run, "metadata", "Driver")).contains("Connector/J");
        assertThat(run.toString()).doesNotContain("jdbc:mysql").doesNotContain("password");
        assertThat(evidence(run, "close", "isClosed() on the object the application holds")).isEqualTo("true");
        assertThat(evidence(run, "close", "MySQL session still open after close() (processlist)")).isEqualTo("true");
        assertThat(evidence(run, "use-closed", "Outcome")).startsWith("refused");
        assertThat(step(run, "connection-is-a-session").get("provenance").asText()).isEqualTo("CONCEPTUAL");
        assertThat(labRows()).isZero();
    }

    @Test
    void driverManagerOpensAndEndsASessionEachTimeWhileThePoolKeepsItOpen() throws Exception {
        JsonNode run = run("jdbc-drivermanager-vs-datasource");
        assertThat(evidence(run, "drivermanager-close", "MySQL session still present after close() (processlist)")).isEqualTo("false");
        assertThat(evidence(run, "drivermanager-second", "Same session as the first call")).isEqualTo("false");
        assertThat(evidence(run, "datasource-first", "Object returned")).contains("HikariProxyConnection");
        assertThat(evidence(run, "datasource-close", "MySQL session still present after close() (processlist)")).isEqualTo("true");
        assertThat(step(run, "comparison").get("provenance").asText()).isEqualTo("DERIVED");
    }

    @Test
    void theDriverIsFoundThroughTheServiceProviderMechanism() throws Exception {
        JsonNode run = run("jdbc-driver-discovery");
        assertThat(evidence(run, "registered-drivers", "Registered java.sql.Driver implementations")).contains("com.mysql.cj.jdbc.Driver");
        assertThat(evidence(run, "choose-driver", "Driver chosen for jdbc:mysql:")).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(evidence(run, "choose-driver", "acceptsURL(\"jdbc:postgresql:...\") on the MySQL driver")).isEqualTo("false");
        assertThat(evidence(run, "choose-driver", "DriverManager.getDriver(\"jdbc:postgresql:...\")")).startsWith("no suitable driver");
    }

    // ---------------------------------------------------------------------------------------------- statements

    @Test
    void aStatementReturnsACursorAndMySqlCountsOneSelect() throws Exception {
        JsonNode run = run("jdbc-statement-select");
        assertThat(evidence(run, "create-and-execute", "Columns in the ResultSet")).isEqualTo("2: id, name");
        assertThat(evidence(run, "iterate", "Rows read")).isEqualTo("3");
        assertThat(evidence(run, "iterate", "next() after the last row")).isEqualTo("false");
        assertThat(evidence(run, "close", "ResultSet.isClosed()")).isEqualTo("true");
        assertThat(evidence(run, "close", "Statement.isClosed()")).isEqualTo("true");
        assertThat(evidence(run, "close", "MySQL session counters (change during the experiment)")).isEqualTo("Com_select +1");
        var statements = sql(step(run, "create-and-execute"));
        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).contains("jdbc_lab_customers").contains("<run key>");
        assertThat(triggers(step(run, "create-and-execute")).get(0)).contains("Statement.executeQuery returned a ResultSet");
        assertThat(labRows()).isZero();
    }

    @Test
    void aPreparedStatementKeepsPlaceholdersAndIsReused() throws Exception {
        JsonNode run = run("jdbc-prepared-select");
        assertThat(evidence(run, "prepare", "Placeholders in the statement")).isEqualTo("2");
        assertThat(evidence(run, "first-execution", "Rows returned")).isEqualTo("1");
        assertThat(evidence(run, "second-execution", "Rows returned")).isEqualTo("1");
        var first = sql(step(run, "first-execution"));
        var second = sql(step(run, "second-execution"));
        assertThat(first).hasSize(1).first().asString().contains("run_key = ? and name = ?");
        assertThat(second).isEqualTo(first);
        assertThat(run.get("sql").toString()).doesNotContain("Ada").doesNotContain("Grace");
        assertThat(step(run, "client-side-preparation").get("provenance").asText()).isEqualTo("CONCEPTUAL");
    }

    @Test
    void aBoundQuoteAndOrTextIsDataAndMatchesNothing() throws Exception {
        JsonNode run = run("jdbc-sql-injection");
        assertThat(step(run, "unsafe-construction").get("provenance").asText()).isEqualTo("CONCEPTUAL");
        assertThat(step(run, "unsafe-construction").get("sql")).isEmpty(); // the unsafe text is never executed
        assertThat(step(run, "unsafe-construction").get("explanation").asText()).contains("does NOT run");
        assertThat(evidence(run, "safe-binding", "Rows returned when quote-and-OR text is bound as the name")).isEqualTo("0");
        assertThat(evidence(run, "safe-binding", "Rows returned for the ordinary name 'Ada'")).isEqualTo("1");
        assertThat(sql(step(run, "safe-binding"))).hasSize(2).allMatch(s -> s.contains("run_key = ? and name = ?") && !s.contains("'"));
        assertThat(run.get("sql").toString()).doesNotContain("OR '1'");
    }

    @Test
    void theThreeExecuteMethodsReturnDifferentThingsAndTheWrongOneIsRefused() throws Exception {
        JsonNode run = run("jdbc-execute-methods");
        assertThat(evidence(run, "execute-query", "Return value")).isEqualTo("a ResultSet with 3 rows");
        assertThat(evidence(run, "execute-update", "Return value")).isEqualTo("3 (rows affected)");
        assertThat(evidence(run, "execute-generic", "execute(SELECT)")).contains("returned true").contains("getResultSet() is a ResultSet").contains("getUpdateCount() = -1");
        assertThat(evidence(run, "execute-generic", "execute(UPDATE)")).contains("returned false").contains("getResultSet() is null").contains("getUpdateCount() = 3");
        assertThat(evidence(run, "wrong-method", "executeQuery(UPDATE)")).startsWith("refused");
        assertThat(evidence(run, "wrong-method", "executeUpdate(SELECT)")).startsWith("refused");
    }

    @Test
    void closingAStatementClosesItsResultSetAndAClosedConnectionCannotBeUsed() throws Exception {
        JsonNode run = run("jdbc-resource-closing");
        assertThat(evidence(run, "open-resources", "ResultSet.isClosed()")).isEqualTo("false");
        assertThat(evidence(run, "close-statement", "ResultSet.isClosed()")).isEqualTo("true");
        assertThat(evidence(run, "close-statement", "Connection.isClosed()")).isEqualTo("false");
        assertThat(evidence(run, "close-connection", "Connection.isClosed()")).isEqualTo("true");
        assertThat(evidence(run, "close-connection", "Using the closed Statement")).startsWith("refused");
    }

    // -------------------------------------------------------------------------------------------------- CRUD

    @Test
    void crudReturnsRowCountsAndGeneratedKeysAndMySqlCountsTheStatements() throws Exception {
        JsonNode run = run("jdbc-crud-single");
        assertThat(evidence(run, "insert", "Rows affected")).isEqualTo("1");
        assertThat(evidence(run, "insert", "Generated key received")).isEqualTo("true");
        assertThat(evidence(run, "state-after-insert", "jdbc_lab_customers")).contains("name=Ada");
        assertThat(evidence(run, "select-one", "Row found")).isEqualTo("true");
        assertThat(evidence(run, "update", "Rows affected for an existing id")).isEqualTo("1");
        assertThat(evidence(run, "update", "Rows affected for an id that does not exist")).isEqualTo("0");
        assertThat(evidence(run, "delete", "Rows affected the first time")).isEqualTo("1");
        assertThat(evidence(run, "delete", "Rows affected the second time")).isEqualTo("0");
        assertThat(evidence(run, "state-after-delete", "jdbc_lab_customers")).isEqualTo("(no rows)");
        assertThat(evidence(run, "state-after-delete", "MySQL session counters (change during the experiment)"))
                .contains("Com_select +1").contains("Com_insert +1").contains("Com_update +2").contains("Com_delete +2");
        assertThat(labRows()).isZero();
    }

    @Test
    void severalRowsAreReadByHandAndCountIsOneRow() throws Exception {
        JsonNode run = run("jdbc-read-many");
        assertThat(evidence(run, "select-all", "Rows read")).isEqualTo("5");
        assertThat(evidence(run, "select-all", "Names in price order")).isEqualTo("Cable, Mouse, Keyboard, Dock, Monitor");
        assertThat(evidence(run, "select-all", "Sum of the price column (BigDecimal)")).isEqualTo("362.64");
        assertThat(evidence(run, "select-count", "count(*)")).isEqualTo("5");
    }

    @Test
    void aBatchIsInvisibleUntilCommitAndMySqlStillReceivesFiveInserts() throws Exception {
        JsonNode run = run("jdbc-batch-insert");
        assertThat(evidence(run, "add-batch", "Update counts returned by executeBatch")).isEqualTo("[1, 1, 1, 1, 1]");
        assertThat(triggers(step(run, "add-batch")).get(0)).contains("sent 5 statements");
        assertThat(evidence(run, "before-commit", "Rows visible to another connection")).isEqualTo("0");
        assertThat(evidence(run, "server-confirmation", "Rows visible to another connection")).isEqualTo("5");
        assertThat(evidence(run, "server-confirmation", "MySQL session counters (change during the experiment)")).contains("Com_insert +5").contains("Com_commit +1");
        assertThat(evidence(run, "batching-note", "Statements received by MySQL")).isEqualTo("5");
        assertThat(labRows()).isZero();
    }
}
