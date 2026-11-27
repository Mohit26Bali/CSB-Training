package com.springlab.lab.jpa;

import com.springlab.lab.jpa.entity.Account;
import com.springlab.lab.jpa.entity.Course;
import com.springlab.lab.jpa.entity.Department;
import com.springlab.lab.jpa.entity.Employee;
import com.springlab.lab.jpa.entity.Profile;
import com.springlab.lab.jpa.entity.Student;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;

import static com.springlab.lab.jpa.ExperimentRecorder.ev;
import static com.springlab.lab.jpa.ExperimentRecorder.nodes;

/**
 * One-to-one, owning side / mappedBy and many-to-many experiments. Every statement shown comes from Hibernate while the
 * action ran, and every row shown was read from MySQL; nothing is typed in advance.
 */
@Component
public class RelationshipExperiments implements ExperimentSet {

    private enum Change { OWNER_ONLY, INVERSE_ONLY, BOTH_WITH_HELPER }

    @Override
    public Map<String, Experiment> experiments() {
        return Map.of(
                "one-to-one-fk", this::oneToOne,
                "owning-owner-only", c -> owningSide(c, Change.OWNER_ONLY),
                "owning-inverse-only", c -> owningSide(c, Change.INVERSE_ONLY),
                "owning-helper-both", c -> owningSide(c, Change.BOTH_WITH_HELPER),
                "m2m-join-table", this::manyToMany,
                "m2m-inverse-only", this::manyToManyInverseOnly);
    }

    // ------------------------------------------------------------------------------------------ one-to-one

    private void oneToOne(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        var s = new Object() { Account account; Profile profile; };

        rec.live("link-in-memory", "Create two objects and link both sides",
                nodes("Account", "Profile", "Account.profile", "Profile.account"), null, null,
                "Plain Java so far: no EntityManager, no SQL. attachProfile() sets Account.profile AND Profile.account, because a "
                        + "bidirectional association has two Java references that the developer must keep consistent.",
                null, () -> {
                    s.account = new Account(c.runKey, "ada");
                    s.profile = new Profile(c.runKey, "Writes the first program");
                    s.account.attachProfile(s.profile);
                    return List.of(ev("account.getProfile() is the profile", s.account.getProfile() == s.profile),
                            ev("profile.getAccount() is the account", s.profile.getAccount() == s.account),
                            ev("Ids before persist()", s.account.getId() + " / " + s.profile.getId() + " (both objects are transient)"));
                });

        TransactionStatus tx = c.begin();
        rec.live("persist-account", "persist(account): the inverse side is stored first",
                nodes("Account", "jpa_lab_account"), "Account", "jpa_lab_account",
                "The table jpa_lab_account holds no reference to the profile (Account is the inverse side, mappedBy = \"account\"), so "
                        + "it can be inserted on its own. The primary key uses IDENTITY, so Hibernate must run the INSERT right now to learn the id.",
                "jpa-account-inverse", () -> {
                    c.em.persist(s.account);
                    return List.of(ev("Account id assigned by MySQL", s.account.getId()));
                });
        rec.live("persist-profile", "persist(profile): the owning side writes the foreign key",
                nodes("Profile", "jpa_lab_profile", "Profile.account", "jpa_lab_profile.account_id"), "jpa_lab_profile", "jpa_lab_account",
                "Profile has @OneToOne @JoinColumn(name = \"account_id\"), so it OWNS the relationship: the account id is written into "
                        + "jpa_lab_profile.account_id. Only this side decides what the foreign key column contains.",
                "jpa-profile-one-to-one", () -> {
                    c.em.persist(s.profile);
                    return List.of(ev("Profile id assigned by MySQL", s.profile.getId()));
                });
        rec.live("commit", "Commit", nodes("jpa_lab_account", "jpa_lab_profile"), null, null,
                "Both rows were already sent to MySQL by persist(); commit makes them permanent and visible to other transactions.",
                null, () -> {
                    c.commit(tx);
                    return List.of();
                });
        rec.observe("database-state", "The rows MySQL now holds", nodes("jpa_lab_account", "jpa_lab_profile", "jpa_lab_profile.account_id"),
                "Read back with plain JDBC after the commit. The foreign key is a column of the profile row; the account row has no column for the profile.",
                List.of(ev("jpa_lab_account", c.rows("select id, username from jpa_lab_account where run_key = ?", c.runKey)),
                        ev("jpa_lab_profile", c.rows("select id, account_id, bio from jpa_lab_profile where run_key = ?", c.runKey)),
                        ev("Constraints on jpa_lab_profile", c.rows("""
                                select constraint_name, constraint_type from information_schema.table_constraints
                                where table_schema = database() and table_name = 'jpa_lab_profile' order by constraint_name"""))));

        TransactionStatus tx2 = c.begin();
        rec.live("second-profile", "A second profile for the same account", nodes("jpa_lab_profile.account_id", "jpa_lab_profile"), null, null,
                "@JoinColumn(unique = true) created a UNIQUE constraint, which is what makes this a one-to-one rather than a one-to-many. "
                        + "Hibernate prepares the INSERT; it is MySQL that refuses it.",
                null, () -> {
                    Account loaded = c.em.find(Account.class, s.account.getId());
                    Profile second = new Profile(c.runKey, "Another profile for the same account");
                    second.setAccount(loaded);
                    String outcome;
                    try {
                        c.em.persist(second);
                        c.em.flush();
                        outcome = "accepted (unexpected)";
                    } catch (Exception e) {
                        outcome = "refused: " + ExperimentContext.describe(e);
                    }
                    return List.of(ev("Outcome", outcome));
                });
        c.rollback(tx2);
        rec.concept("unidirectional-vs-bidirectional", "Unidirectional versus bidirectional",
                nodes("Account.profile", "Profile.account"),
                "Profile.account alone would be a unidirectional @OneToOne (the schema would be identical): the profile can reach its "
                        + "account, the account cannot reach its profile. Adding Account.profile with mappedBy makes it bidirectional "
                        + "WITHOUT changing the schema: mappedBy only says which field on the other side owns the foreign key. "
                        + "This step is an explanation; this run does not execute a unidirectional variant.");
    }

