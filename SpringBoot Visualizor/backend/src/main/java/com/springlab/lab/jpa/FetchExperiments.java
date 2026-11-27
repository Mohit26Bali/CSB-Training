package com.springlab.lab.jpa;

import com.springlab.lab.jpa.LabJpaDtos.AttributeInfo;
import com.springlab.lab.jpa.entity.Department;
import com.springlab.lab.jpa.entity.Employee;
import com.springlab.lab.jpa.entity.Task;
import jakarta.persistence.PersistenceUnitUtil;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.springlab.lab.jpa.ExperimentRecorder.ev;
import static com.springlab.lab.jpa.ExperimentRecorder.nodes;

/**
 * Lazy and eager fetching, and the N+1 problem with its two standard remedies. Every statement count in a summary is
 * computed from the statements that were actually captured; no count is written down in advance.
 */
@Component
public class FetchExperiments implements ExperimentSet {

    private static final int EMPLOYEES_PER_DEPARTMENT = 3;

    private final DepartmentRepository departments;
    private final JpaMappingService mappingService;

    public FetchExperiments(DepartmentRepository departments, JpaMappingService mappingService) {
        this.departments = departments;
        this.mappingService = mappingService;
    }

    @Override
    public Map<String, Experiment> experiments() {
        Map<String, Experiment> map = new HashMap<>();
        map.put("lazy-not-accessed", this::lazyNotAccessed);
        map.put("lazy-in-transaction", this::lazyInTransaction);
        map.put("lazy-after-close", this::lazyAfterClose);
        map.put("fetch-defaults", this::fetchDefaults);
        map.put("eager-n-plus-one", this::eagerNPlusOne);
        map.put("n-plus-one", c -> compare(c, "n-plus-one", null));
        map.put("join-fetch", c -> compare(c, "join-fetch", departments::findForRunWithEmployeesJoinFetch));
        map.put("entity-graph", c -> compare(c, "entity-graph", departments::findForRunWithEmployeesGraph));
        map.put("two-collections-cartesian", this::twoCollections);
        return map;
    }

    private long seedDepartmentWithEmployees(ExperimentContext c, String name, int employees) {
        long id = c.seedDepartment(name);
        for (int i = 1; i <= employees; i++) {
            c.seedEmployee(name + " employee " + i, id);
        }
        return id;
    }

    // ---------------------------------------------------------------------------------------------- lazy

    private void lazyNotAccessed(ExperimentContext c) throws Exception {
        long id = seedDepartmentWithEmployees(c, "Engineering", EMPLOYEES_PER_DEPARTMENT);
        PersistenceUnitUtil util = c.em.getEntityManagerFactory().getPersistenceUnitUtil();
        TransactionStatus tx = c.begin();
        c.rec.live("find-department", "find(Department.class, id) and do not touch the employees",
                nodes("Department", "Department.employees", "jpa_lab_department"), "Department", "jpa_lab_department",
                "Department.employees is a @OneToMany, whose Jakarta Persistence default is LAZY. Hibernate loads the department row and puts an "
                        + "uninitialised collection wrapper in its place.",
                null, () -> {
                    Department d = c.em.find(Department.class, id);
                    return List.of(ev("employees loaded (PersistenceUnitUtil.isLoaded)", util.isLoaded(d, "employees")),
                            ev("Collection implementation", d.getEmployees().getClass().getSimpleName()));
                });
        c.rec.infer("no-employee-query", "Only one statement was needed",
                nodes("Department.employees", "jpa_lab_employee"),
                "No statement mentions jpa_lab_employee: the data was not needed, so it was not loaded. Laziness saves queries only while the "
                        + "relationship is not used.",
                List.of(ev("Statements in the step above", c.rec.sqlCountOfStep("find-department"))));
        c.commit(tx);
    }

