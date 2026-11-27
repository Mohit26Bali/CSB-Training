# Visitor Management System

Employees pre-register visitors and get a pass code to share; security sees who is expected today, checks visitors in and out and keeps a daily record. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `visitor_management` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8104)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5104)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5104>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `security` | `guard123` | SECURITY_ADMIN |
| `rohit.gate` | `guard123` | SECURITY_ADMIN |
| `aarav.sharma` (four visitors: expected today with pass `VP-100001`, inside now, yesterday's, tomorrow's) | `employee123` | EMPLOYEE |
| `diya.patel`, `rohan.mehta`, … (30 employees) | `employee123` | EMPLOYEE |

## Features

- **Employee:** invite a visitor (name, phone, company, purpose, date and an optional time) and get a pass with a code and a QR-style pattern to share; see own invitations (upcoming, today, history) with when the visitor arrived and left; edit or cancel an invitation that has not been used.
- **Security:** a gate screen with today's expected visitors (those inside first), live counts, search by name, phone, company, host or pass code across all days, a pass-code lookup, one-click check in and check out with the time inside; a daily report with expected vs arrived for the last 7 days, no-shows, upcoming invitations, average visit length and busiest hosts.
- Visitor search, QR-code-style pass number and daily visitor statistics (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Visitor name, phone and purpose are mandatory; the phone number must look like a phone number | `400` with a field error each |
| The expected date cannot be in the past (today is allowed) | `400` field error |
| Only security checks visitors in or out; only the host edits or cancels an invitation | `403` |
| A visitor cannot be checked out before being checked in, and cannot be checked in or out twice | `409` |
| A visitor can only be checked in on the expected date; a lapsed invitation needs a new one | `400` |
| An invitation can be edited or cancelled only while it is still EXPECTED | `409` |
| The same host cannot invite the same phone number twice for one day (+91 98450 11201 and 9845011201 count as the same number) | `409` |
| Pass codes (VP-123456) are unique | constraint |

**Concurrency:** inviting locks the host row (so a double click makes one pass) and every check-in, check-out, edit or cancel locks the visit row (under `READ_COMMITTED`), so two guards cannot both check a visitor in, and an employee cancelling while the guard checks in ends in exactly one consistent state. The tests check all three with parallel threads.

The pattern on the pass is drawn from the pass code in the browser, so there is nothing to store; it is QR-style, not a scannable QR code.
A 'did not come' visitor is not a status of its own: an invitation that is still EXPECTED after its day is shown as lapsed.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| POST | /api/visitors | EMPLOYEE | Invite `{visitorName, phone, company?, purpose, expectedDate, expectedTime?}` |
| GET | /api/visitors/my?status=&search= | EMPLOYEE | Own invitations |
| PUT | /api/visitors/{id} | EMPLOYEE | Edit an unused invitation |
| DELETE | /api/visitors/{id} | EMPLOYEE | Cancel an unused invitation |
| GET | /api/security/visitors?date=&status=&search= | SECURITY_ADMIN | Gate list (today by default; a search without a date looks at all days) |
| GET | /api/security/pass/{code} | SECURITY_ADMIN | Find a visitor by pass code |
| PUT | /api/security/visitors/{id}/check-in | SECURITY_ADMIN | Check in (only on the expected date) |
| PUT | /api/security/visitors/{id}/check-out | SECURITY_ADMIN | Check out (only after check-in) |
| GET | /api/security/stats | SECURITY_ADMIN | Today's numbers, the week and the busiest hosts |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5104) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/visitors
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, VisitorController (employee), SecurityDeskController
├── service       VisitService (invitations, gate work, statistics), AuthService, LoginAttemptService
├── repo          UserRepository (row lock), VisitRepository (row lock)
├── entity        User, Visit, Role, VisitStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users` and `visits` (host FK, unique pass code, expected date, status, check-in and check-out times and guards).

## Tests

```bash
cd backend
./mvnw test        # 26 tests on in-memory H2 (no MySQL needed): invitations, duplicates, editing, the gate, check-in and out rules, search, statistics, roles, parallel check-ins and invitations
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Visitors.postman_collection.json
```

It runs 42 requests (65 assertions). It registers a fresh employee and uses random phone numbers and names each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- No photo, ID capture, badge printing or SMS to the visitor.
- Filtering and search are done in memory (Java Streams), which is fine for one office but would need paging for a large site.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
