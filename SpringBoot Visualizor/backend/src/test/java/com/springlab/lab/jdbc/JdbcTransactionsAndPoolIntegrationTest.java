package com.springlab.lab.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Topics 4 and 5 of the JDBC lab: transactions observed with two connections, and a dedicated HikariCP pool. */
class JdbcTransactionsAndPoolIntegrationTest extends AbstractJdbcLabTest {

    private static final String ORIGINAL = "Alice=100.00, Bob=50.00";
    private static final String TRANSFERRED = "Alice=70.00, Bob=80.00";

    @Test
    void autoCommitCommitsEachStatementAndAnotherConnectionSeesItAtOnce() throws Exception {
        JsonNode run = run("jdbc-autocommit");
        assertThat(evidence(run, "default", "Connection A autoCommit")).isEqualTo("true");
        assertThat(evidence(run, "update", "Transaction state of connection A")).startsWith("AUTO-COMMIT");
        assertThat(sql(step(run, "update"))).hasSize(2); // the UPDATE and the SELECT that reads it back on the same connection
        assertThat(evidence(run, "other-connection", "Seen by connection B")).isEqualTo("Alice=90.00, Bob=50.00");
        assertThat(triggers(step(run, "update")).get(0)).startsWith("A: PreparedStatement.executeUpdate affected 1 row(s)");
        assertThat(labRows()).isZero();
    }

    @Test
    void changesAreInvisibleToAnotherConnectionUntilCommit() throws Exception {
        JsonNode run = run("jdbc-commit");
        assertThat(evidence(run, "begin", "Connection A autoCommit")).isEqualTo("false");
        assertThat(evidence(run, "begin", "Transaction state of connection A")).startsWith("OPEN");
        assertThat(sql(step(run, "operations")).stream().filter(s -> s.startsWith("update") || s.startsWith("insert")).count()).isEqualTo(4);
        assertThat(evidence(run, "operations", "Seen by connection A (inside its own transaction)")).isEqualTo(TRANSFERRED);
        assertThat(evidence(run, "operations", "Transaction state of connection A")).startsWith("OPEN");
        assertThat(evidence(run, "other-before", "Seen by connection B")).isEqualTo(ORIGINAL);
        assertThat(evidence(run, "other-before", "Ledger rows visible to B")).isEqualTo("0");
        assertThat(evidence(run, "commit", "Transaction state of connection A")).startsWith("COMMITTED");
        assertThat(evidence(run, "commit", "MySQL session counters (change during the experiment)")).contains("Com_commit +1").contains("Com_update +2").contains("Com_insert +2");
        assertThat(step(run, "commit").get("evidence").toString()).contains("A: commit()");
        assertThat(evidence(run, "other-after", "Seen by connection B")).isEqualTo(TRANSFERRED);
        assertThat(evidence(run, "other-after", "Ledger rows now")).isEqualTo("2");
        assertThat(labRows()).isZero();
    }

    @Test
    void rollbackDiscardsEveryStatementIncludingTheOnesThatSucceeded() throws Exception {
        JsonNode run = run("jdbc-rollback");
        assertThat(evidence(run, "operations", "Seen by connection A (inside its own transaction)")).isEqualTo(TRANSFERRED);
        assertThat(evidence(run, "other-before", "Seen by connection B")).isEqualTo(ORIGINAL);
        assertThat(evidence(run, "rollback", "Transaction state of connection A")).startsWith("ROLLED BACK");
        assertThat(evidence(run, "rollback", "MySQL session counters (change during the experiment)")).contains("Com_rollback +1").doesNotContain("Com_commit");
        assertThat(step(run, "rollback").get("evidence").toString()).contains("A: rollback()");
        assertThat(evidence(run, "other-after", "Seen by connection B")).isEqualTo(ORIGINAL);
        assertThat(evidence(run, "other-after", "Seen by connection A")).isEqualTo(ORIGINAL);
        assertThat(evidence(run, "other-after", "Ledger rows now")).isEqualTo("0");
    }

    @Test
    void aFailingStatementDoesNotUndoTheEarlierOneUntilTheCodeRollsBack() throws Exception {
        JsonNode run = run("jdbc-rollback-on-failure");
        assertThat(evidence(run, "failing-operation", "Second statement")).startsWith("refused").contains("1452");
        assertThat(evidence(run, "failing-operation", "Seen by connection A right after the failure")).isEqualTo("Alice=70.00, Bob=50.00");
        assertThat(evidence(run, "failing-operation", "Transaction state of connection A")).contains("still pending");
        assertThat(evidence(run, "rollback", "Seen by connection A")).isEqualTo(ORIGINAL);
        assertThat(evidence(run, "rollback", "Seen by connection B")).isEqualTo(ORIGINAL);
    }

