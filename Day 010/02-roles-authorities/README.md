# 02 · Roles and Authorities: Endpoint Authorization (RBAC)

An HR application in which **ADMIN, HR, MANAGER and USER** accounts can do different things with employee records. Access is controlled in one place, the `SecurityFilterChain`, with two kinds of check:
- **Roles** with `hasRole` / `hasAnyRole`: *who* someone is (admin pages, HR reports, the team view).
- **Permissions** with `hasAuthority`: *what* someone may do (`EMPLOYEE_READ`, `EMPLOYEE_WRITE`, `EMPLOYEE_DELETE`).

Every role is tested on every endpoint, both in JUnit (70 matrix cases) and in Postman (78 requests).

## Tech

Java 17 · Maven · Spring Boot 3.5.6 · **Spring Security 6.5** · Spring Web · Bean Validation · JUnit 5 (parameterized) + MockMvc + spring-security-test · Postman. No database: users and employees are in memory.

## Structure

```text
02-roles-authorities/
├── pom.xml
├── postman/02-roles-authorities.postman_collection.json   78 requests, 156 checks
└── src/
    ├── main/java/com/company/rbac/
    │   ├── RolesAuthoritiesApplication.java       @SpringBootApplication (run this)
    │   ├── config/
    │   │   ├── SecurityConfig.java                the authorization rules (the matrix, in code)
    │   │   ├── PasswordConfig.java                BCrypt (strength 10)
    │   │   └── PortalUsersProperties.java         users with roles + permissions, from application.properties
    │   ├── security/
    │   │   ├── Role.java, Permission.java         ADMIN/HR/MANAGER/USER and EMPLOYEE_READ/WRITE/DELETE
    │   │   ├── PortalUserDetailsService.java      builds each user's authority list: ROLE_x + permissions
    │   │   └── JsonSecurityErrorHandlers.java     401 / 403 as JSON
    │   ├── controller/                            EmployeeController, HrReportController, AdminController,
    │   │                                          MeController, PublicController (no security code inside)
    │   ├── service/EmployeeService.java           in-memory employees (thread-safe map)
    │   ├── model/, dto/, exception/               Employee, request bodies, ErrorResponse, 400/404 handler
    └── main/resources/application.properties      the six demo users (BCrypt hashes only)
```

## How to run

- **Eclipse:** import as an existing Maven project, then run `RolesAuthoritiesApplication`.
- **Terminal:** `mvn spring-boot:run`. The app runs on `http://localhost:8080`.
- **Postman:** import the collection and run it in order (folder 4 creates the employee that folders 5 to 7 change and delete). From a terminal: `postman collection run postman/02-roles-authorities.postman_collection.json`.
- **Tests:** `mvn test`

Every endpoint uses **HTTP Basic** (Postman: *Authorization → Basic Auth*; curl: `-u hana:Hana@123`).

| User | Password | Role | Permissions | Who they are |
|---|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | READ, WRITE, DELETE | Director (employee 1) |
| `hana` | `Hana@123` | HR | READ, WRITE | HR manager (employee 2) |
| `rita` | `Rita@123` | HR | READ | HR intern: has the HR **role**, but not the WRITE **permission** |
| `manoj` | `Manoj@123` | MANAGER | READ | Engineering manager (employee 3), manages Asha and Ravi |
| `asha` | `Asha@123` | USER | READ | Software engineer (employee 4) |
| `hr-sync` | `Sync@123` | *(none)* | READ, WRITE | Integration account that imports employees: **permissions without a role** |

`rita` and `hr-sync` exist to show that roles and permissions are separate checks. Each one passes one half of a combined rule and fails the other half.

## Access-control matrix

`✔` = allowed, `✘` = 403 Forbidden. Without a login, every endpoint except `/api/public/**` returns 401.

| Endpoint | Rule | admin | hana (HR) | rita (HR, read) | manoj (MGR) | asha (USER) | hr-sync |
|---|---|:-:|:-:|:-:|:-:|:-:|:-:|
| `GET /api/public/hello` | `permitAll()` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `GET /api/me` | `authenticated()` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `GET /api/employees` | `hasAuthority("EMPLOYEE_READ")` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `GET /api/employees/{id}` | `hasAuthority("EMPLOYEE_READ")` | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| `GET /api/employees/team` | `hasAnyRole("MANAGER", "ADMIN")` | ✔ | ✘ | ✘ | ✔ | ✘ | ✘ |
| `POST /api/employees` | `hasAuthority("EMPLOYEE_WRITE")` | ✔ | ✔ | ✘ | ✘ | ✘ | ✔ |
| `PUT /api/employees/{id}` | `hasAuthority("EMPLOYEE_WRITE")` | ✔ | ✔ | ✘ | ✘ | ✘ | ✔ |
| `PATCH /api/employees/{id}/department` | `(HR or ADMIN role) AND EMPLOYEE_WRITE` | ✔ | ✔ | ✘ | ✘ | ✘ | ✘ |
| `DELETE /api/employees/{id}` | `hasAuthority("EMPLOYEE_DELETE")` | ✔ | ✘ | ✘ | ✘ | ✘ | ✘ |
| `GET /api/hr/reports/headcount` | `hasAnyRole("HR", "ADMIN")` | ✔ | ✔ | ✔ | ✘ | ✘ | ✘ |
| `GET /api/admin/users` | `hasRole("ADMIN")` | ✔ | ✘ | ✘ | ✘ | ✘ | ✘ |
| anything else under `/api/employees/**` | `denyAll()` | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |

