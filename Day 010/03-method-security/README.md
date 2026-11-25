# 03 · Method-Level Security: @PreAuthorize, @PostAuthorize and @Secured

An HR service whose sensitive **business methods** each carry their own authorization rule:

| Operation | Who may do it |
|---|---|
| Read an employee | HR, ADMIN, the employee themselves, or their line manager |
| Read a salary | HR, ADMIN, or the employee themselves |
| Update a salary | HR or ADMIN, never on their own record |
| Change a designation | HR, ADMIN, or the line manager |
| List a team | HR, ADMIN, or the manager of that team |
| Delete an employee | ADMIN only |

The scenario from the brief is built in: a second controller, `HrToolsController`, exposes the same service methods under `/api/tools/**`, a path with **no URL rule of its own**. The methods stay protected anyway.

## Tech

Java 17 · Maven · Spring Boot 3.5.6 · **Spring Security 6.5** (`@EnableMethodSecurity`) · Spring Web · Bean Validation · JUnit 5 + MockMvc + spring-security-test (`@WithMockUser`) · Postman. No database: users and employees are in memory.

## Structure

```text
03-method-security/
├── pom.xml
├── postman/03-method-security.postman_collection.json   59 requests, 113 checks
└── src/
    ├── main/java/com/company/methodsec/
    │   ├── MethodSecurityApplication.java          @SpringBootApplication (run this)
    │   ├── config/
    │   │   ├── SecurityConfig.java                 @EnableMethodSecurity + the (coarse) URL chain
    │   │   ├── PasswordConfig.java                 BCrypt (strength 10)
    │   │   └── PortalUsersProperties.java          demo users from application.properties
    │   ├── service/EmployeeService.java            ★ the secured methods (all the rules are here)
    │   ├── security/
    │   │   ├── EmployeeAccess.java                 custom authorization bean: isSelf, isManagerOf
    │   │   ├── PortalUserDetailsService.java       in-memory users
    │   │   └── JsonSecurityErrorHandlers.java      401 / 403 JSON
    │   ├── repository/EmployeeRepository.java      unsecured in-memory storage
    │   ├── controller/
    │   │   ├── EmployeeController.java             the main API
    │   │   ├── HrToolsController.java              the "other controller": same methods, no URL rule
    │   │   └── PublicController.java
    │   ├── dto/, model/, exception/                responses without salary, request bodies, 400/404
    └── main/resources/application.properties
```

## How to run

- **Eclipse:** import as an existing Maven project, then run `MethodSecurityApplication`. The `#id` parameter names in the rules need the compiler's `-parameters` flag. The Spring Boot parent POM turns it on, and Eclipse (m2e) picks it up from the POM.
- **Terminal:** `mvn spring-boot:run`. The app runs on `http://localhost:8080`.
- **Postman:** run `postman/03-method-security.postman_collection.json` on a **freshly started** app. The last folder deletes employees 6 and 7, so restart the app before running it again.
- **Tests:** `mvn test`

Every endpoint uses HTTP Basic. Each login belongs to the employee record with the same username:

| User | Password | Role | Employee |
|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | 1 Aditi Rao, Director; manages 2, 3 and 7 |
| `hana` | `Hana@123` | HR | 2 Hana Kapoor, HR Manager; manages 6 |
| `manoj` | `Manoj@123` | MANAGER | 3 Manoj Pillai, Engineering Manager; manages 4 and 5 |
| `asha` | `Asha@123` | USER | 4 Asha Verma, Software Engineer |
| `ravi` | `Ravi@123` | USER | 5 Ravi Iyer, Software Engineer |

Employees 6 (Meena Das, HR) and 7 (Karan Shah, Finance) have no login.

## The rules ([EmployeeService.java](src/main/java/com/company/methodsec/service/EmployeeService.java))

