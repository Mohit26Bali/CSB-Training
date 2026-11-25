# 04 · Database-Backed Authentication: JPA Users, Roles, Permissions and UserDetailsService

Login accounts live in **MySQL** instead of being hard-coded:
- Users, roles and permissions are JPA entities with two **many-to-many** relationships.
- A custom `UserDetailsService` loads a user and turns their roles and permissions into `GrantedAuthority` objects.
- Passwords are stored only as **BCrypt hashes**. MySQL itself refuses anything else.

On top of that:
- registration with validation and duplicate checks
- enabled/disabled accounts
- **account locking** after 5 failed logins
- password change and **password reset** with one-time tokens
- an admin API to enable, disable, unlock and assign roles

## Tech

Java 17 · Maven · Spring Boot 3.5.6 · **Spring Security 6.5** · Spring Data JPA (Hibernate 6) · **MySQL 8** · Flyway · Bean Validation · JUnit 5 + MockMvc against a real MySQL test database · Postman.

## Structure

```text
04-database-auth/
├── pom.xml
├── postman/04-database-auth.postman_collection.json   42 requests, 76 checks (re-runnable)
└── src/
    ├── main/java/com/company/dbauth/
    │   ├── DatabaseAuthApplication.java            @SpringBootApplication (run this)
    │   ├── config/
    │   │   ├── SecurityConfig.java                 HTTP Basic, stateless, URL rules by permission
    │   │   ├── AppConfig.java                      BCryptPasswordEncoder + Clock beans
    │   │   └── PortalSecurityProperties.java       lock limit/duration, token validity (portal.security.*)
    │   ├── entity/                                 AppUser, Role, Permission, PasswordResetToken
    │   ├── repository/                             Spring Data repositories (entity graph for the login query)
    │   ├── security/
    │   │   ├── DatabaseUserDetailsService.java     ★ loads the user, maps roles/permissions to authorities
    │   │   ├── PortalUserPrincipal.java            the immutable UserDetails copy kept in the SecurityContext
    │   │   ├── LoginAttemptListener.java           counts failed logins, locks the account
    │   │   └── JsonSecurityErrorHandlers.java      401 (with the reason) / 403 JSON
    │   ├── service/                                AccountService (register, change password, directory),
    │   │                                           PasswordResetService, UserAdminService
    │   ├── controller/                             AuthController, MeController, DirectoryController,
    │   │                                           AdminUserController
    │   ├── dto/, exception/                        request/response records, ApiException, error handler
    └── main/resources/
        ├── application.properties
        ├── db-secret.properties                    MySQL password (git-ignored; copy db-secret.properties.example)
        └── db/migration/V1, V2 + db/sample/V1_1    Flyway: schema, reference data, demo users
```

## How to run

1. MySQL 8 must be running on `localhost:3306`. Put the root password in `src/main/resources/db-secret.properties`. This file is git-ignored:
   ```properties
   spring.datasource.password=your-mysql-password
   ```
2. **Eclipse:** import as an existing Maven project, then run `DatabaseAuthApplication`.
   **Terminal:** `mvn spring-boot:run`.
3. The first start creates the database `hr_auth_day10`. Flyway then runs the migrations: tables, then roles and permissions, then the demo users.
4. **Postman:** `postman collection run postman/04-database-auth.postman_collection.json`. Each run registers a new user, so it can be run any number of times.
5. **Tests:** `mvn test`. They use a separate database, `hr_auth_day10_test`, which Flyway wipes and rebuilds at the start of every run.

| User | Password | Roles | Authorities after login |
|---|---|---|---|
| `admin` | `Admin@123` | ADMIN | EMPLOYEE_DELETE, EMPLOYEE_READ, EMPLOYEE_WRITE, USER_MANAGE, ROLE_ADMIN |
| `hana` | `Hana@123` | HR | EMPLOYEE_READ, EMPLOYEE_WRITE, ROLE_HR |
| `manoj` | `Manoj@123` | MANAGER **and** USER | EMPLOYEE_READ, ROLE_MANAGER, ROLE_USER |
| `asha` | `Asha@123` | USER | EMPLOYEE_READ, ROLE_USER |
| `vikram` | `Vikram@123` | USER, **disabled** | (refused: "Account is disabled") |

## Endpoints