    private void lazyInTransaction(ExperimentContext c) throws Exception {
        long id = seedDepartmentWithEmployees(c, "Engineering", EMPLOYEES_PER_DEPARTMENT);
        PersistenceUnitUtil util = c.em.getEntityManagerFactory().getPersistenceUnitUtil();
        TransactionStatus tx = c.begin();
        var s = new Object() { Department department; };
        c.rec.live("find-department", "find(Department.class, id)", nodes("Department", "jpa_lab_department"), "Department", "jpa_lab_department",
                "The department is loaded; its employees are not.", null, () -> {
                    s.department = c.em.find(Department.class, id);
                    return List.of(ev("employees loaded", util.isLoaded(s.department, "employees")));
                });
        c.rec.live("touch-employees", "department.getEmployees().size() inside the transaction",
                nodes("Department.employees", "jpa_lab_employee", "jpa_lab_employee.department_id"), "Department", "Employee",
                "Touching the collection makes Hibernate run a second SELECT, filtered by the foreign key. This works because the persistence context "
                        + "(the Hibernate session) is still open for the duration of the transaction.",
                null, () -> {
                    int size = s.department.getEmployees().size();
                    return List.of(ev("employees", size), ev("employees loaded now", util.isLoaded(s.department, "employees")));
                });
        c.commit(tx);
    }

    private void lazyAfterClose(ExperimentContext c) throws Exception {
        long id = seedDepartmentWithEmployees(c, "Engineering", EMPLOYEES_PER_DEPARTMENT);
        var s = new Object() { Department department; };
        TransactionStatus tx = c.begin();
        c.rec.live("load-and-close", "Load the department and finish the transaction",
                nodes("Department", "jpa_lab_department"), "Department", "jpa_lab_department",
                "With a transaction-scoped EntityManager the persistence context ends when the transaction does. The department the code still holds "
                        + "is now DETACHED.",
                null, () -> {
                    s.department = c.em.find(Department.class, id);
                    c.commit(tx);
                    return List.of(ev("em.contains(department) after the transaction", c.em.contains(s.department)));
                });
        c.rec.live("touch-after-close", "department.getEmployees().size() after the persistence context closed",
                nodes("Department.employees", "jpa_lab_employee"), null, null,
                "The collection wrapper needs a session to run its SELECT. There is none, so Hibernate throws LazyInitializationException instead of "
                        + "loading. This is not a bug in the lazy mapping: the code used the data outside the layer that owns the session.",
                null, () -> {
                    String outcome;
                    try {
                        outcome = "loaded " + s.department.getEmployees().size();
                    } catch (Exception e) {
                        outcome = ExperimentContext.describe(e);
                    }
                    return List.of(ev("Outcome", outcome));
                });
        c.rec.observe("open-in-view", "Why this application does not hide the problem", nodes("Department.employees"),
                "With Open Session in View the session stays open for the whole web request, so this access would work in a controller or while "
                        + "rendering JSON, at the price of queries from anywhere in the request. SpringLab sets spring.jpa.open-in-view=false, so lazy "
                        + "loading must happen inside the service layer, which is where it is easiest to reason about and test.",
                List.of(ev("spring.jpa.open-in-view (this application)", mappingService.mapping().facts().openInView())));
    }

