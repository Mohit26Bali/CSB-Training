# 15. JPA One-to-Many Relationship

**Concept:** JPA. **One `Department` has many `Employee`s.** This is a bidirectional mapping:
- `Department.employees`: `@OneToMany(mappedBy = "department", cascade = ALL, orphanRemoval = true)`
- `Employee.department`: `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "department_id")`

APIs create a department, add employees to it, and retrieve them.

- **Recursive JSON is prevented** by returning **DTOs only**. A demo endpoint shows what happens if the entities are returned directly.
- **The optional extension** compares **LAZY vs EAGER** and the **N+1 problem**, using the SQL Hibernate generated.

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** · Spring Data JPA / **Hibernate 6.6.29** · starter-validation · MySQL 8

**Database:** shared Day 07 database `Day_007_Assigments`:
- **`a15_departments`** is the "one" side.
- **`a15_employees`** is the "many" side. It holds the foreign key **`department_id` → `a15_departments`**, so it is the owning side.

Seed data: 3 departments and 5 employees. `ddl-auto=validate`.

## Structure

```text
15-jpa-one-to-many/
├── pom.xml
└── src/main/
    ├── java/com/company/boot/
    │   ├── OneToManyApplication.java                @SpringBootApplication (run this)
    │   ├── entity/
    │   │   ├── Department.java                      @OneToMany(mappedBy) - inverse side, addEmployee()/removeEmployee()
    │   │   └── Employee.java                        @ManyToOne + @JoinColumn - OWNING side
    │   ├── repository/
    │   │   ├── DepartmentRepository.java            @EntityGraph("employees") queries
    │   │   └── EmployeeRepository.java              findByDepartmentId..., lazy vs graph lists
    │   ├── service/DepartmentService.java           entities -> DTOs inside the transaction
    │   ├── controller/
    │   │   ├── DepartmentController.java            /api/departments ...
    │   │   ├── EmployeeController.java              /api/employees ...
    │   │   └── EntityDemoController.java            /api/demo/entity/... (returns ENTITIES - what NOT to do)
    │   ├── dto/DepartmentRequest, EmployeeRequest, DepartmentResponse (+EmployeeSummary), EmployeeResponse, ErrorResponse
    │   └── exception/ResourceNotFoundException, DuplicateResourceException, GlobalExceptionHandler
    └── resources/
        ├── application.properties                   open-in-view=false, SQL logging
        ├── db-secret.properties                     password (git-ignored; copy db-secret.properties.example)
        └── schema.sql                               2 tables + FOREIGN KEY + seed data
```

## Endpoints

| Method | URL | Purpose |
|---|---|---|
| POST | `/api/departments` | **create a department** → 201 |
| POST | `/api/departments/{id}/employees` | **add an employee** to the department → 201 |
| GET | `/api/departments/{id}/employees` | **retrieve the department's employees** |
| GET | `/api/departments/{id}` | the department with its employees nested |
| GET | `/api/departments[?fetch=lazy]` | all departments with employees (`lazy` = N+1 demo) |
| GET | `/api/employees/{id}`, `/api/employees[?fetch=lazy]` | employees with their department name |
| DELETE | `/api/departments/{id}/employees/{employeeId}` | remove an employee (`orphanRemoval`) |
| DELETE | `/api/departments/{id}` | delete a department (`cascade` removes its employees) |
| GET | `/api/demo/entity/departments/{id}/lazy` and `/fetched` | **demo:** return the raw entity |

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It recreates both `a15_` tables with seed data; re-run it to reset.
2. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `15-jpa-one-to-many`, then right-click `OneToManyApplication` → **Run As → Java Application**.
3. **In Postman:**
   - `POST http://localhost:8080/api/departments` with `{"name":"Marketing","location":"Delhi"}`
   - `POST http://localhost:8080/api/departments/4/employees` with `{"name":"Priya Nair","email":"priya@company.com","salary":70000}`
   - `GET http://localhost:8080/api/departments/4/employees`
4. The Eclipse Console shows every SQL statement.

## Observed output

### Create a department → add employees → retrieve

