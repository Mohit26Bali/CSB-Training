# 06 · Secure Employee REST API (JWT + RBAC) with Custom 401/403 Errors

A production-style employee service:
- **Login and roles:** JWT login; ADMIN, HR, MANAGER and EMPLOYEE roles with ownership rules.
- **Data protection:** DTOs that never expose more than allowed, and validation.
- **Salary:** stricter, audited salary rules.
- **Errors:** **one JSON error contract** for every error, with a correlation id. Security errors and controller errors are told apart by a `code` field.

## Tech

Java 17 · Spring Boot 3.5.6 · Spring Security 6.5 (`@EnableMethodSecurity`) · jjwt 0.12.6 · Spring Data JPA · MySQL 8 · Flyway · JUnit 5 + MockMvc · Postman.

## How to run

1. MySQL on `localhost:3306`.
2. In `src/main/resources/` (both files are git-ignored):
   - `db-secret.properties`: `spring.datasource.password=...`
   - `jwt-secret.properties`: `portal.jwt.secret=<openssl rand -base64 32>`
3. Run `SecureApiApplication` (Eclipse) or `mvn spring-boot:run`. The database `hr_secure_day10` is created by Flyway.
4. `mvn test` uses `hr_secure_day10_test` and a test-only key. Postman: `postman collection run postman/06-secure-rest-api.postman_collection.json` (re-runnable).

| User | Password | Role | Employee |
|---|---|---|---|
| admin | Admin@123 | ADMIN | 1 Aditi Rao (manages 2, 3, 7) |
| hana | Hana@123 | HR | 2 Hana Kapoor |
| manoj | Manoj@123 | MANAGER | 3 Manoj Pillai (manages 4, 5) |
| asha | Asha@123 | EMPLOYEE | 4 Asha Verma |
| ravi | Ravi@123 | EMPLOYEE | 5 Ravi Iyer |

The token carries `sub` (username), `emp` (employee id) and `authorities`. The ownership rules ("own record", "my team") need no database lookup to find who is asking.

## Authorization matrix

Rules live on `EmployeeService` (`@PreAuthorize`). The URL chain only requires a token, plus ADMIN for `/api/admin/**`. `✔` = allowed, `✘` = 403 `ACCESS_DENIED`. Without a token, every endpoint gives 401 `AUTH_REQUIRED`.

