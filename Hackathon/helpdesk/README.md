# IT Help Desk Ticket Management System

Employees raise tickets for technical problems; support agents work a queue ordered by SLA urgency, assign and resolve them. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `help_desk` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8114)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5114)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5114>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `support` | `agent123` | SUPPORT_AGENT |
| `sanjay.rao`, `fatima.sheikh` | `agent123` | SUPPORT_AGENT |
| `aarav.sharma` (a ticket in every state) | `employee123` | EMPLOYEE |
| `diya.patel`, `rohan.mehta`, … (30 employees) | `employee123` | EMPLOYEE |

## Features

- **Employee:** raise a ticket (title, details, category, priority; the form shows the fix-within hours for each priority); see own tickets with filter by status and search by title or reference (HD-1042); open a ticket to follow progress on a OPEN, IN PROGRESS, RESOLVED, CLOSED tracker, read the resolution and reply in the conversation.
- **Support agent:** a queue ordered by urgency (tickets closest to, or past, their SLA deadline first) with filters for status, priority and assignee (anyone, me, unassigned, a named agent) and search; start work in one click; assign and reassign; move a ticket along the status flow; reply to the employee or leave internal notes only agents see; a dashboard with unassigned, mine, past SLA, at risk, average time to resolve, and breakdowns by priority and category.
- Priority filtering, ticket search and an SLA indicator (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Title, description, category and priority are mandatory; unknown categories and priorities are refused | `400` with a field error each |
| Only agents change the assignment or the status; only employees raise tickets and only on their own behalf | `403` |
| The flow is OPEN, IN_PROGRESS, RESOLVED, CLOSED, one step at a time; a resolved ticket may go back to IN_PROGRESS if the fix did not hold | `409`, the message lists what is allowed |
| Closed tickets cannot be reopened, reassigned or commented on | `409` |
| Resolving needs a note describing the fix, which the employee sees as the resolution | `400` field error |
| Starting work takes the ticket for the agent if nobody owns it; work in progress cannot be left without an owner; only agents can be assignees | automatic / `400` |
| An employee sees only their own tickets and never the internal notes | `403` / filtered |
| SLA hours by priority: critical 4, high 8, medium 24, low 72. State: on track, at risk (under a quarter of the time left), breached; once resolved: met or missed | computed |

**Concurrency:** every status, assignment and comment change locks the ticket row (under `READ_COMMITTED`), so when several agents click Start or two agents resolve the same ticket, exactly one wins and the others get a clear `409`. The tests check both with parallel threads.

The reference (HD-1042) is the ticket id plus 1000, so it never needs a separate counter.
One page, `/tickets/{id}`, serves both roles: agents get the assignment and status controls on it, employees do not.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| POST | /api/tickets | EMPLOYEE | Raise `{title, description, category, priority}` |
| GET | /api/tickets/my?status=&search= | EMPLOYEE | Own tickets |
| GET | /api/tickets/meta | any | Categories and priorities with their SLA hours |
| GET | /api/tickets/{id} | owner / agents | A ticket with its conversation (no internal notes for employees) |
| POST | /api/tickets/{id}/comments | owner / agents | Add `{body, internal?}` (internal: agents only) |
| GET | /api/agent/tickets?status=&priority=&assignee=&search= | SUPPORT_AGENT | The queue |
| PUT | /api/agent/tickets/{id}/assign | SUPPORT_AGENT | Assign `{assignee}` (empty = unassigned) |
| PUT | /api/agent/tickets/{id}/status | SUPPORT_AGENT | Move `{status, note?}` |
| GET | /api/agent/agents | SUPPORT_AGENT | Agents with their active load |
| GET | /api/agent/dashboard | SUPPORT_AGENT | Counts, SLA problems and breakdowns |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5114) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/helpdesk
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, TicketController (everyone), AgentController
├── service       TicketService (tickets, queue, assignment, status flow, comments, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository, TicketRepository (row lock), TicketCommentRepository
├── entity        User, Ticket, TicketComment, Role, Category, Priority (knows its SLA hours), TicketStatus (knows its legal next steps)
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `tickets` (requester and assignee FKs, status, priority, SLA due time, resolution) and `ticket_comments` (internal-note flag).

## Tests

```bash
cd backend
./mvnw test        # 25 tests on in-memory H2 (no MySQL needed): creation, visibility, comments, status flow, assignment, queue order, SLA states, dashboard, roles, parallel updates
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/HelpDesk.postman_collection.json
```

It runs 55 requests (88 assertions). It registers a fresh employee and creates uniquely titled tickets each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- SLA hours run on the clock, not on working hours.
- No attachments or email notifications.
- Filtering, search and the queue order are done in memory (Java Streams), which is fine for one company's tickets but would need paging at scale.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
