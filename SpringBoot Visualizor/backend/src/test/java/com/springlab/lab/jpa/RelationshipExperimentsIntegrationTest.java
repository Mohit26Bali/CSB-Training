package com.springlab.lab.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the one-to-one, owning-side, many-to-many and cascade experiments against the local MySQL test schema. */
class RelationshipExperimentsIntegrationTest extends AbstractJpaLabTest {

    @Test
    void oneToOneForeignKeyLivesInTheOwningTableAndIsUnique() throws Exception {
        JsonNode run = run("one-to-one-fk");
        assertThat(sql(step(run, "persist-account")).get(0)).startsWith("insert into jpa_lab_account");
        assertThat(sql(step(run, "persist-account"))).hasSize(1); // IDENTITY: the INSERT runs at persist()
        String profileInsert = sql(step(run, "persist-profile")).get(0);
        assertThat(profileInsert).startsWith("insert into jpa_lab_profile").contains("account_id");
        String accountId = evidence(run, "persist-account", "Account id assigned by MySQL");
        assertThat(evidence(run, "database-state", "jpa_lab_profile")).contains("account_id=" + accountId);
        assertThat(evidence(run, "database-state", "Constraints on jpa_lab_profile")).contains("uk_jpa_lab_profile_account")
                .contains("UNIQUE").contains("FOREIGN KEY");
        assertThat(evidence(run, "second-profile", "Outcome")).startsWith("refused").contains("1062");
        assertThat(labRows()).isZero();
    }

    @Test
    void settingOnlyTheOwningSideWritesTheForeignKeyButLeavesTheInverseCollectionStale() throws Exception {
        JsonNode run = run("owning-owner-only");
        assertThat(evidence(run, "database-state", "department_id is NULL")).isEqualTo("false");
        assertThat(evidence(run, "database-state", "jpa_lab_employee")).contains("department_id=");
        assertThat(evidence(run, "database-state", "department.getEmployees().size() in memory")).isEqualTo("0");
        assertThat(sql(step(run, "persist-employee")).get(0)).startsWith("insert into jpa_lab_employee");
        assertThat(labRows()).isZero();
    }

    @Test
    void changingOnlyTheInverseCollectionNeverWritesTheForeignKey() throws Exception {
        JsonNode run = run("owning-inverse-only");
        assertThat(evidence(run, "change-association", "department.getEmployees() contains the employee")).isEqualTo("true");
        assertThat(evidence(run, "change-association", "employee.getDepartment() is set")).isEqualTo("false");
        assertThat(evidence(run, "database-state", "department_id is NULL")).isEqualTo("true");
        assertThat(evidence(run, "database-state", "jpa_lab_employee")).contains("department_id=NULL");
        assertThat(evidence(run, "database-state", "department.getEmployees().size() in memory")).isEqualTo("1");
        assertThat(evidence(run, "conclusion", "Foreign key written")).isEqualTo("false");
    }

    @Test
    void theHelperMethodKeepsBothSidesAndTheForeignKeyConsistent() throws Exception {
        JsonNode run = run("owning-helper-both");
        assertThat(evidence(run, "database-state", "department_id is NULL")).isEqualTo("false");
        assertThat(evidence(run, "database-state", "department.getEmployees().size() in memory")).isEqualTo("1");
        assertThat(evidence(run, "conclusion", "Foreign key written")).isEqualTo("true");
    }

    @Test
    void theOwningCollectionWritesJoinRowsAndWithdrawingDeletesOnlyTheJoinRow() throws Exception {
        JsonNode run = run("m2m-join-table");
        assertThat(count(sql(step(run, "persist-entities")), "insert into jpa_lab_")).isEqualTo(3);
        assertThat(sql(step(run, "enroll"))).isEmpty(); // collection changes wait for the flush
        assertThat(count(sql(step(run, "flush-join-rows")), "insert into jpa_lab_enrollment")).isEqualTo(2);
        assertThat(count(sql(step(run, "withdraw")), "delete from jpa_lab_enrollment")).isEqualTo(1);
        assertThat(sql(step(run, "withdraw")).stream().noneMatch(s -> s.contains("jpa_lab_course"))).isTrue();
        assertThat(evidence(run, "after-withdraw", "jpa_lab_course (rows)")).isEqualTo("2");
        assertThat(evidence(run, "after-withdraw", "jpa_lab_enrollment (rows)")).isEqualTo("1");
        assertThat(labRows()).isZero();
    }

