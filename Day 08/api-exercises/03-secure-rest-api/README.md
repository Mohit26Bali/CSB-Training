# Exercise 3: Secure REST API (Employee Portal)

An internal employee portal protected by **Spring Security** with **JWT** (JSON Web Token) authentication and **role-based authorization**:

| Role | May do |
|---|---|
| **USER** | view employee details |
| **HR** | also see payroll, and create and update employees |
| **ADMIN** | also delete employees |

- **401** means: no token, or a bad or expired one.
- **403** means: a valid token, but a role that is not allowed.

All three challenge extensions are included:
- **refresh tokens**
- **token expiration handling**
- **audit logging** of administrative operations, logins and denied attempts

## Tech

Java 17 · Maven · Spring Boot 3.5.6 · **Spring Security 6.5** · `spring-boot-starter-oauth2-resource-server` (JWT validation + `JwtEncoder`, Nimbus JOSE) · Spring Data JPA · MySQL 8, database **`employee_portal`**

## Test credentials

| Username | Password | Role |
|---|---|---|
| `user` | `user123` | USER |
| `hr` | `hr123` | HR |
| `admin` | `admin123` | ADMIN |

The users are kept in memory, with BCrypt-hashed passwords (`SecurityConfig`).

## Structure

```text
03-secure-rest-api/
├── pom.xml
├── postman/secure-rest-api.postman_collection.json      login requests store the tokens automatically
└── src/main/
    ├── java/com/example/portal/
    │   ├── PortalApplication.java                          run this
    │   ├── security/
    │   │   ├── SecurityConfig.java                         ALL access rules, users, password encoder
    │   │   ├── JwtService.java                             create + validate tokens
    │   │   ├── JsonAuthenticationEntryPoint.java           401 as JSON
    │   │   └── JsonAccessDeniedHandler.java                403 as JSON + audit log
    │   ├── controller/AuthController.java                  POST /api/auth/login, /api/auth/refresh
    │   ├── controller/EmployeeController.java              protected employee endpoints
    │   ├── service/AuthService.java                        login and refresh logic
    │   ├── service/EmployeeService.java                    employee operations + audit log
    │   ├── repository/EmployeeRepository.java
    │   ├── model/Employee.java, EmployeeStatus.java
    │   ├── dto/LoginRequest, RefreshRequest, TokenResponse, EmployeeRequest, EmployeeResponse, PayrollResponse, ErrorResponse
    │   └── exception/EmployeeNotFoundException, DuplicateEmailException, InvalidTokenException, GlobalExceptionHandler
    └── resources/
        ├── application.properties                          JWT issuer and lifetimes
        ├── secrets.properties                              DB password + JWT secret (git-ignored; copy secrets.properties.example)
        ├── schema.sql
        └── sample-data.sql                                 3 employees
```

## How to run

1. Create the database:

   ```text
   mysql -u root -p < src/main/resources/schema.sql
   mysql -u root -p < src/main/resources/sample-data.sql
   ```

2. In Eclipse, import it as an existing Maven project and run `PortalApplication`.
3. In Postman, import `postman/secure-rest-api.postman_collection.json` and run **folder 1 (Login) first**. It stores `userToken`, `hrToken`, `adminToken` and `hrRefreshToken`, which the other requests use as `Authorization: Bearer {{...}}`.

## Endpoint access matrix

This was produced automatically by calling every endpoint with each role:

```text
endpoint                           anonymous  USER   HR     ADMIN
GET    /api/employees              401        200    200    200
GET    /api/employees/1            401        200    200    200
GET    /api/employees/1/payroll    401        403    200    200
POST   /api/employees              401        403    201    201
PUT    /api/employees/2            401        403    200    200
DELETE /api/employees/4            401        403    403    204
```

| Endpoint | Rule in `SecurityConfig` |
|---|---|
| `POST /api/auth/login`, `POST /api/auth/refresh` | `permitAll()` (public) |
| `GET /api/employees`, `GET /api/employees/{id}` | `hasAnyRole("USER", "HR", "ADMIN")` |
| `GET /api/employees/{id}/payroll` | `hasAnyRole("HR", "ADMIN")` |
| `POST /api/employees`, `PUT /api/employees/{id}` | `hasAnyRole("HR", "ADMIN")` |
| `DELETE /api/employees/{id}` | `hasRole("ADMIN")` |
| anything else | `denyAll()` (unknown endpoints are refused, even for ADMIN) |

