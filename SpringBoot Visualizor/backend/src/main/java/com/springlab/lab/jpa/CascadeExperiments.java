package com.springlab.lab.jpa;

import com.springlab.lab.jpa.entity.Department;
import com.springlab.lab.jpa.entity.Employee;
import com.springlab.lab.jpa.entity.Member;
import com.springlab.lab.jpa.entity.Project;
import com.springlab.lab.jpa.entity.Task;
import com.springlab.lab.jpa.entity.Team;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;

import static com.springlab.lab.jpa.ExperimentRecorder.ev;
import static com.springlab.lab.jpa.ExperimentRecorder.nodes;

/**
 * Cascade and orphanRemoval experiments on three isolated parent/child pairs, each mapped differently:
 * Department/Employee (no cascade), Project/Task (cascade ALL, no orphanRemoval), Team/Member (cascade ALL + orphanRemoval).
 */
@Component
public class CascadeExperiments implements ExperimentSet {

    @Override
    public Map<String, Experiment> experiments() {
        return Map.of(
                "cascade-persist-none", this::persistWithoutCascade,
                "cascade-persist-all", this::persistWithCascade,
                "cascade-update-parent", this::updateParent,
                "remove-child-no-orphan", this::removeChildWithoutOrphanRemoval,
                "remove-child-orphan", this::removeChildWithOrphanRemoval,
                "remove-parent-no-cascade", this::removeParentWithoutCascade,
                "remove-parent-cascade-all", this::removeParentWithCascade);
    }

    private void persistWithoutCascade(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        TransactionStatus tx = c.begin();
        var s = new Object() { Department department; Employee employee; };
        rec.live("build-graph", "Build a department that contains an employee (helper keeps both sides consistent)",
                nodes("Department", "Employee", "Department.employees"), null, null,
                "Plain Java: the department's list contains the employee and the employee points back. Neither is stored yet.",
                null, () -> {
                    s.department = new Department(c.runKey, "Research");
                    s.employee = new Employee(c.runKey, "Mae");
                    s.department.addEmployee(s.employee);
                    return List.of(ev("department.getEmployees().size()", s.department.getEmployees().size()));
                });
        rec.live("persist-parent", "persist(department): there is NO cascade on Department.employees",
                nodes("Department", "Department.employees", "jpa_lab_department"), "Department", "jpa_lab_department",
                "persist() applies to the department only, because the association declares no cascade. The employee is a different entity "
                        + "and is not reached.",
                "jpa-department-inverse", () -> {
                    c.em.persist(s.department);
                    return List.of(ev("Department id", s.department.getId()), ev("Employee id", s.employee.getId()),
                            ev("em.contains(employee)", c.em.contains(s.employee)));
                });
        rec.live("flush", "flush(): what does Hibernate do with the employee still in the collection?",
                nodes("Employee", "jpa_lab_employee"), null, null,
                "Department.employees is the INVERSE side, so Hibernate writes nothing for it, and without a cascade it does not persist its elements "
                        + "either. The outcome below is what actually happened: no statement mentions the employee, so it would never reach the database.",
                null, () -> {
                    String outcome;
                    try {
                        c.em.flush();
                        outcome = "flush succeeded";
                    } catch (Exception e) {
                        outcome = "flush failed: " + ExperimentContext.describe(e);
                    }
                    return List.of(ev("Outcome", outcome));
                });
        c.rollback(tx);
        rec.observe("database-state", "Rows in MySQL after the rollback", nodes("jpa_lab_department", "jpa_lab_employee"),
                "The transaction was rolled back to keep the lab tidy, so the tables are empty; the statements above show what Hibernate did before that.",
                List.of(ev("jpa_lab_employee (rows)", c.count("select count(*) from jpa_lab_employee where run_key = ?", c.runKey))));
    }

