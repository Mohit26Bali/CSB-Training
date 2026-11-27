# Customer Complaint Management Portal

Customers file complaints and follow them on a timeline to a written resolution; support agents work a priority-ordered queue, assign, resolve and close. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `complaint_portal` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8117)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5117)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5117>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `agent` | `agent123` | SUPPORT_AGENT |
| `kiran.bhat`, `zoya.khan` | `agent123` | SUPPORT_AGENT |
| `aarav.sharma` (a complaint in every state, one waiting to be rated) | `customer123` | CUSTOMER |
| `diya.patel`, `rohan.mehta`, … (30 customers) | `customer123` | CUSTOMER |

## Features

- **Customer:** file a complaint (subject, details, category, priority); see own complaints with a status filter and search by subject or reference (CMP-2042); open one to follow a timeline (filed, assigned, status changes), read the resolution and rate how it was handled with 1 to 5 stars.
- **Support agent:** a queue ordered by priority then waiting time, with filters for status, priority, category and assignee and search; start work in one click; assign and reassign; resolve with resolution notes, close; a statistics page with counts, resolution rate, average time to resolve, customer rating, a 7-day filed vs resolved chart, and breakdowns by priority and category.
- Priority, category filtering and complaint statistics (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Subject, description, category and priority are mandatory; unknown categories and priorities are refused | `400` with a field error each |
| A customer can only view their own complaints | `403` |
| Only support agents change status or assignment, and only customers can file or rate | `403` |
| The flow is OPEN, IN_PROGRESS, RESOLVED, CLOSED, one step at a time; a resolved complaint can go back to IN_PROGRESS; closed is final | `409`, the message lists what is allowed |
| Resolution notes are mandatory to resolve; the customer sees them | `400` field error |
| A complaint can be rated only once, only by its owner, and only after it is resolved | `409` / `403` |
| Starting work takes the complaint for the agent if nobody owns it; work in progress cannot be left without an owner; only agents can be assignees | automatic / `400` |

**Concurrency:** every status, assignment and feedback change locks the complaint row (under `READ_COMMITTED`), so two agents resolving the same complaint resolve it once and a double-clicked rating counts once. The tests check both with parallel threads.

The timeline is stored as events (filed, assigned, status changed, feedback), so it can never disagree with the complaint itself.
The reference (CMP-2042) is the complaint id plus 2000. One page, `/complaints/{id}`, serves both roles, with agent controls only for agents.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| POST | /api/complaints | CUSTOMER | File `{subject, description, category, priority}` |
| GET | /api/complaints/my?status=&search= | CUSTOMER | Own complaints |
| GET | /api/complaints/meta | any | Categories and priorities |
| GET | /api/complaints/{id} | owner / agents | A complaint with its timeline |
| POST | /api/complaints/{id}/feedback | CUSTOMER | Rate `{rating 1-5, comment?}` (once, after resolution) |
| GET | /api/agent/complaints?status=&priority=&category=&assignee=&search= | SUPPORT_AGENT | The queue |
| PUT | /api/agent/complaints/{id}/assign | SUPPORT_AGENT | Assign `{assignee}` (empty = unassigned) |
| PUT | /api/agent/complaints/{id}/status | SUPPORT_AGENT | Move `{status, note?}` (note mandatory for RESOLVED) |
| GET | /api/agent/agents | SUPPORT_AGENT | Agents with their active load |
| GET | /api/agent/statistics | SUPPORT_AGENT | Counts, rating, breakdowns and the last 7 days |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5117) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/complaints
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, ComplaintController (everyone), AgentController
├── service       ComplaintService (complaints, queue, assignment, status flow, timeline, feedback, statistics), AuthService, LoginAttemptService
├── repo          UserRepository, ComplaintRepository (row lock), ComplaintEventRepository
├── entity        User, Complaint, ComplaintEvent, Role, Category, Priority, ComplaintStatus (knows its legal next steps), EventType
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `complaints` (customer and assignee FKs, status, resolution, rating) and `complaint_events` (the timeline).

## Tests

```bash
cd backend
./mvnw test        # 23 tests on in-memory H2 (no MySQL needed): filing, visibility, resolution notes, status flow, timeline, assignment, feedback, queue order, statistics, roles, parallel updates
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Complaints.postman_collection.json
```

It runs 53 requests (84 assertions). It registers a fresh customer and creates uniquely titled complaints each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- No attachments, no email or SMS notifications to the customer.
- A closed complaint is final; a customer with a new problem files a new complaint.
- Filtering, search and the queue order are done in memory (Java Streams), which is fine for one company's complaints but would need paging at scale.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