```java
@PreAuthorize("isAuthenticated()")
public List<Employee> findAll()

@PostAuthorize("hasAnyRole('HR', 'ADMIN')"
        + " or returnObject.username() == authentication.name"
        + " or @employeeAccess.isManagerOf(returnObject.id(), authentication)")
public Employee getEmployee(Long id)

@PreAuthorize("hasAnyRole('HR', 'ADMIN') or @employeeAccess.isSelf(#id, authentication)")
public BigDecimal getSalary(Long id)

@PreAuthorize("hasAnyRole('HR', 'ADMIN') and !@employeeAccess.isSelf(#id, authentication)")
public Employee updateSalary(Long id, BigDecimal newSalary)

@PreAuthorize("hasAnyRole('HR', 'ADMIN') or @employeeAccess.isManagerOf(#id, authentication)")
public Employee updateDesignation(Long id, String designation)

@PreAuthorize("hasAnyRole('HR', 'ADMIN') or (hasRole('MANAGER') and @employeeAccess.isSelf(#managerId, authentication))")
public List<Employee> findTeam(Long managerId)

@Secured("ROLE_ADMIN")
public void deleteEmployee(Long id)
```

What the expressions can use:
- `#id`: a method **parameter**, by name.
- `returnObject`: the **returned value** (only in `@PostAuthorize`).
- `authentication`: the logged-in user from the `SecurityContext`.
- `@employeeAccess`: a **Spring bean**, here the custom ownership rules.

### Authorization matrix

`✔` = allowed, `✘` = 403. These are the rows of [HttpAuthorizationMatrixTest.java](src/test/java/com/company/methodsec/HttpAuthorizationMatrixTest.java).

| Request (service method) | admin | hana (HR) | manoj (MGR of 4, 5) | asha (emp 4) | ravi (emp 5) |
|---|:-:|:-:|:-:|:-:|:-:|
| `GET /api/employees` (`findAll`) | ✔ | ✔ | ✔ | ✔ | ✔ |
| `GET /api/employees/4` (`getEmployee`) | ✔ | ✔ | ✔ manager | ✔ self | ✘ |
| `GET /api/employees/4/salary` (`getSalary`) | ✔ | ✔ | ✘ | ✔ self | ✘ |
| `PUT /api/employees/4/salary` (`updateSalary`) | ✔ | ✔ | ✘ | ✘ | ✘ |
| `PUT /api/employees/2/salary`, hana's own record | ✔ | ✘ **own** | ✘ | ✘ | ✘ |
| `PATCH /api/employees/4/designation` (`updateDesignation`) | ✔ | ✔ | ✔ manager | ✘ | ✘ |
| `GET /api/managers/3/team` (`findTeam`) | ✔ | ✔ | ✔ own team | ✘ | ✘ |
| `GET /api/managers/2/team` | ✔ | ✔ | ✘ not theirs | ✘ | ✘ |
| `DELETE /api/employees/7` (`deleteEmployee`) | ✔ | ✘ | ✘ | ✘ | ✘ |
| `GET /api/tools/salary/5` (`getSalary`, other controller) | ✔ | ✔ | ✘ | ✘ | ✔ self |
| `POST /api/tools/offboard/7` (`deleteEmployee`, other controller) | ✔ | ✘ | ✘ | ✘ | ✘ |

Responses never contain a salary except from the two salary endpoints. List and detail responses use DTOs without that field.

## How it works

### 1. Two layers: URL security and method security

[SecurityConfig.java](src/main/java/com/company/methodsec/config/SecurityConfig.java) turns on both layers:

```java
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)    // @PreAuthorize/@PostAuthorize (default) + @Secured
public class SecurityConfig {
    ...
        .requestMatchers("/api/public/**", "/error").permitAll()
        .requestMatchers(HttpMethod.DELETE, "/api/employees/**").hasRole("ADMIN")   // defence in depth
        .anyRequest().authenticated()                                               // the rest: just "logged in"
```