    private void persistWithCascade(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        TransactionStatus tx = c.begin();
        var s = new Object() { Project project; };
        rec.live("build-graph", "Build a project with two tasks", nodes("Project", "Task", "Project.tasks"), null, null,
                "Project.tasks is mapped with cascade = ALL. The tasks are plain unsaved objects.", null, () -> {
                    s.project = new Project(c.runKey, "Apollo");
                    s.project.addTask(new Task(c.runKey, "Design"));
                    s.project.addTask(new Task(c.runKey, "Build"));
                    return List.of(ev("project.getTasks().size()", s.project.getTasks().size()));
                });
        rec.live("persist-parent", "persist(project): the cascade also persists the tasks",
                nodes("Project", "Project.tasks", "Task", "jpa_lab_project", "jpa_lab_task"), "jpa_lab_task", "jpa_lab_project",
                "CascadeType.PERSIST (part of ALL) makes persist() propagate along the association. Hibernate inserts the project first, then each "
                        + "task with the project's id in project_id.",
                "jpa-project-cascade", () -> {
                    c.em.persist(s.project);
                    return List.of(ev("Project id", s.project.getId()),
                            ev("Task ids", s.project.getTasks().stream().map(t -> String.valueOf(t.getId())).toList()));
                });
        rec.live("commit", "Commit", nodes("jpa_lab_project", "jpa_lab_task"), null, null, "Makes the inserts permanent.", null, () -> {
            c.commit(tx);
            return List.of();
        });
        rec.observe("database-state", "Rows in MySQL", nodes("jpa_lab_project", "jpa_lab_task", "jpa_lab_task.project_id"),
                "One persist() call, three rows.",
                List.of(ev("jpa_lab_task", c.rows("select id, title, project_id from jpa_lab_task where run_key = ? order by id", c.runKey))));
    }

    private void updateParent(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        long projectId = c.seedProject("Apollo");
        c.seedTask("Design", projectId);
        c.seedTask("Build", projectId);
        TransactionStatus tx = c.begin();
        var s = new Object() { Project project; };
        rec.live("load-parent", "Load the project", nodes("Project", "jpa_lab_project"), "Project", "jpa_lab_project",
                "The project is now managed. Its tasks collection is lazy and is not loaded.", null, () -> {
                    s.project = c.em.find(Project.class, projectId);
                    return List.of(ev("em.contains(project)", c.em.contains(s.project)));
                });
        rec.live("rename", "project.setName(...) and flush(): no save() call", nodes("Project", "jpa_lab_project"), "Project", "jpa_lab_project",
                "A managed entity is dirty-checked at flush: the changed field produces an UPDATE of the project row only. Cascade is not involved "
                        + "here, and the (unloaded) tasks are not touched.",
                null, () -> {
                    s.project.setName("Apollo 2");
                    c.em.flush();
                    return List.of();
                });
        rec.live("commit", "Commit", nodes("jpa_lab_project"), null, null, "Makes the update permanent.", null, () -> {
            c.commit(tx);
            return List.of();
        });
    }

    private void removeChildWithoutOrphanRemoval(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        long projectId = c.seedProject("Apollo");
        c.seedTask("Design", projectId);
        c.seedTask("Build", projectId);
        TransactionStatus tx = c.begin();
        var s = new Object() { Project project; };
        rec.live("load-with-tasks", "Load the project and its tasks", nodes("Project", "Project.tasks", "jpa_lab_task"), "Project", "jpa_lab_project",
                "Project.tasks has cascade = ALL but no orphanRemoval.", null, () -> {
                    s.project = c.em.find(Project.class, projectId);
                    return List.of(ev("project.getTasks().size()", s.project.getTasks().size()));
                });
        rec.live("remove-from-collection", "Remove the first task from project.getTasks(), then flush()", nodes("Project.tasks", "jpa_lab_task"), null, null,
                "Cascade ALL says what happens when an operation is applied to the PARENT. It does not say that removing a child from the list "
                        + "deletes it: that is orphanRemoval, which is not enabled here. The collection is also the inverse side, so Hibernate writes nothing.",
                "jpa-project-cascade", () -> {
                    s.project.getTasks().remove(0);
                    c.em.flush();
                    return List.of(ev("project.getTasks().size() in memory", s.project.getTasks().size()));
                });
        rec.observe("database-state", "The task table afterwards", nodes("jpa_lab_task", "jpa_lab_task.project_id"),
                "Both task rows still exist and still point at the project.",
                List.of(ev("jpa_lab_task", c.rows("select id, title, project_id from jpa_lab_task where run_key = ? order by id", c.runKey))));
        c.rollback(tx);
    }

