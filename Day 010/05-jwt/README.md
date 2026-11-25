# 05 · JWT-Based Stateless Authentication

A REST API for mobile and single-page clients with **no server-side session**:
1. The client logs in once with `POST /api/auth/login`.
2. It receives a **signed JWT access token**.
3. It sends that token as `Authorization: Bearer …` with every request.
4. A custom filter verifies the signature and expiry, and authenticates the request from the token alone.

Also covered:
- **Refresh tokens:** rotated on every use, with reuse detection.
- **Revocation:** logout, admin "log out everywhere", disabled users.
- **Signing-key rotation:** a `kid` header selects the key.
- **Externalised JWT settings.**

Users, roles and permissions are in MySQL, as in `04-database-auth`.

## Tech

Java 17 · Maven · Spring Boot 3.5.6 · **Spring Security 6.5** · **jjwt 0.12.6** (HS256) · Spring Data JPA · MySQL 8 · Flyway · JUnit 5 + MockMvc against a MySQL test database · Postman.

## Structure

```text
05-jwt/
├── pom.xml                                         + jjwt-api / jjwt-impl / jjwt-jackson
├── postman/05-jwt.postman_collection.json          41 requests, 80 checks (re-runnable)
└── src/
    ├── main/java/com/company/jwt/
    │   ├── JwtAuthApplication.java                 @SpringBootApplication (run this)
    │   ├── config/
    │   │   ├── SecurityConfig.java                 ★ stateless chain + JWT filter + AuthenticationManager bean
    │   │   ├── JwtProperties.java                  portal.jwt.* (issuer, lifetimes, clock skew, keys)
    │   │   └── AppConfig.java                      BCrypt (12) + Clock
    │   ├── security/
    │   │   ├── JwtService.java                     ★ creates and verifies tokens
    │   │   ├── JwtAuthenticationFilter.java        ★ "Authorization: Bearer" -> SecurityContext
    │   │   ├── JwtKeyRing.java                     signing keys by "kid" (key rotation)
    │   │   ├── RevokedAccessTokens.java            deny-list of logged-out token ids (jti)
    │   │   ├── AuthenticatedUser.java              the principal built from the token
    │   │   ├── DatabaseUserDetailsService.java     used by the login only
    │   │   ├── JsonSecurityErrorHandlers.java      401 / 403 JSON + WWW-Authenticate: Bearer
    │   │   └── InvalidBearerTokenException.java, UnknownSigningKeyException.java
    │   ├── service/                                AuthService (login/refresh/logout), RefreshTokenService,
    │   │                                           UserAdminService
    │   ├── controller/                             AuthController, MeController, DirectoryController, AdminController
    │   ├── entity/, repository/, dto/, exception/
    └── main/resources/
        ├── application.properties                  the JWT settings (no secrets)
        ├── jwt-secret.properties                   signing keys (git-ignored; copy jwt-secret.properties.example)
        ├── db-secret.properties                    MySQL password (git-ignored; copy db-secret.properties.example)
        └── db/migration/V1, V2 + db/sample/V1_1    users/roles/permissions, refresh_tokens, demo users
```

## How to run

1. MySQL 8 must be running on `localhost:3306`.
2. Create the two git-ignored files in `src/main/resources/`:
   ```properties
   # db-secret.properties
   spring.datasource.password=your-mysql-password

   # jwt-secret.properties: two keys of 32 random bytes each (openssl rand -base64 32)
   portal.jwt.keys.k2026-01=<base64 key>
   portal.jwt.keys.k2026-10=<base64 key>
   ```
3. **Eclipse:** run `JwtAuthApplication`. **Terminal:** `mvn spring-boot:run`. The first start creates the database `hr_jwt_day10`.
4. **Postman:** `postman collection run postman/05-jwt.postman_collection.json` (re-runnable).
5. **Tests:** `mvn test`. They use the database `hr_jwt_day10_test` and their **own test keys**.

Demo users (from 04): `admin / Admin@123` (ADMIN), `hana / Hana@123` (HR), `manoj / Manoj@123` (MANAGER + USER), `asha / Asha@123` (USER), `vikram / Vikram@123` (disabled).

```bash
# 1. log in
curl -s -H "Content-Type: application/json" -d '{"username":"hana","password":"Hana@123"}' localhost:8080/api/auth/login
# 2. use the token
curl -s -H "Authorization: Bearer <accessToken>" localhost:8080/api/me
```