| Endpoint | Access | Purpose |
|---|---|---|
| `GET /api/public/hello` | public | status |
| `POST /api/auth/register` | public | create an account (role USER) |
| `POST /api/auth/password-reset/request` | public | ask for a reset token (always 202) |
| `POST /api/auth/password-reset/confirm` | public | set a new password with the token |
| `GET /api/me` | logged in | username, name and authorities |
| `POST /api/me/password` | logged in | change own password |
| `GET /api/directory` | `EMPLOYEE_READ` | staff directory (enabled users) |
| `GET /api/admin/users` | `USER_MANAGE` | all accounts with status, never hashes |
| `PATCH /api/admin/users/{id}/enabled` | `USER_MANAGE` | enable or disable |
| `POST /api/admin/users/{id}/unlock` | `USER_MANAGE` | end a lock early |
| `PUT /api/admin/users/{id}/roles` | `USER_MANAGE` | replace the roles |

## The data model

```text
 users                        user_roles            roles                role_permissions        permissions
┌──────────────────────┐    ┌──────────┐    ┌──────────────┐    ┌───────────────┐    ┌─────────────────┐
│ id                PK │───<│ user_id  │    │ id        PK │───<│ role_id       │    │ id           PK │
│ username      UNIQUE │    │ role_id  │>───│ name  UNIQUE │    │ permission_id │>───│ name     UNIQUE │
│ email         UNIQUE │    └──────────┘    │ description  │    └───────────────┘    │ description     │
│ full_name            │                    └──────────────┘                         └─────────────────┘
│ password_hash CHAR60 │  CHECK: must look like a BCrypt hash
│ enabled              │
│ failed_login_attempts│        password_reset_tokens
│ locked_until         │───<   ┌────────────────────────────┐
│ password_changed_at  │       │ user_id, token_hash CHAR64 │  SHA-256 of the token, UNIQUE
│ created_at           │       │ expires_at, used_at        │
└──────────────────────┘       └────────────────────────────┘
```

- **User ↔ Role** and **Role ↔ Permission** are both many-to-many. The JPA side uses `@ManyToMany` + `@JoinTable` on `AppUser.roles` and `Role.permissions`.
- **Roles are stored without the `ROLE_` prefix.** A CHECK constraint refuses `ROLE_HR`, and the prefix is added in code.

| Role | Permissions (`role_permissions`) |
|---|---|
| ADMIN | EMPLOYEE_DELETE, EMPLOYEE_READ, EMPLOYEE_WRITE, USER_MANAGE |
| HR | EMPLOYEE_READ, EMPLOYEE_WRITE |
| MANAGER | EMPLOYEE_READ |
| USER | EMPLOYEE_READ |

`AppUser` (not `User`) is the entity name, so it can't be confused with Spring Security's `User` class. The entity is **never** given to Spring Security or returned as JSON. That keeps the hash out of responses and avoids lazy-loading surprises.

## How a login works: from the request to the `UserDetailsService`

```text
GET /api/me   Authorization: Basic aGFuYTpIYW5hQDEyMw==        (base64 of "hana:Hana@123")
   │
   ▼
BasicAuthenticationFilter        decodes the header -> UsernamePasswordAuthenticationToken("hana", "Hana@123"), not yet authenticated
   │
   ▼
ProviderManager (AuthenticationManager)   asks each AuthenticationProvider; here there is one:
   │
   ▼
DaoAuthenticationProvider
   1. userDetailsService.loadUserByUsername("hana")      ── DatabaseUserDetailsService
   │        └─ ONE SQL query: users ⟕ user_roles ⟕ roles ⟕ role_permissions ⟕ permissions
   │        └─ returns PortalUserPrincipal(hash, enabled, nonLocked, [EMPLOYEE_READ, EMPLOYEE_WRITE, ROLE_HR])
   2. pre-checks:  isAccountNonLocked()? isEnabled()?    ── LockedException / DisabledException
   3. passwordEncoder.matches("Hana@123", "$2a$10$FJP1...")   ── BadCredentialsException if not
   │
   ▼
success: an AUTHENTICATED token (principal + authorities) is stored in the SecurityContextHolder
         + AuthenticationSuccessEvent  ── LoginAttemptListener resets the failure counter
failure: AuthenticationEntryPoint ── 401 JSON with the reason
         + AuthenticationFailureBadCredentialsEvent ── LoginAttemptListener counts it
   │
   ▼
AuthorizationFilter               checks the URL rule against the authorities (e.g. EMPLOYEE_READ for /api/directory)
   ▼
controller                        @AuthenticationPrincipal PortalUserPrincipal
```

