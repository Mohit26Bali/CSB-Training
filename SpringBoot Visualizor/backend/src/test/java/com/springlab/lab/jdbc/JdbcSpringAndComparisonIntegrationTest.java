package com.springlab.lab.jdbc;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Topics 6 and 7 of the JDBC lab: Spring JDBC, and JDBC versus Spring JDBC versus JPA/Hibernate. */
class JdbcSpringAndComparisonIntegrationTest extends AbstractJdbcLabTest {

    @Test
    void jdbcTemplateReturnsTheSameRowsAndMakesTheResourceCallsForYou() throws Exception {
        JsonNode run = run("spring-jdbc-query");
        assertThat(evidence(run, "manual-jdbc", "Rows mapped by hand")).isEqualTo("3");
        assertThat(evidence(run, "template-query", "Rows returned as Java objects")).isEqualTo("3");
        assertThat(evidence(run, "template-query", "Same rows as the plain JDBC version")).isEqualTo("true");
        String calls = evidence(run, "template-query", "Other JDBC calls observed");
        assertThat(calls).contains("template: connection obtained").contains("template: prepareStatement").contains("template: close()");
        assertThat(sql(step(run, "template-query"))).hasSize(1).first().asString().contains("run_key = ?");
        assertThat(evidence(run, "extractor", "Entries in the Map")).isEqualTo("3");
        assertThat(evidence(run, "scalar", "count(*)")).isEqualTo("3");
        assertThat(step(run, "what-it-did").get("provenance").asText()).isEqualTo("DERIVED");
        assertThat(labRows()).isZero();
    }

    @Test
    void namedParametersAreRewrittenToPlaceholdersBeforeTheDriverSeesThem() throws Exception {
        JsonNode run = run("spring-jdbc-named-update");
        assertThat(evidence(run, "named-update", "Rows affected")).isEqualTo("1");
        assertThat(sql(step(run, "named-update")).get(0)).contains("values (?, ?)").doesNotContain(":runkey").doesNotContain(":name");
        assertThat(sql(step(run, "repeated-name")).get(0)).contains("run_key = ? and (name = ? or name = ?)");
        assertThat(evidence(run, "repeated-name", "Rows matched")).isEqualTo("1");
        assertThat(evidence(run, "batch-update", "Update counts")).isEqualTo("[1, 1, 1, 1]");
        assertThat(evidence(run, "state", "Customers in the table for this run")).isEqualTo("5");
        assertThat(run.get("sql").toString()).doesNotContain("Customer 1");
    }

    @Test
    void mySqlErrorsBecomeSpringDataAccessExceptionsWithTheSqlExceptionAsCause() throws Exception {
        JsonNode run = run("spring-jdbc-exception-translation");
        assertThat(evidence(run, "duplicate", "Outcome")).contains("DuplicateKeyException extends DataIntegrityViolationException")
                .contains("NonTransientDataAccessException extends DataAccessException").contains("1062");
        assertThat(evidence(run, "foreign-key", "Outcome")).contains("DataIntegrityViolationException").contains("1452");
        assertThat(evidence(run, "bad-sql", "Outcome")).contains("BadSqlGrammarException").contains("1146");
    }

    @Test
    void springUsesOneConnectionAndMakesTheSameTransactionCallsAsHandWrittenJdbc() throws Exception {
        JsonNode run = run("spring-jdbc-transaction");
        assertThat(evidence(run, "commit", "A transaction was active inside the callback")).isEqualTo("true");
        String commitCalls = evidence(run, "commit", "Other JDBC calls observed");
        assertThat(commitCalls).contains("S: setAutoCommit(false)").contains("S: commit()");
        assertThat(commitCalls.split("S: connection obtained", -1)).hasSize(2); // exactly one connection for the whole callback
        assertThat(evidence(run, "commit", "Seen by another connection after the commit")).contains("balance=70.00").contains("balance=80.00");
        assertThat(sql(step(run, "commit"))).hasSize(2);
        String rollbackCalls = evidence(run, "rollback", "Other JDBC calls observed");
        assertThat(rollbackCalls).contains("S: rollback()").doesNotContain("S: commit()");
        assertThat(evidence(run, "rollback", "Seen by another connection after the rollback")).contains("balance=70.00").contains("balance=80.00");
        assertThat(evidence(run, "rollback", "Outcome")).contains("IllegalStateException");
        assertThat(labRows()).isZero();
    }