```text
request ─► URL chain (filters)            "logged in?"  "DELETE on /api/employees/** needs ADMIN"
             │ no login ─────────────────► 401
             │ URL rule fails ───────────► 403
             ▼
           controller ─► @Valid body ────► 400
             ▼
           EmployeeService PROXY          checks @PreAuthorize / @Secured  ─► AccessDeniedException ─► 403
             ▼
           the real method runs
             ▼
           PROXY again                    checks @PostAuthorize on the returned object ─► 403
```

`@EnableMethodSecurity` makes Spring wrap `EmployeeService` in a **proxy**. Every call from another bean goes through it. When a rule fails, the proxy throws `AccessDeniedException` (Spring Security 6: `AuthorizationDeniedException`). The exception travels up out of Spring MVC to the `ExceptionTranslationFilter`, which calls the same `AccessDeniedHandler`. So a method denial produces the same 403 JSON as a URL denial.

For that reason [GlobalExceptionHandler.java](src/main/java/com/company/methodsec/exception/GlobalExceptionHandler.java) deliberately has **no** catch-all `Exception` handler. A catch-all would swallow the denial and return 500.

### 2. Why method security complements URL security: the experiment

URL rules only know the HTTP method and the path. `HrToolsController` exposes `getSalary` and `deleteEmployee` under `/api/tools/**`, where the URL chain only asks "logged in?".

To measure the difference, `@EnableMethodSecurity` was commented out and the HTTP matrix test was run again:

```text
[ERROR] Tests run: 59, Failures: 24
```

| What leaked with only URL security | Why |
|---|---|
| ravi read Asha's record; manoj and asha read other salaries | no rule looks at **which** employee |
| manoj, asha and ravi changed salaries; **hana raised the HR account's own salary** | only "logged in" at the URL level |
| asha and ravi listed teams; manoj listed another manager's team | the parameter (`managerId`) is invisible to a URL rule |
| **hana, manoj, asha and ravi deleted employee 7** via `POST /api/tools/offboard/7` | the URL rule covers `DELETE /api/employees/**`, not this path |
| `DELETE /api/employees/7` **stayed protected** | the URL rule still applied there |

The last two rows are the scenario from the brief. A URL rule protects **one path**. A method rule protects **the operation**, whichever path, controller, scheduled job or other service reaches it. With the annotation restored, all 59 cases pass again.

| | URL security (`SecurityFilterChain`) | Method security (`@PreAuthorize` …) |
|---|---|---|
| Runs | in a filter, before any controller | in a proxy, around the service method |
| Knows | HTTP method, path, user | user, **parameters**, **return value**, other beans |
| Protects | one URL pattern | the operation, from **every** caller |
| Good for | coarse gates: login required, admin area, public pages | business rules: ownership, "not your own salary" |
| Without a web request | does nothing | still applies (a job or test with no user is refused) |
| Cost of a mistake | one forgotten path is open | one forgotten annotation is open |

Using **both** is defence in depth. Deleting employees is checked twice: by the URL rule for `DELETE /api/employees/**`, and by `@Secured` on the method.

### 3. Parameter-based ownership and the custom authorization bean

The ownership rules need data: which employee belongs to which login, and who manages whom. They live in [EmployeeAccess.java](src/main/java/com/company/methodsec/security/EmployeeAccess.java), a normal bean that the annotations call by name:

```java
@Component("employeeAccess")
public class EmployeeAccess {
    public boolean isSelf(Long employeeId, Authentication authentication)      // my own record?
    public boolean isManagerOf(Long employeeId, Authentication authentication) // my direct report?
}
```

```java
@PreAuthorize("hasAnyRole('HR', 'ADMIN') or @employeeAccess.isSelf(#id, authentication)")
public BigDecimal getSalary(Long id)
```

`#id` is the method's parameter. When asha calls `getSalary(4)`, `isSelf(4, asha)` is true. With `getSalary(5)` it is false, so the result is 403.