    // ---------------------------------------------------------------------------------------- owning side

    private void owningSide(ExperimentContext c, Change change) throws Exception {
        ExperimentRecorder rec = c.rec;
        long departmentId = c.seedDepartment("Engineering");
        rec.observe("start-state", "Starting point: one department, no employees",
                nodes("jpa_lab_department", "jpa_lab_employee"),
                "Seeded with plain JDBC, so Hibernate has not seen it yet and its persistence context is empty.",
                List.of(ev("jpa_lab_department", c.rows("select id, name from jpa_lab_department where run_key = ?", c.runKey)),
                        ev("jpa_lab_employee", c.rows("select id, name, department_id from jpa_lab_employee where run_key = ?", c.runKey))));

        TransactionStatus tx = c.begin();
        var s = new Object() { Department department; Employee employee; int collectionSizeBefore; };
        rec.live("load-department", "Load the department and its (empty) employee collection",
                nodes("Department", "Department.employees", "jpa_lab_department"), "Department", "jpa_lab_department",
                "find() loads the department; reading getEmployees().size() initialises the lazy collection with a second SELECT. "
                        + "Afterwards the collection is a normal in-memory list that Hibernate will NOT refresh by itself.",
                "jpa-department-inverse", () -> {
                    s.department = c.em.find(Department.class, departmentId);
                    s.collectionSizeBefore = s.department.getEmployees().size();
                    return List.of(ev("department.getEmployees().size()", s.collectionSizeBefore));
                });

        String title = switch (change) {
            case OWNER_ONLY -> "Set only the owning side: employee.setDepartment(department)";
            case INVERSE_ONLY -> "Change only the inverse side: department.getEmployees().add(employee)";
            case BOTH_WITH_HELPER -> "Use the helper: department.addEmployee(employee)";
        };
        rec.live("change-association", title,
                nodes("Department.employees", "Employee.department", "Employee"), null, null,
                switch (change) {
                    case OWNER_ONLY -> "Employee.department is the owning side. Nothing touches the department's own list in memory.";
                    case INVERSE_ONLY -> "Department.employees is the inverse side (mappedBy = \"department\"). The employee's own department reference stays null.";
                    case BOTH_WITH_HELPER -> "The helper updates both Java references together, so the object graph is consistent in memory and the owning side is set.";
                },
                null, () -> {
                    s.employee = new Employee(c.runKey, "Eve");
                    switch (change) {
                        case OWNER_ONLY -> s.employee.setDepartment(s.department);
                        case INVERSE_ONLY -> s.department.getEmployees().add(s.employee);
                        case BOTH_WITH_HELPER -> s.department.addEmployee(s.employee);
                    }
                    return List.of(ev("employee.getDepartment() is set", s.employee.getDepartment() != null),
                            ev("department.getEmployees() contains the employee", s.department.getEmployees().contains(s.employee)));
                });

        rec.live("persist-employee", "persist(employee): which value goes into department_id?",
                nodes("Employee", "jpa_lab_employee", "jpa_lab_employee.department_id"), "jpa_lab_employee", "jpa_lab_department",
                "Hibernate builds the INSERT from the OWNING side only: the foreign key column receives the id of employee.getDepartment(), "
                        + "or NULL when that reference is null. The inverse collection is ignored when writing. IDENTITY makes the INSERT run now.",
                "jpa-employee-owning", () -> {
                    c.em.persist(s.employee);
                    return List.of(ev("Employee id assigned by MySQL", s.employee.getId()));
                });
        rec.live("flush", "flush(): send any remaining changes", nodes("jpa_lab_employee"), null, null,
                "Flushing synchronises the persistence context with the database. No further statement is expected for the foreign key: "
                        + "the inverse collection never produces SQL.",
                null, () -> {
                    c.em.flush();
                    return List.of();
                });

        String fk = c.rows("select id, name, department_id from jpa_lab_employee where run_key = ?", c.runKey);
        Long dbFk = c.jdbc.query("select department_id from jpa_lab_employee where run_key = ?",
                rs -> rs.next() ? (Long) rs.getObject(1) : null, c.runKey);
        rec.observe("database-state", "What MySQL holds now (same transaction, read with plain JDBC)",
                nodes("jpa_lab_employee", "jpa_lab_employee.department_id"),
                "These rows were flushed but not committed yet; the JDBC read uses the same connection, so it sees them.",
                List.of(ev("jpa_lab_employee", fk),
                        ev("department_id is NULL", dbFk == null),
                        ev("department.getEmployees().size() in memory", s.department.getEmployees().size())));

        rec.infer("conclusion", "Which side controls the foreign key",
                nodes("Employee.department", "Department.employees", "jpa_lab_employee.department_id"),
                switch (change) {
                    case OWNER_ONLY -> "The foreign key is set because the OWNING side (Employee.department) was set. The department's in-memory list was not "
                            + "updated and is now out of date; it will only be right again after a reload or if the helper method is used.";
                    case INVERSE_ONLY -> "The foreign key is NULL even though the department's list contains the employee: the inverse side is ignored when "
                            + "writing. Modifying a Java collection never changes a foreign key by itself.";
                    case BOTH_WITH_HELPER -> "Both references agree and the foreign key is set. This is why bidirectional associations should be changed through "
                            + "a helper method that updates both sides.";
                },
                List.of(ev("Owning side set", change != Change.INVERSE_ONLY), ev("Inverse side updated in memory", change != Change.OWNER_ONLY),
                        ev("Foreign key written", dbFk != null)));
        rec.live("commit", "Commit", nodes("jpa_lab_employee"), null, null, "Makes the flushed state permanent.", null, () -> {
            c.commit(tx);
            return List.of();
        });
    }

