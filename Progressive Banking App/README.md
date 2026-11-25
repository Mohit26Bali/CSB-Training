# Crestline Bank (Progressive Banking Application)

Training project: a digital banking application with a Spring Boot REST backend (`backend/`) and a React frontend
(`frontend/`). Customers open accounts, deposit, withdraw, transfer and review their history; administrators manage
account status. Based on `Progressive_Banking_Project_Description.pdf`.

```
React (Vite) --/api--> Controller -> Service -> Repository -> MySQL "bank"
                         (DTOs)     (rules)     (JPA)        (Flyway-managed schema)
```

## Run it

Needs JDK 17, Node 20+, and MySQL with a database called `bank`. There are **no default secrets**: before the first start set
`DB_PASSWORD` (your MySQL password, user `root` unless you set `DB_USERNAME`) and `JWT_SECRET` (32+ random characters; the backend
refuses to start without it). Add `SEED_DEMO_DATA=true` once to create the demo logins below (they have published passwords, so
never use them on a real deployment).

```powershell
$env:DB_PASSWORD = "your-mysql-password"
$env:JWT_SECRET  = node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
$env:SEED_DEMO_DATA = "true"       # first run only, for the demo logins
```

```powershell
# terminal 1 - backend, http://localhost:8080 (first start creates the tables via Flyway; demo data only with SEED_DEMO_DATA=true)
cd backend
.\mvnw spring-boot:run

# terminal 2 - frontend, http://localhost:5173
cd frontend
npm install        # first time only
npm run dev
```

| Login | Password | Role |
|---|---|---|
| `ravi` | `customer123` | Customer |
| `priya` | `customer123` | Customer |
| `admin` | `admin12345` | Administrator |

These demo logins are seeded directly, so they are exempt from the sign-up password rules. A new customer must choose a
strong password: 8-20 characters with upper and lower case, a digit and a symbol, not a common password and not
containing their username or email; the sign-up form shows a live checklist, and the server enforces the same rules.

## API documentation (Swagger)

With the backend running: **http://localhost:8080/swagger-ui.html** (OpenAPI JSON at `/v3/api-docs`).
Call `POST /api/auth/login`, copy the `token`, press **Authorize**, paste it, and every "Try it out" call is sent with it.
Set `SWAGGER_ENABLED=false` to switch the docs off in production.

## Database migrations (Flyway)

The schema is owned by Flyway, not Hibernate (`ddl-auto=validate`: Hibernate only checks that entities match tables).

- Scripts: `backend/src/main/resources/db/migration/mysql/V1__initial_schema.sql` (MySQL) and
  `.../h2/V1__initial_schema.sql` (the in-memory database of the automated tests). Both folders must contain every
  version, and an applied script must never be edited.
- A database that already had the tables (created by Hibernate before Flyway) is baselined at V1 automatically.
- To change the schema: add `V2__what_changed.sql` to **both** folders, then change the entity. The tests fail if an
  entity and the migrations disagree.

## Tests

| Suite | Command | What it covers |
|---|---|---|
| Backend unit tests (Mockito) | `cd backend; .\mvnw test` | Ledger rules, services, JWT, idempotency, lockout, filters, password strength (139 tests with the API tests) |
| Backend API tests (MockMvc + H2) | same command | Every endpoint through the real security chain, Swagger docs, history filters |
| Browser end-to-end (Playwright, 67 tests) | `cd frontend; npm run e2e` | Login/registration and password strength, banking flows, filters, admin, theme, responsive layout |

The end-to-end suite starts its own isolated backend (port 8091, in-memory H2) and frontend (port 5174),
so it does not touch your MySQL data and can run while the dev servers are up. As a safety net, `frontend/e2e/global-setup.ts`
aborts the run before any test starts if the backend holds anything other than the fresh seed data. It uses the installed Microsoft Edge
(`PW_CHANNEL=chromium` after `npx playwright install chromium` to use Chromium instead). `PW_REUSE=1` reuses servers
that are already running, which makes repeated runs fast. `npm run e2e:ui` opens Playwright's interactive runner and
`npm run e2e:report` the last HTML report.

## What is where

- `backend/` - Spring Boot app. Every class starts with a comment explaining its role and which classes it works with;
  every package has a `package-info.java` describing the layer.
- `frontend/` - React app; see `frontend/README.md` for its structure.
- `Progressive_Banking_Project_Description.pdf` - the original requirements.