How this covers the brief:
- **USER reads employees:** everyone with `EMPLOYEE_READ`.
- **HR creates and updates:** `EMPLOYEE_WRITE`. rita is the exception: that account has only READ.
- **MANAGER views team data:** `/team` returns the caller's own direct reports.
- **ADMIN does everything:** ADMIN holds every permission and every role-based area admits ADMIN.
- **The ADMIN+HR endpoint:** `/api/hr/**`.

The same table is the test data of [AccessControlMatrixTest.java](src/test/java/com/company/rbac/AccessControlMatrixTest.java). The `Y`/`-` grid there has the same rows as the table above.

## How the authorization works

### 1. A role is just an authority with a prefix

Spring Security has only **one** concept: each logged-in user carries a list of `GrantedAuthority` strings. A **role** is a naming convention: an authority whose name starts with `ROLE_`.

[PortalUserDetailsService.java](src/main/java/com/company/rbac/security/PortalUserDetailsService.java) builds each list: roles get the prefix, permissions do not.

```text
hana      ROLE_HR,    EMPLOYEE_READ, EMPLOYEE_WRITE
rita      ROLE_HR,    EMPLOYEE_READ
hr-sync               EMPLOYEE_READ, EMPLOYEE_WRITE
```

`GET /api/me` shows the list exactly as Spring Security sees it:

```text
$ curl -u rita:Rita@123 localhost:8080/api/me
{"authorities":["EMPLOYEE_READ","ROLE_HR"],"username":"rita"}
```

### 2. `ROLE_ADMIN` versus `ADMIN`, and `hasRole` versus `hasAuthority`

| Check | Looks for the string | Matches `ROLE_ADMIN`? | Matches `ADMIN`? |
|---|---|:-:|:-:|
| `hasRole("ADMIN")` | `ROLE_ADMIN` (adds the prefix itself) | ✔ | ✘ |
| `hasAuthority("ROLE_ADMIN")` | `ROLE_ADMIN` (exact) | ✔ | ✘ |
| `hasAuthority("ADMIN")` | `ADMIN` (exact) | ✘ | ✔ |
| `hasRole("ROLE_ADMIN")` | **refused at startup** (it would look for `ROLE_ROLE_ADMIN`) | | |

So `hasRole("ADMIN")` is exactly the same check as `hasAuthority("ROLE_ADMIN")`. The difference is only which name you write. Likewise, `.roles("ADMIN")` on a user is a shortcut for `.authorities("ROLE_ADMIN")`.

**When to use which:**
- `hasRole` for **coarse areas** that follow a job: the admin pages, HR reports, the team view.
- `hasAuthority` for **specific operations**. Permissions can be given out independently of job titles: the HR intern can read without writing, and the integration account can write without being "HR".

Two traps, both covered by tests in [RoleVersusAuthorityTest.java](src/test/java/com/company/rbac/RoleVersusAuthorityTest.java):
- **Writing the prefix in config.** `roles=ROLE_ADMIN` in `application.properties` would store `ROLE_ROLE_ADMIN`. Roles here are an enum, so this value fails to bind and the app does not start.
- **The `User` builder.** `roles(...)` and `authorities(...)` each **replace** the whole list. `User.withUsername("x").roles("HR").authorities("EMPLOYEE_READ")` ends up with only `EMPLOYEE_READ`, so the role is silently lost. That's why the service builds one list and calls `authorities(...)` once.