Why a bean rather than a long expression:
- The annotation stays readable.
- The logic is plain Java, so it is unit-tested without Spring in [EmployeeAccessTest.java](src/test/java/com/company/methodsec/EmployeeAccessTest.java).
- The same rule is reused by several methods.

The bean reads the **unsecured** repository on purpose. Calling the secured service from inside a rule would trigger the rules again.

**Manager ownership** (`isManagerOf`) checks only **direct** reports:
- manoj may change Asha's designation, but not Meena's (Meena reports to hana).
- admin manages manoj, but not Asha.

`findTeam` combines a role and ownership: `hasRole('MANAGER') and isSelf(#managerId)`. asha calling `findTeam(4)` passes `isSelf`, but asha is not a manager, so the result is 403.

**Separation of duties:** `updateSalary` uses `and !@employeeAccess.isSelf(#id, authentication)`. Even HR and ADMIN cannot change **their own** salary, so somebody else must do it.

### 4. The @PostAuthorize experiment

`@PostAuthorize` checks the rule **after** the method has returned, against `returnObject`. This is useful when the decision depends on data that is only known once it is loaded, here the employee's `username` and manager.

Observed behaviour (tests in `ServiceMethodSecurityTest.PostAuthorizeExperiment`, plus the live log):

| Call | Result | What it shows |
|---|---|---|
| asha → `getEmployee(4)` | returned | own record (`returnObject.username() == authentication.name`) |
| manoj → `getEmployee(5)` | returned | line manager (`isManagerOf(returnObject.id(), …)`) |
| asha → `getEmployee(5)` | **403**, but the log says `Loading employee 5` | **the method ran first**, then the result was refused |
| asha → `getEmployee(999)` | **404** | the method failed before the rule was ever checked |

```text
INFO ... c.c.methodsec.service.EmployeeService : Loading employee 5     <- asha was then denied (403)
INFO ... c.c.methodsec.service.EmployeeService : Loading employee 999   <- 404, no rule checked
```

Lessons:
1. **Never put `@PostAuthorize` on a method with side effects** (saving, deleting, sending mail). The work is already done by the time the rule says no. That's why all the write methods use `@PreAuthorize`.
2. **It can leak existence.** asha gets 404 for an id that doesn't exist and 403 for a colleague's id, so the caller can tell which ids exist. With `@PreAuthorize` the rule would run before the lookup.
3. It costs a database read even for callers who will be refused.

### 5. @Secured versus @PreAuthorize

`deleteEmployee` uses `@Secured("ROLE_ADMIN")`. Everything else uses `@PreAuthorize`.

| | `@Secured("ROLE_ADMIN")` | `@PreAuthorize("hasRole('ADMIN')")` |
|---|---|---|
| Switched on by | `@EnableMethodSecurity(securedEnabled = true)` | `@EnableMethodSecurity` (on by default) |
| Value | a list of authority names, **with** `ROLE_` written out; several names = OR | a SpEL expression |
| Parameters, return value, beans, `and`/`not` | ✘ | ✔ `#id`, `returnObject`, `@employeeAccess`, `and`, `or`, `!` |
| Checked | before the method | before (`@PreAuthorize`), or after (`@PostAuthorize`) |
| Use when | a plain role check is all you need | anything involving ownership or conditions |

