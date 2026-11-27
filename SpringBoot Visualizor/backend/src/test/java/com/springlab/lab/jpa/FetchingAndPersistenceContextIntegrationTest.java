package com.springlab.lab.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The fetching, N+1 and persistence-context experiments, run against the local MySQL test schema. */
class FetchingAndPersistenceContextIntegrationTest extends AbstractJpaLabTest {

    @Test
    void aLazyCollectionThatIsNeverTouchedCostsNoEmployeeStatement() throws Exception {
        JsonNode run = run("lazy-not-accessed");
        var statements = sql(step(run, "find-department"));
        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).contains("from jpa_lab_department").doesNotContain("jpa_lab_employee");
        assertThat(evidence(run, "find-department", "employees loaded (PersistenceUnitUtil.isLoaded)")).isEqualTo("false");
        assertThat(evidence(run, "find-department", "Collection implementation")).contains("Persistent");
        assertThat(evidence(run, "no-employee-query", "Statements in the step above")).isEqualTo("1");
    }

    @Test
    void touchingTheCollectionInsideTheTransactionRunsOneMoreSelect() throws Exception {
        JsonNode run = run("lazy-in-transaction");
        assertThat(sql(step(run, "find-department"))).hasSize(1);
        var statements = sql(step(run, "touch-employees"));
        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).contains("from jpa_lab_employee").contains("department_id");
        assertThat(evidence(run, "touch-employees", "employees")).isEqualTo("3");
        assertThat(evidence(run, "touch-employees", "employees loaded now")).isEqualTo("true");
    }

    @Test
    void touchingTheCollectionAfterTheSessionClosedThrowsLazyInitializationException() throws Exception {
        JsonNode run = run("lazy-after-close");
        assertThat(evidence(run, "load-and-close", "em.contains(department) after the transaction")).isEqualTo("false");
        assertThat(evidence(run, "touch-after-close", "Outcome")).contains("LazyInitializationException");
        assertThat(sql(step(run, "touch-after-close"))).isEmpty();
        assertThat(evidence(run, "open-in-view", "spring.jpa.open-in-view (this application)")).isEqualTo("false");
    }

    @Test
    void theRealDefaultsAreEagerForManyToOneAndLazyForOneToMany() throws Exception {
        JsonNode run = run("fetch-defaults");
        assertThat(evidence(run, "annotations", "Task.project (@ManyToOne)")).isEqualTo("EAGER");
        assertThat(evidence(run, "annotations", "Employee.department (@ManyToOne, fetch written)")).isEqualTo("LAZY");
        assertThat(evidence(run, "annotations", "Department.employees (@OneToMany)")).isEqualTo("LAZY");
        var task = sql(step(run, "find-task"));
        assertThat(task).hasSize(1);
        assertThat(task.get(0)).contains("join jpa_lab_project");
        assertThat(evidence(run, "find-task", "project loaded")).isEqualTo("true");
        var employee = sql(step(run, "find-employee"));
        assertThat(employee).hasSize(1);
        assertThat(employee.get(0)).doesNotContain("jpa_lab_department");
        assertThat(evidence(run, "find-employee", "department loaded")).isEqualTo("false");
        assertThat(evidence(run, "find-employee", "Proxy class (simple name)")).startsWith("Department$");
    }

    @Test
    void eagerFetchingDoesNotAvoidNPlusOne() throws Exception {
        JsonNode run = run("eager-n-plus-one");
        int distinctProjects = Integer.parseInt(evidence(run, "count", "Distinct projects among the tasks"));
        assertThat(distinctProjects).isEqualTo(3);
        var statements = sql(step(run, "load-tasks"));
        assertThat(statements).hasSize(1 + distinctProjects);
        assertThat(statements.get(0)).contains("from jpa_lab_task");
        assertThat(statements.subList(1, statements.size())).allMatch(s -> s.contains("from jpa_lab_project"));
        assertThat(evidence(run, "count", "Statements prepared")).isEqualTo(String.valueOf(1 + distinctProjects));
    }

    @Test
    void nPlusOneRunsOneStatementPerDepartmentPlusTheInitialQuery() throws Exception {
        JsonNode run = run("n-plus-one");
        assertThat(sql(step(run, "plain-load"))).hasSize(1);
        assertThat(sql(step(run, "plain-touch"))).hasSize(6).allMatch(s -> s.contains("from jpa_lab_employee"));
        assertThat(sql(step(run, "small-load"))).hasSize(1);
        assertThat(sql(step(run, "small-touch"))).hasSize(3);
        assertThat(evidence(run, "n-plus-one-summary", "6 departments")).isEqualTo("7 statements");
        assertThat(evidence(run, "n-plus-one-summary", "3 departments")).isEqualTo("4 statements");
        assertThat(evidence(run, "n-plus-one-summary", "Statements per extra department")).isEqualTo("1.0");
        assertThat(run.get("sqlCount").asInt()).isEqualTo(7 + 4);
    }

    @Test
    void joinFetchLoadsDepartmentsAndEmployeesInOneStatement() throws Exception {
        JsonNode run = run("join-fetch");
        var load = sql(step(run, "remedy-load"));
        assertThat(load).hasSize(1);
        assertThat(load.get(0)).contains("join jpa_lab_employee");
        assertThat(sql(step(run, "remedy-touch"))).isEmpty();
        assertThat(evidence(run, "before-after", "Before (plain load, then use)")).isEqualTo("7 statements");
        assertThat(evidence(run, "before-after", "After (JOIN FETCH)")).isEqualTo("1 statement(s)");
        assertThat(evidence(run, "before-after", "Statements saved")).isEqualTo("6");
        assertThat(evidence(run, "remedy-touch", "Employees read")).isEqualTo("18");
    }

    @Test
    void anEntityGraphFetchesTheEmployeesWithTheDepartmentsInOneStatement() throws Exception {
        JsonNode run = run("entity-graph");
        var load = sql(step(run, "remedy-load"));
        assertThat(load).hasSize(1);
        assertThat(load.get(0)).contains("jpa_lab_employee");
        assertThat(sql(step(run, "remedy-touch"))).isEmpty();
        assertThat(evidence(run, "before-after", "After (EntityGraph)")).isEqualTo("1 statement(s)");
        assertThat(evidence(run, "before-after", "Statements saved")).isEqualTo("6");
    }

    @Test
    void fetchingTwoCollectionsMultipliesTheRowsTheDatabaseReturns() throws Exception {
        JsonNode run = run("two-collections-cartesian");
        var load = sql(step(run, "fetch-both"));
        assertThat(load).hasSize(1);
        assertThat(load.get(0)).contains("jpa_lab_employee").contains("jpa_lab_room");
        assertThat(evidence(run, "fetch-both", "Distinct employee objects")).isEqualTo("6");
        assertThat(evidence(run, "fetch-both", "Entries in the employees Lists (a bag keeps duplicates)")).isEqualTo("24");
        assertThat(evidence(run, "fetch-both", "Rooms (a Set removes duplicates)")).isEqualTo("8");
        assertThat(evidence(run, "row-multiplication", "Rows returned by the equivalent join")).isEqualTo("24"); // 2 x (3 x 4)
        assertThat(evidence(run, "row-multiplication", "Objects needed (departments + employees + rooms)")).isEqualTo("16");
        assertThat(evidence(run, "row-multiplication", "Statements prepared by Hibernate")).isEqualTo("1");
    }

    @Test
    void anEntityMovesThroughTheFourStatesAndIdentityInsertsImmediately() throws Exception {
        JsonNode run = run("entity-states");
        assertThat(evidence(run, "new", "em.contains(employee)")).isEqualTo("false");
        assertThat(evidence(run, "new", "id")).isEqualTo("null");
        assertThat(sql(step(run, "new"))).isEmpty();
        assertThat(sql(step(run, "persist"))).hasSize(1);
        assertThat(sql(step(run, "persist")).get(0)).startsWith("insert into jpa_lab_employee"); // IDENTITY
        assertThat(evidence(run, "persist", "em.contains(employee)")).isEqualTo("true");
        assertThat(sql(step(run, "detach"))).isEmpty();
        assertThat(evidence(run, "detach", "em.contains(employee)")).isEqualTo("false");
        assertThat(evidence(run, "detach", "id")).isNotEqualTo("null");
        assertThat(evidence(run, "merge", "returned object is the same instance")).isEqualTo("false");
        assertThat(evidence(run, "merge", "em.contains(returned copy)")).isEqualTo("true");
        assertThat(sql(step(run, "merge"))).hasSize(1);
        assertThat(sql(step(run, "merge")).get(0)).startsWith("select");
        assertThat(sql(step(run, "remove"))).isEmpty(); // the DELETE waits for the flush
        assertThat(count(sql(step(run, "flush")), "delete from jpa_lab_employee")).isEqualTo(1);
        assertThat(labRows()).isZero();
    }

    @Test
    void dirtyCheckingUpdatesAtFlushAndOtherTransactionsSeeItOnlyAfterCommit() throws Exception {
        JsonNode run = run("dirty-checking");
        assertThat(sql(step(run, "change"))).isEmpty();
        var flush = sql(step(run, "flush"));
        assertThat(flush).hasSize(1);
        assertThat(flush.get(0)).startsWith("update jpa_lab_employee");
        assertThat(evidence(run, "other-connection-before-commit", "Own transaction sees")).isEqualTo("name=Ada Lovelace");
        assertThat(evidence(run, "other-connection-before-commit", "Another transaction sees")).isEqualTo("name=Ada");
        assertThat(evidence(run, "other-connection-after-commit", "Another transaction sees")).isEqualTo("name=Ada Lovelace");
    }

    @Test
    void theFirstLevelCacheAnswersTheSecondFindWithoutSql() throws Exception {
        JsonNode run = run("first-level-cache");
        assertThat(sql(step(run, "find-1"))).hasSize(1);
        assertThat(sql(step(run, "find-2"))).isEmpty();
        assertThat(evidence(run, "find-2", "same instance as the first call")).isEqualTo("true");
        assertThat(sql(step(run, "query"))).hasSize(1);
        assertThat(evidence(run, "query", "same instance as the first call")).isEqualTo("true");
        assertThat(sql(step(run, "clear-find"))).hasSize(1);
        assertThat(evidence(run, "clear-find", "same instance as the first call")).isEqualTo("false");
    }
}
