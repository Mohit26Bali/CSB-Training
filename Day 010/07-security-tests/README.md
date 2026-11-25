# 07 · Spring Security Testing Lab

An automated security test suite, written with **JUnit 5, MockMvc and Spring Security Test**, that catches authorization regressions before release. It tests the employee API from `06-secure-rest-api`, copied here with two additions: a public `/api/public/health` endpoint and a CORS rule for `http://localhost:3000`.

The tests run against a **real MySQL test database** (`hr_tests_day10_test`). Flyway rebuilds it on every run.

## How to run

MySQL on `localhost:3306`, with the password in `src/main/resources/db-secret.properties` (git-ignored; copy db-secret.properties.example). Then:

```bash
mvn test
```

## Maven output

```text
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0 -- in com.company.secureapi.AuthenticationTest
[INFO] Tests run: 60, Failures: 0, Errors: 0, Skipped: 0 -- in com.company.secureapi.AuthorizationMatrixTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.company.secureapi.CorsTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.company.secureapi.MethodSecurityTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0 -- in com.company.secureapi.RoleAuthorizationTest
[INFO] Tests run: 82, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Required tests and where they are

| Requirement | Test |
|---|---|
| Public endpoint without authentication | `AuthenticationTest.publicEndpoint_withoutAuthentication_is200` |
| Protected endpoint without authentication | `AuthenticationTest.protectedEndpoint_withoutAuthentication_is401`, plus every matrix endpoint in `AuthorizationMatrixTest.withoutAToken_everyEndpointGives401` |
| USER on an allowed endpoint | `RoleAuthorizationTest.user_onAnAllowedEndpoint_is200` |
| USER on an ADMIN-only endpoint | `RoleAuthorizationTest.user_onAnAdminOnlyEndpoint_is403` |
| HR creating an employee | `RoleAuthorizationTest.hr_createsAnEmployee_201` |
| ADMIN deleting an employee | `RoleAuthorizationTest.admin_deletesAnEmployee_withRealTokens` (HR gets 403, ADMIN gets 204, then 404) |
| Invalid credentials | `AuthenticationTest.invalidCredentials_are401_andGiveNoToken` |
| Invalid / expired JWT | `AuthenticationTest.invalidJwt_is401`, `expiredJwt_is401`, `tamperedJwt_is401` |
| Method-level rules | `MethodSecurityTest`: calls `EmployeeService` directly, with no HTTP |
| Run the suite with Maven | `mvn test` (output above) |

## Authorization test matrix

`AuthorizationMatrixTest`: 5 real users × 10 requests, each with a real JWT and the exact expected status. `✔` = allowed, `✘` = 403.

| Request | admin | hana (HR) | manoj (MANAGER) | asha (EMPLOYEE) | ravi (EMPLOYEE) |
|---|:-:|:-:|:-:|:-:|:-:|
| `GET /api/employees` | ✔ | ✔ | ✔ | ✔ | ✔ |
| `GET /api/employees/4` (asha's record) | ✔ | ✔ | ✔ manager | ✔ self | ✘ |
| `GET /api/employees/team` | ✘ | ✘ | ✔ | ✘ | ✘ |
| `GET /api/employees/4/salary` | ✔ | ✔ | ✘ | ✔ self | ✘ |
| `PUT /api/employees/7/salary` | ✔ | ✔ | ✘ | ✘ | ✘ |
| `PUT /api/employees/2/salary` (hana's own) | ✔ | ✘ | ✘ | ✘ | ✘ |
| `POST /api/employees` | ✔ 201 | ✔ 201 | ✘ | ✘ | ✘ |
| `PUT /api/employees/7` | ✔ | ✔ | ✘ | ✘ | ✘ |
| `DELETE /api/employees/999` | ✔ (404) | ✘ | ✘ | ✘ | ✘ |
| `GET /api/admin/users` | ✔ | ✘ | ✘ | ✘ | ✘ |
| any of the above **without a token** | 401 | 401 | 401 | 401 | 401 |

## Test techniques used

| Technique | Where | Why |
|---|---|---|
| `@WithMockUser(roles = "HR")` | `RoleAuthorizationTest`, `MethodSecurityTest` | puts a fake user in the `SecurityContext`, with no login or token: tests only the rule |
| `@WithAnonymousUser` | `RoleAuthorizationTest` | an explicit "not logged in" user |
| Real login → JWT → `Authorization: Bearer` | `AuthenticationTest`, `AuthorizationMatrixTest`, the delete test | tests the whole path: login, JWT filter and rules |
| Hand-built tokens (jjwt) | `SecurityTestSupport.expiredBearer`, the tampered test | tokens no real client would send |
| `SecurityContextHolder` + `TestingAuthenticationToken(new AuthenticatedUser(...))` | `MethodSecurityTest` | ownership rules need the employee id that the JWT filter normally provides |
| `@ParameterizedTest` + `@MethodSource` | `AuthorizationMatrixTest` | the whole matrix as data: one row per endpoint, one cell per role |
| `@SpringBootTest` + MySQL test database + Flyway clean/migrate | all | the same schema and the same queries as production |

## Challenge extensions covered

- **Real test database:** MySQL `hr_tests_day10_test`, wiped and rebuilt by `TestFlywayConfig` on each run.
- **CORS tests** (`CorsTest`):
  - A preflight `OPTIONS` from the allowed origin passes without a token and gets `Access-Control-Allow-Origin`.
  - A preflight from another origin gets 403.
  - CORS does not replace authentication: the real request from the allowed origin still needs a token.
- **CSRF for browser/session flows:** this API is stateless (JWT in a header), so CSRF protection is switched off on purpose. CSRF is tested in `01-basic-security`'s `BrowserSecurityTest`, which has a session-based form login: the login page contains the CSRF token, a form login without the token gets 403, and logout only works as a POST with the token (a GET `/logout` link does not log anyone out).