    private void removeChildWithOrphanRemoval(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        long teamId = c.seedTeam("Rockets");
        c.seedMember("Ann", teamId);
        c.seedMember("Bo", teamId);
        TransactionStatus tx = c.begin();
        var s = new Object() { Team team; };
        rec.live("load-with-members", "Load the team and its members", nodes("Team", "Team.members", "jpa_lab_member"), "Team", "jpa_lab_team",
                "Team.members has cascade = ALL AND orphanRemoval = true.", null, () -> {
                    s.team = c.em.find(Team.class, teamId);
                    return List.of(ev("team.getMembers().size()", s.team.getMembers().size()));
                });
        rec.live("remove-from-collection", "team.removeMember(first), then flush()", nodes("Team.members", "jpa_lab_member"), "jpa_lab_member", "jpa_lab_team",
                "orphanRemoval = true tells Hibernate that a child removed from the parent's collection no longer has a reason to exist, so it "
                        + "schedules a DELETE for it. This is a different mechanism from cascade REMOVE, which reacts to deleting the PARENT.",
                "jpa-team-orphan-removal", () -> {
                    Member first = s.team.getMembers().get(0);
                    s.team.removeMember(first);
                    c.em.flush();
                    return List.of(ev("team.getMembers().size() in memory", s.team.getMembers().size()));
                });
        rec.observe("database-state", "The member table afterwards", nodes("jpa_lab_member"),
                "The removed member's row is gone.",
                List.of(ev("jpa_lab_member", c.rows("select id, name, team_id from jpa_lab_member where run_key = ? order by id", c.runKey))));
        c.rollback(tx);
    }

    private void removeParentWithoutCascade(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        long departmentId = c.seedDepartment("Closing down");
        c.seedEmployee("Kim", departmentId);
        c.seedEmployee("Lou", departmentId);
        TransactionStatus tx = c.begin();
        var s = new Object() { Department department; };
        rec.live("load-parent", "Load the department", nodes("Department", "jpa_lab_department"), "Department", "jpa_lab_department",
                "Two employee rows in MySQL reference this department through their foreign key.", null, () -> {
                    s.department = c.em.find(Department.class, departmentId);
                    return List.of();
                });
        rec.live("remove-parent", "remove(department), then flush()", nodes("Department", "jpa_lab_department", "jpa_lab_employee.department_id"),
                "jpa_lab_employee", "jpa_lab_department",
                "Department.employees declares no cascade, so remove() applies to the department only and Hibernate prepares one DELETE for it. "
                        + "The foreign key constraint is enforced by MySQL, not by Hibernate: the database refuses the statement.",
                null, () -> {
                    String outcome;
                    try {
                        c.em.remove(s.department);
                        c.em.flush();
                        outcome = "flush succeeded";
                    } catch (Exception e) {
                        outcome = "refused: " + ExperimentContext.describe(e);
                    }
                    return List.of(ev("Outcome", outcome));
                });
        c.rollback(tx);
        rec.observe("database-state", "Rows after the rollback", nodes("jpa_lab_department", "jpa_lab_employee"),
                "Nothing was deleted; the constraint protected the employees.",
                List.of(ev("jpa_lab_department (rows)", c.count("select count(*) from jpa_lab_department where run_key = ?", c.runKey)),
                        ev("jpa_lab_employee (rows)", c.count("select count(*) from jpa_lab_employee where run_key = ?", c.runKey))));
    }

    private void removeParentWithCascade(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        long teamId = c.seedTeam("Rockets");
        c.seedMember("Ann", teamId);
        c.seedMember("Bo", teamId);
        TransactionStatus tx = c.begin();
        var s = new Object() { Team team; };
        rec.live("load-parent", "Load the team", nodes("Team", "jpa_lab_team"), "Team", "jpa_lab_team",
                "Two member rows reference this team.", null, () -> {
                    s.team = c.em.find(Team.class, teamId);
                    return List.of();
                });
        rec.live("remove-parent", "remove(team), then flush()", nodes("Team", "Team.members", "jpa_lab_member", "jpa_lab_team"), "jpa_lab_member", "jpa_lab_team",
                "CascadeType.REMOVE (part of ALL) propagates remove() to the members, so Hibernate loads them, deletes each member and only then "
                        + "deletes the team. Children go first because of the foreign key.",
                null, () -> {
                    c.em.remove(s.team);
                    c.em.flush();
                    return List.of();
                });
        rec.observe("database-state", "Rows after the flush (same transaction)", nodes("jpa_lab_team", "jpa_lab_member"),
                "Both tables are empty for this run.",
                List.of(ev("jpa_lab_team (rows)", c.count("select count(*) from jpa_lab_team where run_key = ?", c.runKey)),
                        ev("jpa_lab_member (rows)", c.count("select count(*) from jpa_lab_member where run_key = ?", c.runKey))));
        c.rollback(tx);
    }
}
