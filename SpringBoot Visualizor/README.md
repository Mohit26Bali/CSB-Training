# SpringLab

Interactive learning platform for Java and Spring Boot (Spring Boot 3.5, Java 17, MySQL 8.4, React 19, Tailwind 4).
Status: Phase 9 (Microservices Architecture Lab) implemented (manual browser acceptance pending); JDBC Lab (Phase 8), JPA Relationship Lab (Phase 7), JWT Security Simulator (Phase 6), learning framework and REST Request Lifecycle (Phase 5) before it. See `docs/LEARNING_FRAMEWORK.md`
(requirements and architecture) and `docs/REST_LIFECYCLE_MODULE.md`, `docs/JWT_SIMULATOR_MODULE.md`, `docs/JPA_RELATIONSHIP_MODULE.md`, `docs/JDBC_MODULE.md` and `docs/MICROSERVICES_MODULE.md` (what is LIVE vs INFERRED vs SIMULATED, SQL capture, claim-to-test maps).

SpringLab is a **modular monolith**: one Spring Boot application and one MySQL schema. The Microservices Lab (`/learn/microservices`) is a simulator inside it: its
services are classes in one JVM that talk through an in-process service bus, with simulated time, failures, event channel, gateway and registry. It is not a deployed
microservices system, and its databases are logically (not physically) separated. `docs/MICROSERVICES_MODULE.md` states exactly what is real and what is simulated.

Coding practice (Phase F3, backend only) lets a learner run and submit real Java against sample and hidden tests, judged in a sandbox outside the backend JVM; it is disabled by default. See `docs/CODING_PLATFORM.md` (and `judge-worker/README.md` for the worker).

The interface uses one dark theme (Graphite + Lime): see `docs/DESIGN_SYSTEM.md`. Photo credits: `docs/IMAGE_CREDITS.md`.

## Configuration (environment variables, never committed)

| Variable | Purpose | Default |
|---|---|---|
| `JWT_SECRET` | HS256 signing key, **required**, at least 32 bytes | none (app refuses to start) |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL credentials | `springlab` / empty |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | MySQL location | `localhost` / `3306` / `springlab` |
| `TEST_DB_NAME` | schema used by `mvn test` | `springlab_test` |

| `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Optional. Set **all three** to create the first ADMIN at startup when no active admin exists (password >= 12 chars). Never reset on restart, never logged | unset (no admin created) |

Generate a key: `openssl rand -hex 32`

## Security decisions

- **Authorization is read from the database on every authenticated request** (`DatabaseBackedJwtAuthenticationConverter`).
  The JWT proves identity (signature, expiry, issuer); the `users` table decides the current role and whether the
  account is enabled. A role change or disabling an account therefore applies on the *next request*, with the
  existing token. The token's `role` claim is informational only and is never trusted.
  - Cost: one indexed lookup (unique `users.username`, 3 columns, no password hash) per authenticated request.
  - Deliberately not cached, because any cache would reintroduce a staleness window. If load ever demands it, a
    short-TTL cache with explicit invalidation on admin changes is the next step.
- **Admin invariants live in the service layer** (they hold for any caller): an administrator cannot demote or disable
  themselves, and the last *active* administrator can never be demoted or disabled. Active admin rows are locked
  (`SELECT ... FOR UPDATE`) during these changes so two admins acting at the same time cannot leave zero admins.
- `/api/admin/**` is protected by a URL rule **and** `@PreAuthorize`; the frontend route guard is only UX.
- List sorting is whitelisted (e.g. `sort=passwordHash` is rejected).
- **Challenge Arena integrity:** correct answers/explanations exist only in the result DTOs, which are served only for
  SUBMITTED attempts; scoring happens on the server from stored data; the attempt row is locked
  (`SELECT ... FOR UPDATE`) while submitting, so an attempt can be scored exactly once even under concurrent requests.
- **Question ownership:** a teacher may change only their own questions; an admin any; seed questions (no author) are
  admin-only. Optimistic locking (`@Version`) rejects stale edits with 409. Questions are deactivated, never deleted.
- Recovery if all admins are disabled/lost: set `ADMIN_*` with an unused username and restart.

## Run

```bash
# backend (http://localhost:8080)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# frontend (http://localhost:5173, proxies /api to the backend)
cd frontend && npm install && npm run dev
```

## Test

```bash
cd backend && mvn test      # uses the local MySQL; no Docker required
cd frontend && npm run lint && npm test && npm run build
```

Flyway applies migrations `V1` to `V10` on startup (the Microservices Lab needs `V8`, `V9` and `V10`). The Microservices Lab's frontend fixture is checked against the
real backend; to refresh it after changing an experiment: `cd backend && mvn test -Dtest=MicroFrontendFixtureTest -Dmicro.fixture.write=true`.