```text
POST /api/departments  {"name":"Marketing","location":"Delhi"}               -> 201  Location: /api/departments/4
{"id":4,"name":"Marketing","location":"Delhi","employeeCount":0,"employees":[]}
SQL: select d1_0.department_id from a15_departments d1_0 where upper(d1_0.name)=upper(?) limit ?
     insert into a15_departments (location,name) values (?,?)

POST /api/departments/4/employees  {"name":"Priya Nair","email":"priya@company.com","salary":70000}   -> 201  Location: /api/employees/6
{"id":6,"name":"Priya Nair","email":"priya@company.com","salary":70000,"departmentId":4,"departmentName":"Marketing"}
SQL: select d1_0.department_id,d1_0.location,d1_0.name from a15_departments d1_0 where d1_0.department_id=?
     select e1_0.employee_id from a15_employees e1_0 where upper(e1_0.email)=upper(?) limit ?
     insert into a15_employees (department_id,email,name,salary) values (?,?,?,?)       <- department_id written by the OWNING side

POST /api/departments/4/employees  {"name":"Arjun Mehta","email":"Arjun@Company.com","salary":68000}  -> 201  Location: /api/employees/7
{"id":7,"name":"Arjun Mehta","email":"arjun@company.com","salary":68000,"departmentId":4,"departmentName":"Marketing"}
```

The employee was saved **without calling `employeeRepository.save()`**. The service calls `department.addEmployee(employee)`, and **`cascade = ALL`** makes Hibernate INSERT the new employee. Hibernate did **not** load the existing employees just to append one: there was no `select ... from a15_employees where department_id=?`.

```text
GET /api/departments/4/employees    -> 200
[{"id":6,"name":"Priya Nair","email":"priya@company.com","salary":70000.00,"departmentId":4,"departmentName":"Marketing"},
 {"id":7,"name":"Arjun Mehta","email":"arjun@company.com","salary":68000.00,"departmentId":4,"departmentName":"Marketing"}]
SQL: select ... from a15_departments d1_0 where d1_0.department_id=?                       (404 check)
     select e1_0.employee_id,e1_0.department_id,... from a15_employees e1_0 join a15_departments d1_0
            on d1_0.department_id=e1_0.department_id where d1_0.department_id=? order by e1_0.employee_id

GET /api/departments/4              -> 200
{"id":4,"name":"Marketing","location":"Delhi","employeeCount":2,"employees":[
  {"id":6,"name":"Priya Nair","email":"priya@company.com","salary":70000.00},
  {"id":7,"name":"Arjun Mehta","email":"arjun@company.com","salary":68000.00}]}
SQL: select d1_0.department_id,e1_0.department_id,e1_0.employee_id,... from a15_departments d1_0
            left join a15_employees e1_0 on d1_0.department_id=e1_0.department_id where d1_0.department_id=? order by e1_0.employee_id
```

### The relationship verified in the database

The foreign key, from `information_schema`:

```text
| CONSTRAINT_NAME             | TABLE_NAME    | COLUMN_NAME   | REFERENCED_TABLE_NAME | REFERENCED_COLUMN_NAME |
| fk_a15_employees_department | a15_employees | department_id | a15_departments       | department_id          |
```

The rows joined through it, after the API calls and the deletes in the edge-case section:

```text
| department_id | department  | employee_id | employee   | fk   |
|             1 | Engineering |           1 | Asha Rao   |    1 |
|             1 | Engineering |           2 | Ravi Kumar |    1 |
|             3 | HR          |           5 | Neha Singh |    3 |
|             4 | Marketing   |           6 | Priya Nair |    4 |
|             5 | Legal       |        NULL | NULL       | NULL |      <- a department with no employees
```

MySQL itself enforces the relationship:

```text
INSERT INTO a15_employees (...,department_id) VALUES (...,999)
ERROR 1452 (23000): Cannot add or update a child row: a foreign key constraint fails (... CONSTRAINT `fk_a15_employees_department` FOREIGN KEY (`department_id`) REFERENCES `a15_departments` (`department_id`))

DELETE FROM a15_departments WHERE department_id = 1          (Engineering still has employees)
ERROR 1451 (23000): Cannot delete or update a parent row: a foreign key constraint fails (... `fk_a15_employees_department` ...)
```

## Recursive JSON serialization: the problem and the prevention

**The problem.** `Department` → `employees` → each `Employee` → `department` → `employees` → ... is a **cycle** in the object graph. `EntityDemoController` returns the **entities** directly to show both ways this fails.

**1. `GET /api/demo/entity/departments/1/lazy`: the collection was not loaded → 500.**

```json
{"timestamp":"2026-10-11T12:07:30","status":500,"error":"Internal Server Error","message":"Response could not be serialized to JSON","path":"/api/demo/entity/departments/1/lazy"}
```

```text
ERROR GlobalExceptionHandler : JSON serialization failed on /api/demo/entity/departments/1/lazy: org.hibernate.LazyInitializationException: failed to lazily initialize a collection of role: com.company.boot.entity.Department.employees: could not initialize proxy - no Session
```