    private void fetchDefaults(ExperimentContext c) throws Exception {
        long projectId = c.seedProject("Apollo");
        long taskId = c.seedTask("Design", projectId);
        long departmentId = c.seedDepartment("Engineering");
        long employeeId = c.seedEmployee("Eve", departmentId);
        PersistenceUnitUtil util = c.em.getEntityManagerFactory().getPersistenceUnitUtil();
        c.rec.observe("annotations", "What the mappings say (read from the compiled annotations)",
                nodes("Task.project", "Employee.department", "Department.employees"),
                "Task.project is written WITHOUT a fetch attribute, so the specification default applies. Employee.department says LAZY explicitly. "
                        + "The defaults are: @ManyToOne and @OneToOne EAGER, @OneToMany and @ManyToMany LAZY.",
                List.of(ev("Task.project (@ManyToOne)", fetchOf("Task", "project")),
                        ev("Employee.department (@ManyToOne, fetch written)", fetchOf("Employee", "department")),
                        ev("Department.employees (@OneToMany)", fetchOf("Department", "employees"))));
        TransactionStatus tx = c.begin();
        c.rec.live("find-task", "find(Task.class, id): an EAGER @ManyToOne", nodes("Task", "Task.project", "jpa_lab_task", "jpa_lab_project"),
                "jpa_lab_task", "jpa_lab_project",
                "Hibernate must return a fully usable task, so it also loads the project, here in the same statement with a join.",
                "jpa-task-eager-default", () -> {
                    Task task = c.em.find(Task.class, taskId);
                    return List.of(ev("project loaded", util.isLoaded(task, "project")));
                });
        c.rec.live("find-employee", "find(Employee.class, id): a LAZY @ManyToOne", nodes("Employee", "Employee.department", "jpa_lab_employee"),
                "Employee", "jpa_lab_employee",
                "Only the employee row is read. employee.getDepartment() is a proxy: an object that knows the department id and loads the row the first "
                        + "time something other than the id is requested.",
                "jpa-employee-owning", () -> {
                    Employee employee = c.em.find(Employee.class, employeeId);
                    return List.of(ev("department loaded", util.isLoaded(employee, "department")),
                            ev("Proxy class (simple name)", trimProxyName(employee.getDepartment().getClass().getSimpleName())));
                });
        c.commit(tx);
    }

    private static String trimProxyName(String simpleName) {
        int dollar = simpleName.indexOf('$');
        return dollar < 0 ? simpleName : simpleName.substring(0, dollar) + "$...";
    }

    private String fetchOf(String entity, String attribute) {
        return mappingService.mapping().entities().stream().filter(e -> e.id().equals(entity)).flatMap(e -> e.attributes().stream())
                .filter(a -> a.name().equals(attribute)).map(AttributeInfo::fetch).findFirst().orElse("?");
    }

    private void eagerNPlusOne(ExperimentContext c) throws Exception {
        for (int p = 1; p <= 3; p++) {
            long projectId = c.seedProject("Project " + p);
            c.seedTask("Task " + p + ".1", projectId);
            c.seedTask("Task " + p + ".2", projectId);
        }
        TransactionStatus tx = c.begin();
        var s = new Object() { int tasks; };
        c.rec.live("load-tasks", "A JPQL query for all tasks of this run: select t from Task t",
                nodes("Task", "Task.project", "jpa_lab_task", "jpa_lab_project"), "jpa_lab_task", "jpa_lab_project",
                "Task.project is EAGER. A JPQL query is translated to SQL exactly as written, so it does not join the project; afterwards Hibernate "
                        + "notices that every task needs its project and loads each missing one with an extra SELECT. EAGER therefore does not avoid "
                        + "N+1, it can cause it.",
                null, () -> {
                    List<Task> tasks = c.em.createQuery("select t from Task t where t.runKey = :key order by t.id", Task.class)
                            .setParameter("key", c.runKey).getResultList();
                    s.tasks = tasks.size();
                    return List.of(ev("Tasks returned", tasks.size()));
                });
        int statements = c.rec.sqlCountOfStep("load-tasks");
        c.rec.infer("count", "Counting the statements of that one query", nodes("jpa_lab_task", "jpa_lab_project"),
                "One statement loaded the tasks; every other statement is an extra SELECT for a project. The totals below are counted from the captured "
                        + "statements, not assumed.",
                List.of(ev("Statements prepared", statements), ev("Statements for tasks", 1), ev("Extra statements for projects", statements - 1),
                        ev("Distinct projects among the tasks", c.count("select count(distinct project_id) from jpa_lab_task where run_key = ?", c.runKey))));
        c.commit(tx);
    }

    // ------------------------------------------------------------------------------------------- N+1 family

