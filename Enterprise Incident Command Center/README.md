# Enterprise Incident Command Center (ICC)

A training project: a thin console where IT responders report and handle incidents, run major incidents, record root causes and see a dashboard. Java, Spring Boot microservices, and a React (Material UI) frontend.

Current status: the Spring Boot part of Phases 1 to 3 is done and tested (Assignment with login, Incident, Major Incident, RCA, Dashboard, the gateway and Eureka; 156 tests). The React frontend covers every screen except the ServiceNow sync status (F0 to F6 done; 113 browser checks and 11 Playwright tests). Not yet: Dockerfiles, and ServiceNow (Phase 4). See [docs/ROADMAP.md](docs/ROADMAP.md).

## Prerequisites

- Java 17 (Maven must run on JDK 17)
- Maven 3.9 or newer
- MySQL 8.4 running on port 3306

## One-time setup

### 1. Create the schemas and the user

In the MySQL CLI as root (`mysql -u root -p`). Use your own password in place of `change-me`:

```sql
CREATE DATABASE icc_incident       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE icc_assignment     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE icc_major_incident CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE icc_rca            CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'icc'@'localhost' IDENTIFIED BY 'change-me';

GRANT ALL PRIVILEGES ON icc_incident.*       TO 'icc'@'localhost';
GRANT ALL PRIVILEGES ON icc_assignment.*     TO 'icc'@'localhost';
GRANT ALL PRIVILEGES ON icc_major_incident.* TO 'icc'@'localhost';
GRANT ALL PRIVILEGES ON icc_rca.*            TO 'icc'@'localhost';
```

### 2. Create `.env`

Copy `.env.example` to `.env` and put the real password in it. `.env` is not committed.

## Run

In PowerShell, from the project root. Spring Boot does not read `.env`, so load it into the window first:

```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^\s*([^#=]+)=(.*)$') { Set-Item "env:$($matches[1].Trim())" $matches[2].Trim() } }

mvn install -DskipTests                             # FIRST TIME: build everything and install the shared parent and common-lib
                                                    # into your local Maven repository (spring-boot:run below needs them there)
mvn verify                                          # optional: build and run all 156 tests (needs MySQL and .env)
mvn -pl infra/eureka-server spring-boot:run         # start Eureka first
mvn -pl infra/api-gateway spring-boot:run           # then the gateway
mvn -pl services/assignment-service spring-boot:run # then each service, one terminal each
```

Services: `assignment-service`, `incident-service`, `major-incident-service`, `rca-service`, `dashboard-service`. `.env` must contain `JWT_SECRET` (32 characters or more), because the gateway and every service verify tokens with it.

**Demo data (for a demo or a review):** start the Incident, Major Incident and RCA services with the `demo` profile and they also load a ready-made data set: 12 incidents in all states, 2 major incidents with timelines (one still active, one resolved), and 2 problems (one DRAFT for showing the RCA, one SUBMITTED with actions). The ids match across services (users 1 to 7, teams 1 to 4). It is safe to start with the profile again: existing rows are left alone.

```powershell
mvn -pl services/incident-service spring-boot:run "-Dspring-boot.run.profiles=demo"
mvn -pl services/major-incident-service spring-boot:run "-Dspring-boot.run.profiles=demo"
mvn -pl services/rca-service spring-boot:run "-Dspring-boot.run.profiles=demo"
```

With the jars: `java -jar services/incident-service/target/incident-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo`. After starting, wait about a minute before the first request, because the gateway and the Dashboard need that long to learn where the services are.

**Stopping and rebuilding `common-lib`:** on Windows a running service locks the `common-lib` jar. Stop all services before `mvn -pl libs/common-lib -DskipTests install`, then start them again.

## Frontend (React)

The frontend is in `frontend/` (see [docs/FRONTEND-PLAN.md](docs/FRONTEND-PLAN.md) for the phases; F0 (foundation), F1 (login, role-based menu) and F2 (incidents: report, queue, accept, resolve, close, reassign, comments) F3 (major incidents: declare, status steps, timeline) F4 (problems: root cause, corrective actions, submit, close) F5 (dashboard, assignment rules, audit trails) and F6 (screen layout, Playwright tests) are done; log in with a demo user, for example `ananya@icc.com` / `Icc@Demo2026`). You need Node 20 or newer.

```powershell
cd frontend
npm install
npm run dev          # http://localhost:5173 (the gateway allows this address)
npm run build        # type-check and build
npm run gen:api      # with all services running: fetch the API contract and regenerate the types
npm run check:api    # with all services running: fails if the saved contract is out of date
npm run check:login      # dev server + backend running: 22 checks of the login flow in a real browser (Edge or Chrome)
npm run check:incidents  # dev server + backend running: 29 checks of the incident lifecycle (creates a few "[e2e]" incidents)
npm run check:major      # dev server + backend running: 21 checks of the major incident screens (also creates "[e2e]" test data)
npm run check:problems   # dev server + backend running: 23 checks of the problem and RCA screens (also creates "[e2e]" test data)
npm run check:admin      # dev server + backend running: 18 checks of the dashboard, assignment rules and audit trails (also creates "[e2e]" test data; puts the rule back)
npm run test:e2e         # stack running: 11 Playwright tests (the demo story, keyboard and labels, phone width) in Microsoft Edge
```

The Playwright tests (`frontend/e2e/`) use the Edge that Windows already has, so no browser is downloaded; set `BROWSER_CHANNEL=chrome` to use Chrome instead. They create records that start with "[e2e-pw]".