Jackson touched the LAZY `employees` list **after** the transaction had closed (`open-in-view=false`).

**2. `GET /api/demo/entity/departments/1/fetched`: the collection was loaded → infinite recursion.**

```text
HTTP status 200, Content-Type: application/json, 50 863 characters:
{"id":1,"name":"Engineering","location":"Bengaluru","employees":[{"id":1,"name":"Asha Rao","email":"asha@company.com","salary":95000.00,"department":{"id":1,"name":"Engineering","location":"Bengaluru","employees":[{"id":1,"name":"Asha Rao", ... (repeats) ...
...}]}}]}}]}}]}}]}{"timestamp":"2026-10-11T12:09:02","status":500,"error":"Internal Server Error","message":"Response could not be serialized to JSON","path":"/api/demo/entity/departments/1/fetched"}

PowerShell ConvertFrom-Json on the body -> "JSON INVALID: RecursionLimit exceeded."
```

```text
ERROR GlobalExceptionHandler : JSON serialization failed on /api/demo/entity/departments/1/fetched: com.fasterxml.jackson.core.exc.StreamConstraintsException: Document nesting depth (1001) exceeds the maximum allowed (1000, from `StreamWriteConstraints.getMaxNestingDepth()`)
```

Jackson stopped at 1000 nesting levels, but **the response had already been committed with status 200**. The error handler could only append its JSON to the broken output, so the client received **"success" plus invalid JSON**. That is worse than a clean error. Older Jackson versions without the depth limit end in a `StackOverflowError` instead.

**The prevention used in this project: DTOs.**
- `DepartmentResponse` contains `EmployeeSummary` items, which have **no department field**.
- `EmployeeResponse` refers to its department by **`departmentId` + `departmentName` only**, not by a `Department` object.

Since no DTO points back to its parent, a cycle is impossible, and every real endpoint above returns finite, valid JSON. DTOs are built **inside** the `@Transactional` service, so lazy data is loaded while the session is open. That also removes the `LazyInitializationException`.

Alternatives, not used here because they put JSON concerns on the entities:
- `@JsonManagedReference` on `Department.employees` + `@JsonBackReference` on `Employee.department`
- `@JsonIgnore` on one side
- `@JsonIdentityInfo`

## Optional extension: LAZY vs EAGER, and the N+1 problem

### Loading a list (the same JSON either way, very different SQL)

| Request | Strategy | Statements | SQL |
|---|---|---|---|
| `GET /api/departments?fetch=lazy` | `findAll()`, then touch each LAZY `employees` | **5** (1 + 4 departments) | `select ... from a15_departments ...` then 4 × `select ... from a15_employees e1_0 where e1_0.department_id=?` |
| `GET /api/departments` | `@EntityGraph(attributePaths = "employees")` | **1** | `select d1_0...,e1_0... from a15_departments d1_0 left join a15_employees e1_0 on d1_0.department_id=e1_0.department_id order by ...` |
| `GET /api/employees?fetch=lazy` | `findByOrderByIdAsc()`, then `employee.getDepartment().getName()` | **5** (1 + 4 departments) | `select ... from a15_employees ...` then 4 × `select ... from a15_departments d1_0 where d1_0.department_id=?` |
| `GET /api/employees` | `@EntityGraph(attributePaths = "department")` | **1** | `select e1_0...,d1_0... from a15_employees e1_0 join a15_departments d1_0 on ... order by e1_0.employee_id` |

**N+1 queries:** 1 query for the list, then 1 more **per row** for its lazy association. With 1,000 rows, that would be 1,001 queries. An `@EntityGraph` (or `JOIN FETCH`) loads everything in **one** query.

### LAZY vs EAGER on `Employee.department`

The project uses `@ManyToOne(fetch = LAZY)`. As an experiment, it was temporarily changed to `EAGER`, rebuilt and run on fresh data, then **changed back**; the delivered code is LAZY.

| Request | `LAZY` (delivered) | `EAGER` (experiment) |
|---|---|---|
| `GET /api/employees/1` → `findById` | **2** statements: `select ... from a15_employees where employee_id=?` then `select ... from a15_departments where department_id=?` (only when the DTO reads `departmentName`) | **1** statement: `select e1_0...,d1_0... from a15_employees e1_0 join a15_departments d1_0 on ... where e1_0.employee_id=?` |
| `GET /api/employees?fetch=lazy` → JPQL query | 1 + N: one department select per distinct department, run when the names are read | **still 1 + N**: `select ... from a15_employees ...` then 3 × `select ... from a15_departments where department_id=?`, run **immediately**, even when unused |