    @Test
    void theSameRowIsReadThroughThreePathsAndEachShowsItsOwnSql() throws Exception {
        JsonNode run = run("compare-read");
        for (String path : new String[] {"path-a-jdbc", "path-b-spring", "path-c-jpa"}) {
            assertThat(evidence(run, path, "Name read")).isEqualTo("Ada");
            assertThat(sql(step(run, path))).hasSize(1);
        }
        assertThat(sql(step(run, "path-a-jdbc")).get(0)).isEqualTo("select name from jdbc_lab_customers where id = ?");
        assertThat(sql(step(run, "path-b-spring")).get(0)).isEqualTo("select name from jdbc_lab_customers where id = ?");
        assertThat(sql(step(run, "path-c-jpa")).get(0)).contains("from jdbc_lab_customers").contains(".id=?").contains("run_key");
        assertThat(step(run, "path-a-jdbc").get("sql").get(0).get("phase").asText()).isEqualTo("JDBC");
        assertThat(step(run, "path-c-jpa").get("sql").get(0).get("phase").asText()).isEqualTo("HIBERNATE");
        assertThat(triggers(step(run, "path-c-jpa")).get(0)).contains("not proof of execution");
        assertThat(evidence(run, "path-c-jpa", "Result type")).isEqualTo("JdbcLabCustomer");
        assertThat(step(run, "same-row").get("provenance").asText()).isEqualTo("DERIVED");
    }

    @Test
    void equivalentInsertsThroughThreePathsProduceThreeRowsAndOneStatementEach() throws Exception {
        JsonNode run = run("compare-insert");
        for (String path : new String[] {"path-a-jdbc", "path-b-spring", "path-c-jpa"}) {
            assertThat(evidence(run, path, "Generated key")).isEqualTo("received");
            assertThat(sql(step(run, path)).stream().filter(s -> s.startsWith("insert into jdbc_lab_customers")).count()).isEqualTo(1);
        }
        String rows = evidence(run, "state", "jdbc_lab_customers");
        assertThat(rows).contains("Ada via JDBC").contains("Ada via Spring").contains("Ada via JPA");
        assertThat(step(run, "path-a-jdbc").get("evidence").toString()).contains("JDBC: commit()");
        assertThat(step(run, "path-b-spring").get("evidence").toString()).contains("Spring: commit()");
        assertThat(evidence(run, "transaction-handling", "Not observed")).isEqualTo("path C commit");
        assertThat(labRows()).isZero();
    }

    @Test
    void hibernateAndJdbcTemplateShareTheMySqlSessionInOneTransaction() throws Exception {
        JsonNode run = run("compare-same-session");
        assertThat(evidence(run, "template-session", "Same MySQL session as Hibernate")).isEqualTo("true");
        assertThat(evidence(run, "shared-uncommitted", "Rows seen by JdbcTemplate in the same transaction")).isEqualTo("1");
        assertThat(evidence(run, "shared-uncommitted", "Rows seen by a new connection")).isEqualTo("0");
        assertThat(evidence(run, "commit", "Rows seen by another connection after commit")).isEqualTo("1");
        assertThat(evidence(run, "hibernate-uses-jdbc", "Connection class Hibernate works with")).contains("HikariProxyConnection");
        assertThat(evidence(run, "hibernate-uses-jdbc", "Driver behind it")).contains("Connector/J");
        assertThat(step(run, "conclusion").get("provenance").asText()).isEqualTo("DERIVED");
        assertThat(labRows()).isZero();
    }
}