Points to notice:
- **`loadUserByUsername` never sees the typed password.** It only loads. The comparison happens in `DaoAuthenticationProvider` with the `PasswordEncoder` bean.
- **Nothing is wired by hand.** Spring Security finds the single `UserDetailsService` bean and the `PasswordEncoder` bean, and builds the `DaoAuthenticationProvider` from them.
- **An unknown username** throws `UsernameNotFoundException`, which Spring reports as "Bad credentials". So the client gets the same message as for a wrong password. Spring also runs a dummy BCrypt check for unknown users, so the response time doesn't reveal which usernames exist either.

From the application log (`--logging.level.org.springframework.security=DEBUG --logging.level.org.hibernate.SQL=DEBUG`), a successful login:

```text
FilterChainProxy        : Securing GET /api/me
org.hibernate.SQL       : select au1_0.id, ..., au1_0.password_hash, r1_1.name, p1_1.name, ...
                          from users au1_0 left join user_roles r1_0 on au1_0.id=r1_0.user_id
                          left join roles r1_1 on r1_1.id=r1_0.role_id
                          left join role_permissions p1_0 on r1_1.id=p1_0.role_id
                          left join permissions p1_1 on p1_1.id=p1_0.permission_id where au1_0.username=?
DaoAuthenticationProvider : Authenticated user
BasicAuthenticationFilter : Set SecurityContextHolder to UsernamePasswordAuthenticationToken
                          [Principal=PortalUserPrincipal[hana, [EMPLOYEE_READ, EMPLOYEE_WRITE, ROLE_HR]], Credentials=[PROTECTED], ...]
FilterChainProxy        : Secured GET /api/me
```

A wrong password, with the failure counter going up:

```text
FilterChainProxy          : Securing GET /api/me
org.hibernate.SQL         : select ... from users au1_0 left join user_roles ... where au1_0.username=?
DaoAuthenticationProvider : Failed to authenticate since password does not match stored value
ProviderManager           : Authentication failed with provider DaoAuthenticationProvider since Bad credentials
org.hibernate.SQL         : update users set ..., failed_login_attempts=?, ... where id=?      <- LoginAttemptListener
```

The single query comes from `@EntityGraph(attributePaths = {"roles", "roles.permissions"})` on `findWithAuthoritiesByUsername`. Without it, Hibernate would run one query for the user, one for the roles, and another for **each** role's permissions (the N+1 problem). With HTTP Basic that would happen on every request.

### Mapping database roles to `GrantedAuthority`

[DatabaseUserDetailsService.java](src/main/java/com/company/dbauth/security/DatabaseUserDetailsService.java):

```java
for (Role role : user.getRoles()) {
    roleNames.add("ROLE_" + role.getName());                 // HR            -> ROLE_HR
    for (Permission permission : role.getPermissions()) {
        permissionNames.add(permission.getName());           // EMPLOYEE_READ -> EMPLOYEE_READ
    }
}
```

The permissions are collected in a **Set**. manoj has MANAGER and USER, and both grant `EMPLOYEE_READ`, so the result is `EMPLOYEE_READ, ROLE_MANAGER, ROLE_USER` with no duplicates.

## Passwords: only BCrypt hashes, enforced in three places

| Where | How |
|---|---|
| Code | Registration, password change and reset all call `passwordEncoder.encode(...)`. The plain password exists only in the request. |
| Entity | `password_hash CHAR(60)`: a BCrypt hash is always exactly 60 characters. |
| **Database** | `CHECK (password_hash REGEXP '^\\$2[aby]\\$[0-9]{2}\\$')`. MySQL refuses a plain-text value **whatever program writes it**. |

What MySQL stores (evidence query):

```text
username      hash_start     len  enabled  fails
admin         $2a$10$kOgeb    60     1       0
hana          $2a$10$FJP1b    60     1       0
vikram        $2a$10$cQ6W5    60     0       0       <- disabled
neha7836806   $2a$10$btLHe    60     0       0       <- registered by Postman, later disabled by admin
```