## Endpoints

| Endpoint | Access | Purpose |
|---|---|---|
| `POST /api/auth/login` | public | username + password → access token + refresh token |
| `POST /api/auth/refresh` | public (needs a refresh token) | refresh token → **new** access token + **new** refresh token |
| `POST /api/auth/logout` | Bearer token | revokes this session's tokens |
| `GET /api/me` | Bearer token | who am I (taken from the token) |
| `GET /api/directory` | `EMPLOYEE_READ` | staff directory |
| `GET /api/admin/users` | `USER_MANAGE` | users |
| `PATCH /api/admin/users/{id}/enabled` | `USER_MANAGE` | enable / disable (disabling revokes refresh tokens) |
| `POST /api/admin/users/{id}/revoke-tokens` | `USER_MANAGE` | log the user out everywhere |
| `GET /api/public/hello` | public | status |

## The flow: login → token → protected endpoint

```text
 Client                                   Server
   │ POST /api/auth/login                   │
   │ {"username":"hana","password":"…"} ──► │ AuthService.login
   │                                        │   AuthenticationManager.authenticate(...)
   │                                        │     DaoAuthenticationProvider → DatabaseUserDetailsService (MySQL)
   │                                        │     BCrypt check, enabled check
   │                                        │   JwtService.issueAccessToken  → signed JWT (15 min)
   │                                        │   RefreshTokenService.issue    → random token, hash stored (7 days)
   │ ◄── {"tokenType":"Bearer","accessToken":"eyJ…","expiresIn":900,"refreshToken":"…","refreshExpiresIn":604800}
   │                                        │
   │ GET /api/directory                     │
   │ Authorization: Bearer eyJ… ──────────► │ JwtAuthenticationFilter
   │                                        │   verify signature (key by "kid"), "iss", "exp"; jti not revoked
   │                                        │   SecurityContext ← (hana, [EMPLOYEE_READ, EMPLOYEE_WRITE, ROLE_HR])
   │                                        │ AuthorizationFilter: hasAuthority("EMPLOYEE_READ") ✔
   │ ◄── 200 [...]                          │ controller          (no session, no database lookup for the user)
   │                                        │
   │ … 15 minutes later the access token has expired …
   │ POST /api/auth/refresh {"refreshToken":"…"} ──► rotate: old refresh token revoked, user reloaded
   │ ◄── a NEW access token and a NEW refresh token
```

### What the token contains

A real token from this API, decoded. The parts are separated by dots:

```text
eyJraWQiOiJrMjAyNi0xMCIsImFsZyI6IkhTMjU2...JKE1hp4g3mgQ        (3 parts: header.payload.signature)

header : {"kid":"k2026-10","alg":"HS256"}
payload: {"iss":"hr-portal","sub":"hana","jti":"0f4285d5-7e72-4b77-be4e-8c342eada880",
          "iat":1792948860,"exp":1792949760,"authorities":["EMPLOYEE_READ","EMPLOYEE_WRITE","ROLE_HR"]}
```

| Claim | Meaning |
|---|---|
| `sub` (subject) | who the token is about: the username |
| `iat` (issued at) / `exp` (expiry) | seconds since 1970; `exp − iat = 900` = 15 minutes |
| `authorities` | the roles and permissions at login time (custom claim) |
| `iss` (issuer) | which server issued it; tokens from other systems are refused |
| `jti` (JWT id) | a unique id, so this one token can be revoked |
| header `kid` | which key signed it (key rotation) |
| header `alg` | HS256 = HMAC with SHA-256 |

**Signed, not encrypted.** The header and payload are only Base64URL-encoded, so **anyone holding the token can read them**. That's why a token must never contain secrets; there is no password or hash in it. The **signature** is `HMAC-SHA256(header + "." + payload, secret key)`. Without the key nobody can change a single character without the signature failing.

### The filter ([JwtAuthenticationFilter.java](src/main/java/com/company/jwt/security/JwtAuthenticationFilter.java))

| `Authorization` header | Result |
|---|---|
| absent | continue **anonymously**. Public endpoints work; protected ones get 401 later from the `AuthenticationEntryPoint` |
| `Bearer <valid token>` | `SecurityContext` ← authenticated token with the **authorities from the token**, then continue |
| anything else | **stop**: 401 JSON with the reason, plus `WWW-Authenticate: Bearer error="invalid_token", error_description="…"` (RFC 6750) |