`@Secured("ROLE_ADMIN")` and `@PreAuthorize("hasRole('ADMIN')")` do exactly the same thing for `deleteEmployee`. But a rule such as "HR, but not on their own record" **cannot** be written with `@Secured`. (JSR-250's `@RolesAllowed`, enabled with `jsr250Enabled = true`, is the standard Java equivalent of `@Secured`.)

### 6. Things to watch

- **Self-invocation is not checked.** The rules live in the proxy, so a call from inside `EmployeeService` to its own method (`this.updateSalary(...)`) skips the check. Secured methods should be called from other beans.
- **Validation runs first.** `@Valid` on the controller runs before the service method. So asha sending `{"salary": -5}` gets **400**, not 403. URL rules run before validation, so 02's URL-only API answered 403 there.
- **No user means refused.** A call with no `Authentication` at all (for example a scheduled job) gets `AuthenticationCredentialsNotFoundException`. Method security does not depend on HTTP.

## Evidence

```text
$ curl -u asha:Asha@123 localhost:8080/api/tools/salary/5          (no URL rule; getSalary's rule stops it)
HTTP/1.1 403
{"timestamp":"2026-10-25T14:01:58.245201700Z","status":403,"error":"Forbidden",
 "message":"You do not have permission to access this resource","path":"/api/tools/salary/5"}

$ curl -u ravi:Ravi@123 localhost:8080/api/tools/salary/5          (own salary)
{"employeeId":5,"salary":92000}

$ curl -u hana:Hana@123 -X PUT -H "Content-Type: application/json" -d '{"salary":999999}' \
       localhost:8080/api/employees/2/salary                       (HR, but hana's own record)
HTTP/1.1 403

$ curl -u asha:Asha@123 localhost:8080/api/employees/5              (@PostAuthorize: a colleague)
HTTP/1.1 403

$ curl -u asha:Asha@123 localhost:8080/api/employees/999            (@PostAuthorize: unknown id)
HTTP/1.1 404
{"timestamp":"...","status":404,"error":"Not Found","message":"Employee 999 not found","path":"/api/employees/999"}

$ curl -u manoj:Manoj@123 localhost:8080/api/managers/3/team         (own team)
[{"id":4,"name":"Asha Verma","department":"Engineering"},{"id":5,"name":"Ravi Iyer","department":"Engineering"}]
```

### Postman

There is one folder per service method. Each folder sends the request as the users for whom the answer differs, and checks the exact status and body. Folder 7 calls the same methods through `HrToolsController`.

```text
postman collection run postman/03-method-security.postman_collection.json

|                requests |                  59 |                   0 |
|            test-scripts |                  59 |                   0 |
|              assertions |                 113 |                   0 |
```

## Tests: 80 passing

```text
mvn test
EmployeeAccessTest            3 tests   0 failures
HttpAuthorizationMatrixTest  59 tests   0 failures
ServiceMethodSecurityTest    18 tests   0 failures   (in @Nested groups, one per method)
```

| Test class | What it proves |
|---|---|
| `ServiceMethodSecurityTest` | The rules on the **service itself**, with no HTTP, controller or URL rule. `@WithMockUser(username = "asha", roles = "USER")` puts a user into the `SecurityContext`, then the method is called directly. Covered: no user at all is refused; salary read (HR, own, not a colleague's, not by a manager); salary update (HR yes, own record no, ADMIN own record no, manager no); `@Secured` delete; manager ownership for designation and team; the `@PostAuthorize` experiment (method ran before the denial, captured from the log; unknown id gives 404). |
| `HttpAuthorizationMatrixTest` | The matrix above: 5 real users × 11 requests over HTTP Basic. Also: a method denial gives the standard 403 JSON; no login gives 401 from the URL chain; responses contain no salary; an invalid body gives 400 before the method rule runs. |
| `EmployeeAccessTest` | The custom authorization bean as plain Java: `isSelf`, `isManagerOf` (direct reports only), unknown users and ids. |

Data is in memory, so the tests that change it call `repository.resetToSampleData()` before each case.

## Challenge extensions covered

- **Custom authorization bean:** `EmployeeAccess` (`@employeeAccess.isSelf`, `@employeeAccess.isManagerOf`), unit-tested on its own.
- **Manager ownership checks:** `updateDesignation` for direct reports only, and `findTeam` for the manager's own team only.
- **URL vs method security compared:** the comparison table in section 2, plus the measured experiment (24 failures with method security off, including deletes through the second controller).