The employee details that every role sees **do not contain the salary**. Salary is payroll data, returned only by `/payroll`, which only HR and ADMIN can call.

## How a request is checked

```text
Client                          Spring Security filter chain                       Controller
  │  POST /api/auth/login ─────► permitAll ─────────────────────────────────────────► AuthController
  │  ◄── {accessToken, refreshToken}       (password checked with BCrypt, token signed with the secret)
  │
  │  GET /api/employees/1/payroll
  │  Authorization: Bearer eyJ... ─► BearerTokenAuthenticationFilter
  │                                    ├─ no token?                → 401 (JsonAuthenticationEntryPoint)
  │                                    ├─ JwtDecoder: signature, exp, iss, token_type
  │                                    │      any check fails?      → 401
  │                                    ├─ roles claim ["HR"] → authority ROLE_HR
  │                                  AuthorizationFilter (rules above)
  │                                    └─ role not allowed?        → 403 (JsonAccessDeniedHandler)
  │                                  ─────────────────────────────────────────────────► EmployeeController
```

## Observed output

### Login → 200

```text
POST /api/auth/login  {"username":"hr","password":"hr123"}
-> 200
{"accessToken":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJociIsInJvbGVzIjpbIkhSIl0sImlzcyI6ImVtcGxveWVlLXBvcnRhbCIsImV4cCI6MTc5MTczODcyMCwidG9rZW5fdHlwZSI6ImFjY2VzcyIsImlhdCI6MTc5MTczNzgyMH0.ukjPJdEdLIBOCRfrWG2haK6h7wCSwEOVttmzyZr9ya0",
 "tokenType":"Bearer","expiresIn":900,
 "refreshToken":"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJociIsInJvbGVzIjpbXSwiaXNzIjoiZW1wbG95ZWUtcG9ydGFsIiwiZXhwIjoxNzkxODI0MjIwLCJ0b2tlbl90eXBlIjoicmVmcmVzaCIsImlhdCI6MTc5MTczNzgyMH0.dG85POmJ6MkwdpQxMl8gQh6yVuv74uvyi_eDoRXimlk",
 "username":"hr","roles":["HR"]}
```

The middle part of the access token, decoded from Base64, is the **claims**:

```json
{"sub":"hr","roles":["HR"],"iss":"employee-portal","exp":1791738720,"token_type":"access","iat":1791737820}
```

| Request | Status | Response |
|---|---|---|
| `{"username":"hr","password":"wrong"}` | **401** | `"message":"Invalid username or password"` |
| `{"username":"nobody","password":"x"}` | **401** | `"message":"Invalid username or password"` (the same message, so attackers cannot learn which usernames exist) |
| `{"username":""}` | **400** | `"fieldErrors":{"password":"password is required","username":"username is required"}` |

### Allowed and rejected requests

```text
GET /api/employees/1   (USER)                -> 200
{"id":1,"name":"Asha Rao","email":"asha.rao@company.com","department":"Engineering","designation":"Senior Engineer","status":"ACTIVE"}

GET /api/employees/1/payroll   (HR)          -> 200
{"employeeId":1,"name":"Asha Rao","department":"Engineering","annualSalary":1440000.00,"monthlySalary":120000.00}

GET /api/employees/1/payroll   (USER)        -> 403
{"timestamp":"2026-10-11T22:27:04","status":403,"error":"Forbidden","message":"User 'user' with roles [ROLE_USER] is not allowed to GET /api/employees/1/payroll","path":"/api/employees/1/payroll"}

GET /api/employees   (no Authorization header)   -> 401
{"timestamp":"2026-10-11T22:27:04","status":401,"error":"Unauthorized","message":"Authentication required: send the header 'Authorization: Bearer <access token>'","path":"/api/employees"}

POST /api/employees   (HR)  {"name":"Kiran Das",...}   -> 201  Location: /api/employees/6
DELETE /api/employees/6   (HR)                        -> 403  "User 'hr' with roles [ROLE_HR] is not allowed to DELETE /api/employees/6"
DELETE /api/employees/6   (ADMIN)                     -> 204
POST /api/employees   (HR, invalid body)              -> 400  "fieldErrors":{"email":"email must be a valid e-mail address","name":"name is required","salary":"salary must be greater than 0"}
GET /api/employees/99   (USER)                        -> 404  "Employee not found with id 99"
GET /api/reports   (ADMIN, endpoint not in the rules) -> 403  (denyAll)
```

### Invalid JWT scenarios → all 401

