# Scholarship Application Management System

Students browse scholarships and apply with their academic and financial details; administrators review the applications and approve or reject them with a reason. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `scholarship_mgmt` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8105)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5105)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5105>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `scholarship` | `admin123` | SCHOLARSHIP_ADMIN |
| `meena.krishnan` | `admin123` | SCHOLARSHIP_ADMIN |
| `aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (14 students) | `student123` | STUDENT |

## Features

- **Student:** browse active scholarships (amount, deadline, criteria); apply with CGPA, family income, course, year and a statement, with a live eligibility preview; track every application, its status and the reason for the decision.
- **Administrator:** review applications (filter by scholarship and status, search by student or course) and approve or reject with a reason; create, edit and hide scholarships; statistics per scholarship (counts, approval rate, average score, total awarded).
- Eligibility score, filtering by scholarship and application statistics (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| A student cannot apply twice to the same scholarship (service check and a database unique constraint) | `409` |
| CGPA (0 to 10), family income, course, year (1 to 6) and a statement are all mandatory | `400` with a field error each |
| Closed (deadline passed) or hidden scholarships do not accept applications; the deadline day itself is still open | `400` |
| Only administrators approve or reject | `403` for students |
| Approving and rejecting both need a reason | `400` with a field error |
| A decided application cannot be decided again | `409` |
| Students see only their own applications; administrators cannot apply | `403` / filtered by user |
| Scholarship names are unique, ignoring case | `400` with a field error |

**Concurrency:** applying twice at the same moment (a double click) creates one application, because the unique constraint settles the race and the loser gets a clear `409`; deciding takes a row lock under `READ_COMMITTED`, so two administrators cannot both decide the same application. The tests check both with parallel threads.

**Eligibility score (0 to 100):** up to 60 points for academics (CGPA ÷ 10 × 60) plus up to 40 for need (falling in a straight line from 40 at zero income to 0 at the scholarship's income ceiling). "Meets criteria" is separate: CGPA at or above the minimum and income at or below the ceiling. An application that does not meet the criteria can still be submitted; the administrator decides.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/programs | any | Active scholarships (open ones flagged) |
| POST | /api/applications | STUDENT | Apply `{programId, gpa, annualFamilyIncome, course, yearOfStudy, statement}` |
| GET | /api/applications/my | STUDENT | Own applications with score and decision |
| GET | /api/admin/programs | SCHOLARSHIP_ADMIN | All scholarships with application counts |
| POST | /api/admin/programs | SCHOLARSHIP_ADMIN | Create a scholarship |
| PUT | /api/admin/programs/{id} | SCHOLARSHIP_ADMIN | Edit (or hide with `active: false`) |
| GET | /api/admin/applications?programId=&status=&search= | SCHOLARSHIP_ADMIN | Applications |
| PUT | /api/admin/applications/{id}/approve | SCHOLARSHIP_ADMIN | Approve with `{reason}` |
| PUT | /api/admin/applications/{id}/reject | SCHOLARSHIP_ADMIN | Reject with `{reason}` |
| GET | /api/admin/statistics | SCHOLARSHIP_ADMIN | Totals and per-scholarship statistics |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5105) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/scholarship
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, ProgramController, ApplicationController (student), AdminController
├── service       ProgramService, ApplicationService (rules, statistics), EligibilityCalculator, AuthService, LoginAttemptService
├── repo          UserRepository, ScholarshipProgramRepository, ApplicationRepository (row lock)
├── entity        User, ScholarshipProgram, Application, Role, ApplicationStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `scholarship_programs` (unique name), `applications` (student and programme FKs, **UNIQUE (student, programme)**, eligibility and decision fields).

## Tests

```bash
cd backend
./mvnw test        # 25 tests on in-memory H2 (no MySQL needed): eligibility maths, one application per scholarship, deadlines, reasons, visibility, roles, statistics, concurrency
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Scholarship.postman_collection.json
```

It runs 42 requests (70 assertions). It registers a fresh student, creates uniquely named scholarships and hides them at the end, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- An application cannot be edited or withdrawn after it is submitted.
- The eligibility score is a simple formula, not a machine-learned ranking.
- Filtering and statistics are computed in memory (Java Streams), which is fine for one college but would need paging at scale.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