    @Test
    void theInverseSideOfAManyToManyWritesNothing() throws Exception {
        JsonNode run = run("m2m-inverse-only");
        assertThat(sql(step(run, "inverse-only")).stream().noneMatch(s -> s.contains("jpa_lab_enrollment"))).isTrue();
        assertThat(evidence(run, "no-join-row", "jpa_lab_enrollment (rows)")).isEqualTo("0");
        assertThat(count(sql(step(run, "owner-side")), "insert into jpa_lab_enrollment")).isEqualTo(1);
        assertThat(evidence(run, "join-row", "jpa_lab_enrollment (rows)")).isEqualTo("1");
    }

    @Test
    void persistWithoutCascadeDoesNotReachTheEmployee() throws Exception {
        JsonNode run = run("cascade-persist-none");
        assertThat(count(sql(step(run, "persist-parent")), "insert into jpa_lab_department")).isEqualTo(1);
        assertThat(sql(step(run, "persist-parent")).stream().noneMatch(s -> s.contains("jpa_lab_employee"))).isTrue();
        assertThat(evidence(run, "persist-parent", "em.contains(employee)")).isEqualTo("false");
        assertThat(evidence(run, "persist-parent", "Employee id")).isEqualTo("null");
        assertThat(evidence(run, "flush", "Outcome")).isEqualTo("flush succeeded");
        assertThat(sql(step(run, "flush")).stream().noneMatch(s -> s.startsWith("insert into jpa_lab_employee"))).isTrue();
    }

    @Test
    void persistWithCascadeAllInsertsTheProjectAndItsTasks() throws Exception {
        JsonNode run = run("cascade-persist-all");
        var statements = sql(step(run, "persist-parent"));
        assertThat(statements.get(0)).startsWith("insert into jpa_lab_project");
        assertThat(count(statements, "insert into jpa_lab_task")).isEqualTo(2);
        assertThat(evidence(run, "database-state", "jpa_lab_task")).contains("project_id=");
        assertThat(labRows()).isZero();
    }

    @Test
    void updatingAManagedParentProducesOneUpdateAndNoChildStatements() throws Exception {
        JsonNode run = run("cascade-update-parent");
        var statements = sql(step(run, "rename"));
        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).startsWith("update jpa_lab_project");
    }

    @Test
    void cascadeAllDoesNotDeleteAChildRemovedFromTheCollection() throws Exception {
        JsonNode run = run("remove-child-no-orphan");
        assertThat(sql(step(run, "remove-from-collection"))).isEmpty();
        assertThat(evidence(run, "remove-from-collection", "project.getTasks().size() in memory")).isEqualTo("1");
        assertThat(evidence(run, "database-state", "jpa_lab_task")).contains("project_id=").doesNotContain("NULL");
        assertThat(count(java.util.List.of(evidence(run, "database-state", "jpa_lab_task")), "title=")).isEqualTo(1); // one string, two rows
        assertThat(evidence(run, "database-state", "jpa_lab_task").split("\\|")).hasSize(2);
    }

    @Test
    void orphanRemovalDeletesAChildRemovedFromTheCollection() throws Exception {
        JsonNode run = run("remove-child-orphan");
        assertThat(count(sql(step(run, "remove-from-collection")), "delete from jpa_lab_member")).isEqualTo(1);
        assertThat(evidence(run, "database-state", "jpa_lab_member").split("\\|")).hasSize(1);
    }

    @Test
    void removingAParentWithChildrenIsRefusedByMySqlWhenThereIsNoCascade() throws Exception {
        JsonNode run = run("remove-parent-no-cascade");
        assertThat(count(sql(step(run, "remove-parent")), "delete from jpa_lab_department")).isEqualTo(1);
        assertThat(evidence(run, "remove-parent", "Outcome")).startsWith("refused").contains("1451");
        assertThat(evidence(run, "database-state", "jpa_lab_department (rows)")).isEqualTo("1");
        assertThat(evidence(run, "database-state", "jpa_lab_employee (rows)")).isEqualTo("2");
        assertThat(labRows()).isZero();
    }

    @Test
    void cascadeRemoveDeletesTheChildrenBeforeTheParent() throws Exception {
        JsonNode run = run("remove-parent-cascade-all");
        var statements = sql(step(run, "remove-parent"));
        assertThat(count(statements, "delete from jpa_lab_member")).isEqualTo(2);
        int lastMember = statements.lastIndexOf(statements.stream().filter(s -> s.startsWith("delete from jpa_lab_member")).reduce((a, b) -> b).orElseThrow());
        int team = statements.indexOf(statements.stream().filter(s -> s.startsWith("delete from jpa_lab_team")).findFirst().orElseThrow());
        assertThat(lastMember).isLessThan(team);
        assertThat(evidence(run, "database-state", "jpa_lab_member (rows)")).isEqualTo("0");
    }
}