| Scenario | `message` |
|---|---|
| Malformed token `abc.def.ghi` | `Invalid or expired token: An error occurred while attempting to decode the Jwt: Malformed token` |
| **Forged token:** the payload was edited from `"roles":["USER"]` to `"roles":["ADMIN"]` and used to DELETE | `Invalid or expired token: ... Signed JWT rejected: Invalid signature` |
| A **refresh** token sent as the access token | `Invalid or expired token: ... The token_type claim is not valid` |
| An **access** token sent to `/api/auth/refresh` | `Invalid or expired refresh token: ... The token_type claim is not valid` |
| **Expired** access token (see below) | `Invalid or expired token: ... Jwt expired at 2026-10-11T16:57:26Z` |

The forged token shows **why JWTs are signed**. Anyone can Base64-decode and edit the payload, but without the secret key the signature cannot be recalculated, so the change is detected.

### Challenge extension: expiration handling + refresh token

The application was started with `--jwt.access-token-validity-seconds=5 --jwt.clock-skew-seconds=0`:

```text
POST /api/auth/login (user)              -> 200  "expiresIn":5
GET  /api/employees/1                    -> 200                                   (immediately)
... 7 seconds later ...
GET  /api/employees/1                    -> 401  "Invalid or expired token: An error occurred while attempting to decode the Jwt: Jwt expired at 2026-10-11T16:57:26Z"
POST /api/auth/refresh {"refreshToken":"..."}  -> 200  new accessToken
GET  /api/employees/1 (new token)        -> 200
```

**Why two tokens:**
- **The access token is short-lived** (15 minutes by default). It travels with every request, so if one is stolen, it is useful only briefly.
- **The refresh token lives longer** (24 hours) but is only accepted by `/api/auth/refresh`. The `token_type` claim prevents mixing the two up.
- **On refresh, the user's roles are loaded again,** so a role change takes effect without a new login.

### Challenge extension: audit log

The `AUDIT` logger records who did what. This is the output of the test run:

```text
INFO user=hr action=LOGIN result=SUCCESS roles=[HR]
WARN user=hr action=LOGIN result=FAILED
WARN user=nobody action=LOGIN result=FAILED
WARN user=user roles=[ROLE_USER] action="GET /api/employees/1/payroll" result=DENIED
WARN user=user roles=[ROLE_USER] action="POST /api/employees" result=DENIED
INFO user=hr action=CREATE_EMPLOYEE employeeId=4 result=SUCCESS
INFO user=hr action=UPDATE_EMPLOYEE employeeId=2 result=SUCCESS
WARN user=hr roles=[ROLE_HR] action="DELETE /api/employees/4" result=DENIED
INFO user=admin action=DELETE_EMPLOYEE employeeId=4 email=new.hr@company.com result=SUCCESS
WARN user=admin roles=[ROLE_ADMIN] action="GET /api/reports" result=DENIED
```

## Explanation

**Authentication vs authorization:**
- **Authentication** answers *who are you?* It happens at login (username + password, checked against a BCrypt hash), and afterwards by validating the JWT on every request. A failure gives **401 Unauthorized**.
- **Authorization** answers *what may you do?* The role in the token is compared with the rules in `SecurityConfig`. A failure gives **403 Forbidden**.

**JWT:**
- **Signing.** The server signs the claims (user, roles, expiry) with a secret key (HMAC-SHA256).
- **No server state.** The server keeps **no session**: every request carries the token, and the server only has to verify the signature and the claims. That is why `SessionCreationPolicy.STATELESS` is used.
- **CSRF disabled.** CSRF protection is switched off because no cookie authenticates the request.

**What is checked on every request** (`JwtService.createDecoder`):
1. **signature:** the token was issued by this server and not modified
2. **`exp`:** not expired, within a small clock skew
3. **`iss`:** issued by `employee-portal`
4. **`token_type`:** an access token, not a refresh token

**Where security is enforced.** It is enforced centrally in the **filter chain**, before any controller runs. Controllers contain no security code. They only use `@AuthenticationPrincipal Jwt jwt` to know *who* made a change, for the audit log.

**API security practices used here:**
- passwords are hashed with BCrypt
- the JWT secret is kept out of source control (`secrets.properties`, git-ignored)
- short-lived access tokens
- the same message for unknown user and wrong password
- deny by default (`anyRequest().denyAll()`)
- sensitive data (salary) separated into its own endpoint
- consistent JSON errors
- an audit trail

In production, the API must also run over **HTTPS**, so that tokens cannot be read in transit.
