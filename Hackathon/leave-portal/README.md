# Leave Request and Approval Portal

Employees apply for leave and see their balance; managers approve or reject requests. Approved leave updates the balance. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `leave_portal` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8112)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5112)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5112>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `manager` | `manager123` | MANAGER |
| `neha.kapoor` | `manager123` | MANAGER |
| `aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (10 employees) | `employee123` | EMPLOYEE |

## Features

- **Employee:** see the yearly balance for annual, sick and casual leave (and unpaid, which has no limit), including days already requested; request leave with a live working-day count; see all requests and their status; cancel a pending request, or approved leave that has not started yet.
- **Manager:** review queue with approve / reject (an optional message goes to the employee), search and filter by type, a team calendar showing who is away each day, and managers can take leave too (another manager decides it).
- Different leave types, calendar view and search/filter (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| End date cannot be before the start date | `400` with a field error |
| Leave days must be positive: Saturday and Sunday are not counted, so a weekend-only range is refused | `400` with a field error |
| Leave must start and end in the same calendar year | `400` with a field error |
| Not enough balance (pending requests count against it too) | `400` |
| Overlapping pending or approved leave for the same employee is prevented, at request time and again at approval | `409` |
| Approving deducts the days from the balance; cancelling approved future leave returns them | balance stays correct |
| An employee cannot approve or reject their own request (managers' own requests need another manager) | `403` |
| Only managers can decide requests; a decided request cannot be decided again | `403` / `409` |
| Leave that has already started cannot be cancelled | `400` |

**Concurrency:** every request, approval and cancellation for one employee first locks that employee's balance rows (then the request row), under `READ_COMMITTED`. Two managers deciding the same request, or five overlapping requests submitted at once, cannot both succeed. The tests check this with parallel threads.

Working days are Monday to Friday; public holidays are not modelled.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/leave/types | any | Leave types |
| GET | /api/leave/balance?year= | any | Own balance per type (this year, or last/next year) |
| POST | /api/leave | any | Request leave `{leaveType, startDate, endDate, reason}` |
| GET | /api/leave/my?status=&type= | any | Own requests |
| DELETE | /api/leave/{id} | any | Cancel own pending, or approved-and-not-started, leave |
| GET | /api/manager/leave?status=&type=&search= | MANAGER | All requests |
| PUT | /api/manager/leave/{id}/approve | MANAGER | Approve, optional `{note}` |
| PUT | /api/manager/leave/{id}/reject | MANAGER | Reject, optional `{note}` |
| GET | /api/manager/leave/calendar?month=2026-10 | MANAGER | Approved leave in a month |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5112) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/leave
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, LeaveController (everyone), ManagerLeaveController
├── service       LeaveService (all rules), AuthService, LoginAttemptService
├── repo          UserRepository, LeaveRequestRepository, LeaveBalanceRepository (locking queries)
├── entity        User, LeaveRequest, LeaveBalance, LeaveType, LeaveStatus, Role
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `leave_requests` (employee FK, type, dates, working days, status, decision fields) and `leave_balances` (unique per user, year and type).

## Tests

```bash
cd backend
./mvnw test        # 36 tests on in-memory H2 (no MySQL needed): working days, validation, overlap, balances, approval, cancellation, calendar, roles, concurrency
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/LeavePortal.postman_collection.json
```

It runs 38 requests (63 assertions). It uses next year's dates and registers a fresh employee, so it never clashes with the seed data. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- Public holidays and half-days are not modelled.
- Leave cannot span two calendar years: submit two requests.
- Filtering is done in memory (Java Streams), which is fine for a team but would need paging for a large company.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