| Endpoint | Rule | admin | hana | manoj | asha | ravi |
|---|---|:-:|:-:|:-:|:-:|:-:|
| `GET /api/employees` (summary: id, name, dept, designation) | logged in | ✔ | ✔ | ✔ | ✔ | ✔ |
| `GET /api/employees/{id}` (detail, no salary), e.g. id 4 | HR/ADMIN, self, line manager | ✔ | ✔ | ✔ | ✔ self | ✘ |
| `GET /api/employees/team` | MANAGER (own reports) | ✘ | ✘ | ✔ | ✘ | ✘ |
| `GET /api/employees/{id}/salary`, e.g. id 4 | HR/ADMIN, self | ✔ | ✔ | ✘ | ✔ self | ✘ |
| `PUT /api/employees/{id}/salary`, e.g. id 7 | HR/ADMIN, **not own** | ✔ | ✔ | ✘ | ✘ | ✘ |
| `PUT /api/employees/2/salary` (hana's own) | | ✔ | ✘ own | ✘ | ✘ | ✘ |
| `GET /api/employees/{id}/salary/history` | HR/ADMIN | ✔ | ✔ | ✘ | ✘ | ✘ |
| `POST /api/employees`, `PUT /api/employees/{id}` | HR/ADMIN | ✔ | ✔ | ✘ | ✘ | ✘ |
| `DELETE /api/employees/{id}` | ADMIN | ✔ | ✘ | ✘ | ✘ | ✘ |
| `GET/POST /api/admin/users` (manage logins) | ADMIN | ✔ | ✘ | ✘ | ✘ | ✘ |

**Salary is protected more strictly:**
1. It is not in the summary or detail DTOs, only in `/salary`.
2. Only HR/ADMIN or the employee can read it.
3. Only HR/ADMIN can change it, never on their own record.
4. The update DTO for an employee has no salary field.
5. Every change writes a row to `salary_audit` and an `audit_event=SALARY_CHANGED` log line.

## The error contract

Every error, from the security filters or from a controller, is an `ApiError`:

```json
{"timestamp":"2026-10-25T17:31:42.593977300Z","status":403,"error":"Forbidden","code":"ACCESS_DENIED",
 "message":"You do not have permission to access this resource","path":"/api/employees/7",
 "correlationId":"36ae07b4-f2db-46ca-880e-76234e85927f"}
```

| code | status | written by | when |
|---|---|---|---|
| `AUTH_REQUIRED` | 401 | `AuthenticationEntryPoint` | no token |
| `AUTH_INVALID_TOKEN` | 401 | `AuthenticationEntryPoint` | malformed, bad signature, expired |
| `AUTH_BAD_CREDENTIALS` | 401 | `GlobalExceptionHandler` | wrong login |
| `ACCESS_DENIED` | 403 | `AccessDeniedHandler` | valid token, role/ownership rule fails (URL **or** `@PreAuthorize`) |
| `VALIDATION_FAILED`, `MALFORMED_REQUEST` | 400 | `GlobalExceptionHandler` | bad body |
| `EMPLOYEE_NOT_FOUND` | 404 | `GlobalExceptionHandler` | unknown id |
| `DUPLICATE` | 409 | `GlobalExceptionHandler` | e-mail / username / login already used |

**Keeping security and controller errors apart.** Both use the same shape, but the `code` tells them apart.
- The security handlers (`JsonSecurityErrorHandlers`) write `AUTH_*` and `ACCESS_DENIED`.
- `GlobalExceptionHandler` writes the rest.
- It deliberately has **no** `AccessDeniedException` or catch-all handler. A `@PreAuthorize` denial must reach Spring Security's `ExceptionTranslationFilter`, which calls the `AccessDeniedHandler`. A catch-all would turn every 403 into a 500.

**Correlation ids** ([CorrelationIdFilter.java](src/main/java/com/company/secureapi/config/CorrelationIdFilter.java)):
- Each request uses the client's `X-Correlation-Id`, or a new UUID if the client sent none.
- The id is put in the MDC, the response header, the error body and **every log line**.
- The filter runs before the security chain (`HIGHEST_PRECEDENCE`), so 401/403 responses carry it too.

**Structured security logging:** one `key=value` line per security event, on the logger `security.audit` (real lines from the running app):

```text
WARN [36ae07b4-f2db-46ca-880e-76234e85927f] security.audit : security_event=ACCESS_DENIED user=asha authorities=[ROLE_EMPLOYEE] method=DELETE path=/api/employees/7
WARN [9027818b-023a-4343-97b3-c17feade90d3] security.audit : security_event=AUTH_INVALID_TOKEN method=GET path=/api/employees reason="Token is malformed or invalid"
WARN [postman-check-1]                      security.audit : security_event=AUTH_REQUIRED method=GET path=/api/employees reason="Authentication is required to access this resource"
INFO [...]                                  security.audit : audit_event=SALARY_CHANGED employee=6 old=70000.00 new=72000 by=hana
INFO [...]                                  security.audit : audit_event=USER_CREATED username=meena role=EMPLOYEE employee=6
```

## Results

**Postman:** 30 requests, 61 assertions, 0 failures. It was run twice in a row. Every error response is checked for the full contract, including that `correlationId` equals the `X-Correlation-Id` header.

**Tests:** 69 passing (`mvn test`).

| Test class | What it proves |
|---|---|
| `AuthorizationMatrixTest` (60) | the matrix above: 5 users × 10 requests with real JWTs, exact statuses; plus every request without a token gives 401 `AUTH_REQUIRED` |
| `ErrorContractAndDataTest` (9) | full 401 contract with correlation id = header; client correlation id kept; malformed and **tampered** tokens give `AUTH_INVALID_TOKEN`; a valid token without permission gives 403 and the structured log line; controller errors (400/404/409, bad login) have their own codes; no salary outside `/salary`; salary change is audited (table + log); manager team; admin creates a login |

## Challenge extensions covered

- **Ownership-based access:** self and line-manager rules via the `@access` bean.
- **Audit:** salary changes (table + log) and user creation (log).
- **Correlation ids, structured security logging, centralized error contract:** described above.

Fine-grained permissions are covered in `02-roles-authorities` and `04-database-auth`; this API uses roles only.