    // --------------------------------------------------------------------------------------- many-to-many

    private void manyToMany(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        TransactionStatus tx = c.begin();
        var s = new Object() { Student student; Course java; Course sql; };
        rec.live("persist-entities", "Persist one student and two courses",
                nodes("Student", "Course", "jpa_lab_student", "jpa_lab_course"), null, null,
                "Three separate rows. There is no cascade on the association, so every entity is persisted explicitly.",
                null, () -> {
                    s.java = new Course(c.runKey, "Java");
                    s.sql = new Course(c.runKey, "SQL");
                    s.student = new Student(c.runKey, "Grace");
                    c.em.persist(s.java);
                    c.em.persist(s.sql);
                    c.em.persist(s.student);
                    return List.of(ev("Ids", s.student.getId() + ", " + s.java.getId() + ", " + s.sql.getId()));
                });
        rec.live("enroll", "student.enroll(java), student.enroll(sql): Java collections only", nodes("Student.courses", "Course.students"), null, null,
                "enroll() adds each course to Student.courses (the owning side) and the student to Course.students. Nothing is sent to MySQL "
                        + "yet: the change waits in the persistence context until the next flush.",
                null, () -> {
                    s.student.enroll(s.java);
                    s.student.enroll(s.sql);
                    return List.of(ev("student.getCourses().size()", s.student.getCourses().size()));
                });
        rec.live("flush-join-rows", "flush(): Hibernate writes the join table rows",
                nodes("Student.courses", "jpa_lab_enrollment", "jpa_lab_enrollment.student_id", "jpa_lab_enrollment.course_id"),
                "jpa_lab_enrollment", "jpa_lab_student",
                "Student.courses is the OWNING side and declares @JoinTable, so the flush inserts one row per course into jpa_lab_enrollment. "
                        + "A many-to-many relationship is stored as rows of a third table; neither entity table gets a foreign key.",
                "jpa-student-join-table", () -> {
                    c.em.flush();
                    return List.of();
                });
        rec.observe("join-table-rows", "The three tables after the flush",
                nodes("jpa_lab_student", "jpa_lab_course", "jpa_lab_enrollment"),
                "Read with plain JDBC on the same connection.",
                List.of(ev("jpa_lab_student", c.rows("select id, name from jpa_lab_student where run_key = ?", c.runKey)),
                        ev("jpa_lab_course", c.rows("select id, title from jpa_lab_course where run_key = ? order by id", c.runKey)),
                        ev("jpa_lab_enrollment", c.rows("""
                                select e.student_id, e.course_id from jpa_lab_enrollment e
                                join jpa_lab_student s on s.id = e.student_id where s.run_key = ? order by e.course_id""", c.runKey))));
        rec.live("withdraw", "student.withdraw(sql), then flush()",
                nodes("Student.courses", "jpa_lab_enrollment"), "jpa_lab_enrollment", "jpa_lab_student",
                "Removing a course from the owning collection deletes only the matching JOIN ROW. The course itself is not deleted, because "
                        + "no cascade REMOVE exists (and a REMOVE cascade on a many-to-many would delete shared courses).",
                null, () -> {
                    s.student.withdraw(s.sql);
                    c.em.flush();
                    return List.of();
                });
        rec.observe("after-withdraw", "Rows after the withdrawal", nodes("jpa_lab_course", "jpa_lab_enrollment"),
                "The course row remains; only the join row is gone.",
                List.of(ev("jpa_lab_course (rows)", c.count("select count(*) from jpa_lab_course where run_key = ?", c.runKey)),
                        ev("jpa_lab_enrollment (rows)", c.count("""
                                select count(*) from jpa_lab_enrollment e join jpa_lab_student s on s.id = e.student_id
                                where s.run_key = ?""", c.runKey))));
        rec.live("commit", "Commit", nodes("jpa_lab_enrollment"), null, null, "Makes the result permanent.", null, () -> {
            c.commit(tx);
            return List.of();
        });
        rec.concept("association-entity", "Why real applications often use an association entity",
                nodes("Student", "Course", "jpa_lab_enrollment"),
                "A join table that must carry its own data (enrolment date, grade, status) cannot be a plain @ManyToMany: it needs its own entity "
                        + "(for example Enrollment with two @ManyToOne references). The pure @ManyToMany shown here is fine only for a bare link "
                        + "with no attributes. This step is an explanation, not an observation.");
    }

