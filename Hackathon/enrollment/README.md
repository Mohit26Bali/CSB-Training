# Online Course Enrollment and Approval System

Employees browse internal training courses and request a seat. Administrators create and manage courses and
approve or reject requests, while course capacity is enforced. Spring Boot REST API plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 running on `localhost:3306`.

```bash
# one-time: create the database (user root / password mohit123 are the defaults)
mysql -uroot -pmohit123 -e "CREATE DATABASE IF NOT EXISTS course_enrollment"

# terminal 1: backend  (http://localhost:8080)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5173)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5173>. The sun/moon button (top right) switches between light and dark mode; it follows your
operating system setting until you choose, then remembers your choice. On first start the backend creates the tables and seeds demo data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `employee1` … `employee4` | `employee123` | EMPLOYEE |
| `aarav.sharma`, `diya.patel`, `rohan.mehta`, … (30 employees in total) | `employee123` | EMPLOYEE |

The seed also creates 16 courses with pictures and a spread of pending, approved and rejected requests.
Seeding is idempotent and can be switched off with `app.seed.enabled=false`.

## Technology

| Area | Choice |
|---|---|
| Backend | Java 17, Spring Boot 4.1, Spring MVC (`@RestController`, DTOs), Maven wrapper |
| Persistence | Spring Data JPA / Hibernate, MySQL 8 |
| Security | Spring Security, stateless JWT bearer tokens, BCrypt, role-based authorization (URL rules and `@PreAuthorize`) |
| Validation | Bean Validation on requests plus business rules in the services |
| Front end | React 19, TypeScript, Vite, React Router, TanStack Query, Axios, Tailwind CSS |
| Tests | JUnit 5, MockMvc, H2 in memory (no MySQL needed), Postman CLI collection |

## Architecture

```
React (Vite) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
com.hackathon.enrollment
├── config        SecurityConfig (filter chain, CORS, BCrypt), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    Auth, Course, Enrollment, AdminCourse, AdminEnrollment, AdminDashboard
├── service       AuthService, LoginAttemptService, CourseService, EnrollmentService, DashboardService
├── repo          JpaRepository interfaces (row-locking queries for capacity)
├── entity        User, Course, Enrollment, Role, EnrollmentStatus
├── dto           request/response objects, ErrorResponse (with per-field errors)
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src
├── api           typed Axios client and endpoint functions
├── auth          AuthContext (session, expiry), RequireRole route guard
├── components    Layout, AuthShell (animated login panel), Toast, shared UI
└── pages         LoginPage, RegisterPage, employee/*, admin/*
```

Controllers handle HTTP only, services hold every business rule, repositories only query.
Data processing in the services uses Java Streams.

## Database

| Table | Notes |
|---|---|
| `users` | `username` UNIQUE, BCrypt `password`, `role` (`ADMIN` / `EMPLOYEE`), `enabled` |
| `courses` | `title`, `description`, `category`, `image_url`, `capacity`, `active`, `created_at` |
| `enrollments` | FKs to user and course, `status`, `requested_at`, `decided_by`, `decided_at`, **UNIQUE (`user_id`, `course_id`)** |

Tables are created and updated by Hibernate (`ddl-auto=update`).

## Authentication

`POST /api/auth/login` returns a JWT valid for 60 minutes. Send it as `Authorization: Bearer <token>` on every
other request. The user is re-read from the database on each request, so disabling an account takes effect at once.

| Situation | Response |
|---|---|
| Missing, invalid or expired token; wrong password | `401` |
| Valid login but wrong role | `403` |
| 5 failed logins for a username | `429` for 15 minutes |

Self-registration always creates an EMPLOYEE; a `role` in the request is ignored.

## API reference

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | `/api/auth/login` | public | Log in, returns the token |
| POST | `/api/auth/register` | public | Create an employee account |
| GET | `/api/auth/me` | any | Current user |
| GET | `/api/courses?search=&category=` | any | Browse **active** courses |
| GET | `/api/courses/categories` | any | Categories of active courses |
| POST | `/api/enrollments` | EMPLOYEE | Request enrollment `{"courseId": 1}` |
| GET | `/api/enrollments/my` | EMPLOYEE | Own requests, newest first |
| DELETE | `/api/enrollments/{id}` | EMPLOYEE | Cancel own **pending** request |
| GET | `/api/admin/dashboard` | ADMIN | Totals and per-course seat counts |
| GET | `/api/admin/courses?search=&category=` | ADMIN | All courses, including inactive |
| POST | `/api/admin/courses` | ADMIN | Create a course |
| PUT | `/api/admin/courses/{id}` | ADMIN | Update a course |
| DELETE | `/api/admin/courses/{id}` | ADMIN | Delete, or close it if it has enrollments |
| POST | `/api/admin/courses/{id}/reject-pending` | ADMIN | Reject every pending request of a course |
| GET | `/api/admin/enrollments[?status=PENDING]` | ADMIN | List requests |
| PUT | `/api/admin/enrollments/{id}/approve` | ADMIN | Approve a pending request |
| PUT | `/api/admin/enrollments/{id}/reject` | ADMIN | Reject a pending request |

Course body: `title` (required, max 100), `description` (max 255), `category` (required, max 50),
`capacity` (1 to 10000), `imageUrl` (optional: a site path like `/course-images/x.jpg` or an http(s) URL), `active`.

Every error has the same shape. Validation errors also list each bad field:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "title: Title is required",
  "fieldErrors": { "title": "Title is required" },
  "timestamp": "2026-10-22T16:20:41.123"
}
```

## Business rules

| Rule | Result |
|---|---|
| A user cannot enroll in the same course twice (service check and DB unique constraint) | `409` |
| Enrollment cannot exceed capacity (checked on request **and** on approval) | `409` |
| Inactive courses cannot accept or approve enrollments | `400` |
| Only `PENDING` requests can be approved, rejected or cancelled | `409` |
| Capacity cannot be lowered below the number of approved seats | `409` |
| Course titles are unique, ignoring case | `409` |

Design decisions:

- Capacity counts **approved** enrollments only; pending requests do not hold a seat.
- Rejected requests are final: the employee cannot request that course again. Cancelling a pending request deletes it.
- Deleting a course that has enrollments closes it instead, so history is kept.
- **Concurrency:** requests, approvals and course updates take a row lock on the course (and approvals on the
  enrollment), under `READ_COMMITTED`. Two admins approving the last seat at once cannot both succeed.
- Every decision records who made it and when (`decidedBy`, `decidedAt`).

## Tests

```bash
./mvnw test            # 17 tests on in-memory H2: business rules, concurrency, security, validation
```

API walkthrough with the Postman CLI (start the backend first; the run is repeatable):

```bash
postman collection run docs/postman/CourseEnrollment.postman_collection.json
```

Or import `docs/postman/CourseEnrollment.postman_collection.json` into the Postman app and run it with the Collection Runner.
It runs 39 requests (64 assertions) covering login, browsing, enrolling, approving, full courses and security checks.

## Configuration

Environment variables override the defaults in `application.properties`:

| Variable | Default |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/course_enrollment` |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `mohit123` |
| `JWT_SECRET` | a development value; **set your own (32+ characters) outside local use** |
| `CORS_ORIGINS` | `http://localhost:5173` |

## Images

Course and login pictures live in `frontend/public/course-images` and `frontend/public/hero`. They were downloaded from
[Pexels](https://www.pexels.com) (free licence) with `tools/fetch_course_images.py` (in the Hackathon folder). Run it with `PEXELS_API_KEY` set to refresh them.

## Known limitations

- Login throttling is kept in memory, so it resets when the server restarts and is per server instance.
- The front end is run with the Vite dev server; it is not bundled into the Spring Boot jar.
- The JWT lives in `sessionStorage` (per tab). That is simple, but a production system would use an httpOnly cookie.