    /** Loads the same departments the "bad" way and then with {@code remedy}; both counts are measured. */
    private void compare(ExperimentContext c, String id, BiFunction<String, String, List<Department>> remedy) throws Exception {
        int departmentCount = 6;
        for (int i = 1; i <= departmentCount; i++) {
            seedDepartmentWithEmployees(c, "q" + i, EMPLOYEES_PER_DEPARTMENT);
        }
        // The same departments, loaded plainly: this is the N+1 pattern whenever the employees are used afterwards.
        runPlain(c, "plain", "q%", departmentCount);
        int plainStatements = c.rec.sqlCountOfStep("plain-load") + c.rec.sqlCountOfStep("plain-touch");
        if (remedy == null) {
            // A second size, to show that the count follows the number of departments, not a fixed constant.
            for (int i = 1; i <= 3; i++) {
                seedDepartmentWithEmployees(c, "r" + i, EMPLOYEES_PER_DEPARTMENT);
            }
            runPlain(c, "small", "r%", 3);
            int smallStatements = c.rec.sqlCountOfStep("small-load") + c.rec.sqlCountOfStep("small-touch");
            c.rec.infer("n-plus-one-summary", "The statement count grows with the number of departments",
                    nodes("Department", "Department.employees", "Employee"),
                    "1 statement loads the N departments, then each department's employees are loaded by their own statement, so the total is 1 + N. "
                            + "Both totals are counted from captured statements.",
                    List.of(ev("3 departments", smallStatements + " statements"), ev("6 departments", plainStatements + " statements"),
                            ev("Statements per extra department", (plainStatements - smallStatements) / 3.0)));
            return;
        }

        TransactionStatus tx = c.begin();
        var held = new Object() { List<Department> loaded; };
        c.rec.live("remedy-load", id.equals("join-fetch") ? "The same departments with a JPQL JOIN FETCH (repository method)"
                        : "The same departments with an EntityGraph (repository method)",
                nodes("Department", "Department.employees", "jpa_lab_employee"), "Department", "Employee",
                id.equals("join-fetch")
                        ? "select distinct d from Department d join fetch d.employees: the employees are fetched by the same statement, by joining the two tables."
                        : "@EntityGraph(attributePaths = \"employees\") on the repository method asks Hibernate to fetch the named association together with the entity, "
                        + "without changing the JPQL.",
                "jpa-department-repository", () -> {
                    held.loaded = remedy.apply(c.runKey, "q%");
                    return List.of(ev("Departments returned", held.loaded.size()));
                });
        c.rec.live("remedy-touch", "Use every department's employees", nodes("Department.employees", "Employee"), null, null,
                "The collections are already initialised, so reading them costs no statement.",
                null, () -> {
                    int total = 0;
                    for (Department d : held.loaded) {
                        total += d.getEmployees().size();
                    }
                    return List.of(ev("Employees read", total));
                });
        c.commit(tx);
        int remedyStatements = c.rec.sqlCountOfStep("remedy-load");
        c.rec.infer("before-after", "Query count before and after", nodes("Department", "Employee"),
                "Both numbers are counted from the statements Hibernate prepared for the same data. The remedy turns 1 + N statements into one. "
                        + "The trade-off: the joined rows repeat each department once per employee, and fetching several collections this way multiplies rows "
                        + "(see the two-collections experiment).",
                List.of(ev("Before (plain load, then use)", plainStatements + " statements"),
                        ev("After (" + (id.equals("join-fetch") ? "JOIN FETCH" : "EntityGraph") + ")", remedyStatements + " statement(s)"),
                        ev("Statements saved", plainStatements - remedyStatements)));
    }