Two more rules:
- **The password policy** is 8–72 characters with upper case, lower case, a digit and a symbol. The **72** is BCrypt's limit: it only reads the first 72 bytes, so a longer password would be silently cut. It is refused instead.
- **Request records never print passwords.** `toString()` is overridden on every request type that carries one, so a log line can't leak it.

## Registration and duplicates

`POST /api/auth/register` validates the body:
- **username:** 3–30 characters from letters, digits, `._-`
- **email:** must be well formed
- **password:** the policy above

Then:
- The username and email are **lower-cased**, so `ASHA` and `asha` are the same account.
- The password is hashed.
- The account gets the **USER** role. Only an admin can give more.

Duplicates are checked **twice**:
1. `existsByUsername` / `existsByEmail` give a clear **409** message: `Username 'asha' is already taken`.
2. The `UNIQUE` constraints in MySQL catch two simultaneous registrations that both passed step 1. `saveAndFlush` runs the INSERT inside the `try`, and the `DataIntegrityViolationException` becomes the same 409.

## Account states: enabled, disabled, locked

| State | Stored as | Login result |
|---|---|---|
| Normal | `enabled = 1`, `locked_until` NULL or past | 200 |
| **Disabled** | `enabled = 0` (set by an admin) | 401 `Account is disabled`, even with the right password |
| **Locked** | `locked_until` in the future | 401 `Account is temporarily locked after too many failed logins`, even with the right password |

**How locking works** ([LoginAttemptListener.java](src/main/java/com/company/dbauth/security/LoginAttemptListener.java)):
- Spring Security publishes an event after every login attempt, and the listener reacts.
- A wrong password increments `failed_login_attempts`.
- On the 5th failure, `locked_until` is set to now + 15 minutes, and the log says `Account neha7836806 locked until 2026-10-25T20:02:21 after 5 failed logins`.
- A successful login sets the counter back to 0.
- The lock ends by itself when the time passes. An admin unlock or a password reset ends it early.

Two consequences, both tested:
- **Changes apply on the next request.** HTTP Basic calls the `UserDetailsService` on every request. So disabling an account, removing a role or locking takes effect on the user's very next request. (With JWT, in the next project, a token issued earlier stays valid until it expires. That's the revocation problem.)
- **Spring checks the account flags *before* the password.** A locked account answers "locked" even to someone with the **wrong** password, so an attacker can tell that the account is locked. That was chosen here so that real users understand what happened. The stricter alternative is to send the same "Invalid username or password" for every failure.

## Password change and reset

**Change** (`POST /api/me/password`, logged in):
- The current password is required, and the new one must be different.
- The next request must use the new password.

**Reset** ("forgot my password", public):
1. `POST /api/auth/password-reset/request {"email": ...}` always answers **202** with the same message, whether or not the address exists. The endpoint can't be used to discover accounts. For an active account it creates a random 256-bit token, valid for 15 minutes.
2. `POST /api/auth/password-reset/confirm {"token", "newPassword"}` sets the password, marks the token **used** (single use), and clears any lock.

**Only the SHA-256 hash of the token is stored**, so someone who reads the table can't use it. Why SHA-256 here and BCrypt for passwords? A token is 256 random bits and can't be guessed, so a fast hash is enough. Passwords are chosen by people and are guessable, so they need a deliberately slow hash.

> **Demo setting:** there is no mail server, so `portal.security.return-reset-token=true` returns the token in the 202 response. That lets Postman continue the flow. In a real deployment it must be `false`, and the token would only be e-mailed.

## Evidence

```text
$ curl -u hana:Hana@123 localhost:8080/api/me
{"username":"hana","fullName":"Hana Kapoor","authorities":["EMPLOYEE_READ","EMPLOYEE_WRITE","ROLE_HR"]}

$ curl -u asha:Wrong@123 localhost:8080/api/me
{"timestamp":"2026-10-25T14:17:11.000018600Z","status":401,"error":"Unauthorized",
 "message":"Invalid username or password","path":"/api/me"}
```

The disabled account (`vikram`, correct password) is checked by the Postman request *"vikram, DISABLED, correct password -> 401"*, which asserts the message `Account is disabled`, and by `AuthenticationFlowTest.disabledUser_isRefused_evenWithTheRightPassword`.

Database contents after the Postman runs:

```text
role     permissions
ADMIN    EMPLOYEE_DELETE,EMPLOYEE_READ,EMPLOYEE_WRITE,USER_MANAGE
HR       EMPLOYEE_READ,EMPLOYEE_WRITE
MANAGER  EMPLOYEE_READ
USER     EMPLOYEE_READ

token_hash_start   used          <- SHA-256 hashes, each used once
722fc6648e0b6371   1
c9bfaf49acbe6e24   1
```

### Postman

The collection follows one new account through its whole life:
- register
- log in
- check permissions
- change the password
- get locked out and be unlocked
- reset the password
- receive a new role
- be disabled

It was run twice in a row against the same database, which shows it is re-runnable:

```text
postman collection run postman/04-database-auth.postman_collection.json

|                requests |                   42 |                   0 |
|            test-scripts |                   42 |                   0 |
|              assertions |                   76 |                   0 |
```

| Folder | Requests |
|---|---|
| 1. Login: valid, two roles, capitals, wrong password, unknown, disabled, no credentials | 8 |
| 2. Registration: 201, immediate login, duplicate username / e-mail (409), validation (400) | 5 |
| 3. Database permissions decide access (directory, admin list) | 4 |
| 4. Change password | 4 |
| 5. Locking: 5 failures, locked even with the right password, admin unlock | 9 |
| 6. Reset: unknown e-mail, token, confirm, old/new password, token reuse | 6 |
| 7. Administration: new role → new authority; disable → 401 at once; cannot disable self | 6 |

## Tests: 38 passing, against MySQL

```text
mvn test
Tests run: 10, Failures: 0, Errors: 0 -- in com.company.dbauth.AuthenticationFlowTest
Tests run: 8,  Failures: 0, Errors: 0 -- in com.company.dbauth.RegistrationTest
Tests run: 5,  Failures: 0, Errors: 0 -- in com.company.dbauth.AccountLockingTest
Tests run: 8,  Failures: 0, Errors: 0 -- in com.company.dbauth.PasswordManagementTest
Tests run: 7,  Failures: 0, Errors: 0 -- in com.company.dbauth.UserAdministrationTest
```

| Test class | What it proves |
|---|---|
| `AuthenticationFlowTest` | **valid, invalid, disabled and unknown** users. Also: authorities from the database, the union for two roles, case-insensitive usernames, permission-based access, and the `UserDetailsService` on its own (hash, authorities, `UsernameNotFoundException`). |
| `RegistrationTest` | 201 with role USER and no hash in the JSON; the stored value is a 60-character BCrypt hash that matches; immediate login; duplicate username (other case) and e-mail give 409; validation messages; the 72-character limit; the MySQL **UNIQUE** and **CHECK** constraints refuse a duplicate, a plain-text password and a `ROLE_` role name, written directly with SQL. |
| `AccountLockingTest` | 5 failures lock the account, even against the right password; success resets the counter; the lock expires by itself; admin unlock; "locked" is shown even for a wrong password, and that attempt is not counted. |
| `PasswordManagementTest` | Change password (old stops working, new works, wrong current, same password); reset end-to-end; single use; expired token; made-up token; same answer for unknown or disabled e-mails; reset also unlocks; only the SHA-256 hash is in the table. |
| `UserAdministrationTest` | Disable/enable takes effect on the next request; an admin cannot disable their own account; changing roles changes authorities immediately; an unknown role gives 400 and changes nothing; 404; the list shows status but never hashes; non-admins get 403. |

Each test that changes an account first registers its own fresh user, so the tests are independent of each other and of their order. MySQL quirk found by the tests: a CHECK violation comes back as MySQL error 3819 with SQL state `HY000`, which Spring does not translate to `DataIntegrityViolationException`. It arrives as `UncategorizedSQLException`; both are `DataAccessException`s.

## Challenge extensions covered

- **Account locking:** 5 failures → 15-minute lock (configurable), automatic expiry, admin unlock, cleared by a password reset.
- **Enabled/disabled users:** `users.enabled`, seeded disabled user `vikram`, admin enable/disable endpoint, effective on the next request, admins can't disable themselves.
- **Password reset:** one-time, 15-minute tokens stored as SHA-256 hashes, no account enumeration, single use.
- **Many-to-many permissions:** `roles ⟷ permissions` through `role_permissions`, combined with `users ⟷ roles`. Authorities are the union of all of a user's roles.
