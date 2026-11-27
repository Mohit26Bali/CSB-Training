# Community Maintenance Request System

Residents report maintenance problems and follow them to completion; facility managers assign technicians, move requests through the work and record what was done. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `community_maintenance` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8110)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5110)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5110>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `facility` | `manager123` | FACILITY_MANAGER |
| `ravi.menon` | `manager123` | FACILITY_MANAGER |
| `aarav.sharma` (a request in every state) | `resident123` | RESIDENT |
| `diya.patel`, `rohan.mehta`, … (30 residents) | `resident123` | RESIDENT |

## Features

- **Resident:** report a problem (category, description, location, priority); see own requests with a five-step journey (reported, assigned, in progress, resolved, closed), who is assigned, how long it has waited and, once done, what was done.
- **Facility manager:** a queue ordered by urgency (emergencies first, then the longest waiting) with filters for status, category, priority and age, and search by place, problem, reference (MR-3042) or resident; assign or reassign a technician (those of the right trade and with the fewest jobs listed first); start work, resolve with notes, close; a technician directory (add, make unavailable); a dashboard with where requests are, ageing counts, requests per category and each technician's workload.
- Priority, category dashboard, search and filter, and an aging indicator (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Category, description, location and priority are mandatory; unknown categories and priorities are refused | `400` with a field error each |
| Only facility managers assign or change status; only residents report, and residents see only their own requests | `403` |
| The flow is OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED. ASSIGNED comes only from assigning a technician; a resolved request can go back to IN_PROGRESS; closed is final | `409` / `400` |
| Resolution notes are mandatory to resolve; the resident sees them | `400` field error |
| Only active technicians can be assigned; a technician with active jobs cannot be made unavailable; technician names are unique | `400` |
| Aging of an active request: under 2 days is fresh, 2 to 4 days ageing, 5 or more stale | computed |

**Concurrency:** every assignment and status change locks the request row (under `READ_COMMITTED`), so when several managers click Start work on the same assigned request, or two resolve it, exactly one wins and the others get a clear `409`. The tests check both with parallel threads.

The reference (MR-3042) is the request id plus 3000. Residents' requests are never edited after they are sent; the manager's notes carry the story.
The technician list is separate from user accounts: technicians can be staff or outside vendors and do not log in.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| POST | /api/requests | RESIDENT | Report `{category, description, location, priority}` |
| GET | /api/requests/my?status=&search= | RESIDENT | Own requests |
| GET | /api/requests/meta | any | Categories and priorities |
| GET | /api/requests/{id} | owner / managers | One request |
| GET | /api/manager/requests?status=&priority=&category=&technicianId=&aging=&search= | FACILITY_MANAGER | The queue |
| PUT | /api/manager/requests/{id}/assign | FACILITY_MANAGER | Assign `{technicianId}` (moves OPEN to ASSIGNED) |
| PUT | /api/manager/requests/{id}/status | FACILITY_MANAGER | Move `{status, notes?}` (notes mandatory for RESOLVED) |
| GET | /api/manager/technicians | FACILITY_MANAGER | Technicians with their active jobs |
| POST | /api/manager/technicians | FACILITY_MANAGER | Add `{name, trade, phone?}` |
| PUT | /api/manager/technicians/{id}/active?value= | FACILITY_MANAGER | Make available or unavailable |
| GET | /api/manager/dashboard | FACILITY_MANAGER | Statuses, ageing, categories, technician load |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5110) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/maintenance
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, RequestController (everyone), ManagerController
├── service       MaintenanceService (requests, queue, assignment, status flow, technicians, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository, MaintenanceRequestRepository (row lock), TechnicianRepository
├── entity        User, MaintenanceRequest, Technician, Role, Category, Priority, RequestStatus (knows its legal next steps)
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `technicians` (unique name, trade, active) and `maintenance_requests` (resident and technician FKs, status, location, resolution notes).

## Tests

```bash
cd backend
./mvnw test        # 25 tests on in-memory H2 (no MySQL needed): reporting, visibility, assignment, status flow, ageing, queue order, technicians, dashboard, roles, parallel updates
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Maintenance.postman_collection.json
```

It runs 53 requests (83 assertions). It registers a fresh resident and uses a uniquely named location and technician each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- No photos of the problem, no scheduled visits and no notifications.
- Filtering, search and the queue order are done in memory (Java Streams), which is fine for one community but would need paging at scale.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