Design notes:
- It's a `OncePerRequestFilter`, added with `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`.
- It is created with `new` inside `SecurityConfig`, **not** declared as a `@Component`. Spring Boot automatically registers every `Filter` bean as a servlet filter too, so a `@Component` filter would run **twice**.
- An invalid token is refused **even on a public endpoint**. A client with a broken token should find out, rather than be silently treated as anonymous. (Spring's own resource server behaves the same way.)

### Stateless configuration ([SecurityConfig.java](src/main/java/com/company/jwt/config/SecurityConfig.java))

```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.csrf(csrf -> csrf.disable())
.httpBasic(basic -> basic.disable())
.formLogin(form -> form.disable())
.logout(logout -> logout.disable())
.addFilterBefore(new JwtAuthenticationFilter(jwtService, revokedTokens, errors), UsernamePasswordAuthenticationFilter.class)
```

- **No HTTP session and no `JSESSIONID` cookie.** Every request carries its own proof of identity. Any server instance can answer any request, because none of them keeps login state.
- **CSRF off.** The token travels in a header that a browser never adds on its own, so a forged cross-site request has no token.
- **Login uses the normal Spring Security machinery.** `AuthService.login` calls the `AuthenticationManager` (exposed as a bean from `AuthenticationConfiguration`). It goes through the same `DaoAuthenticationProvider` → `UserDetailsService` → BCrypt path as form login or HTTP Basic. The token is created only after that succeeds, from the authorities it returned.

### Session (01–04) vs stateless JWT (this project)

| | HTTP session / HTTP Basic | JWT |
|---|---|---|
| Identity stored | on the server (session) or re-checked every request (Basic: BCrypt + DB per request) | in the signed token, held by the client |
| Per-request cost | session lookup, or DB + BCrypt | one HMAC check, no DB |
| Scaling to many servers | shared session store needed | any server with the key can verify |
| Logout / disable | immediate (delete session / next DB check) | the token stays valid until `exp`, unless the server keeps a deny-list |
| Role changes | next request | next token (refresh) |
| CSRF | needed (cookies) | not needed (header) |

## Rejecting bad tokens

`JwtService.verify` uses jjwt's `parseSignedClaims`, which checks the signature with the key named by `kid`, then the issuer, then the expiry. Each failure becomes a clear 401:

| Token | Message (and `error_description`) |
|---|---|
| no `Authorization` header | `Authentication is required to access this resource` (`WWW-Authenticate: Bearer`) |
| `Basic …` instead of `Bearer …` | `Authorization header must be: Bearer <token>` |
| `not-a-jwt`, or an empty token | `Token is malformed` |
| `exp` in the past (beyond the 30 s clock skew) | `Token has expired` |
| signed with a different key | `Token signature is invalid` |
| **payload edited** (e.g. `USER_MANAGE` added), original signature kept | `Token signature is invalid` |
| `"alg":"none"`, an **unsigned** token | `Token must be signed` |
| `kid` this server doesn't have | `Token was signed with an unknown key` |
| right key, but `iss` is another system | `Token was not issued by this server` |
| logged out (its `jti` is on the deny-list) | `Token has been revoked` |

Two things worth knowing:
- **The `alg: none` attack.** An attacker removes the signature and sets the algorithm to "none". jjwt's `parseSignedClaims` only accepts signed tokens, so this is refused.
- **The clock skew** (30 s) lets a token that expired a few seconds ago still pass, so servers whose clocks differ slightly don't reject each other's tokens. This is tested both ways.

Evidence:

```text
$ curl -i -H "Authorization: Bearer <token that expired yesterday>" localhost:8080/api/me
HTTP/1.1 401
WWW-Authenticate: Bearer error="invalid_token", error_description="Token has expired"
{"timestamp":"2026-10-25T17:21:01.966594600Z","status":401,"error":"Unauthorized","message":"Token has expired","path":"/api/me"}
```

**Why the signing key must stay secret.** The server **trusts the claims without asking the database**. A test (`theServerTrustsTheSignedClaims_notTheDatabase`) signs a token with the real key claiming asha has `USER_MANAGE`, and the admin API opens, although asha has no such permission in MySQL. Whoever holds the key can create any identity. So the keys live in a git-ignored file or environment variables, never in the code or the repository.

## Challenge extensions

### Refresh tokens: rotation and reuse detection

- **Why two tokens?** The **access token** is short-lived (15 minutes) and can't be recalled. The **refresh token** is long-lived (7 days) but is **stored**, so it **can** be revoked.
- **What a refresh token is.** It's an opaque random value (32 bytes), not a JWT. Its only job is to be checked against the database and cancelled when needed, and a random string does that and leaks nothing. Only its **SHA-256 hash** is stored, in the `refresh_tokens` table.

**Rotation.** Every `POST /api/auth/refresh`:
1. revokes the presented token (reason `ROTATED`);
2. issues a new one in the same **family** (one family = one login on one device);
3. **reloads the user**. A disabled account gets nothing, and role changes made since the login appear in the new access token.

**Reuse detection.** A legitimate client only ever holds the newest refresh token. If a token that was already `ROTATED` comes back, someone kept a copy, so it was stolen. The **whole family** is revoked, and both the thief and the real user must log in again:

```text
WARN ... RefreshTokenService : Refresh token reuse detected for hana: session dd609f99-a877-4198-8fb0-e9c7ff52ba1e revoked

refresh_tokens:  username  hash_start    family    revoked_reason
                 hana      a40a23f9859c  dd609f99  ROTATED          <- used once, then replaced
                 hana      4c910693ba43  dd609f99  REUSE_DETECTED   <- the newest one, revoked because the old one came back
                 manoj     e245959eaa55  48c6958f  LOGOUT           <- the "laptop" session
                 manoj     2ddcf5eab881  63278010  NULL             <- the "phone" session, still active
```

### Revocation: what can and cannot be cancelled

| Action | Refresh tokens | Access token already issued |
|---|---|---|
| **Logout** (`POST /api/auth/logout`) | this session's family → `LOGOUT` | its `jti` goes on the **deny-list** → `Token has been revoked` |
| **Admin: revoke tokens** | all of the user's → `ADMIN` | still valid until `exp` (≤ 15 min) |
| **Admin: disable user** | all of the user's revoked; login refused | still valid until `exp`: **tested and shown in Postman** |
| Role change | next refresh carries the new roles | old roles until `exp` |

This is the core trade-off of stateless tokens. To recall an access token **instantly**, the server would have to check something on **every request** (a deny-list or the database), which gives up part of the benefit. The choices made here:
- **Short access tokens** (15 min) limit the damage window.
- **Refresh tokens are stored and revocable**, so no *new* access token can be obtained.
- **A small `jti` deny-list** handles logout.
  - It holds an id only until the token would have expired anyway, so it stays tiny.
  - **Limitation:** it is in memory, so it is per instance and lost on restart. With several servers it would live in a shared store such as Redis, with the same automatic expiry.

### Signing-key rotation

[JwtKeyRing.java](src/main/java/com/company/jwt/security/JwtKeyRing.java) holds several keys by id:
- **Signing:** new tokens are signed with the **active** key, and its id goes into the `kid` header.
- **Verifying:** jjwt reads the `kid` and asks the key ring for that key (a `LocatorAdapter`), so tokens signed with an **older** key keep working while that key is listed.

```text
rotation:  1. add k2026-10 next to k2026-01        (both verify)
           2. portal.jwt.active-key-id=k2026-10    (new tokens: kid k2026-10)
           3. after 15 minutes no valid k2026-01 token can exist -> delete k2026-01
```

Evidence: a token signed with the older key `k2026-01` (made for this check, 60-second life) is still accepted:

```text
$ curl -H "Authorization: Bearer <token with kid k2026-01>" localhost:8080/api/me
{"username":"asha","authorities":["EMPLOYEE_READ","ROLE_USER"],"tokenId":"old-key-demo","tokenExpiresAt":"2026-10-25T17:22:01Z"}
```

**Checked at startup:**
- The active key must exist.
- Every key must be **at least 256 bits**. `Keys.hmacShaKeyFor` throws `WeakKeyException` for a shorter key.

So a typo or a weak secret stops the application instead of producing weak tokens.

### Externalised JWT settings

Nothing JWT-specific is hard-coded. [JwtProperties.java](src/main/java/com/company/jwt/config/JwtProperties.java) binds `portal.jwt.*` and is validated:

```properties
portal.jwt.issuer=hr-portal
portal.jwt.access-token-ttl=PT15M
portal.jwt.refresh-token-ttl=P7D
portal.jwt.clock-skew=PT30S
portal.jwt.active-key-id=k2026-10
portal.jwt.keys.k2026-01=…        # in jwt-secret.properties (git-ignored; copy jwt-secret.properties.example)
portal.jwt.keys.k2026-10=…
```

Each setting can be overridden per environment without rebuilding, for example `PORTAL_JWT_ACCESSTOKENTTL=PT5M` as an environment variable. The tests use their own settings and their **own** keys (`src/test/resources/application.properties`), so the real keys are never needed for `mvn test`.

## Postman

```text
postman collection run postman/05-jwt.postman_collection.json      (run twice in a row: both green)

|                requests |                   41 |                   0 |
|            test-scripts |                   41 |                   0 |
|              assertions |                   80 |                   0 |
```

| Folder | What it shows |
|---|---|
| 0. Public endpoint and no token | 200 without a token; 401 + `WWW-Authenticate: Bearer` on `/api/me` |
| 1. Login | the token pair; the test script **decodes** the token and checks header (`kid`, `alg`) and payload (`sub`, `iss`, `jti`, `exp − iat = 900`, authorities); no cookie; wrong password / unknown / disabled / missing field |
| 2. Token → protected endpoints | 200 with `EMPLOYEE_READ`; 403 on the admin API (valid token, not allowed) |
| 3. Rejected tokens | Basic scheme, malformed, expired, wrong key, **tampered** (a pre-request script edits the real token's payload), unsigned, unknown `kid`, wrong issuer |
| 4. Refresh | rotation; the old refresh token again → reuse detected → the newest is revoked too |
| 5. Logout | two sessions for manoj; logging out one revokes its access and refresh token; the other keeps working |
| 6. Disable a user | asha's **existing** access token still works; refresh and login refused; re-enable; admin revokes all sessions; admin cannot disable themselves |

The hand-made tokens stored in the collection are all **unusable on purpose**: expired, wrong key, unsigned, unknown `kid`, wrong issuer. The committed file therefore contains no working credential.

## Tests: 34 passing

```text
mvn test
Tests run: 8,  Failures: 0, Errors: 0 -- in com.company.jwt.LoginAndAccessTest
Tests run: 11, Failures: 0, Errors: 0 -- in com.company.jwt.TokenRejectionTest
Tests run: 10, Failures: 0, Errors: 0 -- in com.company.jwt.RefreshAndRevocationTest
Tests run: 5,  Failures: 0, Errors: 0 -- in com.company.jwt.KeyRotationTest
```

| Test class | What it proves |
|---|---|
| `LoginAndAccessTest` | login returns the pair (900 s / 7 days); the decoded token has `alg`, `kid`, `sub`, `iss`, `jti`, `exp − iat = 900`, authorities, and no password or hash; token → `/api/me` and `/api/directory` 200, admin 403; **the server trusts the signed claims, not the database**; wrong / unknown / disabled login 401; missing field 400; **no session or cookie** at login or afterwards; public endpoint without a token. |
| `TokenRejectionTest` | missing, wrong scheme, malformed (also empty), expired, **within the clock skew → accepted**, other key, **tampered payload**, **unsigned `alg:none`**, unknown `kid`, other issuer, an invalid token on a public endpoint. Each one checks the 401 message and the `WWW-Authenticate` header. |
| `RefreshAndRevocationTest` | rotation; **reuse detection** revokes the family (`ROTATED`, `REUSE_DETECTED` in the table); made-up refresh token; only SHA-256 hashes stored; **logout** revokes this access token (deny-list) and refresh token while another session keeps working; logout needs a token; **disabled user keeps the current access token but cannot refresh or log in**; a role change appears after the next refresh; admin revoke-all; an admin cannot disable themselves. |
| `KeyRotationTest` | new tokens carry `kid` test-new; a token from the **old** key is still accepted; the `kid` must match the key that signed; startup fails for a missing active key and for a key under 256 bits. |

Tests that change accounts create their own users first (directly in the test database), so they don't depend on each other.

## Limitations (deliberate, documented)

- **Access tokens can't be recalled instantly** except through the deny-list. Disabling a user leaves their current token valid for up to 15 minutes.
- **The deny-list is in memory**: one server instance, cleared on restart. In production it would use Redis or similar.
- **HS256 uses one shared secret for signing and verifying.** If other services had to verify tokens without being able to create them, an asymmetric algorithm (RS256 / ES256, private key signs, public key verifies) would be the next step.