Authorities are **not inherited**. A user with only `ROLE_ADMIN` and no permissions gets 403 on `GET /api/employees`. ADMIN can do everything here only because the admin account is given every permission. (Spring Security's `RoleHierarchy` could make ADMIN imply the others. This project keeps it explicit so the matrix stays readable.)

### 3. Combining a role and an authority

A department transfer is an **HR decision**, so holding `EMPLOYEE_WRITE` is not enough. The rule needs both checks:

```java
.requestMatchers(HttpMethod.PATCH, "/api/employees/*/department").access(hrDecisionWithWriteAccess())

private static AuthorizationManager<RequestAuthorizationContext> hrDecisionWithWriteAccess() {
    return AuthorizationManagers.allOf(
            hasAnyRole("HR", "ADMIN"),          // who: an HR person (or the admin)
            hasAuthority("EMPLOYEE_WRITE"));    // what: allowed to change records
}
```

| User | HR or ADMIN role? | `EMPLOYEE_WRITE`? | Result |
|---|:-:|:-:|---|
| hana | ✔ | ✔ | 200 |
| rita | ✔ | ✘ | 403 (role without the permission) |
| hr-sync | ✘ | ✔ | 403 (permission without the role) |

`AuthorizationManagers.allOf` / `anyOf` are the type-safe Spring Security 6 way to combine rules. The older string form, `access("hasRole('HR') and hasAuthority('EMPLOYEE_WRITE')")`, is only checked when it runs, so a typo is not caught at compile time.

### 4. Rule order and deny by default

The rules in [SecurityConfig.java](src/main/java/com/company/rbac/config/SecurityConfig.java) are checked **top to bottom, and the first match decides**:

```text
/api/public/**, /error                      permitAll
/api/admin/**                               hasRole ADMIN
/api/hr/**                                  hasAnyRole HR, ADMIN
GET    /api/employees/team                  hasAnyRole MANAGER, ADMIN     <- must be ABOVE the next line
GET    /api/employees, /api/employees/*     hasAuthority EMPLOYEE_READ
POST   /api/employees                       hasAuthority EMPLOYEE_WRITE
PUT    /api/employees/*                     hasAuthority EMPLOYEE_WRITE
PATCH  /api/employees/*/department          (HR or ADMIN) AND EMPLOYEE_WRITE
DELETE /api/employees/*                     hasAuthority EMPLOYEE_DELETE
/api/employees/**                           denyAll                       <- anything not listed
any other request                           authenticated
```

**Why order matters, with real evidence.** `GET /api/employees/team` also matches `GET /api/employees/*`. As an experiment, the team rule was moved below the read rule and the matrix test was run again:

```text
[ERROR] Tests run: 70, Failures: 4
[ERROR]   AccessControlMatrixTest.accessMatchesTheMatrix:84 Response status expected:<403> but was:<200>   (x4)
```

The read rule matched first, so hana, rita, asha and hr-sync could all see the team list. With the original order restored, all 70 cases pass.

**Deny by default.** The `denyAll()` line closes every path and method under `/api/employees/**` that no rule mentions. So a new endpoint is refused, even for ADMIN, until someone writes a rule for it. Two checks show this:
- `PATCH /api/employees/4` has no rule (only `/department` is listed), so admin gets 403.
- Neither does `GET /api/employees/4/salary`, so admin gets 403 there too.

`/error` is `permitAll` so that Spring Boot's error page can still be rendered after a request has already been allowed.

### 5. Where the decision happens: 401, 403 and the controller

```text
request ─► BasicAuthenticationFilter     reads "Authorization: Basic ...", checks the BCrypt hash
              │ wrong / missing ─────────► ExceptionTranslationFilter ─► 401 JSON (AuthenticationEntryPoint)
              ▼
           AuthorizationFilter           walks the rules above; first match decides
              │ denied ──────────────────► ExceptionTranslationFilter ─► 403 JSON (AccessDeniedHandler)
              ▼
           DispatcherServlet ─► controller ─► validation (400), not found (404), success (200/201/204)
```

Because security runs **before** the controller:
- An invalid body from asha gets **403**, not 400. The request is stopped before validation runs.
- The same body from hana gets **400** with the field errors.
- A 404 means "you were allowed in, but the employee does not exist".

The DEBUG log shows the difference between two transfer attempts on the same URL:

```text
Securing PATCH /api/employees/4/department
Set SecurityContextHolder to UsernamePasswordAuthenticationToken [... Username=hr-sync ...]
                                                     (no "Secured" line: AuthorizationFilter refused it -> 403)
Securing PATCH /api/employees/4/department
Set SecurityContextHolder to UsernamePasswordAuthenticationToken [... Username=hana ...]
Secured PATCH /api/employees/4/department            (allowed -> the controller runs)
```

The chain itself (from the startup log): `DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter, SecurityContextHolderFilter, HeaderWriterFilter, LogoutFilter, BasicAuthenticationFilter, RequestCacheAwareFilter, SecurityContextHolderAwareRequestFilter, AnonymousAuthenticationFilter, SessionManagementFilter, ExceptionTranslationFilter, AuthorizationFilter`.

### 6. Why BCrypt strength 10 here

With HTTP Basic the password is sent with **every** request, so BCrypt runs on every request. The strength experiment in `01-basic-security` measured about 140 ms per check at strength 10 and about 540 ms at 12. This project sends hundreds of requests in its tests, so it uses 10, which is Spring's default. Token-based login (the JWT project) checks the password only once, at login.

## Evidence

```text
$ curl -u asha:Asha@123 localhost:8080/api/employees/team
HTTP/1.1 403
{"timestamp":"2026-10-25T13:53:05.474840700Z","status":403,"error":"Forbidden",
 "message":"You do not have permission to access this resource","path":"/api/employees/team"}

$ curl -u manoj:Manoj@123 localhost:8080/api/employees/team
[{"id":4,"name":"Asha Verma",...,"managerId":3},{"id":5,"name":"Ravi Iyer",...,"managerId":3}]

$ curl -u hr-sync:Sync@123 -X PATCH -H "Content-Type: application/json" \
       -d '{"department":"Finance"}' localhost:8080/api/employees/4/department
HTTP/1.1 403
{"timestamp":"...","status":403,"error":"Forbidden","message":"You do not have permission to access this resource",
 "path":"/api/employees/4/department"}

$ curl -u rita:Rita@123 localhost:8080/api/hr/reports/headcount          (the ADMIN+HR endpoint)
{"Engineering":3,"Finance":1,"Human Resources":2,"Management":1}

$ curl -u admin:Admin@123 localhost:8080/api/admin/users                 (no password hashes)
[{"username":"admin","roles":["ADMIN"],"permissions":["EMPLOYEE_READ","EMPLOYEE_WRITE","EMPLOYEE_DELETE"]},
 {"username":"asha","roles":["USER"],"permissions":["EMPLOYEE_READ"]}, ...
 {"username":"hr-sync","roles":[],"permissions":["EMPLOYEE_READ","EMPLOYEE_WRITE"]}, ...]

$ curl localhost:8080/api/employees
{"timestamp":"...","status":401,"error":"Unauthorized","message":"Authentication is required to access this resource",
 "path":"/api/employees"}
```

### Postman

The collection has one folder per rule. Each folder sends the request as every user, plus once without a login, and checks the exact status and the JSON error body.

```text
postman collection run postman/02-roles-authorities.postman_collection.json

|              iterations |                   1 |                   0 |
|                requests |                  78 |                   0 |
|            test-scripts |                  78 |                   0 |
|              assertions |                 156 |                   0 |
```

| Folder | Requests |
|---|---|
| 0. Public endpoint, wrong password, `/api/me` for each user (checks the exact authority list) | 9 |
| 1–3. Read all, read one, team view | 21 |
| 4. Create (saves the new id), plus a 400 validation case | 8 |
| 5–7. Update, transfer, delete that employee, then a 404 and clean-up | 24 |
| 8–9. HR report, admin user list | 14 |
| 10. Deny by default (admin on two paths with no rule) | 2 |

## Tests: 86 passing

```text
mvn test
Tests run: 70, Failures: 0, Errors: 0 -- in com.company.rbac.AccessControlMatrixTest
Tests run: 8,  Failures: 0, Errors: 0 -- in com.company.rbac.EmployeeApiTest
Tests run: 8,  Failures: 0, Errors: 0 -- in com.company.rbac.RoleVersusAuthorityTest
```

| Test class | What it proves |
|---|---|
| `AccessControlMatrixTest` | 6 real users × 10 endpoints = 60 cases with the exact expected status, plus 10 cases without a login (401). |
| `EmployeeApiTest` | What allowed users get back: the team is the caller's direct reports; HR creates and transfers, then admin deletes (201, 200, 204, then 404). Also: 401/403/400/404 JSON bodies, 400 for HR but 403 for USER on the same invalid body, and the admin user list never contains a hash. |
| `RoleVersusAuthorityTest` | `hasRole` needs `ROLE_`; `hasAuthority` compares the exact string; ADMIN does not inherit permissions; `hasRole("ROLE_ADMIN")` is refused; the combined rule needs both halves; deny by default applies even to admin; the real users' authority lists; the `User` builder overwrite trap; `ROLE_ADMIN` in the config fails to bind. |

## Challenge extensions covered

- **`EMPLOYEE_READ/WRITE/DELETE` permissions:** the `Permission` enum, used for all employee CRUD rules.
- **Combining role and authority checks:** the department transfer, `AuthorizationManagers.allOf(hasAnyRole("HR","ADMIN"), hasAuthority("EMPLOYEE_WRITE"))`, shown with rita and hr-sync each failing a different half.
- **An ADMIN+HR endpoint:** `GET /api/hr/reports/headcount` (`/api/hr/**` → `hasAnyRole("HR", "ADMIN")`).