**Conclusions:**
- **EAGER** only joins automatically for `findById`/`em.find`. For queries it **still causes N+1**, and it loads the association **every time**, even when nobody needs it, and cannot be switched off per query.
- **LAZY** loads only on access, and a query that needs the association can ask for it explicitly with `@EntityGraph` / `JOIN FETCH`.

The usual recommendation is therefore **LAZY everywhere, plus fetch joins where needed**. `@ManyToOne` defaults to EAGER, so it must be set to LAZY explicitly, as done here.

## Failure / edge-case evidence

| Request | Status | Response | SQL / observation |
|---|---|---|---|
| `POST /api/departments/99/employees` | **404** | `Department not found with id 99` | 1 select, no insert |
| `GET /api/departments/99/employees` | **404** | `Department not found with id 99` | |
| `POST /api/departments/4/employees` `{"email":"ASHA@company.com",...}` | **409** | `An employee with email 'asha@company.com' already exists` | case-insensitive check; no insert |
| `POST /api/departments` `{"name":"engineering"}` | **409** | `Department 'engineering' already exists` | |
| `POST /api/departments/4/employees` `{"name":"","email":"bad","salary":-1}` | **400** | `"validationErrors":{"email":"email must be a valid e-mail address","name":"name is required","salary":"salary must be greater than 0"}` | **no SQL** |
| `POST /api/departments` `{"name":"Legal"}`, then `GET /api/departments/5` | **201**, **200** | `{"id":5,"name":"Legal","location":null,"employeeCount":0,"employees":[]}` | LEFT JOIN returns the department even with no employees |
| `DELETE /api/departments/1/employees/3` (employee 3 is in Finance) | **404** | `Employee in department 1 not found with id 3` | only searches department 1's own collection |
| `DELETE /api/departments/4/employees/7` | **204** | (empty body) | **orphanRemoval:** `delete from a15_employees where employee_id=?`, with no repository delete call; removing it from the list was enough |
| `DELETE /api/departments/2` (Finance, 2 employees) | **204** | (empty body) | **cascade REMOVE:** `delete from a15_employees where employee_id=?` **×2**, then `delete from a15_departments where department_id=?`. The children are deleted first, so the foreign key is satisfied. |
| `GET /api/employees/3` (was in Finance) | **404** | `Employee not found with id 3` | removed by the cascade |

## Explanation

**Owning side.** In a relationship, the owning side is the one whose field **controls the foreign key column**.
- Here that is **`Employee.department`**: its `@JoinColumn(name = "department_id")` maps the FK in `a15_employees`.
- Hibernate writes `department_id` **only** from this field.
- Changing only `department.getEmployees()` without setting `employee.department` would **not** update the column. That is why `Department.addEmployee()` sets **both** sides.
- In a one-to-many relationship, the many side (the table with the FK) is the owner.

**`mappedBy`.** On the inverse (non-owning) side, `@OneToMany(mappedBy = "department")` means "this relationship is already mapped by the `department` field of `Employee`; use that, do not create anything new." Without `mappedBy`, Hibernate would treat it as a **separate** one-to-many and expect an extra **join table** (`a15_departments_employees`). With `ddl-auto=validate`, startup would fail on the missing table.

**`cascade`.** This passes entity operations from parent to children. `cascade = ALL` covers PERSIST, MERGE, REMOVE, REFRESH and DETACH:
- **PERSIST:** a new employee added to a managed department is inserted automatically, with no `save()` call (shown above).
- **REMOVE:** deleting the department deletes its employees first (shown above: 2 child deletes, then the parent).

**`orphanRemoval = true`** goes further: an employee **removed from the collection** is deleted, even though the department still exists. Use cascade only from a parent that truly owns its children's lifecycle; never cascade REMOVE from the many side to the one side.

**`fetch`.** This decides **when** an association is loaded.
- **LAZY:** loaded on first access, through a proxy or persistent collection. It needs an open session, otherwise `LazyInitializationException` (demo 1).
- **EAGER:** always loaded together with the entity, whether needed or not, and still N+1 for queries.

| Annotation | Default fetch |
|---|---|
| `@OneToMany`, `@ManyToMany` | LAZY |
| `@ManyToOne`, `@OneToOne` | EAGER |

This project makes **both** sides LAZY. Whenever an association is really needed, it is fetched in the same query with `@EntityGraph`.

**DTO mapping (optional extension).** Entities model the database, including the cycle. DTOs model **the API response**:
- They are flat or tree-shaped, never cyclic.
- They contain exactly the fields the client should see.
- They are filled inside the transaction.

This prevents recursion and lazy-loading errors, and it keeps the API stable when the entities change.