    private void runPlain(ExperimentContext c, String prefix, String namePattern, int expectedDepartments) throws Exception {
        TransactionStatus tx = c.begin();
        var s = new Object() { List<Department> loaded; };
        c.rec.live(prefix + "-load", "Load the departments: one query (" + expectedDepartments + " departments)",
                nodes("Department", "jpa_lab_department"), "Department", "jpa_lab_department",
                "A plain repository query. The employees are LAZY, so none of them is read yet.",
                null, () -> {
                    s.loaded = departments.findForRun(c.runKey, namePattern);
                    return List.of(ev("Departments returned", s.loaded.size()));
                });
        c.rec.live(prefix + "-touch", "Use each department's employees", nodes("Department.employees", "jpa_lab_employee", "jpa_lab_employee.department_id"),
                "Department", "Employee",
                "Each department's collection is initialised on first use, with its own SELECT filtered by that department's id. The loop looks harmless in "
                        + "Java; the statements show its real cost.",
                "jpa-department-repository", () -> {
                    int total = 0;
                    for (Department d : s.loaded) {
                        total += d.getEmployees().size();
                    }
                    return List.of(ev("Employees read", total));
                });
        c.commit(tx);
    }

    private void twoCollections(ExperimentContext c) throws Exception {
        for (int d = 1; d <= 2; d++) {
            long id = seedDepartmentWithEmployees(c, "q" + d, EMPLOYEES_PER_DEPARTMENT);
            for (int r = 1; r <= 4; r++) {
                c.seedRoom("Room " + d + "." + r, id);
            }
        }
        TransactionStatus tx = c.begin();
        var s = new Object() { int employeeEntries; int distinctEmployees; int rooms; int loaded; };
        c.rec.live("fetch-both", "select distinct d from Department d join fetch d.employees join fetch d.rooms",
                nodes("Department", "Department.employees", "Department.rooms", "jpa_lab_employee", "jpa_lab_room"), "Department", "Employee",
                "One statement fetches both collections. A relational join of a parent with two independent child tables returns every combination "
                        + "of one employee and one room, so rows are multiplied. Hibernate maps them back to objects: the Set of rooms drops the "
                        + "duplicates, but the List (a bag) of employees keeps one entry per row, so each employee appears once per room.",
                null, () -> {
                    List<Department> result = c.em.createQuery(
                            "select distinct d from Department d join fetch d.employees join fetch d.rooms where d.runKey = :key", Department.class)
                            .setParameter("key", c.runKey).getResultList();
                    s.loaded = result.size();
                    java.util.Set<Employee> distinct = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                    for (Department d : result) {
                        s.employeeEntries += d.getEmployees().size();
                        distinct.addAll(d.getEmployees());
                        s.rooms += d.getRooms().size();
                    }
                    s.distinctEmployees = distinct.size();
                    return List.of(ev("Departments", s.loaded), ev("Entries in the employees Lists (a bag keeps duplicates)", s.employeeEntries),
                            ev("Distinct employee objects", s.distinctEmployees), ev("Rooms (a Set removes duplicates)", s.rooms));
                });
        c.commit(tx);
        long rows = c.count("""
                select count(*) from jpa_lab_department d
                join jpa_lab_employee e on e.department_id = d.id
                join jpa_lab_room r on r.department_id = d.id
                where d.run_key = ?""", c.runKey);
        c.rec.observe("row-multiplication", "How many rows the database has to return for that join", nodes("jpa_lab_department", "jpa_lab_employee", "jpa_lab_room"),
                "Measured by running an equivalent join with plain JDBC (this is the lab's own verification query, not Hibernate's statement). The "
                        + "number of rows is employees times rooms for each department, far more than the number of objects actually needed.",
                List.of(ev("Rows returned by the equivalent join", rows),
                        ev("Objects needed (departments + employees + rooms)", s.loaded + s.distinctEmployees + s.rooms),
                        ev("Statements prepared by Hibernate", c.rec.sqlCountOfStep("fetch-both"))));
        c.rec.concept("multiple-bag", "Two Lists in one fetch join",
                nodes("Department.employees", "Department.rooms"),
                "Here rooms is a Set and employees is a List, which Hibernate allows. If BOTH collections were Lists (bags), Hibernate refuses to fetch "
                        + "them in one query and throws MultipleBagFetchException. Typical remedies are fetching one collection per query, a Set, or "
                        + "batch fetching. This step is an explanation; it was not run.");
    }
}