    private void manyToManyInverseOnly(ExperimentContext c) throws Exception {
        ExperimentRecorder rec = c.rec;
        TransactionStatus tx = c.begin();
        var s = new Object() { Student student; Course course; };
        rec.live("persist-entities", "Persist a student and a course", nodes("Student", "Course", "jpa_lab_student", "jpa_lab_course"), null, null,
                "Two independent rows; the relationship does not exist yet.", null, () -> {
                    s.student = new Student(c.runKey, "Alan");
                    s.course = new Course(c.runKey, "Algorithms");
                    c.em.persist(s.student);
                    c.em.persist(s.course);
                    return List.of();
                });
        rec.live("inverse-only", "course.getStudents().add(student): the INVERSE side only",
                nodes("Course.students", "Student.courses"), null, null,
                "Course.students is mappedBy = \"courses\", the inverse side. The Java object graph now says the student attends the course "
                        + "from one direction.",
                "jpa-course-inverse", () -> {
                    s.course.getStudents().add(s.student);
                    c.em.flush();
                    return List.of(ev("course.getStudents().size()", s.course.getStudents().size()),
                            ev("student.getCourses().size()", s.student.getCourses().size()));
                });
        rec.observe("no-join-row", "The join table after flushing the inverse-side change",
                nodes("jpa_lab_enrollment"),
                "No row was written. The inverse collection is not used when Hibernate writes the join table.",
                List.of(ev("jpa_lab_enrollment (rows)", c.count("""
                        select count(*) from jpa_lab_enrollment e join jpa_lab_student s on s.id = e.student_id
                        where s.run_key = ?""", c.runKey))));
        rec.live("owner-side", "student.getCourses().add(course): the OWNING side", nodes("Student.courses", "jpa_lab_enrollment"),
                "jpa_lab_enrollment", "jpa_lab_student",
                "Now the owning collection changes, and the next flush inserts the join row.", null, () -> {
                    s.student.getCourses().add(s.course);
                    c.em.flush();
                    return List.of();
                });
        rec.observe("join-row", "The join table after changing the owning side", nodes("jpa_lab_enrollment"),
                "One row now links the two entities.",
                List.of(ev("jpa_lab_enrollment (rows)", c.count("""
                        select count(*) from jpa_lab_enrollment e join jpa_lab_student s on s.id = e.student_id
                        where s.run_key = ?""", c.runKey))));
        rec.live("commit", "Commit", nodes("jpa_lab_enrollment"), null, null, "Makes the result permanent.", null, () -> {
            c.commit(tx);
            return List.of();
        });
    }
}