    @Test
    void aSavepointUndoesOnlyTheLaterWorkAndTheTransactionStaysOpen() throws Exception {
        JsonNode run = run("jdbc-savepoint");
        assertThat(evidence(run, "second-operation", "Seen by connection A")).isEqualTo("Alice=90.00, Bob=60.00");
        assertThat(evidence(run, "rollback-to-savepoint", "Seen by connection A")).isEqualTo("Alice=90.00, Bob=50.00");
        assertThat(evidence(run, "rollback-to-savepoint", "Seen by connection B")).isEqualTo(ORIGINAL);
        assertThat(evidence(run, "rollback-to-savepoint", "Transaction state of connection A")).contains("still open");
        assertThat(evidence(run, "commit", "Seen by connection B")).isEqualTo("Alice=90.00, Bob=50.00");
        assertThat(evidence(run, "commit", "MySQL session counters (change during the experiment)"))
                .contains("Com_savepoint +1").contains("Com_rollback_to_savepoint +1").contains("Com_commit +1");
    }

    @Test
    void isolationLevelsChangeWhatASecondReadSees() throws Exception {
        JsonNode run = run("jdbc-isolation");
        assertThat(evidence(run, "setup", "MySQL default isolation for a new connection")).isEqualTo("REPEATABLE-READ");

        assertThat(evidence(run, "repeatable-read", "Isolation level")).contains("REPEATABLE-READ");
        assertThat(evidence(run, "repeatable-read", "A reads first")).isEqualTo("100.00");
        assertThat(evidence(run, "repeatable-read", "A reads again, still in the same transaction")).isEqualTo("100.00");
        assertThat(evidence(run, "repeatable-read", "A reads after finishing its transaction")).isEqualTo("60.00");
        assertThat(evidence(run, "repeatable-read", "Observed")).contains("stable snapshot");

        assertThat(evidence(run, "read-committed", "Isolation level")).contains("READ-COMMITTED");
        assertThat(evidence(run, "read-committed", "A reads first")).isEqualTo("60.00");
        assertThat(evidence(run, "read-committed", "A reads again, still in the same transaction")).isEqualTo("40.00");
        assertThat(evidence(run, "read-committed", "Observed")).contains("non-repeatable read");

        assertThat(evidence(run, "read-uncommitted", "Isolation level")).contains("READ-UNCOMMITTED");
        assertThat(evidence(run, "read-uncommitted", "A reads first")).isEqualTo("40.00");
        assertThat(evidence(run, "read-uncommitted", "A reads again, still in the same transaction")).isEqualTo("20.00");
        assertThat(evidence(run, "read-uncommitted", "A reads after finishing its transaction")).isEqualTo("40.00");
        assertThat(evidence(run, "read-uncommitted", "Observed")).contains("dirty read");
        assertThat(step(run, "summary").get("provenance").asText()).isEqualTo("DERIVED");
        assertThat(labRows()).isZero();
    }

    // ------------------------------------------------------------------------------------------------ pooling

    @Test
    void closeReturnsAPooledConnectionAndTheNextBorrowerGetsTheSameSession() throws Exception {
        JsonNode run = run("pool-acquire-release");
        assertThat(evidence(run, "pool-created", "Lab pool")).contains("(maximum 1)");
        assertThat(evidence(run, "pool-created", "Application pool (read-only)")).contains("maximum");
        assertThat(evidence(run, "acquire", "Object returned")).contains("HikariProxyConnection");
        assertThat(evidence(run, "acquire", "Lab pool")).contains("active=1").contains("idle=0");
        assertThat(evidence(run, "release", "Lab pool")).contains("active=0").contains("idle=1");
        assertThat(evidence(run, "release", "MySQL session still present after close()")).isEqualTo("true");
        assertThat(evidence(run, "reacquire", "Same physical session as the first call")).isEqualTo("true");
        assertThat(run.toString()).doesNotContain("jdbc:mysql").doesNotContain("password");
    }

    @Test
    void severalSimultaneousBorrowersGetDifferentSessions() throws Exception {
        JsonNode run = run("pool-multiple");
        assertThat(evidence(run, "acquire-three", "All three are different sessions")).isEqualTo("true");
        assertThat(evidence(run, "acquire-three", "Lab pool")).contains("active=3").contains("total=3").contains("idle=0");
        assertThat(evidence(run, "release-three", "Lab pool")).contains("active=0").contains("idle=3");
    }

    @Test
    void anExhaustedPoolMakesTheCallerWaitAndThenFailsWithATimeout() throws Exception {
        JsonNode run = run("pool-exhaustion");
        assertThat(evidence(run, "borrow-all", "Lab pool")).contains("active=2").contains("idle=0");
        assertThat(evidence(run, "third-request", "Outcome")).startsWith("refused").contains("SQLTransientConnectionException");
        assertThat(Long.parseLong(evidence(run, "third-request", "Waited about (ms)"))).isGreaterThanOrEqualTo(450);
        assertThat(evidence(run, "third-request", "Configured connectionTimeout (ms)")).isEqualTo("500");
        assertThat(evidence(run, "release-one", "Request served")).isEqualTo("true");
        assertThat(evidence(run, "release-one", "It reused the session that was returned")).isEqualTo("true");
        assertThat(step(run, "real-life").get("provenance").asText()).isEqualTo("CONCEPTUAL");
    }
}
