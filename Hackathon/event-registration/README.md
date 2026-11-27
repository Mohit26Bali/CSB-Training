# Event Registration and Approval System

Admins publish internal events; employees browse upcoming ones, register, and join a waitlist when an event is full. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `event_registration` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8118)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5118)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5118>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `eventadmin` | `admin123` | EVENT_ADMIN |
| `rahul.deshpande` | `admin123` | EVENT_ADMIN |
| `aarav.sharma`, `diya.patel`, `neha.kapoor`, … (26 employees) | `employee123` | EMPLOYEE |

## Features

- **Employee:** browse upcoming published events (search by title, venue or description, filter by category) with live seat counts; register in one click; join the waitlist of a full event and see the position in the queue; cancel until the event starts; a list of all own registrations.
- **Event admin:** create events as drafts, edit, publish, cancel (which releases every registration); see the participants of each event (confirmed first, then the waitlist in order); a dashboard of seats filled and waitlist sizes per event.
- Search and filter, participant-count dashboard and waitlist (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Past (already started) events and drafts or cancelled events do not accept registration | `400` |
| An employee cannot register twice for the same event (confirmed or on the waitlist) | `409` |
| Capacity is never exceeded; a full event is refused unless the employee explicitly joins the waitlist | `409`, the message offers the waitlist |
| When a confirmed registration is cancelled, or the capacity is raised, waitlisted people are confirmed automatically, oldest first | automatic |
| Capacity cannot be lowered below the number already confirmed | `400` field error |
| A new event must start in the future, end after it starts, use a known category and have a capacity between 1 and 5000 | `400` with a field error each |
| Started or cancelled events cannot be edited; started events cannot be cancelled; only drafts can be published | `400` / `409` |
| Only the owner can cancel a registration, and only once | `403` / `409` |
| Only admins manage events; only employees register | `403` |

**Concurrency:** registering takes a row lock on the event (under `READ_COMMITTED`), so when many employees go for the last seat exactly one wins and the rest get a clear `409`; a unique (event, employee) constraint settles double clicks; cancelling and capacity changes use the same lock, so the waitlist never over-fills. The tests check all three with parallel threads.

Cancelling keeps the registration row (status CANCELLED) and registering again re-activates it at the back of the queue, which keeps the unique constraint simple.
Each event response carries the caller's own status (`myStatus`, `myRegistrationId`) so the UI shows Register, Join waitlist or Cancel.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/events?search=&category= | any | Upcoming published events with seat counts and own status |
| GET | /api/events/categories | any | Event categories |
| POST | /api/registrations | EMPLOYEE | Register `{eventId, joinWaitlist?}` |
| GET | /api/registrations/my | EMPLOYEE | Own registrations with waitlist position |
| DELETE | /api/registrations/{id} | EMPLOYEE | Cancel own registration (promotes the waitlist) |
| GET | /api/admin/events?status= | EVENT_ADMIN | All events |
| POST | /api/admin/events | EVENT_ADMIN | Create a draft `{title, category, venue, description, startsAt, endsAt, capacity}` |
| PUT | /api/admin/events/{id} | EVENT_ADMIN | Edit an event |
| PUT | /api/admin/events/{id}/publish | EVENT_ADMIN | Publish a draft |
| PUT | /api/admin/events/{id}/cancel | EVENT_ADMIN | Cancel an event and its registrations |
| GET | /api/admin/events/{id}/participants | EVENT_ADMIN | Confirmed participants, then the waitlist |
| GET | /api/admin/dashboard | EVENT_ADMIN | Seats filled and waitlists per event |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5118) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/event
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, EventController (employee), AdminController
├── service       EventService (events, dashboard), RegistrationService (capacity, waitlist, locking), AuthService, LoginAttemptService
├── repo          UserRepository, EventRepository (row lock), RegistrationRepository
├── entity        User, Event, Registration, Role, EventStatus, RegistrationStatus
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `events` and `registrations` (event and user FKs, **UNIQUE (event, user)**, status, registration time).

## Tests

```bash
cd backend
./mvnw test        # 27 tests on in-memory H2 (no MySQL needed): registration rules, capacity, waitlist promotion, admin rules, roles, parallel registrations
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Event.postman_collection.json
```

It runs 55 requests (92 assertions). It registers a fresh employee and creates a uniquely named event each run, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- Registrations are not approved individually: they are confirmed automatically while seats last (the waitlist covers the rest).
- There are no email notifications when a waitlisted person is promoted.
- Searching and filtering is done in memory (Java Streams), which is fine for one company's events but would need paging at scale.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