**Definition of done for a frontend task:** `npm run build` passes; `npm run check:api` passes against the running backend (the saved contract matches the code); the browser checks and `npm run test:e2e` pass; the test data is removed.

The browser only talks to the gateway on port 8080. Never put secrets in the frontend. The API types in `frontend/src/api/generated` are generated from the backend; do not edit them by hand.

## Demo for a review

[DEMO-SCRIPT.md](DEMO-SCRIPT.md) is a 10-minute script with what to say at each step. Its requests are in `postman/ICC-demo.postman_collection.json` (needs the demo data), and `scripts/reset-demo-data.sql` puts the data back to its starting state.

## Test it in Postman

1. Start MySQL, Eureka, the gateway and the five services (see "Run"), and wait about a minute.
2. In Postman: **Import** the file `postman/ICC.postman_collection.json`.
3. Open the collection and click **Run**. The requests run in order, log in as each seed user, pass the ids on to the next request, and each has a test that shows pass or fail. You can also click a single request and press **Send**; run the folder "0. Log in" first, because it stores the tokens.
4. The base address is the collection variable `baseUrl` (`http://localhost:8080`). Change it there if needed.

The collection has 74 requests in 9 folders: log in, the incident flow, major incident, problem and RCA, finishing the incidents, the dashboard, the audit trails, assignment rules and some security checks. Expected refusals (400, 403, 404, 409) are tests too: a request passes when it returns the status the rules require.

To see the dashboard's partial result, stop the RCA service and send "Summary" again: it still answers 200 and lists `problems` as unavailable.

## Try it through the gateway

Everything goes through port 8080. Log in as a seed user (see "Demo users"), then send the token:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType 'application/json' -Body '{"username":"ananya@icc.com","password":"Icc@Demo2026"}'
$h = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/incidents -Headers $h -ContentType 'application/json' -Body '{"shortDescription":"Core switch down","description":"Floor 3 offline","category":"Network","impact":1,"urgency":1}'
```

The flow, with the user who does each step:

1. `ananya@icc.com` reports an incident (`POST /api/incidents`). It becomes priority 1 and goes to the team that handles the category.
2. The engineer of that team (`sai@icc.com` for Network) sees it in `GET /api/incidents?scope=assigned`, accepts it (`POST /{id}/accept`), adds work notes and resolves it.
3. For a priority 1 incident, an engineer escalates it (`POST /api/major-incidents`), adds timeline updates and moves the status DECLARED, MITIGATING, RESOLVED.
4. `mohit@icc.com` creates a problem (`POST /api/problems`), saves the RCA once the major incident is resolved, adds corrective actions and closes the problem.
5. `mohit@icc.com` reads `GET /api/dashboard/summary` and `GET /api/dashboard/trends?days=7`. If one service is down, the summary still answers and names the missing part.
6. `mohit@icc.com` can read each service's audit trail (`GET /api/incidents/audit`, `/api/major-incidents/audit`, `/api/problems/audit`, `/api/assignment/audit`).

All endpoints and their roles are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Ports

| Component | Port |
| --- | --- |
| API gateway (the only address a browser uses) | 8080 |
| Eureka | 8761 |
| Assignment | 8082 |
| Incident | 8086 |
| Major Incident | 8083 |
| RCA | 8084 |
| Dashboard | 8085 |

Incident is on 8086 because Jenkins uses 8081 on the development machine.

## Check that it works

- `http://localhost:8761` lists the five services and `API-GATEWAY` (allow about 30 seconds). The gateway registers itself too, and it also reads the registry to find the services.
- The gateway needs up to about 30 seconds after the services register before it routes to them (the load balancer refreshes its list). Until then you may see a 503.
- `http://localhost:<port>/actuator/health` returns `{"status":"UP"}` for each service.

If a service says "Port ... already in use", another copy is already running. Stop it first.

## Demo users

The Assignment service seeds these users when it starts. They all share one **local-only demo password**: `Icc@Demo2026`. Never reuse it anywhere real.

| Username | Role | Team |
| --- | --- | --- |
| `mohit@icc.com` | ADMIN | none |
| `priya@icc.com` | SUPPORT_ENGINEER | Service Desk |
| `sai@icc.com` | SUPPORT_ENGINEER | Network |
| `sachin@icc.com` | SUPPORT_ENGINEER | Database |
| `vinod@icc.com` | SUPPORT_ENGINEER | Applications |
| `ananya@icc.com`, `rohan@icc.com` | EMPLOYEE | none |

## Documents

- [ICC-Testing-Manual.html](ICC-Testing-Manual.html): **open in a browser**: how to set up, run in Eclipse, run the 156 tests, understand JWT and test every endpoint in Postman, with a checklist
- [ENDPOINTS.md](ENDPOINTS.md): every endpoint on one page, with roles and example requests
- [PROJECT-GUIDE.md](PROJECT-GUIDE.md): **start here if you are new**: what the project is, why it is built this way, how a request travels, file by file
- [PROGRESS.md](PROGRESS.md): what is built so far and how it works
- [VIVA-QA.md](VIVA-QA.md): flow, tables and likely questions
- [docs/](docs/): DECISIONS, ARCHITECTURE, DATA-MODEL, ROADMAP, WALKTHROUGH, FRONTEND-PLAN (the React plan and how it stays in sync with the backend)
